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

必要環境はJava 21、Docker、Python 3、OpenSSLです。backendの設定を`reBack/.env`に用意した後、リポジトリのルートで以下を実行するとPostgreSQL、backend、frontendを停止して再起動します。

~~~sh
python3 scripts/restart_local.py
~~~

スクリプトはbackendのhealth endpointとHTTPS frontendの応答を確認してから成功を表示します。frontendは `https://localhost:5051` で起動します。初回はlocalhost用自己署名証明書の警告が表示される場合があります。`http://localhost:5500` は使用しないでください。起動ログは一時ディレクトリ内の`receipt-analysis-local/logs`に出力します。

DB接続先はDB_HOST、DB_PORT、DB_NAME、DB_USER、DB_PASSWORDで変更できます。Gemini解析には有効なAPIキーの入力が必要です。デモはAPIキーなしで利用できます。

## 開発・公開の前提

設計・実装・テストはローカル環境で行います。ローカル環境で不具合が0件であることを確認してから、フロントエンドをGitHub Pages、バックエンドをRenderへアップロードします。

## テスト

~~~sh
cd reBack
mvn test
~~~

## 関連README

- reFront/README.md — フロントエンド画面
- reBack/README.md — API、設定、テスト
