# フロントエンド

reFront/には利用者向けの静的ページとブラウザー側のJavaScript/CSSがあります。

## ページ

- index.html — デモ、解析、保存済みレシート確認への入口
- demo.html — 固定サンプルを使うAPIキー不要のデモ

管理者ログイン、画像解析、保存済みレシート管理のHTMLはreBackのstatic/adminから配信されます。

## ローカル利用

バックエンドとPostgreSQLを起動した後、index.htmlをブラウザーで開きます。config.jsはfileまたはlocalhost環境でlocalhost:8081をAPI接続先に設定します。

Geminiによる解析では画面にAPIキーを入力します。デモ画面は固定データを使用し、Gemini APIを呼び出しません。解析画面はJPEG/PNG画像と、JPEG/PNGを含むZIPファイルを扱います。
