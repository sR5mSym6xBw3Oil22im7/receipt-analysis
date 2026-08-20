const API_BASE_URL = window.APP_CONFIG?.API_BASE_URL ?? "http://localhost:8080";
const listStatus = document.getElementById("list-status");
const receiptList = document.getElementById("receipt-list");
const detailPanel = document.getElementById("detail-panel");
const detailMeta = document.getElementById("detail-meta");
const detailBubble = document.getElementById("detail-bubble");
const closeDetail = document.getElementById("close-detail");

function formatDate(value) {
  return value ? new Date(value).toLocaleString("ja-JP") : "日時不明";
}

function showDetail(detail) {
  detailMeta.textContent = `${detail.tableName} / ${detail.lineCount}行 / ${formatDate(detail.createdAt)}`;
  detailBubble.replaceChildren();
  for (const line of detail.lines ?? []) {
    const lineElement = document.createElement("p");
    lineElement.className = "receipt-line";
    lineElement.textContent = `${line.lineNo}. ${line.text}`;
    detailBubble.append(lineElement);
  }
  detailPanel.classList.remove("hidden");
  detailPanel.scrollIntoView({ behavior: "smooth", block: "start" });
}

async function openDetail(tableName) {
  try {
    const response = await fetch(`${API_BASE_URL}/api/receipts/${encodeURIComponent(tableName)}`);
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || `HTTP ${response.status}`);
    showDetail(body);
  } catch (error) {
    listStatus.textContent = `エラー: ${error.message}`;
  }
}

function renderList(receipts) {
  receiptList.replaceChildren();
  if (!receipts.length) {
    listStatus.textContent = "保存済みのレシートはありません。";
    return;
  }
  listStatus.textContent = `${receipts.length}件のレシートがあります。`;
  for (const receipt of receipts) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "receipt-list-item";
    const title = document.createElement("strong");
    title.textContent = receipt.tableName;
    const meta = document.createElement("span");
    meta.textContent = `${receipt.lineCount}行 / ${formatDate(receipt.createdAt)}`;
    button.append(title, meta);
    button.addEventListener("click", () => openDetail(receipt.tableName));
    receiptList.append(button);
  }
}

async function loadReceipts() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/receipts`);
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || `HTTP ${response.status}`);
    renderList(body);
  } catch (error) {
    listStatus.textContent = `エラー: ${error.message}`;
  }
}

closeDetail.addEventListener("click", () => detailPanel.classList.add("hidden"));
loadReceipts();
