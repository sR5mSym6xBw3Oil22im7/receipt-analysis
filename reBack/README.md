# バックエンド

reBack/はレシート解析システムのREST APIです。Java 21、Spring Boot、Spring Security、Spring JDBC、PostgreSQLを使用します。

## ローカル起動

PostgreSQLを用意し、管理者IDとBCrypt形式のパスワードハッシュを設定します。

~~~sh
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD_HASH='<BCrypt形式のハッシュ>'
mvn spring-boot:run -Dspring-boot.run.profiles=local
~~~

DB接続はDB_HOST、DB_PORT、DB_NAME、DB_USER、DB_PASSWORDで設定します。既定値はlocalhost:5432/receipt_db、ユーザーpostgres、パスワードpostgresです。HTTP待受ポートの既定値は8081です。

## 主なAPI

- GET /api/health — 稼働確認（公開）
- GET /api/auth/csrf、POST /api/auth/login、GET /api/auth/session — CSRFと管理者セッション（公開）
- POST /api/auth/logout — ログアウト
- POST /api/receipts/analyze — multipartでfileとgeminiApiKeyを受け取る画像解析（管理者）
- POST /api/receipts/save — 解析結果の保存（管理者）
- GET /api/receipts、GET /api/receipts/{tableName} — 一覧・詳細（管理者）
- DELETE /api/receipts/{tableName} — 削除（管理者）

/api/receipts配下のAPIは、HTTPメソッドを問わずすべてROLE_ADMINが必要です。未ログインは401、管理者以外は403を返します。POSTとDELETEにはCSRFトークン（X-XSRF-TOKENヘッダー）も必要です。解析画面（/admin/upload.html）と保存済み一覧画面（/admin/select.html）も、ログインしていない場合はログイン画面へリダイレクトします。

解析画像はJPEG/PNGで最大5 MiBです。Gemini APIキーは解析要求で受け取り、サーバー設定値として保存しません。

## テスト

~~~sh
mvn test
~~~

ReceiptApiAuthorizationIntegrationTestは、解析・保存・一覧・詳細・削除の各APIについて、未ログイン、管理者以外、CSRFなし、ログイン済み管理者の応答を検証します。

本番ではHTTPSを使い、SESSION_COOKIE_SECUREを有効にしてください。DB資格情報と管理者情報は公開ファイルに記載しないでください。
