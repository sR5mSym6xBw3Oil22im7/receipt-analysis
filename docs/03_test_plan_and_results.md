# テスト計画・結果

## 1. テスト方針
Gemini APIキーを成果物へ埋め込まず、通常の自動テストではGeminiをStub化してWeb APIからDB保存までを検証する。Gemini実接続時は `reFront/upload.html` から入力したAPIキーを使用する。Gemini APIキーの環境変数は使用しない。

## 2. 自動テスト項目

| ID | 対象 | 確認内容 |
|---|---|---|
| BE-01 | Repository | レシート1件ごとに異なるテーブル名が生成される |
| BE-02 | Repository | 抽出文字列がline_no順に保存される |
| BE-03 | Validator | JPEG/PNGを許可する |
| BE-04 | Validator | 空ファイルを400相当で拒否する |
| BE-05 | Validator | 非対応MIMEを415相当で拒否する |
| BE-06 | Validator | 5MB超を413相当で拒否する |
| BE-07 | Gemini Adapter | Web入力APIキーが空なら400相当の明示エラーにする |
| BE-08 | Integration | multipart POST + `geminiApiKey` -> Stub解析 -> 動的テーブル作成まで通る |
| BE-09 | Gemini設定 | モデルが `gemini-3.5-flash-lite` である |
| BE-10 | Gemini設定 | Gemini APIキー/Geminiモデル用の環境変数を定義しない |
| BE-11 | APIキー | Web入力APIキーをtrimして利用する |
| BE-12 | APIキー | 異常に長いWeb入力APIキーを拒否する |
| BE-13 | APIキー | multipartの `geminiApiKey` が必須でAnalyzerへ渡る |
| BE-14 | Integration | `/api/receipts/analyze` ではレシートテーブルを作成しない |
| BE-15 | Integration | `/api/receipts/save` で同一 `lines` の2回目保存は409 `DUPLICATE_RECEIPT` となり、テーブル数が増えない |
| BE-16 | Integration/Repository | `receipt_` で始まる管理テーブルが存在しても、実レシートテーブルだけを対象に重複確認して応答する |
| FE-01 | Frontend smoke | JPEG/PNG accept指定がある |
| FE-02 | Frontend smoke | FormDataでPOSTする |
| FE-03 | Frontend smoke | Backend URLを設定ファイルで変更できる |
| FE-04 | Frontend smoke | Gemini APIキー入力がpassword型かつ必須である |
| FE-05 | Frontend smoke | 入力キーを必ずmultipartの `geminiApiKey` で送信する |
| FE-06 | Frontend smoke | `GEMINI_QUOTA_EXCEEDED` 時にAPIキー欄へフォーカスする |
| FE-07 | Frontend smoke | APIキーをlocalStorage/sessionStorageへ保存しない |
| FE-08 | Frontend workflow | 「解析」押下では5枚を解析・表示するだけで、重複チェックAPIと保存APIを呼ばない |
| FE-09 | Frontend workflow | 「PostgreSQLへ保存」押下時に各解析結果を重複チェックする |
| FE-10 | Frontend workflow | 5枚中2・3枚目が重複でも、2・3枚目だけ除外して1・4・5枚目を保存する |
| FE-11 | Frontend workflow | 重複画像番号と保存画像番号を完了メッセージに表示する |
| FE-12 | Frontend workflow | 重複チェック直後に保存APIが409を返しても二重登録せず後続処理を継続する |
| FE-13 | Frontend workflow | 登録済みレシートだけを保存した場合も重複警告を返して処理を完了する |
| FE-14 | Frontend workflow | 保存系APIが30秒応答しない場合は無限待機せずタイムアウトエラーを表示する |
| CFG-01 | Render | Blueprintが `reBack/render.yml` に存在する |
| CFG-02 | Render | `reBack/render.yml` にGemini APIキー/Geminiモデル用環境変数がない |

## 3. 添付レシートZIP確認
- ファイル数: 52
- 画像形式: 52/52 JPEG
- 画像サイズ: 最小 948,217 bytes / 最大 1,726,998 bytes
- 画像寸法: 1920x2560 または 2560x1920
- 5MB上限: 52/52 が範囲内

## 4. 実行結果
このファイルの「実行結果」欄は、成果物生成時に実際にコマンドを実行した結果で更新する。

- Backend Maven test: NOT RUN (`mvn` コマンドがこの実行環境にインストールされていないため)
- Frontend Node test: PASS (16/16。解析時はDB非保存、保存時に5枚中2・3枚目を重複除外して1・4・5枚目を保存するワークフロー、および登録済み1枚だけの重複警告応答を含む)
- Backend Java core smoke: PASS (Web入力APIキー必須/trim/長さ制限)
- 添付ZIPファイル検査: PASS (52/52 JPEG, 52/52 under 5MB)
- Gemini model configuration static check: PASS (`gemini-3.5-flash-lite` 固定)
- Gemini API key static check: PASS (Frontend入力値のみ使用、Gemini APIキー環境変数なし、Browser Storage非保存)
- Render Blueprint static check: PASS (`reBack/render.yml` 配置、`rootDir: reBack`、Gemini関連envVarsなし)
- Gemini実API E2E: NOT RUN (実APIキーを成果物へ保持しないため)
- Render PostgreSQL実環境E2E: NOT RUN (この実行環境からユーザーのRender環境へ接続しないため)


## 解析時保存防止
- 「解析」は `POST /api/receipts/analyze` のみを使用し、PostgreSQLへのINSERT/CREATE TABLEを行わない。
- 旧 `POST /api/receipts` の解析＋保存エンドポイントは廃止し、解析操作から保存処理へ到達するBackend経路を削除した。
- PostgreSQLへの追加は「PostgreSQLへ保存」押下時の `POST /api/receipts/save` のみに限定する。
- 保存時は `POST /api/receipts/check-duplicate` で各解析結果を確認し、重複分だけ除外して未登録分を保存する。
