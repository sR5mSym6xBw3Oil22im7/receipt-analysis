const API_BASE_URL = window.APP_CONFIG?.API_BASE_URL ?? "http://localhost:8081";
const listStatus = document.getElementById("list-status");
const receiptList = document.getElementById("receipt-list");
const detailPanel = document.getElementById("detail-panel");
const detailMeta = document.getElementById("detail-meta");
const detailBubble = document.getElementById("detail-bubble");
const closeDetail = document.getElementById("close-detail");
const deleteSelectedButton = document.getElementById("delete-selected");
const selectedTableNames = new Set();
let receiptCount = 0;

function updateDeleteSelectedButton() {
  deleteSelectedButton.classList.toggle("hidden", receiptCount === 0);
  deleteSelectedButton.disabled = selectedTableNames.size === 0;
}
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
  receiptCount = receipts.length;
  receiptList.replaceChildren();
  if (!receipts.length) {
    listStatus.textContent = "保存済みのレシートはありません。";
    updateDeleteSelectedButton();
    return;
  }
  listStatus.textContent = `${receipts.length}件のレシートがあります。`;
  for (const receipt of receipts) {
    const row = document.createElement("div");
    row.className = "receipt-list-row";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.setAttribute("aria-label", `${receipt.tableName}を選択`);
    checkbox.checked = selectedTableNames.has(receipt.tableName);
    checkbox.addEventListener("change", () => {
      if (checkbox.checked) {
        selectedTableNames.add(receipt.tableName);
      } else {
        selectedTableNames.delete(receipt.tableName);
      }
      updateDeleteSelectedButton();
    });
    const title = document.createElement("strong");
    title.textContent = receipt.tableName;
    const meta = document.createElement("span");
    meta.textContent = `${receipt.lineCount}行 / ${formatDate(receipt.createdAt)}`;
    const referenceButton = document.createElement("button");
    referenceButton.type = "button";
    referenceButton.className = "secondary-button";
    referenceButton.textContent = "参照";
    referenceButton.addEventListener("click", () => openDetail(receipt.tableName));
    const info = document.createElement("div");
    info.className = "receipt-list-info";
    info.append(title, meta);
    const actions = document.createElement("div");
    actions.className = "button-row";
    actions.append(referenceButton);
    row.append(checkbox, info, actions);
    receiptList.append(row);
  }
  updateDeleteSelectedButton();
}

async function deleteSelectedReceipts() {
  if (!selectedTableNames.size) return;
  if (!window.confirm(`${selectedTableNames.size}件のレシートデータを削除しますか？`)) return;

  deleteSelectedButton.disabled = true;
  const tableNames = [...selectedTableNames];
  try {
    for (const tableName of tableNames) {
      const response = await fetch(`${API_BASE_URL}/api/receipts/${encodeURIComponent(tableName)}`, {
        method: "DELETE"
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || `HTTP ${response.status}`);
      selectedTableNames.delete(tableName);
    }
    detailPanel.classList.add("hidden");
    listStatus.textContent = `${tableNames.length}件のレシートを削除しました。`;
    await loadReceipts();
  } catch (error) {
    listStatus.textContent = `削除エラー: ${error.message}`;
    updateDeleteSelectedButton();
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
deleteSelectedButton.addEventListener("click", deleteSelectedReceipts);
updateDeleteSelectedButton();
loadReceipts();
