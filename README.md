# chmate-revanced

ChMate (`jp.co.airfront.android.a2chMate`) 向けの ReVanced Patch です。ChMate 本体の難読化されたクラス名には依存せず、Android / Java の安定した API 境界とリソース構造を対象にします。

## 実装している機能

- スレッド上部およびスレッド途中の広告 View を高さ `0dp`・`GONE` にする
- 実行時に生成される広告 View も Activity のレイアウト更新時に折りたたむ
- 既知の広告ホストを DNS、`URL`、HTTP クライアント、WebView の境界で遮断する
- 既知広告 SDK のクラスから発生する DNS、`URL`、文字列 URL、WebView 通信を送信先にかかわらず遮断する
- 広告 SDK の自動初期化 component と、公開されている初期化・広告 request entry point を無効化する
- HTTP ヘッダー、`http.agent`、WebView に設定可能な User-Agent を適用する
- 独立した設定画面から保存・既定値への復元・ChMate のワンボタン再起動を行う
- 設定画面を日本語、英語、中国語、ヒンディー語、スペイン語、フランス語、アラビア語、ポルトガル語、ベンガル語、ロシア語、ウルドゥー語で表示する

設定画面はパッチ適用後に追加される「ChMate ReVanced」ランチャーアイコンから開きます。空欄を保存すると ChMate 本来の User-Agent に戻ります。

## 互換性の考え方

対象パッケージ名だけを固定し、ChMate のバージョン番号や難読化名は固定していません。

- レイアウトは広告 SDK の View クラス名と限定した広告用 ID / tag で判定する
- 通信は `InetAddress`、`URL`、WebView、一般的な HTTP ヘッダー / URL builder 呼び出しを命令参照で判定する
- 元のランチャー Activity 名はパッチ時に manifest から取得して設定画面へ記録する

この方式はバージョン固定の fingerprint より変更に強い一方、将来の ChMate がネイティブ通信、独自暗号化通信、未登録の広告 SDK、Compose など別の UI 実装へ移行した場合は更新が必要です。「通信を完全に遮断できたこと」は対象 APK と実機でのパケット確認をもって判定してください。

## ビルド

必要なもの:

- JDK 17 以上
- Android SDK Platform 34
- GitHub Packages を読める GitHub Personal Access Token (`read:packages`)

ReVanced の Gradle plugin と Patcher は GitHub Packages から取得されます。トークンをリポジトリへ保存せず、PowerShell の現在のセッションへ設定してください。

```powershell
$env:ORG_GRADLE_PROJECT_githubPackagesUsername = '<GitHubユーザー名>'
$env:ORG_GRADLE_PROJECT_githubPackagesPassword = '<read:packagesトークン>'
.\gradlew.bat :patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid
```

成果物は `patches/build/libs/patches-0.1.0.rvp` です。バージョンは `gradle.properties` の `version` に従います。

認証方法の詳細は [GitHub Packages の公式ドキュメント](https://docs.github.com/ja/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry)を参照してください。

## 適用と確認

1. 自分で正当に入手した ChMate APK または XAPK を用意する。XAPK は base APK を patch し、全 split APK を同じ鍵で再署名する。
2. 対応する ReVanced Manager または ReVanced CLI に生成した `.rvp` をカスタム patch bundle として読み込ませる。
3. `ChMate ReVanced` patch を選び、APK を patch・署名・インストールする。
4. スレッド上部と途中に広告用の空白が残らないことを確認する。
5. 設定画面で User-Agent を保存して再起動し、5ch へのリクエストで値が変わることを確認する。
6. DNS ログまたは端末のパケットキャプチャで広告 SDK の通信が発生しないことを確認する。

ChMate APK や patch 済み APK はこのリポジトリで配布しません。ReVanced の基本的な使用方法は [公式ドキュメント](https://github.com/ReVanced/revanced-documentation)を参照してください。

## 現在の検証状況

- 拡張機能: Android 6 以上向け Debug / Release コンパイル、DEX 化、JUnit、Android Lint 成功
- patch の純粋ロジック: 広告要素分類の JUnit を実装
- patch bundle 全体: 公式 ReVanced CLI 6.0.0 で `.rvp` の読込、resource / DEX patch、zipalign、署名に成功
- APK 適用: `0.8.10.165`、`0.8.10.179`、`0.8.10.202 dev`、`0.8.10.241` の4世代で成功
- 構造検証: 全4世代で元の launcher、追加した設定 Activity / Provider、拡張DEX、署名を確認
- 広告 component: 検出した SDK component を各世代で30/30、35/35、6/6、29/29件無効化済み
- XAPK: `0.8.10.179` の20 APK、`0.8.10.241` の4 APKを同一証明書で再署名し、split setを再構成済み
- 未実施: 接続端末がないため、インストール、画面上の広告高さ、User-Agent、再起動、パケット通信の実機確認

検証用 APK を用意した後の手順と合格条件は [how-to-update.md](how-to-update.md) にあります。

## ライセンス

GPL-3.0-only。詳細は [LICENSE](LICENSE) を参照してください。
