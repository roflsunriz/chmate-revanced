# 変更履歴

このプロジェクトは [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) に従い、バージョン番号は [Semantic Versioning](https://semver.org/lang/ja/) に従います。

## [Unreleased]

### Added

- ChMate の静的・動的広告 View を高さゼロにする resource / runtime patch
- 既知広告ホストと広告 SDK 起点の通信を遮断する bytecode patch
- HTTP、WebView、`http.agent` に適用する User-Agent 設定
- 別プロセスの多言語設定画面とワンボタン再起動
- 広告要素分類と広告ホスト判定の単体テスト
- Android 6 以上向け拡張機能の Debug / Release build と Lint 構成

### Security

- GitHub Packages token を環境変数だけから受け取り、リポジトリへ保存しない構成
