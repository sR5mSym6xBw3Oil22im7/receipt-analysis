const isLocalFrontend = window.location.protocol === "file:"
  || window.location.hostname === ""
  || window.location.hostname === "localhost"
  || window.location.hostname === "127.0.0.1";

window.APP_CONFIG = {
  API_BASE_URL: window.location.origin,
  ADMIN_BASE_URL: isLocalFrontend ? "http://localhost:8081/admin" : "https://receipt-analysis-b8po.onrender.com/admin",
  PUBLIC_BASE_URL: "https://sr5msym6xbw3oil22im7.github.io/"
};
