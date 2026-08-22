const form = document.getElementById("receipt-form");
const fileInputs = [...document.querySelectorAll('input[name="file"]')];
const apiKeyInput = document.getElementById("gemini-api-key");
const clearApiKeyButton = document.getElementById("clear-api-key");
const submitButton = document.getElementById("submit-button");
const saveButton = document.getElementById("save-button");
const statusElement = document.getElementById("status");
const resultCard = document.getElementById("result-card");
const receiptResultsElement = document.getElementById("receipt-results");
let analyzedReceipts = [];
let analysisReady = false;
let busy = false;

const API_BASE_URL = window.APP_CONFIG?.API_BASE_URL ?? "http://localhost:8081";
const SAVE_REQUEST_TIMEOUT_MS = 30000;
const API_KEY_RETRY_CODES = new Set([
  "GEMINI_QUOTA_EXCEEDED",
  "GEMINI_API_KEY_REJECTED",
  "GEMINI_API_KEY_MISSING",
  "INVALID_GEMINI_API_KEY"
]);

function hasPendingReceipts() {
  return analysisReady && analyzedReceipts.some((receipt) => !receipt.stored);
}

function updateSaveButton() {
  const hasPending = hasPendingReceipts();
  saveButton.classList.toggle("hidden", !hasPending);
  saveButton.disabled = busy || !hasPending;
}

function invalidateAnalysis() {
  analyzedReceipts = [];
  analysisReady = false;
  receiptResultsElement.replaceChildren();
  resultCard.classList.add("hidden");
  statusElement.textContent = "";
  updateSaveButton();
}

function resetUploadPagePreservingApiKey() {
  const geminiApiKey = apiKeyInput.value;

  fileInputs.forEach((input, index) => {
    input.value = "";
    const nameElement = document.getElementById(`receipt-file-name-${index + 1}`);
    nameElement.classList.remove("error-message");
    nameElement.textContent = "未選択";
  });

  analyzedReceipts = [];
  analysisReady = false;
  receiptResultsElement.replaceChildren();
  resultCard.classList.add("hidden");
  statusElement.classList.remove("error-message");
  statusElement.textContent = "";
  apiKeyInput.value = geminiApiKey;
  busy = false;
  submitButton.disabled = false;
  submitButton.textContent = "解析";
  saveButton.disabled = true;
  saveButton.classList.add("hidden");
  saveButton.textContent = "PostgreSQLへ保存";
}

fileInputs.forEach((input, index) => {
  input.addEventListener("change", () => {
    invalidateAnalysis();

    const selectedFile = input.files?.[0];
    const nameElement = document.getElementById(`receipt-file-name-${index + 1}`);
    if (!selectedFile) {
      nameElement.classList.remove("error-message");
      nameElement.textContent = "未選択";
      return;
    }

    nameElement.classList.remove("error-message");
    nameElement.textContent = selectedFile.name;
  });
});

function setBusy(mode) {
  busy = Boolean(mode);
  submitButton.disabled = busy;
  submitButton.textContent = mode === "analyze" ? "解析中..." : "解析";
  saveButton.textContent = mode === "save" ? "保存中..." : "PostgreSQLへ保存";
  updateSaveButton();
}

function showApiKeyRetry(errorCode, fallbackMessage) {
  apiKeyInput.focus();
  apiKeyInput.select();

  if (errorCode === "GEMINI_QUOTA_EXCEEDED") {
    statusElement.textContent =
      "Gemini APIの利用上限に達しました。次のAPIキーへ入れ替え、同じ画像のまま再度解析してください。";
    return;
  }

  if (errorCode === "GEMINI_API_KEY_REJECTED") {
    statusElement.textContent =
      "Gemini APIキーが利用できません。別のAPIキーへ入れ替え、同じ画像のまま再度解析してください。";
    return;
  }

  if (errorCode === "GEMINI_API_KEY_MISSING" || errorCode === "INVALID_GEMINI_API_KEY") {
    statusElement.textContent =
      "Gemini APIキーを確認して再度解析してください。";
    return;
  }

  statusElement.textContent = `エラー: ${fallbackMessage}`;
}

async function analyzeReceipt(formData) {
  return fetch(`${API_BASE_URL}/api/receipts/analyze`, {
    method: "POST",
    body: formData
  });
}

async function fetchSaveApi(url, options) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), SAVE_REQUEST_TIMEOUT_MS);

  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch (error) {
    if (error?.name === "AbortError") {
      const timeoutError = new Error(
        "バックエンドサーバーから応答がありません。通信状態を確認して、再度「PostgreSQLへ保存」を押してください。"
      );
      timeoutError.code = "BACKEND_TIMEOUT";
      throw timeoutError;
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }
}

async function readJsonResponse(response) {
  return response.json().catch(() => ({}));
}

async function saveReceipt(lines, sha256) {
  const response = await fetchSaveApi(`${API_BASE_URL}/api/receipts/save`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json; charset=UTF-8",
    },
    body: JSON.stringify({ lines, sha256 })
  });
  const body = await readJsonResponse(response);
  if (!response.ok) {
    const error = new Error(body.message || `HTTP ${response.status}`);
    error.code = body.code || "";
    error.httpStatus = response.status;
    throw error;
  }
  return body;
}

function renderReceiptResults(receipts) {
  receiptResultsElement.replaceChildren();
  receipts.forEach((receipt, index) => {
    const result = document.createElement("article");
    result.className = "receipt-result";
    const heading = document.createElement("h3");
    heading.textContent = `レシート画像${receipt.fileNumber ?? index + 1}`;
    const text = document.createElement("pre");
    text.textContent = receipt.lines.join("\n");

    result.append(heading);
    result.append(text);
    receiptResultsElement.append(result);
  });
}

function buildSaveCompletionMessage(receipts) {
  const storedNumbers = receipts
    .filter((receipt) => receipt.stored)
    .map((receipt) => receipt.fileNumber);

  if (storedNumbers.length) {
    return `画像${storedNumbers.join("、")}をPostgreSQLへ保存しました。`;
  }

  return "PostgreSQLへ保存する対象がありません。";
}

clearApiKeyButton.addEventListener("click", () => {
  apiKeyInput.value = "";
  apiKeyInput.focus();
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  resultCard.classList.add("hidden");
  analyzedReceipts = [];
  analysisReady = false;
  statusElement.classList.remove("error-message");
  statusElement.textContent = "";
  updateSaveButton();

  const geminiApiKey = apiKeyInput.value.trim();
  if (!geminiApiKey) {
    statusElement.textContent = "Gemini APIキーを入力してください。";
    apiKeyInput.focus();
    return;
  }

  const selectedFiles = fileInputs
    .map((input, index) => ({ file: input.files?.[0], fileNumber: index + 1 }))
    .filter((entry) => Boolean(entry.file));
  if (!selectedFiles.length) {
    statusElement.textContent = "画像ファイルを1枚以上選択してください。";
    return;
  }

  setBusy("analyze");
  statusElement.textContent = "";

  try {
    for (const { file, fileNumber } of selectedFiles) {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("geminiApiKey", geminiApiKey);

      const analyzeResponse = await analyzeReceipt(formData);
      const body = await analyzeResponse.json().catch(() => ({}));
      if (!analyzeResponse.ok) {
        const error = new Error(body.message || `HTTP ${analyzeResponse.status}`);
        error.code = body.code || "";
        error.httpStatus = analyzeResponse.status;
        error.fileNumber = fileNumber;
        throw error;
      }

      analyzedReceipts.push({
        fileNumber,
        lines: Array.isArray(body.lines) ? body.lines : [],
        sha256: body.sha256 || "",
        tableName: "",
        stored: false
      });
      renderReceiptResults(analyzedReceipts);
      resultCard.classList.remove("hidden");
    }

    analysisReady = true;
    statusElement.textContent = `${analyzedReceipts.length}枚の解析が完了しました。`;
  } catch (error) {
    analysisReady = false;
    if (API_KEY_RETRY_CODES.has(error.code)) {
      showApiKeyRetry(error.code, error.message);
    } else {
      const fileMessage = error.fileNumber ? `（画像${error.fileNumber}）` : "";
      statusElement.textContent = `解析エラー${fileMessage}: ${error.message}`;
    }

    if (analyzedReceipts.length) {
      renderReceiptResults(analyzedReceipts);
      resultCard.classList.remove("hidden");
    }
  } finally {
    setBusy(null);
  }
});

saveButton.addEventListener("click", async () => {
  if (busy) return;
  if (!hasPendingReceipts()) {
    statusElement.textContent = "PostgreSQLへ保存する未保存の解析結果がありません。先に解析してください。";
    updateSaveButton();
    return;
  }

  statusElement.classList.remove("error-message");
  setBusy("save");
  statusElement.textContent = "";

  let saveCompleted = false;
  try {
    for (const receipt of analyzedReceipts) {
      if (receipt.stored) continue;

      try {
        statusElement.textContent = `画像${receipt.fileNumber}をPostgreSQLへ保存中です。`;
        const saved = await saveReceipt(receipt.lines, receipt.sha256);
        receipt.tableName = saved.tableName || "";
        receipt.stored = true;
      } catch (error) {
        error.fileNumber = receipt.fileNumber;
        throw error;
      }

      renderReceiptResults(analyzedReceipts);
    }

    statusElement.textContent = buildSaveCompletionMessage(analyzedReceipts);
    saveCompleted = true;
  } catch (error) {
    const fileMessage = error.fileNumber ? `（画像${error.fileNumber}）` : "";
    statusElement.textContent = `保存エラー${fileMessage}: ${error.message}`;
  } finally {
    if (saveCompleted) {
      // 保存成功後は初期状態へ戻し、次の解析を開始できるようにする。
      resetUploadPagePreservingApiKey();
    } else {
      // 保存失敗時は解析結果と保存ボタンを残し、再試行できるようにする。
      setBusy(null);
    }
  }
});

updateSaveButton();
