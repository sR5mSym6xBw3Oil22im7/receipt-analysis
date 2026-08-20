const form = document.getElementById("receipt-form");
const fileInput = document.getElementById("receipt-file");
const apiKeyInput = document.getElementById("gemini-api-key");
const clearApiKeyButton = document.getElementById("clear-api-key");
const submitButton = document.getElementById("submit-button");
const saveButton = document.getElementById("save-button");
const statusElement = document.getElementById("status");
const resultCard = document.getElementById("result-card");
const tableNameElement = document.getElementById("table-name");
const lineCountElement = document.getElementById("line-count");
const receiptTextElement = document.getElementById("receipt-text");
let analyzedLines = null;

const API_BASE_URL = window.APP_CONFIG?.API_BASE_URL ?? "http://localhost:8080";
const API_KEY_RETRY_CODES = new Set([
  "GEMINI_QUOTA_EXCEEDED",
  "GEMINI_API_KEY_REJECTED",
  "GEMINI_API_KEY_MISSING",
  "INVALID_GEMINI_API_KEY"
]);

function setBusy(busy) {
  submitButton.disabled = busy;
  submitButton.textContent = busy ? "解析中..." : "解析";
}

function setSaveBusy(busy) {
  saveButton.disabled = busy;
  saveButton.textContent = busy ? "保存中..." : "PostgreSQL保存";
}

function showApiKeyRetry(errorCode, fallbackMessage) {
  apiKeyInput.focus();
  apiKeyInput.select();

  if (errorCode === "GEMINI_QUOTA_EXCEEDED") {
    statusElement.textContent =
      "Gemini APIの利用上限に達しました。次のAPIキーへ入れ替え、同じ画像のまま再度実行してください。";
    return;
  }

  if (errorCode === "GEMINI_API_KEY_REJECTED") {
    statusElement.textContent =
      "Gemini APIキーが利用できません。別のAPIキーへ入れ替え、同じ画像のまま再度実行してください。";
    return;
  }

  if (errorCode === "GEMINI_API_KEY_MISSING" || errorCode === "INVALID_GEMINI_API_KEY") {
    statusElement.textContent =
      "Gemini APIキーを確認して再度実行してください。";
    return;
  }

  statusElement.textContent = `エラー: ${fallbackMessage}`;
}

async function analyzeReceipt(formData) {
  const analyzeResponse = await fetch(`${API_BASE_URL}/api/receipts/analyze`, {
    method: "POST",
    body: formData
  });

  if (analyzeResponse.status !== 404) {
    return { response: analyzeResponse, stored: false };
  }

  // Older deployed Backends expose only POST /api/receipts, which analyzes
  // and stores the receipt in one request.
  const legacyResponse = await fetch(`${API_BASE_URL}/api/receipts`, {
    method: "POST",
    body: formData
  });
  return { response: legacyResponse, stored: true };
}

clearApiKeyButton.addEventListener("click", () => {
  apiKeyInput.value = "";
  apiKeyInput.focus();
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  resultCard.classList.add("hidden");
  saveButton.classList.add("hidden");
  analyzedLines = null;
  statusElement.textContent = "";

  const geminiApiKey = apiKeyInput.value.trim();
  if (!geminiApiKey) {
    statusElement.textContent = "Gemini APIキーを入力してください。";
    apiKeyInput.focus();
    return;
  }

  const file = fileInput.files?.[0];
  if (!file) {
    statusElement.textContent = "画像ファイルを選択してください。";
    return;
  }

  const formData = new FormData();
  formData.append("file", file);
  formData.append("geminiApiKey", geminiApiKey);

  setBusy(true);
  statusElement.textContent = "レシートを解析しています。";

  try {
    const { response, stored } = await analyzeReceipt(formData);

    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(body.message || `HTTP ${response.status}`);
      error.code = body.code || "";
      error.httpStatus = response.status;
      throw error;
    }

    analyzedLines = Array.isArray(body.lines) ? body.lines : [];
    tableNameElement.textContent = stored ? (body.tableName || "保存済み") : "未保存";
    lineCountElement.textContent = String(analyzedLines.length);
    receiptTextElement.textContent = analyzedLines.join("\n");
    if (stored) {
      analyzedLines = null;
      saveButton.classList.add("hidden");
      statusElement.textContent = "解析とPostgreSQL保存が完了しました。";
    } else {
      saveButton.classList.remove("hidden");
      statusElement.textContent = "解析が完了しました。内容を確認してPostgreSQL保存を実行してください。";
    }
    resultCard.classList.remove("hidden");
  } catch (error) {
    if (API_KEY_RETRY_CODES.has(error.code)) {
      showApiKeyRetry(error.code, error.message);
    } else {
      statusElement.textContent = `エラー: ${error.message}`;
    }
  } finally {
    setBusy(false);
  }
});

saveButton.addEventListener("click", async () => {
  if (!analyzedLines) return;

  setSaveBusy(true);
  statusElement.textContent = "PostgreSQLへ保存しています。";

  try {
    const response = await fetch(`${API_BASE_URL}/api/receipts/save`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ lines: analyzedLines })
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || `HTTP ${response.status}`);

    tableNameElement.textContent = body.tableName;
    lineCountElement.textContent = String(body.lineCount);
    statusElement.textContent = "PostgreSQLへの保存が完了しました。";
    saveButton.classList.add("hidden");
    analyzedLines = null;
  } catch (error) {
    statusElement.textContent = `エラー: ${error.message}`;
  } finally {
    setSaveBusy(false);
  }
});
