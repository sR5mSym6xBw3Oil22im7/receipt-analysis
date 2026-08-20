# reFront

GitHub Pagesで配信できる静的Frontend。

1. `config.js` のRender Backend URLを変更する。
2. GitHub Pagesへ `index.html`, `styles.css`, `config.js`, `app.js` を公開する。
3. Web画面でGemini APIキーを入力してからレシート画像を解析する。

Smoke test:
```bash
node --test test/smoke.test.mjs
```

## Gemini APIキー
Gemini APIキーは `index.html` のpassword入力欄で必須入力する。この入力値だけをBackendへ `geminiApiKey` として送信し、Backend環境変数へフォールバックしない。

Geminiの利用上限またはキー拒否エラーを受け取った場合はAPIキー欄へフォーカスし、次のキーへ入れ替えて同じ画像を再送できる。入力値はlocalStorage/sessionStorage/PostgreSQLへ保存しない。
