async function adminFetch(input, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers || {});
  const apiBaseUrl = window.APP_CONFIG?.API_BASE_URL ?? window.location.origin;
  const requestUrl = new URL(input, apiBaseUrl).toString();
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const response = await fetch(`${apiBaseUrl}/api/auth/csrf`, { credentials: "same-origin", cache: "no-store" });
    if (!response.ok) throw new Error(`CSRF token request failed (HTTP ${response.status})`);
    const { token } = await response.json();
    headers.set("X-XSRF-TOKEN", token);
  }
  return fetch(requestUrl, { ...options, method, headers, credentials: "same-origin" });
}
