# reFront

GitHub Pagesで公開する、レシート解析システムのフロントエンドです。HTML、CSS、JavaScriptで構成されています。

## 使い方

1. `config.js` のバックエンドURLを、利用する環境に合わせて設定します。
2. ブラウザで `index.html` を開き、レシート一覧や詳細を確認します。
3. `upload.html` でGemini APIキーとJPEG / PNG画像、または画像を含むZIPファイルを選択します。
4. 「解析」を押すと、画像を1枚ずつ解析して抽出テキストを表示します。直接画像は1枚、ZIPの場合は内部画像を順番に処理します。現行実装ではZIP内画像数の固定上限はありません。
5. 内容を確認し、「PostgreSQLへ保存」を押すと未登録のレシートだけを保存します。

ZIPファイル内はサブディレクトリを無視して検索します。JPEG / PNG以外のファイルが含まれるZIPや、画像が含まれないZIPは処理できません。

## Gemini APIキー

Gemini APIキーは `upload.html` のパスワード入力欄へ入力してください。入力したキーはリクエスト時だけバックエンドへ送信し、ソースコード、localStorage、sessionStorage、データベースには保存しません。

利用上限またはキー拒否エラーが発生した場合は、入力欄のキーを入れ替えて同じ画像を再解析できます。

## 保存処理

「解析」では画像のSHA-256を重複チェック用に登録しますが、レシート本文は保存しません。本文の保存は「PostgreSQLへ保存」を押したときだけ実行されます。

保存済み画像は409エラーとして扱います。現行実装では解析または保存中にエラーが発生すると、その処理ループは中断します。

## 設定

`config.js` の `API_BASE_URL` にバックエンドのURLを設定します。ローカル開発時の標準バックエンドURLは次のとおりです。

```text
http://localhost:8081
```

## ローカル起動

```bash
python3 -m http.server 5051
```

ブラウザで `http://localhost:5051` を開いてください。

## GitHub Pagesへの公開

このディレクトリのHTML、CSS、JavaScript、設定ファイルをGitHub Pagesへ公開します。公開前に `config.js` のバックエンドURLを本番環境のURLへ変更してください。

## テスト

```bash
node --test test/smoke.test.mjs
```
