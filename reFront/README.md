# Frontend

公開トップ画面とAPIキー不要のデモを含む静的HTML/CSS/JavaScriptです。GitHub Pagesでは一般公開を維持します。デモはサンプルデータをブラウザ内で表示し、Backend、Gemini API、データベースへリクエストしません。

## 公開画面

- `index.html` / `index.js`: 公開トップ画面。BackendへのAPIリクエストを行わず、管理画面へのリンクを表示します。
- `demo.html` / `demo.js`: Backend非接続のデモ。
- `config.js`: 公開トップからBackendの管理画面URLへ誘導します。

## Backend管理画面

ログイン、解析、保存、一覧、詳細、削除の画面はBackendから同一オリジンで配信されます。URLは `https://<backend-host>/admin/login.html` です。Frontendリポジトリ内の `upload.html`、`select.html`、`login.html` はBackendに配置する画面の編集元です。GitHub Pages上の公開画面から管理APIを呼び出しません。

認証はBackendセッションCookie、状態変更のCSRF対策は `X-XSRF-TOKEN` ヘッダーを使います。画面制御やReferrerを認証の代わりにしません。

## ローカル確認

公開画面とデモ:

```bash
python3 -m http.server 5051 --directory .
```

Backend画面を使う場合はBackendを起動し、`http://localhost:8081/admin/login.html` を開きます。

## テスト

```bash
node --test test/smoke.test.mjs
```
