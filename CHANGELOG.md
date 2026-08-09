# 変更履歴

このプロジェクトは [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) に従い、バージョン番号は [Semantic Versioning](https://semver.org/lang/ja/) に従います。

## [Unreleased]

### Added

- ChMate の静的・動的広告 View を高さゼロにする resource / runtime patch
- 既知広告ホストと広告 SDK 起点の通信を遮断する bytecode patch
- HTTP、WebView、`http.agent` に適用する User-Agent 設定
- ChMate 本体の設定へ組み込む多言語設定画面とワンボタン再起動
- 広告要素分類と広告ホスト判定の単体テスト
- Android 6 以上向け拡張機能の Debug / Release build と Lint 構成

### Fixed

- APK の再署名後に ChMate の FileProvider または設定画面の多段初期化から異常終了する署名整合性チェックを、難読化名に依存しない結果配列比較と限定した失敗処理の検出で回避
- 設定画面をホストプロセス内で動かして ChMate Application の初期化漏れを防ぎ、main Activity を新しい task として起動してから現在プロセスを終了する即時再起動方式へ変更
- 実在する4世代のChMateで広告SDK通信が初期化段階から始まらないように、Google Mobile Ads内部packageとNend SDKの分類、広告SDK manifest componentの無効化、広告request entry pointの置換を追加
- 難読化された内部classの `$` を含む不正なXML要素名でresource再コンパイルが失敗しないように、Android inflater互換の `<view class="...">` 形式へ正規化
- ハイフンを含むR8生成コンポーネント名を、マニフェストとDEXの両方で再コンパイル可能な名前へ正規化
- 数値だけのresource名・参照・ファイル名を、public IDを維持したままAAPT2互換名へ正規化
- APK直下へ難読化された画像・binary XML resourceを標準resource directoryへ復元
- 実機計測で判明したFirebase Analytics / Crashlytics / Performanceの収集経路をmanifestで無効化し、関連テレメトリホストも接続前に遮断
- ChMateが組み立てた最終User-Agentを安定した`Monazilla/1.00`リテラルで検出し、設定値を接尾辞として付加するのではなくHTTPヘッダー全体へ適用

### Security

- GitHub Packages token を環境変数だけから受け取り、リポジトリへ保存しない構成
