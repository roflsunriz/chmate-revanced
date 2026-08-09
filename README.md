# chmate-revanced

ChMate (`jp.co.airfront.android.a2chMate`) 向けの ReVanced Patch です。ChMate 本体の難読化されたクラス名には依存せず、Android / Java の安定した API 境界とリソース構造を対象にします。

## 実装している機能

- スレッド上部およびスレッド途中の広告 View を高さ `0dp`・`GONE` にする
- 実行時に生成される広告 View も Activity のレイアウト更新時に折りたたむ
- 既知の広告ホストを DNS、`URL`、HTTP クライアント、WebView の境界で遮断する
- 既知広告 SDK のクラスから発生する DNS、`URL`、文字列 URL、WebView 通信を送信先にかかわらず遮断する
- 広告 SDK の自動初期化 component と、公開されている初期化・広告 request entry point を無効化する
- Firebase Analytics / Crashlytics / Performance の収集を manifest で停止し、関連テレメトリホストも遮断する
- HTTP ヘッダー、`http.agent`、WebView に設定可能な User-Agent を適用する
- ChMate 本体の「設定」に組み込んだ画面から保存・既定値への復元・ワンボタン再起動を行う
- 再署名された APK を異常終了させる多段の署名整合性チェックを、結果配列の比較構造と限定した失敗処理に基づいて回避する
- 設定画面を日本語、英語、中国語、ヒンディー語、スペイン語、フランス語、アラビア語、ポルトガル語、ベンガル語、ロシア語、ウルドゥー語で表示する

設定画面は ChMate のメニューから「設定」→「ChMate ReVanced」と進んで開きます。独立したランチャーアイコンは追加しません。空欄を保存すると ChMate 本来の User-Agent に戻ります。

## ダウンロード

[GitHub Releases](https://github.com/roflsunriz/chmate-revanced/releases) から `patches-<version>.rvp` をダウンロードしてください。ReVanced Manager では Patches 画面の追加操作から「ストレージから選択」を選び、`.rvp` をそのまま読み込みます。ZIP への展開は不要です。

同じ Release にある `.rvp.sha256` はダウンロードした patch bundle の照合用です。ChMate APK、XAPK、patch 済み APK は配布しません。

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

成果物は `patches/build/libs/patches-1.0.2.rvp` です。バージョンは `gradle.properties` の `version` に従います。

認証方法の詳細は [GitHub Packages の公式ドキュメント](https://docs.github.com/ja/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry)を参照してください。

## 適用と確認

1. 自分で正当に入手した ChMate APK または XAPK を用意する。XAPK は base APK を patch し、全 split APK を同じ鍵で再署名する。
2. 対応する ReVanced Manager または ReVanced CLI に生成した `.rvp` をカスタム patch bundle として読み込ませる。
3. `ChMate ReVanced` patch を選び、APK を patch・署名・インストールする。
4. スレッド上部と途中に広告用の空白が残らないことを確認する。
5. ChMate の「設定」→「ChMate ReVanced」で User-Agent を保存して再起動し、5ch へのリクエストで値が変わることを確認する。
6. DNS ログまたは端末のパケットキャプチャで広告 SDK の通信が発生しないことを確認する。

ChMate APK や patch 済み APK はこのリポジトリで配布しません。ReVanced の基本的な使用方法は [公式ドキュメント](https://github.com/ReVanced/revanced-documentation)を参照してください。

## 現在の検証状況

- 拡張機能: Android 6 以上向け Debug / Release コンパイル、DEX 化、JUnit、Android Lint 成功
- patch の純粋ロジック: 広告要素分類、署名整合性チェック検出、設定 Activity と本体設定 Preference 生成の JUnit を実装
- patch bundle 全体: 公式 ReVanced CLI 6.0.0 で `.rvp` の読込、resource / DEX patch、zipalign、署名に成功
- ReVanced Manager（2026-08-09）: AQUOS SH-R80P / Android 16、公式ReVanced Manager 2.6.0へ`patches-1.0.2.rvp`をストレージから追加し、`0.8.10.241`の準備、resource / DEX patch、resource再コンパイル、zipalign、署名、patched APK保存がすべて成功
- APK 適用: `0.8.10.165`、`0.8.10.179`、`0.8.10.202 dev`、`0.8.10.241` の4世代で成功
- 構造検証: 全4世代で元の launcher、本体設定に追加した Preference、非公開の設定 Activity / Provider、拡張DEX、署名を確認
- 広告 component: 検出した SDK component を各世代で30/30、35/35、6/6、29/29件無効化済み
- XAPK: `0.8.10.179` の20 APK、`0.8.10.241` の4 APKを同一証明書で再署名し、split setを再構成済み
- 実機起動: Pixel 10a / Android 17 へ `0.8.10.241` の split set をインストールし、「設定」→「ChMate ReVanced」の表示、UAの保存・既定値復元、ワンボタン再起動による PID 変更と本体画面への復帰を確認
- 実画面（2026-08-09）: Pixel 10a / Android 17、ChMate `0.8.10.241`、1080×2424pxで実在する5chスレッドを表示。上部広告修正版では広告コンテナがUIツリーから消え、タイトルViewが`[0,152][1080,247]`から始まるため、従来139pxあったタイトル直上の広告由来追加高は`0px`。レス17〜22のViewGroupは前項の終端と次項の始端が537、858、1283、1660、2037pxで一致し、途中広告由来の空白も`0px`
- UI退行（2026-08-09）: タイトルViewのboundsが`[0,152][1080,247]`で全幅、スレッド一覧を下へ引いた更新spinner、スレッド末尾を上へ引いた「離すと更新」を個別に確認。実ID文字列と同じID行の右側空白を別々にタップし、どちらも同IDポップアップになることを確認
- resource再構築（2026-08-09）: 4世代すべてで`pullSetting`、`targetListId`、ConstraintLayout属性がresource ID付きの整数／参照型として保持され、生文字列へ退行しないことをAAPT2のbinary XML dumpで確認
- User-Agent（2026-08-09）: 「保存して再起動」でmain processが入れ替わった後、ADB reverse先のHTTPサーバーでBBSMENUリクエストを受信。修正前は入力値がChMate既定値へ付加されていたが、最終版では入力した`WireUA-20260809`と受信した`User-Agent`ヘッダーが完全一致。既定値へ戻した後も`egg.5ch.io`の板一覧と実スレッド本文の取得に成功
- 通信遮断（2026-08-09）: AdGuard / VPN / Private DNS / HTTP proxyを無効にした状態で、Amazon Adsの4要求とUnity Adsの1要求がDNS・接続前のpatch境界で例外終了することを確認。Firebaseは`App measurement deactivated via the manifest`を記録し、30秒間のコールドスタート・板一覧・スレッド表示で`FA-SVC`、Crashlytics、firebaseloggingの送信試行は0件。通常の`menu.5ch.io`、`egg.5ch.io`、画像ホスト通信は成功

検証用 APK を用意した後の手順と合格条件は [how-to-update.md](how-to-update.md) にあります。

## ライセンス

GPL-3.0-only。詳細は [LICENSE](LICENSE) を参照してください。
