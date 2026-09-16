# reBack

`reBack` は、レシート画像の解析・保存・参照・削除を提供するバックエンドです。Java 21 / Spring Boot 4.1.0 で構成し、Gemini APIによるレシート解析とPostgreSQLへのデータ保存を行います。

## 主な構成

- Java 21
- Spring Boot 4.1.0
- Spring Web / Spring JDBC
- PostgreSQL
- Google Gen AI SDK 1.67.0
- Gson 2.13.1
- Maven
- Docker / Render

テスト時はH2 Databaseを使用します。

## 必要な環境

ローカルで起動する場合は、次の環境を用意してください。

- Java 21
- Maven
- PostgreSQL

## 環境変数

| 環境変数 | 用途 | 既定値 |
| --- | --- | --- |
| `PORT` | Spring Bootの待受ポート | `8081` |
| `DB_HOST` | PostgreSQLのホスト名 | `localhost` |
| `DB_PORT` | PostgreSQLのポート番号 | `5432` |
| `DB_NAME` | データベース名 | `receipt_db` |
| `DB_USER` | データベースユーザー名 | `postgres` |
| `DB_PASSWORD` | データベースパスワード | `postgres` |
| `APP_FRONTEND_ORIGIN` | CORSで許可するフロントエンドOrigin | `http://localhost:5051` |

Gemini APIキーは環境変数に保存しません。フロントエンドから解析リクエストごとに受け取り、そのGemini API呼び出しにだけ使用します。

## ローカル起動

```bash
mvn spring-boot:run
```

`spring-boot-maven-plugin` の設定により、ローカル起動時は `local` プロファイルを使用します。ログは標準出力に加え、`log/backend.log` にも出力されます。

ヘルスチェックは次のURLで確認できます。

```bash
curl http://localhost:8081/api/health
```

## API

| メソッド | パス | 概要 |
| --- | --- | --- |
| `GET` | `/api/health` | ヘルスチェック |
| `GET` | `/api/receipts` | 保存済みレシート一覧を取得 |
| `GET` | `/api/receipts/{tableName}` | 保存済みレシート詳細を取得 |
| `POST` | `/api/receipts/analyze` | レシート画像を解析し、抽出行・SHA-256・構造化データを返却 |
| `POST` | `/api/receipts/save` | 解析済みレシートをPostgreSQLへ保存 |
| `DELETE` | `/api/receipts/{tableName}` | レシートと対応する重複チェック情報を削除 |

`POST /api/receipts/analyze` は `multipart/form-data` の `file` と `geminiApiKey` を受け取ります。画像はJPEGまたはPNG、1画像あたり5MB以下です。

`POST /api/receipts/save` はJSONで解析結果を受け取り、レシート本文と構造化データを保存します。

## 解析と保存の流れ

1. 画像形式とサイズを検証します。
2. 画像バイト列からSHA-256を算出します。
3. Gemini APIへ画像を送信し、印字行と構造化データを取得します。
4. SHA-256を `receipt_image_hash_registry` に予約登録し、保存済み画像かどうかの判定に使用します。未保存の予約行（`table_name` が `NULL`）は再解析を許可します。
5. フロントエンドで内容確認後、保存APIを実行するとレシート用テーブルを作成し、解析結果を保存します。
6. レシート削除時は、対象の原文テーブル、`receipt_structured_summary` の対象行、対応するSHA-256登録を削除します。

レシート用テーブル名はバックエンドでUUIDから生成します。利用者の入力値やGemini APIの出力値をテーブル名として使用しません。

## 既知の注意点

現行の削除処理では `receipt_structured_item` の関連行を削除していません。また、当該テーブルには原文テーブルやサマリーテーブルへの外部キー制約もありません。そのため、レシート削除後に構造化商品行が残る可能性があります。運用・改修時は `doc/33_known_issues_handover.html` も参照してください。

## Gemini APIキーの扱い

受信したGemini APIキーは、そのリクエストのAPI呼び出しにだけ使用します。データベース、APIレスポンス、アプリケーションログには保存・出力しません。

APIキー未入力、拒否、利用上限超過などは、バックエンドでエラーコードへ変換してフロントエンドへ返します。

使用モデルは `application.yml` の `gemini.receipt-model` で設定し、現行値は `gemini-3.5-flash-lite` です。

## CORS

`/api/**` では、設定値 `APP_FRONTEND_ORIGIN` に加え、次のOriginを許可しています。

- `https://sr5msym6xbw3oil22im7.github.io`
- `http://localhost:5051`
- `http://127.0.0.1:5051`

許可メソッドは `GET` / `POST` / `DELETE` / `OPTIONS` です。

## テスト

```bash
mvn test
```

バックエンドのテストコードは `src/test/` 配下にあります。

## Renderへのデプロイ

`render.yml` にRender Blueprintを定義しています。Web ServiceはDockerでビルドし、`/api/health` をヘルスチェックに使用します。

Render側ではPostgreSQL接続情報と `APP_FRONTEND_ORIGIN` を設定します。Gemini APIキーはRenderの環境変数には設定しません。
