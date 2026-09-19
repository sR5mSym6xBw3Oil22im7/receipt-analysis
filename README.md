# レシート解析システム

レシート画像をGoogle Gemini APIで解析し、抽出した印字行と構造化データをPostgreSQLへ保存・参照・削除できるWebアプリケーションです。Frontendは静的HTML/CSS/JavaScript、BackendはJava 21 / Spring Boot 4.1.0で構成しています。

## ディレクトリ構成

- `reFront/` : GitHub Pages向けFrontend
- `reBack/` : Render向けSpring Boot Backend
- `doc/` : 要件定義、設計、テスト、リリース、運用・引継ぎをまとめたHTML文書
- `index.html` : `reFront/index.html` への入口

## 主な機能

- APIキー不要のデモモード
- JPEG / PNG画像の解析
- JPEG / PNGのみを含むZIPのブラウザ展開と順次解析
- Gemini APIによる印字行と構造化データの抽出
- SHA-256による保存済み画像の重複防止
- 解析とPostgreSQL保存の明確な分離
- 保存済みレシートの一覧・詳細表示
- レシート削除時の原文、構造化サマリー、商品明細、画像ハッシュの関連削除

## デモモード

`reFront/index.html` の「デモを試す」から利用できます。Gemini APIキーは不要です。固定のサンプルレシートと事前作成済みの解析結果をFrontendだけで表示し、Gemini API、Backend API、PostgreSQLには接続しません。

## 通常の解析フロー

1. `reFront/upload.html` でGemini APIキーを入力します。
2. JPEG / PNG、またはJPEG / PNGを含むZIPを選択します。
3. 「解析」で画像を1枚ずつGemini APIへ送信し、解析結果を画面に表示します。
4. 内容を確認後、「PostgreSQLへ保存」を押すと未保存の解析結果を保存します。
5. 保存済みレシートは一覧・詳細画面から参照・削除できます。

Gemini APIキーはリクエスト単位で使用し、ソースコード、環境変数、Browser Storage、データベースへ保存しません。

## ローカル実行

### 必要環境

- Java 21
- Maven
- PostgreSQL
- Python 3等の静的HTTPサーバー
- Node.js（Frontendテストを実行する場合）

### Backend

```bash
cd reBack
mvn spring-boot:run
```

標準ポートは `8081`、ヘルスチェックは `GET http://localhost:8081/api/health` です。

### Frontend

```bash
cd reFront
python3 -m http.server 5051 --directory ..
```

ブラウザで `http://localhost:5051` を開きます。プロジェクトルートを配信するため、納品ドキュメントは `http://localhost:5051/doc/` から参照できます。デモモードだけを確認する場合、Backendは不要です。

## 主要API

| Method | Path | 概要 |
| --- | --- | --- |
| GET | `/api/health` | ヘルスチェック |
| GET | `/api/receipts` | 保存済みレシート一覧 |
| GET | `/api/receipts/{tableName}` | レシート詳細 |
| POST | `/api/receipts/analyze` | 画像解析 |
| POST | `/api/receipts/save` | 解析結果保存 |
| DELETE | `/api/receipts/{tableName}` | レシートと関連データ削除 |

## テスト

Frontend:

```bash
cd reFront
node --test test/smoke.test.mjs
```

2026-09-19の文書再作成時点で18/18 PASSを確認しています。

Backend:

```bash
cd reBack
mvn test
```

Backendには19件のテストコードがあります。`mvn test` を実行し、19/19 PASS（Failures 0、Errors 0）を確認済みです。

## 公開構成

- Frontend: GitHub Pages
- Backend: Render Web Service（Docker）
- Database: Render PostgreSQL
- AI: Google Gemini API

Renderでは `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`、`APP_FRONTEND_ORIGIN` を使用します。Gemini APIキーはRender環境変数へ設定しません。

## 納品ドキュメント

`doc/index.html` から、企画・要件定義・設計・実装・テスト・リリース・運用・引継ぎのHTML文書を参照できます。
