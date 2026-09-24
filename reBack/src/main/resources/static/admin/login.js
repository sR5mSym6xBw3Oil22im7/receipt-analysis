const form = document.getElementById("login-form");
const status = document.getElementById("login-status");

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  status.textContent = "";
  try {
    const response = await adminFetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: form.elements.username.value, password: form.elements.password.value })
    });
    if (!response.ok) {
      status.textContent = response.status === 401 ? "ユーザーIDまたはパスワードが正しくありません。" : `ログインできませんでした (HTTP ${response.status})`;
      return;
    }
    window.location.assign(window.APP_CONFIG?.SELECT_URL
      ?? "https://sr5msym6xbw3oil22im7.github.io/receipt-analysis/reFront/select.html");
  } catch {
    status.textContent = "Backendに接続できませんでした。";
  }
});
