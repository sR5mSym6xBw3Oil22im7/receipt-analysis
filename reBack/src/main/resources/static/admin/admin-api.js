async function adminFetch(input, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers || {});
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const response = await fetch("/api/auth/csrf", { credentials: "same-origin", cache: "no-store" });
    if (!response.ok) throw new Error(`CSRF token request failed (HTTP ${response.status})`);
    const { token } = await response.json();
    headers.set("X-XSRF-TOKEN", token);
  }
  return fetch(input, { ...options, method, headers, credentials: "same-origin" });
}

document.addEventListener("click", async (event) => {
  if (!event.target.closest("[data-logout]")) return;
  const response = await adminFetch("/api/auth/logout", { method: "POST" });
  if (response.ok) window.location.assign("/admin/login.html");
});
