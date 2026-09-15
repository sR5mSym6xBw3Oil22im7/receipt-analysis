# reBack

Java 21、Spring Boot、PostgreSQL、Gemini APIで構成したレシート解析システムのバックエンドです。

## 必要な環境

- Java 21
- Maven
- PostgreSQL

## 環境変数

次の環境変数を設定します。

| 環境変数 | 用途 |
| --- | --- |
| `DB_HOST` | PostgreSQLのホスト名 |
| `DB_PORT` | PostgreSQLのポート番号 |
| `DB_NAME` | データベース名 |
| `DB_USER` | データベースユーザー名 |
| `DB_PASSWORD` | データベースパスワード |
| `APP_FRONTEND_ORIGIN` | フロントエンドのOrigin（CORS設定） |

Gemini APIキー用の環境変数は使用しません。APIキーはフロントエンドからリクエスト単位で受け取ります。

## 起動

```bash
mvn spring-boot:run
```

ローカル起動時は `local` プロファイルが自動的に有効になり、実行ログが `reBack/log/backend.log` に出力されます。ログは標準出力にも引き続き出力されます。

標準ポートは `8081` です。`PORT` 環境変数で変更できます。

ヘルスチェック:

```bash
curl http://localhost:8081/api/health
```

## API

- `GET /api/health`: ヘルスチェック
- `GET /api/receipts`: 保存済みレシートの一覧取得
- `GET /api/receipts/{tableName}`: レシート詳細の取得
- `POST /api/receipts/analyze`: 画像を解析し、抽出テキストとSHA-256を返す
- `POST /api/receipts/save`: 解析済みレシートを保存
- `DELETE /api/receipts/{tableName}`: レシートと重複チェック情報を削除

`POST /api/receipts/analyze` は `multipart/form-data` の `file` と `geminiApiKey` を受け取ります。解析時にはレシート本文のテーブルを作成しません。本文の保存は `POST /api/receipts/save` の実行時だけ行います。

## Gemini APIキーの扱い

受信したAPIキーは、そのリクエストのGemini API呼び出しだけに使用します。データベース、レスポンス、ログには保存・出力しません。Geminiの利用上限やキー拒否は、適切なHTTPエラーとしてフロントエンドへ返します。

## データ保存

画像ごとにSHA-256を重複チェックキーとして `receipt_image_hash_registry` へ登録します。保存時には新しいレシート用テーブルを作成し、抽出したテキストを行単位で保存します。テーブル名はバックエンドがUUIDから生成し、ユーザー入力やGeminiの出力を識別子として使用しません。

レシートを削除すると、紐付いたSHA-256も同じトランザクションで削除されるため、同じ画像を再登録できます。

## Renderへのデプロイ

Render Blueprintは `render.yml` に定義しています。Render DashboardでBlueprint Pathに `reBack/render.yml` を指定してください。

Render側ではDB接続情報と `APP_FRONTEND_ORIGIN` を設定します。Gemini APIキーは環境変数として設定しません。

## テスト

```bash
mvn test
```
