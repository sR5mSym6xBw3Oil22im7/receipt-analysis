const API_BASE_URL = window.APP_CONFIG?.API_BASE_URL ?? "http://localhost:8081";
const listStatus = document.getElementById("list-status");
const receiptList = document.getElementById("receipt-list");
const detailPanel = document.getElementById("detail-panel");
const detailMeta = document.getElementById("detail-meta");
const detailBubble = document.getElementById("detail-bubble");
const closeDetail = document.getElementById("close-detail");
let selectedTableName = null;

function formatDate(value) {
  return value ? new Date(value).toLocaleString("ja-JP") : "日時不明";
}

function showDetail(detail) {
  selectedTableName = detail.tableName;
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

async function deleteReceipt(tableName) {
  if (!window.confirm("このレシートデータを削除しますか？")) return;
  try {
    const response = await fetch(`${API_BASE_URL}/api/receipts/${encodeURIComponent(tableName)}`, {
      method: "DELETE"
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || `HTTP ${response.status}`);
    if (selectedTableName === tableName) {
      selectedTableName = null;
      detailPanel.classList.add("hidden");
    }
    listStatus.textContent = "レシートを削除しました。";
    await loadReceipts();
  } catch (error) {
    listStatus.textContent = `エラー: ${error.message}`;
  }
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
    const row = document.createElement("div");
    row.className = "receipt-list-row";
    const title = document.createElement("strong");
    title.textContent = receipt.tableName;
    const meta = document.createElement("span");
    meta.textContent = `${receipt.lineCount}行 / ${formatDate(receipt.createdAt)}`;
    const referenceButton = document.createElement("button");
    referenceButton.type = "button";
    referenceButton.className = "secondary-button";
    referenceButton.textContent = "参照";
    referenceButton.addEventListener("click", () => openDetail(receipt.tableName));
    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.className = "danger-button";
    deleteButton.textContent = "削除";
    deleteButton.addEventListener("click", () => deleteReceipt(receipt.tableName));
    const info = document.createElement("div");
    info.className = "receipt-list-info";
    info.append(title, meta);
    const actions = document.createElement("div");
    actions.className = "button-row";
    actions.append(referenceButton, deleteButton);
    row.append(info, actions);
    receiptList.append(row);
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
