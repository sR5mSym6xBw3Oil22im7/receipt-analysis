import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import { readFile } from "node:fs/promises";

class FakeClassList {
  constructor(...initial) {
    this.values = new Set(initial);
  }

  add(value) {
    this.values.add(value);
  }

  remove(value) {
    this.values.delete(value);
  }

  contains(value) {
    return this.values.has(value);
  }
}

class FakeElement {
  constructor(id = "") {
    this.id = id;
    this.files = [];
    this.value = "";
    this.textContent = "";
    this.disabled = false;
    this.children = [];
    this.listeners = new Map();
    this.classList = new FakeClassList();
  }

  addEventListener(type, handler) {
    this.listeners.set(type, handler);
  }

  replaceChildren(...children) {
    this.children = [...children];
  }

  append(...children) {
    this.children.push(...children);
  }

  focus() {
    this.focused = true;
  }

  select() {
    this.selected = true;
  }
}

class FakeFormData {
  constructor() {
    this.values = new Map();
  }

  append(name, value) {
    this.values.set(name, value);
  }
}

function response(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return body;
    }
  };
}

test("analysis only displays text; save skips duplicate files 2 and 3 and stores 1, 4 and 5", async () => {
  const source = await readFile(new URL("../app.js", import.meta.url), "utf8");
  const elements = new Map();
  const get = (id) => {
    if (!elements.has(id)) elements.set(id, new FakeElement(id));
    return elements.get(id);
  };

  const fileInputs = Array.from({ length: 5 }, (_, index) => {
    const input = get(`receipt-file-${index + 1}`);
    input.files = [{
      name: `receipt-${index + 1}.jpg`,
      size: 1000 + index,
      lastModified: 100 + index,
      type: "image/jpeg"
    }];
    get(`receipt-file-name-${index + 1}`);
    return input;
  });

  get("receipt-form");
  get("gemini-api-key").value = "test-api-key";
  get("clear-api-key");
  get("submit-button");
  get("save-button");
  get("status");
  get("result-card").classList.add("hidden");
  get("receipt-results");

  const fetchCalls = [];
  let analyzeCall = 0;
  let duplicateCheckCall = 0;
  let saveCall = 0;
  const fakeFetch = async (url, options) => {
    fetchCalls.push({ url, options });
    if (url.endsWith("/api/receipts/analyze")) {
      analyzeCall += 1;
      return response(200, { lines: [`STORE ${analyzeCall}`, `TOTAL ${analyzeCall}00`] });
    }
    if (url.endsWith("/api/receipts/check-duplicate")) {
      duplicateCheckCall += 1;
      return response(200, { duplicate: duplicateCheckCall === 2 || duplicateCheckCall === 3 });
    }
    if (url.endsWith("/api/receipts/save")) {
      saveCall += 1;
      return response(200, {
        tableName: `receipt_${String(saveCall).padStart(32, "0")}`,
        lineCount: 2
      });
    }
    throw new Error(`unexpected URL: ${url}`);
  };

  const document = {
    querySelectorAll(selector) {
      assert.equal(selector, 'input[name="file"]');
      return fileInputs;
    },
    getElementById(id) {
      return get(id);
    },
    createElement() {
      return new FakeElement();
    }
  };

  const context = vm.createContext({
    document,
    window: { APP_CONFIG: { API_BASE_URL: "https://example.test" } },
    fetch: fakeFetch,
    FormData: FakeFormData,
    AbortController,
    setTimeout,
    clearTimeout,
    console
  });
  vm.runInContext(source, context, { filename: "app.js" });

  const submitHandler = get("receipt-form").listeners.get("submit");
  assert.equal(typeof submitHandler, "function");
  await submitHandler({ preventDefault() {} });

  assert.equal(analyzeCall, 5);
  assert.equal(duplicateCheckCall, 0);
  assert.equal(saveCall, 0);
  assert.equal(fetchCalls.length, 5);
  assert.equal(get("status").textContent, "5枚の解析が完了しました。");
  assert.equal(get("receipt-results").children.length, 5);
  for (const result of get("receipt-results").children) {
    assert.equal(result.children.length, 2);
  }
  assert.equal(get("save-button").disabled, false);

  const saveHandler = get("save-button").listeners.get("click");
  assert.equal(typeof saveHandler, "function");
  await saveHandler();

  assert.equal(analyzeCall, 5);
  assert.equal(duplicateCheckCall, 5);
  assert.equal(saveCall, 3);
  assert.equal(fetchCalls.length, 13);
  assert.equal(
    get("status").textContent,
    "画像1、4、5をPostgreSQLへ保存しました。"
  );
  assert.match(get("receipt-results").children[1].children[1].textContent, /既存データと重複/);
  assert.match(get("receipt-results").children[2].children[1].textContent, /既存データと重複/);
  assert.equal(get("receipt-results").children[0].children.length, 2);
  assert.equal(get("receipt-results").children[3].children.length, 2);
  assert.equal(get("receipt-results").children[4].children.length, 2);
  assert.equal(get("save-button").disabled, true);
  assert.equal(get("result-card").classList.contains("hidden"), false);
});

test("save still treats a 409 after duplicate check as duplicate and continues", async () => {
  const source = await readFile(new URL("../app.js", import.meta.url), "utf8");
  assert.match(source, /checkDuplicate\(receipt\.lines\)/);
  assert.match(source, /error\.code === "DUPLICATE_RECEIPT" \|\| error\.httpStatus === 409/);
  assert.match(source, /continue;/);
});

test("save returns a duplicate warning when the receipt is already registered", async () => {
  const source = await readFile(new URL("../app.js", import.meta.url), "utf8");
  const elements = new Map();
  const get = (id) => {
    if (!elements.has(id)) elements.set(id, new FakeElement(id));
    return elements.get(id);
  };

  const fileInputs = Array.from({ length: 5 }, (_, index) => {
    const input = get(`receipt-file-${index + 1}`);
    if (index === 0) {
      input.files = [{
        name: "already-registered.jpg",
        size: 1000,
        lastModified: 100,
        type: "image/jpeg"
      }];
    }
    get(`receipt-file-name-${index + 1}`);
    return input;
  });

  get("receipt-form");
  get("gemini-api-key").value = "test-api-key";
  get("clear-api-key");
  get("submit-button");
  get("save-button");
  get("status");
  get("result-card").classList.add("hidden");
  get("receipt-results");

  let analyzeCall = 0;
  let duplicateCheckCall = 0;
  let saveCall = 0;
  const fakeFetch = async (url) => {
    if (url.endsWith("/api/receipts/analyze")) {
      analyzeCall += 1;
      return response(200, { lines: ["STORE A", "TOTAL 100"] });
    }
    if (url.endsWith("/api/receipts/check-duplicate")) {
      duplicateCheckCall += 1;
      return response(200, { duplicate: true });
    }
    if (url.endsWith("/api/receipts/save")) {
      saveCall += 1;
      return response(200, { tableName: "receipt_00000000000000000000000000000001" });
    }
    throw new Error(`unexpected URL: ${url}`);
  };

  const document = {
    querySelectorAll(selector) {
      assert.equal(selector, 'input[name="file"]');
      return fileInputs;
    },
    getElementById(id) {
      return get(id);
    },
    createElement() {
      return new FakeElement();
    }
  };

  const context = vm.createContext({
    document,
    window: { APP_CONFIG: { API_BASE_URL: "https://example.test" } },
    fetch: fakeFetch,
    FormData: FakeFormData,
    AbortController,
    setTimeout,
    clearTimeout,
    console
  });
  vm.runInContext(source, context, { filename: "app.js" });

  await get("receipt-form").listeners.get("submit")({ preventDefault() {} });
  await get("save-button").listeners.get("click")();

  assert.equal(analyzeCall, 1);
  assert.equal(duplicateCheckCall, 1);
  assert.equal(saveCall, 0);
  assert.equal(
    get("status").textContent,
    "警告: 画像1は既存データと重複するため、PostgreSQLへ保存しませんでした。"
  );
  assert.equal(get("save-button").disabled, true);
});
