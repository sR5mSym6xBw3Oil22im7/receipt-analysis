# reBack

`reBack` は、レシート画像の解析、確認後の保存、一覧・詳細表示、削除を提供するREST APIです。Java 21とSpring Bootで構成し、Gemini APIとPostgreSQLを連携します。

## 使用技術

- Java 21
- Spring Boot 4.1.0
- Spring Web / Spring JDBC
- PostgreSQL
- Google Gen AI SDK 1.67.0
- Gson 2.13.1
- Maven
- Docker / Render

テストではH2 Databaseを使用します。

## 必要な環境

- Java 21
- Maven
- PostgreSQL

## 環境変数

| 変数 | 用途 | 既定値 |
| --- | --- | --- |
| `PORT` | Spring Bootの待受ポート | `8081` |
| `DB_HOST` | PostgreSQLのホスト名 | `localhost` |
| `DB_PORT` | PostgreSQLのポート番号 | `5432` |
| `DB_NAME` | データベース名 | `receipt_db` |
| `DB_USER` | データベースユーザー名 | `postgres` |
| `DB_PASSWORD` | データベースパスワード | `postgres` |
| `APP_FRONTEND_ORIGIN` | CORSで許可するフロントエンドOrigin | `http://localhost:5051` |

Gemini APIキーは環境変数に保存しません。フロントエンドから解析リクエストごとに受け取り、そのリクエストのGemini API呼び出しだけに使用します。

## ローカル起動

```bash
mvn spring-boot:run
```

ローカル起動時は `local` プロファイルを使用します。ログは標準出力に加えて `log/backend.log` にも出力されます。

```bash
curl http://localhost:8081/api/health
```

## API

| メソッド | パス | 概要 |
| --- | --- | --- |
| `GET` | `/api/health` | ヘルスチェック |
| `GET` | `/api/receipts` | 保存済みレシート一覧 |
| `GET` | `/api/receipts/{tableName}` | 保存済みレシート詳細 |
| `POST` | `/api/receipts/analyze` | 画像解析、抽出行・SHA-256・構造化データの返却 |
| `POST` | `/api/receipts/save` | 解析済みレシートの保存 |
| `DELETE` | `/api/receipts/{tableName}` | レシートと関連する重複チェック情報の削除 |

解析APIは `multipart/form-data` の `file` と `geminiApiKey` を受け取り、JPEGまたはPNGかつ1画像5MB以下であることを検証します。保存APIはJSONで解析結果を受け取ります。

## 解析から保存まで

1. 画像形式とサイズを検証します。
2. 画像バイト列からSHA-256を算出します。
3. Gemini APIから印字行と構造化データを取得します。
4. SHA-256を重複チェック用に予約します。未保存の予約は再解析できます。
5. 保存APIの呼び出し時にレシート本文と構造化データを保存します。
6. 削除時は本文、構造化サマリ、商品明細、SHA-256登録を同一トランザクションで削除します。

レシート本文のテーブル名はUUIDから生成し、利用者の入力値やGeminiの出力値を識別子として使用しません。

## セキュリティとCORS

受信したGemini APIキーはリクエスト内だけで使用し、データベース、レスポンス、ログへ出力しません。`APP_FRONTEND_ORIGIN` に加え、公開フロントエンドとローカル開発用Originを許可します。許可メソッドは `GET`、`POST`、`DELETE`、`OPTIONS` です。

## テスト

```bash
mvn test
```

テストコードは `src/test/` 配下にあります。

## Renderへのデプロイ

`render.yml` にRender Blueprintを定義しています。Web ServiceはDockerでビルドし、`/api/health` をヘルスチェックに使用します。Render側ではPostgreSQL接続情報と `APP_FRONTEND_ORIGIN` を設定してください。
