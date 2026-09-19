# reFront

`reFront` は、レシート解析システムの静的フロントエンドです。HTML、CSS、JavaScriptで構成し、GitHub Pagesで公開できます。

## 画面とファイル

| ファイル | 役割 |
| --- | --- |
| `index.html` | デモ、解析、保存済みレシートへのメニュー |
| `demo.html` | APIキー不要の固定データデモ |
| `upload.html` | APIキー入力、画像／ZIP選択、解析結果確認、保存 |
| `select.html` | 保存済みレシートの一覧、詳細、選択削除 |
| `config.js` | 実行環境に応じたバックエンドURLの切り替え |
| `access-guard.js` | メニュー経由の画面遷移を促すUI制御 |
| `app.js` | 画像・ZIP処理、解析API呼び出し、保存処理 |
| `select.js` | 一覧、詳細、削除の画面処理 |
| `index.js` | 保存済みデータの有無に応じたメニュー制御 |

`access-guard.js` は認証機能ではなく、直接アクセス時の画面遷移を整えるためのUI制御です。

## デモ

`index.html` の「デモを試す」から利用できます。デモはフロントエンド内の固定データだけで動作し、Gemini API、バックエンドAPI、PostgreSQLへ接続しません。そのためGemini APIキーは不要です。

## 接続先

`config.js` が実行環境を判定して接続先を切り替えます。

- ローカル：`http://localhost:8081`
- 公開環境：`https://receipt-analysis-b8po.onrender.com`

## レシート解析

1. メニューから「レシートを解析」を選択します。
2. `upload.html` でGemini APIキーを入力します。
3. JPEG / PNG画像、または画像を含むZIPを1つ選択します。
4. 「解析」を押して、画像を1枚ずつ解析します。
5. 内容を確認し、「PostgreSQLへ保存」を押します。

直接選択した画像は1枚、ZIP内のJPEG / PNGは順番に処理します。解析と保存は別操作です。

## ZIPの扱い

- ディレクトリエントリは読み飛ばします。
- サブディレクトリ内のJPEG / PNGは処理対象です。
- JPEG / PNG以外のファイルを含む場合は処理を中断します。
- 画像が1枚もないZIPはエラーにします。
- 暗号化ZIPと未対応の圧縮方式には対応しません。
- 拡張子だけでなく、展開後のデータ形式も確認します。

## APIキーの扱い

APIキーは入力欄から解析リクエスト時だけバックエンドへ送信します。`localStorage`、`sessionStorage`、ソースコード、データベースには保存しません。利用上限超過やキー拒否が発生した場合は、別のキーに入れ替えて再解析できます。

## ローカル起動

```bash
python3 -m http.server 5051
```

ブラウザで `http://localhost:5051` を開きます。デモだけならバックエンドは不要です。通常の解析と保存済みレシート画面を使う場合は、別ターミナルで `reBack` を `http://localhost:8081` として起動します。

## GitHub Pagesへの公開

`reFront/` 配下をGitHub Pagesの公開対象にします。公開環境では `config.js` がRenderのバックエンドURLを使用します。

## テスト

```bash
node --test test/smoke.test.mjs
```

ファイル形式、ZIP処理、タイムアウト、APIキーの非永続化、画面遷移、解析と保存の分離、一覧・詳細・削除を確認します。
