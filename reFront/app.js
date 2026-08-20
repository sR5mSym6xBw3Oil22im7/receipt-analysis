const form = document.getElementById("receipt-form");
const fileInputs = [...document.querySelectorAll('input[name="file"]')];
const apiKeyInput = document.getElementById("gemini-api-key");
const clearApiKeyButton = document.getElementById("clear-api-key");
const submitButton = document.getElementById("submit-button");
const saveButton = document.getElementById("save-button");
const saveStatusElement = document.getElementById("save-status");
const statusElement = document.getElementById("status");
const resultCard = document.getElementById("result-card");
const receiptResultsElement = document.getElementById("receipt-results");
let analyzedReceipts = [];

fileInputs.forEach((input, index) => {
  input.addEventListener("change", () => {
    const selectedFile = input.files?.[0];
    const nameElement = document.getElementById(`receipt-file-name-${index + 1}`);
    if (!selectedFile) {
      nameElement.classList.remove("error-message");
      nameElement.textContent = "未選択";
      return;
    }

    const isDuplicate = fileInputs.some((otherInput, otherIndex) => {
      if (otherIndex === index) return false;
      const otherFile = otherInput.files?.[0];
      return otherFile
        && otherFile.name === selectedFile.name
        && otherFile.size === selectedFile.size
        && otherFile.lastModified === selectedFile.lastModified
        && otherFile.type === selectedFile.type;
    });

    if (isDuplicate) {
      input.value = "";
      nameElement.classList.add("error-message");
      nameElement.textContent = "同じファイルが選択されました。別のファイルを選択してください。";
      return;
    }

    nameElement.classList.remove("error-message");
    nameElement.textContent = selectedFile.name;
  });
});

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
  return { response: analyzeResponse, stored: false };
}

async function checkDuplicate(lines) {
  const response = await fetch(`${API_BASE_URL}/api/receipts/check-duplicate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ lines })
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || `HTTP ${response.status}`);
  return body.duplicate === true;
}

async function saveReceipt(lines) {
  const response = await fetch(`${API_BASE_URL}/api/receipts/save`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ lines })
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(body.message || `HTTP ${response.status}`);
    error.code = body.code || "";
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
    heading.textContent = `レシート画像${index + 1}`;
    const meta = document.createElement("p");
    meta.className = "help-text";
    meta.textContent = receipt.duplicate
      ? `登録済みのため保存しませんでした / ${receipt.lines.length}行`
      : `${receipt.tableName || "未保存"} / ${receipt.lines.length}行`;
    if (receipt.duplicate) meta.classList.add("error-message");
    const text = document.createElement("pre");
    text.textContent = receipt.lines.join("\n");
    result.append(heading, meta, text);
    receiptResultsElement.append(result);
  });
}

clearApiKeyButton.addEventListener("click", () => {
  apiKeyInput.value = "";
  apiKeyInput.focus();
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  resultCard.classList.add("hidden");
  saveButton.classList.add("hidden");
  saveStatusElement.classList.add("hidden");
  analyzedReceipts = [];
  statusElement.classList.remove("error-message");
  statusElement.textContent = "";

  const geminiApiKey = apiKeyInput.value.trim();
  if (!geminiApiKey) {
    statusElement.textContent = "Gemini APIキーを入力してください。";
    apiKeyInput.focus();
    return;
  }

  const files = fileInputs.map((input) => input.files?.[0]).filter(Boolean);
  if (!files.length) {
    statusElement.textContent = "画像ファイルを1枚以上選択してください。";
    return;
  }

  setBusy(true);
  statusElement.textContent = `${files.length}枚のレシートを解析しています。`;

  try {
    const analyzedResults = [];
    for (const [index, file] of files.entries()) {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("geminiApiKey", geminiApiKey);
      const result = await analyzeReceipt(formData);
      const body = await result.response.json().catch(() => ({}));
      if (!result.response.ok) {
        const error = new Error(body.message || `HTTP ${result.response.status}`);
        error.code = body.code || "";
        error.httpStatus = result.response.status;
        error.fileNumber = index + 1;
        throw error;
      }
      const lines = Array.isArray(body.lines) ? body.lines : [];
      analyzedResults.push({
        lines,
        tableName: body.tableName || "",
        stored: result.stored,
        duplicate: await checkDuplicate(lines)
      });
    }

    analyzedReceipts = analyzedResults;
    renderReceiptResults(analyzedReceipts);
    const duplicateNumbers = analyzedReceipts
      .map((receipt, index) => receipt.duplicate ? index + 1 : null)
      .filter(Boolean);
    const pendingReceipts = analyzedReceipts.filter((receipt) => !receipt.stored && !receipt.duplicate);
    if (duplicateNumbers.length) {
      statusElement.textContent = `画像${duplicateNumbers.join("、")}は同じレシートデータがすでに登録済みのため、登録対象から外しました。`;
    }
    if (pendingReceipts.length) {
      saveButton.classList.remove("hidden");
      if (!duplicateNumbers.length) {
        statusElement.textContent = `${files.length}枚の解析が完了しました。内容を確認してPostgreSQL保存を実行してください。`;
      }
    } else if (!duplicateNumbers.length) {
      statusElement.textContent = `${files.length}枚の解析が完了しました。`;
    }
    resultCard.classList.remove("hidden");
  } catch (error) {
    if (API_KEY_RETRY_CODES.has(error.code)) {
      showApiKeyRetry(error.code, error.message);
    } else {
      const fileMessage = error.fileNumber ? `（画像${error.fileNumber}）` : "";
      statusElement.textContent = `エラー${fileMessage}: ${error.message}`;
    }
  } finally {
    setBusy(false);
  }
});

saveButton.addEventListener("click", async () => {
  const receiptsToSave = analyzedReceipts.filter((receipt) => !receipt.stored && !receipt.duplicate);
  if (!receiptsToSave.length) return;

  setSaveBusy(true);
  statusElement.textContent = "PostgreSQLへ保存しています。";
  try {
    for (const receipt of receiptsToSave) {
      const saved = await saveReceipt(receipt.lines);
      receipt.tableName = saved.tableName;
      receipt.stored = true;
    }
    renderReceiptResults(analyzedReceipts);
    saveButton.classList.add("hidden");
    saveStatusElement.textContent = `${receiptsToSave.length}件のレシートをPostgreSQLへ保存しました。`;
    saveStatusElement.classList.remove("hidden");
    analyzedReceipts = [];
  } catch (error) {
    statusElement.textContent = `エラー: ${error.message}`;
  } finally {
    setSaveBusy(false);
  }
});
