window.APP_CONFIG = {
  API_BASE_URL: window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1"
    ? "http://localhost:8081"
    : "https://YOUR-RENDER-SERVICE.onrender.com"
};
