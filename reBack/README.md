# Backend

Java 21、Spring Boot 4、Spring Security、Spring JDBC、PostgreSQLを使用します。Render Web Serviceとして動作し、管理画面のファイルを `/admin/` から同一オリジンで配信します。

## 必須設定

| 環境変数 | 用途 |
| --- | --- |
| `ADMIN_USERNAME` | 管理者ユーザーID |
| `ADMIN_PASSWORD_HASH` | 管理者パスワードのBCryptハッシュ |
| `SESSION_COOKIE_SECURE` | 本番は `true`、ローカルHTTPは `false` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL接続設定 |
| `PORT` | Webポート。既定値は8081 |

管理者設定に既定値はありません。Apache `htpasswd -nBC 12 admin-user` などの対話モードでBCryptハッシュを作り、出力の `admin-user:` より後ろだけを `ADMIN_PASSWORD_HASH` に設定します。Render Dashboardで認証情報を設定し、パスワードそのものをリポジトリ、ログ、文書に保存しないでください。ローカル環境変数の項目例は `.env.example` にあります。

## 認証とAPI

管理者は `ADMIN_USERNAME` とBCryptハッシュで照合され、認証状態はセッションCookieに保存されます。`GET /api/auth/csrf` がCSRFトークンを発行し、ログイン・ログアウトと他の状態変更要求は `X-XSRF-TOKEN` ヘッダーを必要とします。ログアウト後はセッションが無効になります。

`GET /api/health` とログイン画面、CSRF取得、ログイン以外は保護されます。`/api/receipts/**` はADMINロール必須で、未認証は401、権限不足またはCSRF不備は403です。拒否されたリクエストはControllerへ到達しません。CORSを認証機構として使用せず、公開デモの処理はFrontendのみで完結します。

管理画面URL: `https://<backend-host>/admin/login.html`

## ローカル起動

PostgreSQLと管理者用環境変数を用意し、次を実行します。

```bash
mvn spring-boot:run
```

ローカルプロファイルではHTTP用にCookieのSecure属性を無効化します。管理画面は `http://localhost:8081/admin/login.html`、ヘルスチェックは `http://localhost:8081/api/health` です。

## テスト

```bash
mvn test
```

## Render

`render.yml` がWeb ServiceとPostgreSQLを定義します。Blueprint適用後にDashboardで `ADMIN_USERNAME` と `ADMIN_PASSWORD_HASH` を設定し、`SESSION_COOKIE_SECURE=true` を確認してください。RenderヘルスチェックURLは `/api/health` です。
