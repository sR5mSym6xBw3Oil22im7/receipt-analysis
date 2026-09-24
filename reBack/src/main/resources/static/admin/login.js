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
    const result = await response.json();
    window.location.assign(result.redirect || "/admin/select.html");
  } catch {
    status.textContent = "Backendに接続できませんでした。";
  }
});
