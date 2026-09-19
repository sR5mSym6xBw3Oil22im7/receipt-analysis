# レシート解析システム

レシート画像をGemini APIで解析し、抽出した文字列と構造化データを確認したうえでPostgreSQLへ保存するWebアプリケーションです。フロントエンドとバックエンドを分離し、デモ利用と実解析を切り替えられる構成にしています。

## 構成

```text
receipt-analysis/
├─ reFront/    # GitHub Pages向けの静的フロントエンド
├─ reBack/     # Java 21 / Spring BootのREST API
└─ doc/        # 日本語HTML納品ドキュメントと共通CSS
```

- フロントエンド：GitHub Pages
- バックエンド：Render
- データベース：PostgreSQL
- AI解析：Google Gemini API

## 主な機能

- JPEG / PNG画像、またはJPEG / PNGを含むZIPの読み込み
- Gemini APIによるレシート印字行と構造化データの抽出
- 解析結果を確認してから実行するPostgreSQL保存
- 画像SHA-256による保存済みレシートの重複防止
- 保存済みレシートの一覧、詳細表示、選択削除
- Gemini APIキーのリクエスト単位利用
- APIキー不要の固定データデモ

## APIキーの扱い

Gemini APIキーは画面から解析リクエストごとに入力します。アプリケーション、ブラウザストレージ、データベース、ログ、APIレスポンスには保存しません。利用上限超過やキー拒否が発生した場合は、画面で別のキーに入れ替えて再解析できます。

## ローカル起動

### 前提

- Java 21
- Maven
- PostgreSQL
- Node.js（フロントエンドのテストを実行する場合）

### バックエンド

PostgreSQLを用意し、必要に応じて次の環境変数を設定します。

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=receipt_db
export DB_USER=postgres
export DB_PASSWORD='your_password'
export APP_FRONTEND_ORIGIN='http://localhost:5051'
```

Gemini APIキー用の環境変数は設定しません。

```bash
cd reBack
mvn spring-boot:run
```

標準ポートは `8081` です。起動確認には次を使用します。

```bash
curl http://localhost:8081/api/health
```

### フロントエンド

別のターミナルで静的ファイルを配信します。

```bash
cd reFront
python3 -m http.server 5051
```

ブラウザで `http://localhost:5051` を開きます。固定デモだけを確認する場合は、バックエンドを起動する必要はありません。

## APIの概要

| メソッド | パス | 用途 |
| --- | --- | --- |
| `GET` | `/api/health` | ヘルスチェック |
| `GET` | `/api/receipts` | 保存済みレシート一覧 |
| `GET` | `/api/receipts/{tableName}` | レシート詳細 |
| `POST` | `/api/receipts/analyze` | 画像解析、抽出行・SHA-256・構造化データ取得 |
| `POST` | `/api/receipts/save` | 解析結果の保存 |
| `DELETE` | `/api/receipts/{tableName}` | レシートと関連データの削除 |

`/api/receipts/analyze` は `multipart/form-data` の `file` と `geminiApiKey` を受け取ります。解析だけでは本文テーブルを作成せず、保存APIを呼び出した時点で保存します。

## テスト

```bash
# フロントエンド
cd reFront
node --test test/smoke.test.mjs

# バックエンド
cd ../reBack
mvn test
```

## デプロイ

- フロントエンド：`reFront/` をGitHub Pagesへ公開
- バックエンド：`reBack/` をRenderへデプロイ
- Render設定：`reBack/render.yml`

Render側ではデータベース接続情報と `APP_FRONTEND_ORIGIN` を設定します。Gemini APIキーは環境変数へ設定しません。

## ドキュメント

設計、要件、テスト、リリース、運用、引継ぎ資料は [`doc/index.html`](doc/index.html) から参照できます。各ページは日本語で記述し、共通のレスポンシブ／印刷レイアウトを使用しています。
