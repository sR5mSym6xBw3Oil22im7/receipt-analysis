# Receipt Analysis

レシート画像をGemini APIで解析し、管理者が解析結果をPostgreSQLへ保存・閲覧・削除するWebアプリです。公開デモはFrontend内のサンプルデータだけで動作し、Backend、Gemini API、データベースへ接続しません。

## 構成

- `reFront/`: 公開トップ画面とAPIキー不要のデモ。GitHub Pagesで公開します。
- `reBack/`: Spring Boot APIと同一オリジンの管理画面。Renderで公開します。
- `doc/`: 要件、設計、運用、テストに関するHTML文書。

## 認証とURL

Backendは環境変数で指定された管理者アカウントを使い、BCryptパスワードハッシュとサーバーセッションCookieで認証します。ログイン後の管理画面は `https://<backend-host>/admin/login.html` から利用します。ログイン情報をHTMLやJavaScriptへ埋め込みません。

公開URLは `GET /api/health`、`/admin/login.html`、および静的な公開フロントエンドです。`/api/receipts/**` は管理者セッションを必須とし、未認証はHTTP 401、認証済みで権限不足はHTTP 403になります。状態変更にはCSRFトークンも必要です。ログイン後の一覧、詳細、解析、保存、削除はBackendと同じオリジンで動作します。

## Backendの環境変数

Renderでは `ADMIN_USERNAME` と `ADMIN_PASSWORD_HASH` をSecret/Environment Variablesに設定してください。ハッシュはBCrypt形式にし、実パスワードを環境変数以外のファイル、文書、ログへ記載しないでください。ローカル用の項目名は `reBack/.env.example` を参照してください。RenderではTLS用Cookieを有効にするため `SESSION_COOKIE_SECURE=true` を設定します。認証設定がないとBackendは起動しません。

BCryptハッシュはApache `htpasswd` の対話モードなど、パスワードをコマンド引数やシェル履歴へ書かない方法で生成してください。例: `htpasswd -nBC 12 admin-user` はパスワードを対話入力し、`admin-user:<bcrypt-hash>` を出力します。コロン以降のハッシュだけを `ADMIN_PASSWORD_HASH` に設定します。Render Blueprintの `sync: false` 項目は初回作成時にDashboardで設定します。

## ローカル起動

Java 21、Maven、PostgreSQLが必要です。`reBack/.env.example` の値をローカル環境に設定し、ローカル用Cookie設定を利用します。

```bash
cd reBack
mvn spring-boot:run
```

Backendの管理画面は `http://localhost:8081/admin/login.html`、ヘルスチェックは `http://localhost:8081/api/health` です。DB設定と管理者認証情報を用意してください。

公開Frontendの確認:

```bash
python3 -m http.server 5051 --directory .
```

`http://localhost:5051/reFront/` を開くと公開トップとBackend非接続のデモを確認できます。管理画面リンクはローカルBackendへ接続します。

## テスト

```bash
cd reBack
mvn test
```

```bash
cd reFront
node --test test/smoke.test.mjs
```

## 主なAPI

| Method | Path | 認証 |
| --- | --- | --- |
| GET | `/api/health` | 不要 |
| GET | `/api/auth/csrf` | 不要。CSRFトークン発行 |
| POST | `/api/auth/login` | 不要。CSRFトークン必須 |
| POST | `/api/auth/logout` | 管理者セッションとCSRFトークン必須 |
| GET | `/api/receipts` | 管理者セッション必須 |
| GET | `/api/receipts/{tableName}` | 管理者セッション必須 |
| POST | `/api/receipts/analyze` | 管理者セッションとCSRFトークン必須 |
| POST | `/api/receipts/save` | 管理者セッションとCSRFトークン必須 |
| DELETE | `/api/receipts/{tableName}` | 管理者セッションとCSRFトークン必須 |
