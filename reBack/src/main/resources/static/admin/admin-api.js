async function adminFetch(input, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers || {});
  const apiBaseUrl = window.APP_CONFIG?.API_BASE_URL ?? window.location.origin;
  const requestUrl = new URL(input, apiBaseUrl).toString();
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const response = await fetch(`${apiBaseUrl}/api/auth/csrf`, { credentials: "include", cache: "no-store" });
    if (!response.ok) throw new Error(`CSRF token request failed (HTTP ${response.status})`);
    const { token } = await response.json();
    headers.set("X-XSRF-TOKEN", token);
  }
  return fetch(requestUrl, { ...options, method, headers, credentials: "include" });
}

document.addEventListener("click", async (event) => {
  if (!event.target.closest("[data-logout]")) return;
  const response = await adminFetch("/api/auth/logout", { method: "POST" });
  if (response.ok) {
    const loginUrl = window.APP_CONFIG?.ADMIN_BASE_URL
      ? `${window.APP_CONFIG.ADMIN_BASE_URL}/login.html`
      : "/admin/login.html";
    window.location.assign(loginUrl);
  }
});
