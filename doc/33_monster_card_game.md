# レシートモンスター対戦

## 対象と導線

管理者ログイン後、公開メニューの「レシートモンスター対戦」から利用します。候補はDB上の `receipt_<32桁hex>` レシートテーブルだけです。構造化サマリーがある場合は店舗名・日時・合計額を一覧に出し、旧レシートではテーブルIDを代替表示します。画像はDBにないため表示しません。

カードの初回生成では、Backendが保存済みOCR行を読み、許可カテゴリへ変換した商品カテゴリと店舗カテゴリ、生成シードをGeminiへ送ります。OCR全文、店舗名、連絡先、支払情報、商品名はGeminiへ送信しません。Gemini APIキーは生成APIの本文でのみ受け取り、DBやブラウザー保存領域へ保存しません。生成済みカードは `receipt_monster_card` に保存して同じデータなら再利用します。シードはOCR行と構造化カテゴリから作ります。データが変わると古いカードの返却を拒否します。

## 画面とAPI

| 用途 | API |
|---|---|
| 保存済みレシート候補 | `GET /api/game/receipts` |
| 保存カードのプレビュー情報 | `GET /api/game/cards/{receiptId}` |
| 未生成カードの生成 | `POST /api/game/cards/{receiptId}/generate` |
| SVG表示 | `GET /api/game/cards/{receiptId}/svg` |
| 同一ブラウザー状態 | `GET /api/game/local` |
| 同一ブラウザーP1/P2確定・判定 | `POST /api/game/local/p1`, `/p2`, `/resolve` |
| PC対戦ルーム作成・参加・状態・選択・中止 | `/api/game/rooms` と `/api/game/rooms/{code}` |

全ゲームAPIは管理者認証とCSRF保護の対象です。PC対戦のルーム参加コードは8文字、席トークンは32バイトの暗号学的乱数で、SHA-256ハッシュをDBに保存します。席トークンはHttpOnly Cookieで運びます。ルームは無操作60分で失効し、作成から120分を上限にしています。期限切れレコードは次のルーム操作時に削除します。

判定値は次の `SCORE_X2` を使い、内部整数で勝敗を決めます。表示値は2で割って0.5刻みで表示します。同点はカードシード、次にレシートIDで決着します。

```text
SCORE_X2(A, B) = 6 × (POWER_A + GUARD_A + SPEED_A)
               + 4 × max(POWER_A - GUARD_B, 0)
               + 2 × max(SPEED_A - SPEED_B, 0)
               +     max(GUARD_A - POWER_B, 0)
```

## Gemini設定とSVG

カード用モデルは `GEMINI_CARD_MODEL` で設定し、OCR用の `gemini.receipt-model` とは分離しています。初期値は `gemini-3.8-flash` です。Google公式の[モデル一覧](https://ai.google.dev/gemini-api/docs/models)と[テキスト生成API](https://ai.google.dev/api/generate-content)を2026-09-25に確認し、現行テキスト出力対応モデルとして設定しました。実利用時は対象Google AI Studioプロジェクトで当該モデルへのアクセス権を確認してください。

Geminiには主役となるSVGベクターグループを要求します。BackendはDTDと外部実体を無効にしたXMLパーサーを使い、許可要素・属性、サイズ、入れ子、パス数を検査してから信頼済みカード枠と合成します。SVGは `private, no-store` と `nosniff` 付きで返します。

## ローカル確認

```powershell
cd reBack
..\apache-maven-3.9.16\bin\mvn.cmd test
```

ローカルDBには既存の `application-local.yml` を使用します。管理者ログイン後、保存済みレシートが2件以上ある状態で対戦を始めてください。初回生成時のみ有効なGemini APIキーが必要です。

## 検証状況と既知の制限

- Backendテスト28件（既存24件と新規4件）は成功しています。新規テストでは判定スコアとSVGの許可・拒否規則を確認します。ルーム操作とPostgreSQL同時更新を対象にした自動テストはまだありません。
- JavaScript構文検査は成功しています。
- この作業環境からRender PostgreSQLとGemini APIキーへ接続していないため、DB移行・2ブラウザー対戦・Gemini実生成・5種カードの視覚品質・Chrome原寸／縮小表示は未確認です。実生成品質を確認済みとは扱いません。
- Gemini出力の終了理由確認、制限付きリトライ、生成中レコード／期限付き生成権、同時生成数や生成回数のレート制限は未実装です。
- ルーム参加コード試行制限、ログアウト時のルーム無効化、削除と対戦確定の完全なDB排他、再ログイン時のルーム復帰検証は未確認です。
- モデルへ渡すのは店舗・商品カテゴリの抽象特徴のみです。商品名由来の細かなモチーフ変換は今後の改善項目です。
