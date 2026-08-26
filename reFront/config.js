const isLocalFrontend = window.location.protocol === "file:"
  || window.location.hostname === ""
  || window.location.hostname === "localhost"
  || window.location.hostname === "127.0.0.1";

window.APP_CONFIG = {
  // Keep the local workflow pointed at the local Spring Boot server.
  API_BASE_URL: isLocalFrontend
    ? "http://localhost:8081"
    : "https://receipt-reback.onrender.com"
};
