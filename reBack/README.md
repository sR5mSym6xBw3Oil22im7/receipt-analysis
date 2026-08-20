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
`POST /api/receipts` のmultipart項目 `geminiApiKey` は必須。
Frontendの `reFront/index.html` で利用者が入力した値を、そのリクエストのGemini呼び出しだけに使用する。
Backend既定キーやGemini APIキー環境変数へのフォールバックは行わない。

キーはDBへ保存せず、レスポンスにも含めず、ログにも出力しない。Geminiが429/RESOURCE_EXHAUSTEDを返した場合はHTTP 429 / `GEMINI_QUOTA_EXCEEDED`、401/403なら `GEMINI_API_KEY_REJECTED` を返す。

## Render Blueprint
`render.yml` はこの `reBack` ディレクトリ直下に配置する。Render DashboardのBlueprint Pathには `reBack/render.yml` を指定する。
Gemini APIキー関連のenvVarsは定義しない。

## Test
```bash
mvn test
```
