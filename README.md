# レシート解析システム - No01(1) 実装

添付 `prompt_No01(1).md` を基準に、要件定義・設計・実装・テストをまとめた成果物です。

## ディレクトリ
- `reFront/`: GitHub Pages向け HTML/CSS/JavaScript
- `reBack/`: Render向け Spring Boot / Java 21 Backend
- `reBack/render.yml`: Render Blueprint例
- `docs/01_requirements.md`: 要件定義
- `docs/02_design.md`: 設計
- `docs/03_test_plan_and_results.md`: テスト計画・結果
- `test_evidence/`: 実行済みテストのログ

## Gemini APIキーの扱い
Gemini APIキーは **`reFront/upload.html` の入力欄から入力した値だけ** を使用する。
BackendにGemini APIキーの既定値は持たせず、Renderやローカル環境にもGemini APIキー用の環境変数を定義しない。

Frontendは入力されたAPIキーを `multipart/form-data` の `geminiApiKey` としてBackendへ送信する。Backendは受信したキーをそのリクエストのGemini API呼び出しだけに使用し、DB・ログ・レスポンスへ保存/出力しない。FrontendもlocalStorage/sessionStorageへ保存しない。

無料枠の上限に達した場合は、画面のAPIキー欄を次のキーへ入れ替え、選択済みのレシート画像をそのまま再実行する。キー本数はコードへ固定しない。

## ローカル起動

### 1. PostgreSQL
`receipt_db` を作成し、DB接続情報とFrontend Originを環境変数へ設定する。

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=receipt_db
export DB_USER=postgres
export DB_PASSWORD='your_password'
export APP_FRONTEND_ORIGIN='http://localhost:5500'
```

Gemini APIキーの環境変数は設定しない。

### 2. Backend
```bash
cd reBack
mvn spring-boot:run
```

Health Check:
```bash
curl http://localhost:8080/api/health
```

API確認では `geminiApiKey` が必須:
```bash
curl -X POST http://localhost:8080/api/receipts \
  -F 'file=@/path/to/receipt.jpg' \
  -F 'geminiApiKey=YOUR_GEMINI_API_KEY'
```

### 3. Frontend
別ターミナルで:
```bash
cd reFront
python3 -m http.server 5500
```

ブラウザで `http://localhost:5500` を開き、Gemini APIキーとレシート画像を入力する。

## GitHub Pages
`reFront` の内容をGitHub Pagesへ公開する。公開前に `reFront/config.js` の `YOUR-RENDER-SERVICE` を実際のRender Backendホスト名へ変更する。

APIキーそのものは `upload.html` や `config.js` へ書き込まず、Web画面のpassword入力欄から都度入力する。

## Render
Blueprintファイルは **`reBack/render.yml`** に配置する。RenderでBlueprintを作成するときは Blueprint Path に `reBack/render.yml` を指定する。
Render側でGemini APIキーの環境変数は定義しない。

Blueprintで使用する環境変数は次の用途に限定する。
- `APP_FRONTEND_ORIGIN`
- Render PostgreSQLから供給される `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`

GeminiモデルはBackend設定ファイルで `gemini-3.5-flash-lite` に固定している。

## Geminiモデル
- 採用モデル: `gemini-3.5-flash-lite`
- 画像入力とStructured Outputを使用してレシート文字列をJSONで取得する。
- Geminiモデル名およびAPIキーにGemini専用環境変数は使用しない。
- 将来モデルを変更する場合はBackend設定/実装と互換性テストを更新する。

## 重複レシートの扱い
`reFront/upload.html` の「解析」ボタンでは最大5枚を順番に解析し、抽出テキストを画面表示するだけでPostgreSQLへ保存しない。解析後に「PostgreSQLへ保存」ボタンを押したときだけ、各解析結果の重複チェックと保存を行う。

保存時はまずBackendの重複チェックAPIで既存レシートデータとの完全一致を確認する。同じデータが登録済みならその画像だけを警告表示して保存せず、未登録データだけを保存する。保存API自身も409 `DUPLICATE_RECEIPT` で二重登録を防止するため、チェック直後に競合が起きても新しいテーブルは作成しない。たとえば5枚中2・3枚目が登録済みなら、2・3枚目は保存せず、1・4・5枚目だけを保存する。

## 重要な実装仕様
添付仕様に従い、レシート1枚ごとに新しいPostgreSQLテーブルを作る。テーブル名はBackendがUUIDから生成し、ユーザー入力やGemini出力をDDL識別子へ使用しない。


## 解析時保存防止
- 「解析」は `POST /api/receipts/analyze` のみを使用し、PostgreSQLへのINSERT/CREATE TABLEを行わない。
- 旧 `POST /api/receipts` の解析＋保存エンドポイントは廃止し、解析操作から保存処理へ到達するBackend経路を削除した。
- PostgreSQLへの追加は「PostgreSQLへ保存」押下時の `POST /api/receipts/save` のみに限定する。
- 保存時は `POST /api/receipts/check-duplicate` で各解析結果を確認し、重複分だけ除外して未登録分を保存する。
