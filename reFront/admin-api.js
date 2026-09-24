const ADMIN_TOKEN_STORAGE_KEY = "receipt-analysis-admin-token";
let currentAdminToken = null;
const tokenFragment = new URLSearchParams(window.location.hash.slice(1));
const incomingAdminToken = tokenFragment.get("admin_token");
if (incomingAdminToken) {
  currentAdminToken = incomingAdminToken;
  try {
    window.sessionStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, incomingAdminToken);
    tokenFragment.delete("admin_token");
    const remainingHash = tokenFragment.toString();
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}${remainingHash ? `#${remainingHash}` : ""}`);
  } catch {
    // Keep the token in memory and the URL fragment for this tab if storage is unavailable.
  }
}

function getAdminToken() {
  if (currentAdminToken) return currentAdminToken;
  try {
    currentAdminToken = window.sessionStorage.getItem(ADMIN_TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
  return currentAdminToken;
}

async function adminFetch(input, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers || {});
  const apiBaseUrl = window.APP_CONFIG?.API_BASE_URL ?? window.location.origin;
  const requestUrl = new URL(input, apiBaseUrl).toString();
  const token = getAdminToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  } else if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const response = await fetch(`${apiBaseUrl}/api/auth/csrf`, { credentials: "include", cache: "no-store" });
    if (!response.ok) throw new Error(`CSRF token request failed (HTTP ${response.status})`);
    const { token } = await response.json();
    headers.set("X-XSRF-TOKEN", token);
  }
  return fetch(requestUrl, { ...options, method, headers, credentials: token ? "omit" : "include" });
}
