# レシート解析システム

レシート画像をGemini APIで解析し、抽出したテキストをPostgreSQLへ保存するWebアプリケーションです。

## 構成

- `reFront/`: GitHub Pagesで公開するフロントエンド（HTML / CSS / JavaScript）
- `reBack/`: Spring Boot 4.1.0・Java 21で構築したバックエンド
- `doc/`: HTML形式の納品ドキュメント一式（要件定義、設計、テスト、リリース、運用等）
- `test_evidence/`: 実行済みテストの記録

フロントエンドはGitHub Pages、バックエンドはRender、データベースはPostgreSQLでの運用を想定しています。

## 主な機能

1. `upload.html` でGemini APIキーとJPEG / PNG画像、またはJPEG / PNG画像を含むZIPファイルを選択します。
2. 「解析」で画像を1枚ずつ解析し、抽出テキストを画面に表示します。直接画像は1枚、ZIPの場合は内部画像を順番に処理します。
3. 解析時には画像のSHA-256を重複チェック用に登録しますが、レシート本文は保存しません。
4. 「PostgreSQLへ保存」を押すと、未登録の解析結果だけを保存します。
5. 保存済み画像は409エラーとして扱い、同じ画像の二重登録を防止します。

## Gemini APIキーの扱い

APIキーは画面の入力欄からリクエストごとに入力してください。環境変数、ソースコード、localStorage、sessionStorage、データベースには保存しません。バックエンドも受信したキーを、そのリクエストのGemini API呼び出しだけに使用します。

Geminiの利用上限に達した場合やキーが無効な場合は、画面上でキーを入れ替えて同じ画像を再解析できます。

## ローカル環境での起動

### 前提

- Java 21
- Maven
- PostgreSQL
- Node.js（フロントエンドのテストを実行する場合）

### 1. PostgreSQLの設定

`receipt_db` データベースを作成し、必要に応じて次の環境変数を設定します。

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=receipt_db
export DB_USER=postgres
export DB_PASSWORD='your_password'
export APP_FRONTEND_ORIGIN='http://localhost:5051'
```

Gemini APIキー用の環境変数は設定しません。

### 2. バックエンドの起動

```bash
cd reBack
mvn spring-boot:run
```

標準ポートは `8081` です。ヘルスチェックは次のURLで確認できます。

```bash
curl http://localhost:8081/api/health
```

### 3. フロントエンドの起動

別のターミナルで実行します。

```bash
cd reFront
python3 -m http.server 5051
```

ブラウザで `http://localhost:5051` を開き、APIキーとレシート画像を入力してください。

## APIの概要

- `GET /api/health`: ヘルスチェック
- `GET /api/receipts`: 保存済みレシートの一覧取得
- `GET /api/receipts/{tableName}`: レシート詳細の取得
- `POST /api/receipts/analyze`: 画像を解析し、テキストとSHA-256を返す
- `POST /api/receipts/save`: 解析済みテキストをPostgreSQLへ保存
- `DELETE /api/receipts/{tableName}`: レシートと重複チェック情報を削除

`/api/receipts/analyze` は `multipart/form-data` の `file` と `geminiApiKey` を受け取ります。解析時にレシート本文のテーブルは作成されません。保存処理は `/api/receipts/save` を呼び出したときだけ実行されます。

## テスト

バックエンド:

```bash
cd reBack
mvn test
```

フロントエンド:

```bash
cd reFront
node --test test/smoke.test.mjs
```

## デプロイ

- フロントエンド: `reFront/` をGitHub Pagesへ公開
- バックエンド: `reBack/` をRenderへデプロイ
- Render Blueprint: `reBack/render.yml`

RenderではDB接続情報と `APP_FRONTEND_ORIGIN` を環境変数に設定してください。Gemini APIキーは環境変数として設定しません。
