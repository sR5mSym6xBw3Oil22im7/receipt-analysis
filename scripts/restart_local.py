#!/usr/bin/env python3
"""Stop and restart the local PostgreSQL, backend, and HTTPS frontend."""

from __future__ import annotations

import os
import signal
import socket
import ssl
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen


ROOT = Path(__file__).resolve().parent.parent
BACKEND_DIR = ROOT / "reBack"
BACKEND_JAR = BACKEND_DIR / "target" / "receipt-backend-0.0.1-SNAPSHOT.jar"
ENV_FILE = BACKEND_DIR / ".env"
DB_CONTAINER = "receipt-analysis-postgres-local"
BACKEND_PORT = 8081
FRONTEND_PORT = 5051
OLD_FRONTEND_PORT = 5500
RUNTIME_DIR = Path(tempfile.gettempdir()) / "receipt-analysis-local"
LOG_DIR = RUNTIME_DIR / "logs"


def run(command: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, check=check, text=True, capture_output=True)


def listener_pids(port: int) -> list[int]:
    result = run(["lsof", "-ti", f"TCP:{port}", "-sTCP:LISTEN"], check=False)
    return [int(line) for line in result.stdout.splitlines() if line.strip().isdigit()]


def process_command(pid: int) -> str:
    try:
        return Path(f"/proc/{pid}/cmdline").read_bytes().replace(b"\0", b" ").decode()
    except (FileNotFoundError, PermissionError, ProcessLookupError):
        return ""


def preflight_port(port: int, allowed_markers: tuple[str, ...]) -> None:
    for pid in listener_pids(port):
        command = process_command(pid)
        if not all(marker in command for marker in allowed_markers):
            raise RuntimeError(f"port {port} is occupied by an unexpected process: PID {pid}")


def stop_port_processes(port: int, allowed_markers: tuple[str, ...], label: str) -> None:
    pids = listener_pids(port)
    for pid in pids:
        command = process_command(pid)
        if not all(marker in command for marker in allowed_markers):
            raise RuntimeError(f"port {port} is occupied by an unexpected process: PID {pid}")
        print(f"Stopping {label} (PID {pid})")
        os.kill(pid, signal.SIGTERM)
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline and listener_pids(port):
        time.sleep(0.25)
    if listener_pids(port):
        raise RuntimeError(f"{label} did not stop on port {port}")


def load_env() -> dict[str, str]:
    if not ENV_FILE.is_file():
        raise RuntimeError(f"Missing {ENV_FILE}; configure backend settings first")
    values: dict[str, str] = {}
    for line in ENV_FILE.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    required = ("ADMIN_USERNAME", "ADMIN_PASSWORD_HASH")
    missing = [key for key in required if not values.get(key)]
    if missing:
        raise RuntimeError(f"Missing required settings in {ENV_FILE}: {', '.join(missing)}")
    return values


def wait_for_postgres() -> None:
    for _ in range(30):
        result = run(["docker", "exec", DB_CONTAINER, "pg_isready"], check=False)
        if result.returncode == 0:
            return
        time.sleep(1)
    raise RuntimeError("PostgreSQL did not become ready within 30 seconds")


def start_process(command: list[str], cwd: Path, env: dict[str, str], log_path: Path) -> int:
    with log_path.open("ab") as log_file:
        process = subprocess.Popen(
            command,
            cwd=cwd,
            env=env,
            stdin=subprocess.DEVNULL,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    return process.pid


def wait_for_http(url: str, *, allow_self_signed: bool = False) -> None:
    context = ssl._create_unverified_context() if allow_self_signed else None
    for _ in range(30):
        try:
            with urlopen(url, timeout=2, context=context) as response:
                if response.status == 200:
                    return
        except (OSError, URLError):
            time.sleep(1)
    raise RuntimeError(f"Service did not return HTTP 200: {url}")


def main() -> int:
    try:
        if not BACKEND_JAR.is_file():
            raise RuntimeError(f"Backend jar not found: {BACKEND_JAR}; build the backend first")
        env_values = load_env()
        env = os.environ.copy()
        env.update(env_values)
        env["SPRING_PROFILES_ACTIVE"] = "local"

        # Stop only processes known to belong to this app. Never kill an unrelated
        # process just because it happens to use one of the configured ports.
        preflight_port(BACKEND_PORT, ("receipt-backend-0.0.1-SNAPSHOT.jar",))
        preflight_port(FRONTEND_PORT, ("scripts/start_frontend.py",))
        preflight_port(OLD_FRONTEND_PORT, ("http.server 5500", str(ROOT / "reFront")))
        stop_port_processes(BACKEND_PORT, ("receipt-backend-0.0.1-SNAPSHOT.jar",), "backend")
        stop_port_processes(FRONTEND_PORT, ("scripts/start_frontend.py",), "HTTPS frontend")
        stop_port_processes(
            OLD_FRONTEND_PORT,
            ("http.server 5500", str(ROOT / "reFront")),
            "obsolete HTTP frontend",
        )

        print("Restarting PostgreSQL")
        run(["docker", "restart", DB_CONTAINER])
        wait_for_postgres()

        LOG_DIR.mkdir(parents=True, exist_ok=True)
        backend_pid = start_process(
            ["java", "-jar", str(BACKEND_JAR), "--spring.profiles.active=local"],
            BACKEND_DIR,
            env,
            LOG_DIR / "backend.log",
        )
        frontend_pid = start_process(
            [sys.executable, str(ROOT / "scripts" / "start_frontend.py")],
            ROOT,
            env,
            LOG_DIR / "frontend.log",
        )
        (RUNTIME_DIR / "backend.pid").write_text(f"{backend_pid}\n")
        (RUNTIME_DIR / "frontend.pid").write_text(f"{frontend_pid}\n")

        wait_for_http(f"http://localhost:{BACKEND_PORT}/api/health")
        wait_for_http(f"https://localhost:{FRONTEND_PORT}/", allow_self_signed=True)
        if listener_pids(OLD_FRONTEND_PORT):
            raise RuntimeError(f"Unexpected HTTP frontend is listening on port {OLD_FRONTEND_PORT}")
        print("PostgreSQL: ready")
        print(f"Backend: http://localhost:{BACKEND_PORT}/api/health (HTTP 200)")
        print(f"Frontend: https://localhost:{FRONTEND_PORT}/ (HTTP 200)")
        print(f"Logs: {LOG_DIR}")
        return 0
    except (OSError, subprocess.CalledProcessError, RuntimeError) as error:
        print(f"Restart failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
