# reBack

Java 21 + Spring Boot + PostgreSQL + Gemini APIで構成したBackend。

## Required environment variables
Gemini APIキー用の環境変数は定義しない。Backend運用に必要な環境変数はDB接続/CORS用のみ。

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `APP_FRONTEND_ORIGIN`

Geminiモデルは `src/main/resources/application.yml` で `gemini-3.5-flash-lite` に固定する。

## Gemini APIキー
`POST /api/receipts/analyze` のmultipart項目 `geminiApiKey` は必須。
Frontendの `reFront/upload.html` で利用者が入力した値を、そのリクエストのGemini呼び出しだけに使用する。
Backend既定キーやGemini APIキー環境変数へのフォールバックは行わない。

キーはDBへ保存せず、レスポンスにも含めず、ログにも出力しない。Geminiが429/RESOURCE_EXHAUSTEDを返した場合はHTTP 429 / `GEMINI_QUOTA_EXCEEDED`、401/403なら `GEMINI_API_KEY_REJECTED` を返す。

## Render Blueprint
`render.yml` はこの `reBack` ディレクトリ直下に配置する。Render DashboardのBlueprint Pathには `reBack/render.yml` を指定する。
Gemini APIキー関連のenvVarsは定義しない。

## Test
```bash
mvn test
```

## 保存

`POST /api/receipts/analyze` は画像バイト列のSHA-256を計算し、`receipt_image_hash_registry` に未登録状態で確保してレスポンスの `sha256` として返す。同じSHA-256が解析済みでも、まだ保存されていなければ再解析を許可する。保存済みの場合だけ409 `DUPLICATE_RECEIPT_IMAGE` を返す。`POST /api/receipts/save` は解析レスポンスの `sha256` を受け取り、ハッシュ行に保存先テーブルを紐付ける。

`POST /api/receipts/save` は画像SHA-256を重複確認キーとして使用し、既存ハッシュの場合は409 `DUPLICATE_RECEIPT_IMAGE` を返して新しいテーブルをCREATEしない。JDBCクエリは20秒でタイムアウトし、DB障害時は503 `DATABASE_ERROR` のJSONを返す。

`DELETE /api/receipts/{tableName}` はレシートテーブルと紐付く画像SHA-256を同一トランザクションで削除するため、削除後は同じ画像を再登録できる。
