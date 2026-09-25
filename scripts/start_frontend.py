#!/usr/bin/env python3
"""Serve the local frontend over HTTPS on https://localhost:5051."""

from __future__ import annotations

import os
import ssl
import subprocess
import tempfile
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


HOST = "127.0.0.1"
PORT = 5051
PROJECT_ROOT = Path(__file__).resolve().parent.parent
FRONTEND_DIRECTORY = PROJECT_ROOT / "reFront"
RUNTIME_DIRECTORY = Path(tempfile.gettempdir()) / "receipt-analysis-local" / "tls"
CERTIFICATE_FILE = RUNTIME_DIRECTORY / "localhost-cert.pem"
PRIVATE_KEY_FILE = RUNTIME_DIRECTORY / "localhost-key.pem"


def certificate_is_current() -> bool:
    if not CERTIFICATE_FILE.is_file() or not PRIVATE_KEY_FILE.is_file():
        return False

    result = subprocess.run(
        ["openssl", "x509", "-checkend", "86400", "-noout", "-in", str(CERTIFICATE_FILE)],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return result.returncode == 0


def ensure_certificate() -> None:
    RUNTIME_DIRECTORY.mkdir(parents=True, exist_ok=True)
    if certificate_is_current():
        return

    subprocess.run(
        [
            "openssl",
            "req",
            "-x509",
            "-newkey",
            "rsa:2048",
            "-sha256",
            "-nodes",
            "-days",
            "30",
            "-keyout",
            str(PRIVATE_KEY_FILE),
            "-out",
            str(CERTIFICATE_FILE),
            "-subj",
            "/CN=localhost",
            "-addext",
            "subjectAltName=DNS:localhost,IP:127.0.0.1",
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    os.chmod(PRIVATE_KEY_FILE, 0o600)


def main() -> None:
    ensure_certificate()

    handler = partial(SimpleHTTPRequestHandler, directory=str(FRONTEND_DIRECTORY))
    server = ThreadingHTTPServer((HOST, PORT), handler)
    tls_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls_context.load_cert_chain(CERTIFICATE_FILE, PRIVATE_KEY_FILE)
    server.socket = tls_context.wrap_socket(server.socket, server_side=True)

    print(f"Frontend: https://localhost:{PORT}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()

