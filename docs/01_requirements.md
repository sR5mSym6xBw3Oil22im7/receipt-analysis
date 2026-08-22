# 要件定義

## 1. 目的
レシート画像をWeb画面から最大5枚選択し、Gemini APIで印字テキストを抽出して、未登録のレシートだけをRender PostgreSQLへ保存するWebアプリを作成する。

## 2. 添付仕様から確定した要件
- Webアプリであること。
- FrontendはGitHub Pages上のHTML/CSS/JavaScriptで構築すること。
- Frontend開発フォルダ名は `reFront` とすること。
- BackendはRender上で稼働し、Render PostgreSQLを使用すること。
- Backend開発フォルダ名は `reBack` とすること。
- レシート画像解析にはGemini APIを使用し、採用モデルは `gemini-3.5-flash-lite` とすること。
- 添付の `レシート.zip` を開発・確認用レシート画像として扱うこと。
- コア機能を優先し、拡張機能は後回しとすること。
- 「解析」ボタンではレシート画像から文字を抽出して画面表示し、画像バイト列のSHA-256をPostgreSQLへ重複チェックキーとして登録すること。レシート本文は登録しないこと。
- 「PostgreSQLへ保存」ボタン押下時にだけ重複チェックを行い、同じレシートデータが未登録の場合だけPostgreSQLへ新しいテーブルを1個作成して抽出テキストを保存すること。登録済みの場合は警告表示し、新しいテーブルを作成しないこと。
- レシートテーブル削除時は、紐付く画像SHA-256も `receipt_image_hash_registry` から削除し、同じ画像を再登録可能にすること。

## 3. チュートリアル画像から選定した技術
今回のコア実装に必要な範囲だけを選定する。
- HTML / CSS / JavaScript
- Java
- Spring / Spring Boot
- REST API / JSON
- Render
- Docker
- Git / GitHub

Bootstrap、Tailwind CSS、SCSS、Figma等はコア機能に必須ではないため初期実装には採用しない。

## 4. 実装上の補助要件
添付仕様に詳細がないため、動作・安全性に必要な最小限の設計判断を追加する。
- Backendの1回のリクエストではレシート画像1枚のみ受け付ける。Frontendでは最大5枚を選択でき、1枚ずつ順番にBackendへ送信する。
- JPEG / PNGを受け付ける。添付ZIP内52枚はすべてJPEGである。
- 画像サイズ上限は5MBとする。添付ZIP内の最大ファイルは約1.73MBのため全画像が範囲内である。
- 動的テーブル名はユーザー入力やGeminiの文字列を使用せず、BackendでUUIDから生成する。
- Geminiの結果は `lines: string[]` のJSONとして受け取り、1行をDBの1レコードとして保存する。
- `upload.html` の「解析」ボタン押下では各画像を `/api/receipts/analyze` へ送り、抽出テキストとSHA-256を画面表示用に受け取る。BackendはSHA-256を `receipt_image_hash_registry` へ登録し、同一ハッシュは409で拒否する。
- 解析完了後に「PostgreSQLへ保存」ボタンを有効化し、その押下時に各解析結果とSHA-256を保存APIへ送る。
- 重複していない解析結果だけ `POST /api/receipts/save` へ送り、SHA-256の予約行へ新しいレシートテーブルを紐付ける。既存ハッシュの場合は409 `DUPLICATE_RECEIPT_IMAGE` を返して新規テーブルを作成しない。
- 複数選択時に一部だけが登録済みでも処理全体を中止せず、その画像だけを警告表示して除外し、未登録の後続画像を継続して保存する。例: 5枚中2・3枚目が登録済みなら、2・3枚目は非登録、1・4・5枚目は登録する。
- DBパスワード等の秘密情報はソースコードへ埋め込まない。
- **Gemini APIキーは `reFront/upload.html` の入力欄で必須入力し、その入力値だけを使用する。**
- Backend/Render/ローカル環境にGemini APIキーの環境変数を定義しない。
- `POST /api/receipts` はmultipartの `geminiApiKey` を必須とし、Backendは受信したキーだけでGemini APIを呼び出す。
- 運用者が用意した複数のGemini APIキーをソースへ埋め込まず、無料枠のリクエスト上限に達するたびにFrontend上で次のAPIキーへ入れ替えて同じ画像を再試行できること。キー本数はUIへ固定しない。
- Web入力したGemini APIキーはGitHub Pagesのソース、localStorage、sessionStorage、PostgreSQLへ保存しない。BackendへHTTPSでリクエスト単位に送信し、そのリクエストのGemini呼び出しだけに使用する。
- BackendはGemini APIの429/RESOURCE_EXHAUSTEDを `GEMINI_QUOTA_EXCEEDED` として返し、FrontendはAPIキー欄へフォーカスして次のキー入力を促す。無効・権限不足キーは `GEMINI_API_KEY_REJECTED` として再入力を促す。
- Geminiモデルは `gemini-3.5-flash-lite` とし、Geminiモデル用の環境変数も定義しない。
- Render Blueprintは `reBack/render.yml` に配置する。

## 5. 初期版の対象外
- ユーザー認証
- レシート一覧・検索・削除
- ダッシュボード
- OCR結果の編集画面
- 1つのHTTPリクエストに複数画像を含めるバッチアップロードAPI
- レシート画像自体の永続保存
- 高度な店舗別項目の構造化


## 解析時保存防止
- 「解析」は `POST /api/receipts/analyze` でSHA-256を計算して `receipt_image_hash_registry` へINSERTする。レシート本文のINSERT/CREATE TABLEは行わない。
- 旧 `POST /api/receipts` の解析＋保存エンドポイントは廃止し、解析操作から保存処理へ到達するBackend経路を削除した。
- PostgreSQLへの追加は「PostgreSQLへ保存」押下時の `POST /api/receipts/save` のみに限定する。
- 保存時は解析レスポンスのSHA-256を `POST /api/receipts/save` へ渡し、ハッシュをキーに重複分を除外して未登録分を保存する。
