const selectLink = document.getElementById("select-link");
const API_BASE_URL = window.APP_CONFIG?.API_BASE_URL ?? "http://localhost:8081";

selectLink.classList.add("hidden");

async function updateSelectLinkVisibility() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/receipts`, { cache: "no-store" });
    if (!response.ok) {
      selectLink.classList.remove("hidden");
      return;
    }

    const receipts = await response.json();
    if (Array.isArray(receipts)) {
      selectLink.classList.toggle("hidden", receipts.length === 0);
    } else {
      selectLink.classList.remove("hidden");
    }
  } catch {
    selectLink.classList.remove("hidden");
  }
}

updateSelectLinkVisibility();
