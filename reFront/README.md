# reFront

GitHub Pagesで配信できる静的Frontend。

1. `config.js` のRender Backend URLを変更する。
2. GitHub Pagesへ `index.html`, `styles.css`, `config.js`, `app.js` を公開する。
3. `upload.html` でGemini APIキーとJPEG／PNG／ZIPのいずれか1ファイルを選び、「解析」を押す。ZIPの場合は中のJPEG／PNG画像をすべて解析する。ここでは文字抽出と画面表示だけを行い、PostgreSQLへは保存しない。
4. 解析結果を確認して「PostgreSQLへ保存」を押す。解析結果を保存する。

Smoke test:
```bash
node --test test/smoke.test.mjs
```

## Gemini APIキー
Gemini APIキーは `upload.html` のpassword入力欄で必須入力する。この入力値だけをBackendへ `geminiApiKey` として送信し、Backend環境変数へフォールバックしない。

Geminiの利用上限またはキー拒否エラーを受け取った場合はAPIキー欄へフォーカスし、次のキーへ入れ替えて同じ画像を再送できる。入力値はlocalStorage/sessionStorage/PostgreSQLへ保存しない。

## 保存
「PostgreSQLへ保存」押下時にFrontendが `/api/receipts/check-duplicate` で各解析結果を確認する。既存データと重複する画像は警告表示して保存せず、未登録画像だけ `/api/receipts/save` へ送信する。複数画像の途中で重複が見つかっても処理は継続する。保存APIが409 `DUPLICATE_RECEIPT` を返した場合も、その画像だけを重複として扱い後続画像の保存を続ける。保存系APIは30秒でタイムアウトし、Backendから応答がない場合も画面を無期限に待機させずエラーメッセージを表示する。


## 解析時保存防止
- 「解析」は `POST /api/receipts/analyze` のみを使用し、PostgreSQLへのINSERT/CREATE TABLEを行わない。
- 旧 `POST /api/receipts` の解析＋保存エンドポイントは廃止し、解析操作から保存処理へ到達するBackend経路を削除した。
- PostgreSQLへの追加は「PostgreSQLへ保存」押下時の `POST /api/receipts/save` のみに限定する。
- 保存時は `POST /api/receipts/check-duplicate` で各解析結果を確認し、重複分だけ除外して未登録分を保存する。
