import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const html = await readFile(new URL("../upload.html", import.meta.url), "utf8");
const index = await readFile(new URL("../index.html", import.meta.url), "utf8");
const select = await readFile(new URL("../select.html", import.meta.url), "utf8");
const js = await readFile(new URL("../app.js", import.meta.url), "utf8");
const selectJs = await readFile(new URL("../select.js", import.meta.url), "utf8");
const config = await readFile(new URL("../config.js", import.meta.url), "utf8");

test("frontend accepts JPEG and PNG", () => {
  assert.match(html, /accept="image\/jpeg,image\/png"/);
  assert.equal((html.match(/name="file"/g) ?? []).length, 5);
  assert.equal((html.match(/ファイルを選択/g) ?? []).length, 5);
  assert.match(html, /selected-file-name/);
  assert.match(js, /同じファイルが選択されました/);
  assert.match(js, /lastModified/);
  assert.match(html + js, /error-message/);
  assert.match(js, /nameElement\.textContent = "同じファイルが選択されました/);
});


test("upload page cache-busts app.js so old auto-save code is not reused", () => {
  assert.match(html, /<script src="\.\/app\.js\?v=20260821-4"><\/script>/);
});

test("frontend posts multipart data to the receipt endpoint", () => {
  assert.match(js, /FormData/);
  assert.match(js, /\/api\/receipts/);
  assert.match(js, /method:\s*"POST"/);
});

test("frontend keeps each selected receipt result separate", () => {
  assert.match(html, /id="receipt-results"/);
  assert.match(js, /renderReceiptResults/);
  assert.match(js, /fileNumber/);
  assert.match(js, /saveReceipt/);
});

test("analysis and PostgreSQL save are separate operations", () => {
  assert.match(html, /id="submit-button"[^>]*>解析<\/button>/);
  assert.match(html, /id="save-button"[^>]*>PostgreSQLへ保存<\/button>/);
  assert.match(js, /\/api\/receipts\/analyze/);
  assert.match(js, /\/api\/receipts\/check-duplicate/);
  assert.match(js, /\/api\/receipts\/save/);
  assert.match(js, /saveButton\.addEventListener\("click"/);
  assert.match(js, /枚の解析が完了しました/);
  assert.match(js, /PostgreSQLへ保存しました/);
  assert.doesNotMatch(js, /未保存 \/ /);
  assert.doesNotMatch(js, /PostgreSQL保存済み/);
});

test("save button skips duplicates and continues with later receipts", () => {
  assert.match(js, /checkDuplicate\(receipt\.lines\)/);
  assert.match(js, /if \(duplicate\)/);
  assert.match(js, /receipt\.duplicate = true/);
  assert.match(js, /continue;/);
  assert.match(js, /saveReceipt\(receipt\.lines\)/);
  assert.match(js, /receipt\.stored = true/);
  assert.match(js, /DUPLICATE_RECEIPT/);
  assert.match(js, /SAVE_REQUEST_TIMEOUT_MS/);
  assert.match(js, /AbortController/);
  assert.match(js, /バックエンドサーバーから応答がありません/);
  assert.match(js, /既存データと重複するため/);
  assert.match(js, /重複チェック中です/);
  assert.match(js, /保存しませんでした/);
  assert.doesNotMatch(js, /PostgreSQLへ追加しました/);
});

test("frontend has a configurable Render backend URL", () => {
  assert.match(config, /API_BASE_URL/);
  assert.match(config, /onrender\.com/);
});

test("frontend requires a masked Gemini API key", () => {
  assert.match(html, /id="gemini-api-key"/);
  assert.match(html, /type="password"/);
  assert.match(html, /autocomplete="off"/);
  assert.match(html, /id="gemini-api-key"[\s\S]*?required/);
});

test("frontend always sends the entered Gemini API key", () => {
  assert.match(js, /if \(!geminiApiKey\)/);
  assert.match(js, /formData\.append\("geminiApiKey", geminiApiKey\)/);
});

test("frontend focuses API key input when Gemini quota is exhausted", () => {
  assert.match(js, /GEMINI_QUOTA_EXCEEDED/);
  assert.match(js, /apiKeyInput\.focus\(\)/);
  assert.match(js, /次のAPIキーへ入れ替え/);
});

test("frontend does not persist the Gemini API key in browser storage", () => {
  assert.doesNotMatch(js, /localStorage/);
  assert.doesNotMatch(js, /sessionStorage/);
});

test("index links to upload and select pages", () => {
  assert.match(index, /href="\.\/upload\.html"/);
  assert.match(index, /href="\.\/select\.html"/);
});

test("select page loads receipt list and detail bubble", () => {
  assert.match(select, /id="receipt-list"/);
  assert.match(select, /id="detail-bubble"/);
  assert.match(select, /id="close-detail"/);
  assert.doesNotMatch(select, /id="delete-detail"/);
  assert.match(selectJs, /\/api\/receipts/);
  assert.match(selectJs, /detailBubble/);
  assert.match(selectJs, /method:\s*"DELETE"/);
  assert.match(selectJs, /参照/);
  assert.match(selectJs, /削除/);
});
