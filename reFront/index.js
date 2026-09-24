const adminLink = document.getElementById("select-link");
if (adminLink && window.APP_CONFIG?.ADMIN_BASE_URL) {
  adminLink.href = `${window.APP_CONFIG.ADMIN_BASE_URL}/login.html`;
}
