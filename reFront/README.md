# reFront

`reFront` はレシート解析システムの静的Frontendです。HTML / CSS / Vanilla JavaScriptで構成し、GitHub Pagesでの公開を想定しています。

## 主要ファイル

| ファイル | 役割 |
| --- | --- |
| `index.html` | メニュー画面 |
| `demo.html` / `demo.js` | APIキー不要のデモモード |
| `upload.html` / `app.js` | 画像・ZIPの解析、解析結果確認、保存 |
| `login.html` | 保存済みレシート画面へ進むための簡易UI |
| `select.html` / `select.js` | 保存済み一覧、詳細、選択削除 |
| `config.js` | Backend API URL切替 |
| `access-guard.js` | メニュー経由の画面遷移を促すUI制御 |
| `styles.css` | 共通スタイル |
| `test/smoke.test.mjs` | Frontend smoke test |

`login.html` と `access-guard.js` は認証・認可機構ではありません。固定値やReferrerを使った画面遷移上の制御であり、セキュリティ境界としては扱いません。

## デモモード

トップ画面の「デモを試す」から利用できます。固定のサンプルレシートと解析結果をFrontendだけで表示するため、Gemini APIキー、Backend、PostgreSQLは不要です。デモ処理では `fetch()` を実行しません。

## 通常解析

1. `index.html` から「レシートを解析」を選択します。
2. `upload.html` でGemini APIキーを入力します。
3. JPEG / PNG、またはJPEG / PNGを含むZIPを1ファイル選択します。
4. 「解析」を押すと、画像を1枚ずつBackendへ送信します。
5. 解析結果を確認後、「PostgreSQLへ保存」を押して保存します。

直接選択できる画像は1枚です。ZIPの場合は内部のJPEG / PNGを順番に処理します。現行実装ではZIP内画像数の固定上限はありません。

## ZIP処理

- ディレクトリエントリは無視します。
- JPEG / PNG以外の通常ファイルが含まれる場合は処理を中断します。
- 画像が1枚もないZIPはエラーです。
- 暗号化ZIPは非対応です。
- Store(0) / Deflate(8)を処理します。
- 展開後のマジックバイトでもJPEG / PNGを確認します。

## APIキー

Gemini APIキーは `upload.html` のpassword入力欄で受け取り、解析リクエストごとに送信します。`localStorage`、`sessionStorage`、ソースコード、データベースへ保存しません。

## 接続先

`config.js` が実行環境に応じて接続先を切り替えます。

- ローカル: `http://localhost:8081`
- 公開環境: `https://receipt-analysis-b8po.onrender.com`

## タイムアウト

- 解析API: 210秒
- 解析中メッセージ更新: 15秒間隔
- 保存API: 30秒
- トップ画面の保存済み一覧存在確認: 120秒

## ローカル起動

```bash
python3 -m http.server 5051 --directory ..
```

ブラウザで `http://localhost:5051` を開きます。プロジェクトルートを配信するため、納品ドキュメントは `http://localhost:5051/doc/` から参照できます。デモだけであればBackendは不要です。

## テスト

```bash
node --test test/smoke.test.mjs
```

2026-09-19の文書再作成時点で18/18 PASSを確認しています。
