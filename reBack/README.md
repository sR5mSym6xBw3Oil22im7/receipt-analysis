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
`POST /api/receipts/check-duplicate` と `POST /api/receipts/save` は、`receipt_<uuid32>` 形式に完全一致する実レシートテーブルだけを重複確認対象にする。`receipt_analysis_drafts` や `receipt_uniqueness_registry` など `receipt_` で始まる管理テーブルは対象外とする。重複確認では一覧表示用のCOUNT/MAX集計を行わず、保存APIで既存データと一致した場合は409 `DUPLICATE_RECEIPT` を返して新しいテーブルをCREATEしない。JDBCクエリは20秒でタイムアウトし、DB障害時は503 `DATABASE_ERROR` のJSONを返す。
