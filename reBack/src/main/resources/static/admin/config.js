const isLocalFrontend = window.location.protocol === "file:"
  || window.location.hostname === ""
  || window.location.hostname === "localhost"
  || window.location.hostname === "127.0.0.1";

const backendBaseUrl = isLocalFrontend
  ? "http://localhost:8081"
  : "https://receipt-analysis-b8po.onrender.com";

window.APP_CONFIG = {
  API_BASE_URL: backendBaseUrl,
  ADMIN_BASE_URL: `${backendBaseUrl}/admin`,
  SELECT_URL: `${backendBaseUrl}/admin/select.html`,
  PUBLIC_BASE_URL: "https://sr5msym6xbw3oil22im7.github.io/receipt-analysis/reFront/index.html"
};
