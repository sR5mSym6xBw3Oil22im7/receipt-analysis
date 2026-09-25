# レシート解析システム

レシート画像を解析し、結果を確認してPostgreSQLへ保存するWebシステムです。サンプルデータで動作を確認できるデモ画面もあります。

## 機能

- JPEG/PNG画像のGemini解析
- 解析テキストと店舗・商品情報の表示
- 解析結果のPostgreSQL保存
- 保存済みレシートの一覧・詳細・削除
- Gemini APIを呼び出さない固定データのデモ

解析画像は1ファイル5 MiBまでです。解析に使うGemini APIキーは画面から要求ごとに渡します。画像ファイルそのものを保存する処理はありません。同じ画像の再登録はSHA-256で検出します。

## 構成

- reFront/ — 利用者向けページとブラウザー側処理
- reBack/ — Java 21 / Spring Boot API、テスト、Dockerfile
- doc/ — [開発文書目次](doc/index.html)

## ローカル起動

必要環境はJava 21、Maven、PostgreSQLです。application.ymlのDB既定値はlocalhost:5432/receipt_db、ユーザー名・パスワードはpostgresです。管理者IDとBCryptパスワードハッシュを環境変数に設定してください。

~~~sh
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD_HASH='<BCrypt形式のハッシュ>'
cd reBack
mvn spring-boot:run -Dspring-boot.run.profiles=local
~~~

DB接続先はDB_HOST、DB_PORT、DB_NAME、DB_USER、DB_PASSWORDで変更できます。ローカル起動後、reFront/index.htmlをブラウザーで開きます。Gemini解析には有効なAPIキーの入力が必要です。デモはAPIキーなしで利用できます。

## テスト

~~~sh
cd reBack
mvn test
~~~

## 関連README

- reFront/README.md — フロントエンド画面
- reBack/README.md — API、設定、テスト
