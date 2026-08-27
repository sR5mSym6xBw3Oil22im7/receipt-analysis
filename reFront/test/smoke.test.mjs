import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const html = await readFile(new URL("../upload.html", import.meta.url), "utf8");
const index = await readFile(new URL("../index.html", import.meta.url), "utf8");
const select = await readFile(new URL("../select.html", import.meta.url), "utf8");
const js = await readFile(new URL("../app.js", import.meta.url), "utf8");
const selectJs = await readFile(new URL("../select.js", import.meta.url), "utf8");
const config = await readFile(new URL("../config.js", import.meta.url), "utf8");
const indexJs = await readFile(new URL("../index.js", import.meta.url), "utf8");
const accessGuard = await readFile(new URL("../access-guard.js", import.meta.url), "utf8");

test("frontend accepts JPEG, PNG, and ZIP", () => {
  assert.match(html, /accept="image\/jpeg,image\/png,\.zip,application\/zip"/);
  assert.equal((html.match(/name="file"/g) ?? []).length, 1);
  assert.equal((html.match(/ファイルを選択/g) ?? []).length, 1);
  assert.match(html, /selected-file-name/);
  assert.match(html + js, /error-message/);
});

test("ZIP analysis ignores directories but rejects other files and empty archives", () => {
  assert.match(js, /function isZipDirectory\(name, versionMadeBy, externalAttributes\)/);
  assert.match(js, /if \(isZipDirectory\(name, versionMadeBy, externalAttributes\)\) continue;/);
  assert.match(js, /if \(!\/\\\.\(jpe\?g\|png\)\$\/i\.test\(name\)\)/);
  assert.match(js, /ZIPファイルにレシート画像がありません/);
});

test("analysis reports per-image progress and does not wait forever", () => {
  assert.match(js, /ANALYZE_REQUEST_TIMEOUT_MS = 210000/);
  assert.match(js, /ANALYSIS_WAIT_MESSAGE_INTERVAL_MS = 15000/);
  assert.match(js, /setInterval\(\(\) =>/);
  assert.match(js, /clearInterval\(waitMessageTimer\)/);
  assert.match(js, /ブラウザを閉じずにお待ちください/);
  assert.match(js, /signal: controller\.signal/);
  assert.match(js, /画像\$\{fileNumber\}\/\$\{selectedFiles\.length\}を解析中です/);
  assert.match(js, /BACKEND_TIMEOUT/);
});


test("upload page cache-busts app.js so old upload code is not reused", () => {
  assert.match(html, /<script src="\.\/app\.js\?v=20260827-zip-directories-timeout-210-wait15"><\/script>/);
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
  assert.match(js, /\/api\/receipts\/save/);
  assert.match(js, /saveButton\.addEventListener\("click"/);
  assert.match(js, /枚の解析が完了しました/);
  assert.match(js, /PostgreSQLへ保存しました/);
  assert.doesNotMatch(js, /未保存 \/ /);
  assert.doesNotMatch(js, /PostgreSQL保存済み/);
});

test("save button stores each analyzed receipt", () => {
  assert.match(js, /continue;/);
  assert.match(js, /saveReceipt\(receipt\.lines, receipt\.sha256, receipt\.structuredData\)/);
  assert.match(js, /body: JSON\.stringify\(\{ lines, sha256, structuredData \}\)/);
  assert.match(js, /if \(busy\) return;/);
  assert.match(js, /receipt\.stored = true/);
  assert.match(js, /SAVE_REQUEST_TIMEOUT_MS/);
  assert.match(js, /AbortController/);
  assert.match(js, /バックエンドサーバーから応答がありません/);
  assert.doesNotMatch(js, /PostgreSQLへ追加しました/);
  assert.match(js, /resetUploadPagePreservingApiKey/);
  assert.match(js, /apiKeyInput\.value = geminiApiKey/);
});

test("frontend has a configurable Render backend URL", () => {
  assert.match(config, /API_BASE_URL/);
  assert.match(config, /onrender\.com/);
  assert.doesNotMatch(config, /YOUR-RENDER-SERVICE/);
  assert.match(config, /receipt-analysis-b8po\.onrender\.com/);
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
  assert.match(index, /id="select-link"[^>]*href="\.\/select\.html"/);
  assert.match(index, /<script src="\.\/index\.js"><\/script>/);
});

test("index hides the select link when PostgreSQL has no receipts", () => {
  assert.match(indexJs, /STARTUP_CHECK_TIMEOUT_MS = 120000/);
  assert.match(indexJs, /signal: controller\.signal/);
  assert.match(indexJs, /fetch\(`\$\{API_BASE_URL\}\/api\/receipts`/);
  assert.match(indexJs, /selectLink\.classList\.add\("hidden"\)/);
  assert.match(indexJs, /Array\.isArray\(receipts\)/);
  assert.match(indexJs, /selectLink\.classList\.toggle\("hidden", receipts\.length === 0\)/);
});

test("non-index pages redirect direct access to the index page", () => {
  assert.match(select, /<script src="\.\/access-guard\.js"><\/script>/);
  assert.match(html, /<script src="\.\/access-guard\.js"><\/script>/);
  assert.match(accessGuard, /document\.referrer/);
  assert.match(accessGuard, /referrerUrl\.pathname === indexUrl\.pathname/);
  assert.match(accessGuard, /window\.location\.replace\(indexUrl\.href\)/);
});

test("select page loads receipt list and detail bubble", () => {
  assert.match(select, /id="receipt-list"/);
  assert.match(select, /id="detail-bubble"/);
  assert.match(select, /id="close-detail"/);
  assert.match(select, /id="delete-selected"/);
  assert.match(select, /id="delete-selected"[^>]*hidden/);
  assert.match(selectJs, /checkbox/);
  assert.match(selectJs, /\/api\/receipts/);
  assert.match(selectJs, /detailBubble/);
  assert.match(selectJs, /method:\s*"DELETE"/);
  assert.match(selectJs, /selectedTableNames/);
  assert.match(selectJs, /receiptCount/);
  assert.match(select, /チェックしたレシートを削除/);
});
