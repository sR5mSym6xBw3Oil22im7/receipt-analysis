# reBack

`reBack` は、レシート画像の解析、解析結果の保存、保存済みレシートの参照・削除を担当するBackendです。Java 21 / Spring Boot 4.1.0 / Spring JDBCで構成し、Google Gemini APIとPostgreSQLを利用します。

## 主な技術

- Java 21
- Spring Boot 4.1.0
- Spring Web / Spring JDBC
- PostgreSQL
- Google Gen AI SDK 1.67.0
- Gson 2.13.1
- Maven
- Docker / Render
- H2 Database（テスト）

## 環境変数

| 変数 | 用途 | 既定値 |
| --- | --- | --- |
| `PORT` | 待受ポート | `8081` |
| `DB_HOST` | PostgreSQLホスト | `localhost` |
| `DB_PORT` | PostgreSQLポート | `5432` |
| `DB_NAME` | データベース名 | `receipt_db` |
| `DB_USER` | DBユーザー | `postgres` |
| `DB_PASSWORD` | DBパスワード | `postgres` |
| `APP_FRONTEND_ORIGIN` | CORS許可Frontend Origin | `http://localhost:5051` |

Gemini APIキーは環境変数へ保存しません。`POST /api/receipts/analyze` で受け取った値を、そのリクエストのGemini API呼び出しだけに使用します。

## 起動

```bash
mvn spring-boot:run
```

ローカル起動時は `local` プロファイルを使用し、ログは標準出力と `log/backend.log` に出力します。

## API

| Method | Path | 概要 |
| --- | --- | --- |
| GET | `/api/health` | 稼働確認 |
| GET | `/api/receipts` | 保存済みレシート一覧 |
| GET | `/api/receipts/{tableName}` | レシート詳細 |
| POST | `/api/receipts/analyze` | JPEG / PNG画像を解析 |
| POST | `/api/receipts/save` | 解析結果を保存 |
| DELETE | `/api/receipts/{tableName}` | レシートと関連データを削除 |

`/api/receipts/analyze` は `multipart/form-data` の `file` と `geminiApiKey` を受け取ります。画像はJPEG / PNG、1画像5MB以下です。

## 解析と保存

1. 画像形式・サイズを検証します。
2. 画像バイト列からSHA-256を算出します。
3. Gemini APIから印字行と構造化データを取得します。
4. SHA-256を `receipt_image_hash_registry` に予約し、保存済み画像の重複を防ぎます。
5. `/api/receipts/save` で動的な `receipt_<uuid32>` テーブルへ原文行を保存します。
6. 構造化サマリーを `receipt_structured_summary`、商品明細を `receipt_structured_item` へ保存します。

## 削除時の整合性

`DELETE /api/receipts/{tableName}` はトランザクション内で次を削除します。

- 対象の動的原文テーブル
- `receipt_structured_item` の関連商品明細
- `receipt_structured_summary` の関連サマリー
- `receipt_image_hash_registry` の関連ハッシュ

`receipt_structured_item.receipt_table_name` には `receipt_structured_summary(receipt_table_name)` への外部キーを設定し、`ON DELETE CASCADE` も使用します。外部キー設定前には孤立商品行を削除します。

## Gemini API

モデルは `application.yml` の `gemini.receipt-model` で設定し、現行値は `gemini-3.5-flash-lite` です。APIキー不足、無効・権限不足、利用上限超過などはアプリケーション用エラーコードへ変換します。

## CORS

`/api/**` では設定値 `APP_FRONTEND_ORIGIN` に加え、GitHub Pagesの公開Originとローカル `localhost:5051` / `127.0.0.1:5051` を許可します。許可メソッドは `GET` / `POST` / `DELETE` / `OPTIONS` です。

## テスト

```bash
mvn test
```

`src/test/java` 配下には19件のテストがあります。`mvn test` を実行し、19/19 PASS（Failures 0、Errors 0）を確認済みです。

## Render

`render.yml` にWeb ServiceとPostgreSQLのBlueprintを定義しています。Web ServiceはDockerでビルドし、`/api/health` をヘルスチェックに使用します。
