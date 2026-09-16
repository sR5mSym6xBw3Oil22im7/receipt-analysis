# reFront

`reFront` は、レシート解析システムの静的フロントエンドです。HTML / CSS / JavaScriptで構成し、GitHub Pagesでの公開を想定しています。

## 画面構成

| ファイル | 役割 |
| --- | --- |
| `index.html` | メニュー画面。解析画面と保存済みレシート画面への入口 |
| `upload.html` | JPEG / PNG / ZIPの選択、Gemini APIキー入力、解析結果確認、PostgreSQL保存 |
| `select.html` | 保存済みレシート一覧、詳細表示、選択削除 |
| `config.js` | 接続先バックエンドURLの切り替え |
| `access-guard.js` | `upload.html` / `select.html` の直接アクセスをメニューへ戻すUI制御 |
| `app.js` | 画像・ZIP処理、解析API呼び出し、保存処理 |
| `select.js` | 保存済みレシートの一覧・詳細・削除処理 |
| `index.js` | 保存済みデータ有無に応じたメニュー表示制御 |

`access-guard.js` は画面遷移を制御するための仕組みであり、認証機能ではありません。

## 接続先バックエンド

`config.js` が実行環境を判定してAPI接続先を切り替えます。

- ローカル: `http://localhost:8081`
- 公開環境: `https://receipt-analysis-b8po.onrender.com`

## レシート解析の使い方

1. `index.html` から「レシートを解析」を選択します。
2. `upload.html` でGemini APIキーを入力します。
3. JPEG / PNG画像、または画像を含むZIPファイルを1つ選択します。
4. 「解析」を押すと、画像を1枚ずつバックエンドへ送信し、抽出結果を画面へ表示します。
5. 内容を確認した後、「PostgreSQLへ保存」を押すと未保存の解析結果を順次保存します。

直接選択できる画像は1枚です。ZIPの場合は内部のJPEG / PNGを順番に処理するため、複数枚をまとめて解析できます。現行実装ではZIP内画像数の固定上限は設けていません。

## ZIPファイルの扱い

- ZIP内のディレクトリエントリは読み飛ばします。サブディレクトリ配下にあるJPEG / PNGファイル自体は処理対象です。
- JPEG / PNG以外のファイルが含まれている場合は処理を中断します。
- 画像が1枚も含まれていない場合はエラーにします。
- 暗号化ZIPと未対応の圧縮方式は処理できません。
- 拡張子だけでなく、展開後のデータがJPEG / PNGかも確認します。

## Gemini APIキー

Gemini APIキーは `upload.html` のパスワード入力欄へ入力します。入力値は解析リクエスト時だけバックエンドへ送信し、`localStorage`、`sessionStorage`、ソースコード、データベースには保存しません。

Gemini APIの利用上限超過やキー拒否が発生した場合は、画面で別のAPIキーへ入れ替えて再解析できます。

## 解析と保存

「解析」と「PostgreSQLへ保存」は別操作です。

- 解析: `/api/receipts/analyze` を呼び出し、抽出行・SHA-256・構造化データを受け取ります。
- 保存: `/api/receipts/save` を呼び出し、解析結果をPostgreSQLへ保存します。

解析APIの待機上限は210秒、保存APIの待機上限は30秒です。解析中は一定時間ごとに待機メッセージを更新します。

## 保存済みレシート

`index.js` は起動時に保存済みレシート一覧を確認し、0件の場合は「保存済みレシートを見る」を非表示にします。

`select.html` では次の操作ができます。

- 保存済みレシート一覧の表示
- レシート詳細の参照
- チェックしたレシートの削除

## ローカル起動

静的ファイルとして配信します。

```bash
python3 -m http.server 5051
```

ブラウザで次を開きます。

```text
http://localhost:5051
```

バックエンドは別途 `http://localhost:8081` で起動してください。

## GitHub Pagesへの公開

`reFront/` 配下をGitHub Pagesで公開します。公開環境では `config.js` がRenderのバックエンドURLを使用します。

## テスト

```bash
node --test test/smoke.test.mjs
```

テストでは、ファイル形式、ZIP処理、タイムアウト、APIキーの非永続化、画面遷移、解析と保存の分離、保存済みレシートの参照・削除などを確認します。
