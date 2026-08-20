import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const html = await readFile(new URL("../update.html", import.meta.url), "utf8");
const index = await readFile(new URL("../index.html", import.meta.url), "utf8");
const select = await readFile(new URL("../select.html", import.meta.url), "utf8");
const js = await readFile(new URL("../app.js", import.meta.url), "utf8");
const selectJs = await readFile(new URL("../select.js", import.meta.url), "utf8");
const config = await readFile(new URL("../config.js", import.meta.url), "utf8");

test("frontend accepts JPEG and PNG", () => {
  assert.match(html, /accept="image\/jpeg,image\/png"/);
});

test("frontend posts multipart data to the receipt endpoint", () => {
  assert.match(js, /FormData/);
  assert.match(js, /\/api\/receipts/);
  assert.match(js, /method:\s*"POST"/);
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

test("index links to update and select pages", () => {
  assert.match(index, /href="\.\/update\.html"/);
  assert.match(index, /href="\.\/select\.html"/);
});

test("select page loads receipt list and detail bubble", () => {
  assert.match(select, /id="receipt-list"/);
  assert.match(select, /id="detail-bubble"/);
  assert.match(selectJs, /\/api\/receipts/);
  assert.match(selectJs, /detailBubble/);
});
