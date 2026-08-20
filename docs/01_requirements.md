# 要件定義

## 1. 目的
レシート画像をWeb画面から1枚アップロードし、Gemini APIで印字テキストを抽出して、Render PostgreSQLへ保存するWebアプリを作成する。

## 2. 添付仕様から確定した要件
- Webアプリであること。
- FrontendはGitHub Pages上のHTML/CSS/JavaScriptで構築すること。
- Frontend開発フォルダ名は `reFront` とすること。
- BackendはRender上で稼働し、Render PostgreSQLを使用すること。
- Backend開発フォルダ名は `reBack` とすること。
- レシート画像解析にはGemini APIを使用し、採用モデルは `gemini-3.5-flash-lite` とすること。
- 添付の `レシート.zip` を開発・確認用レシート画像として扱うこと。
- コア機能を優先し、拡張機能は後回しとすること。
- レシート画像1枚を解析するたびにPostgreSQLへ新しいテーブルを1個作成し、そのレシートの抽出テキストを保存すること。

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
- 1回のリクエストではレシート画像1枚のみ受け付ける。
- JPEG / PNGを受け付ける。添付ZIP内52枚はすべてJPEGである。
- 画像サイズ上限は5MBとする。添付ZIP内の最大ファイルは約1.73MBのため全画像が範囲内である。
- 動的テーブル名はユーザー入力やGeminiの文字列を使用せず、BackendでUUIDから生成する。
- Geminiの結果は `lines: string[]` のJSONとして受け取り、1行をDBの1レコードとして保存する。
- DBパスワード等の秘密情報はソースコードへ埋め込まない。
- **Gemini APIキーは `reFront/index.html` の入力欄で必須入力し、その入力値だけを使用する。**
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
- 複数画像一括アップロード
- レシート画像自体の永続保存
- 高度な店舗別項目の構造化
