# chmate-revanced

ChMate (`jp.co.airfront.android.a2chMate`) 向けの ReVanced Patch です。ChMate 本体の難読化されたクラス名には依存せず、Android / Java の安定した API 境界とリソース構造を対象にします。

## 実装している機能

- スレッド上部およびスレッド途中の広告 View を高さ `0dp`・`GONE` にする
- 実行時に生成される広告 View も Activity のレイアウト更新時に折りたたむ
- ニコニコ動画のサムネイルを公式の動画情報から取得し、取得できない場合はChMate本来の画像取得へ戻す
- サムネイル対応済みの動画リンクに、公開されているタイトル・再生数・コメント数・マイリスト数・いいね数・投稿日・投稿者・概要欄をスレッド内で表示する（サービスが提供する項目のみ）
- 投稿間隔エラーに待機秒数が明記された場合、指定時間を待って返信・新規スレッド作成を最大2回まで自動で再試行する
- 4chan、ふたば、jschan系サイトの板・スレッドを画像付きで読み込み、各サイトの投稿フォームと認証画面をChMate内から開く
- 既存NG設定に加え、項目別の文字列・数値・時間条件をANDで組み合わせ、否定・板/スレッド範囲・連鎖・通常/透明あぼーんを設定する
- 既知の広告ホストを DNS、`URL`、HTTP クライアント、WebView の境界で遮断する
- 既知広告 SDK のクラスから発生する DNS、`URL`、文字列 URL、WebView 通信を送信先にかかわらず遮断する
- 広告 SDK の自動初期化 component と、公開されている初期化・広告 request entry point を無効化する
- Firebase Analytics / Crashlytics / Performance の収集を manifest で停止し、関連テレメトリホストも遮断する
- HTTP ヘッダー、`http.agent`、WebView に設定可能な User-Agent を適用する
- ChMate 本体の「設定」に組み込んだ画面から保存・既定値への復元・ワンボタン再起動を行う
- 再署名された APK を異常終了させる多段の署名整合性チェックを、結果配列の比較構造と限定した失敗処理に基づいて回避する
- 設定画面を日本語、英語、中国語、ヒンディー語、スペイン語、フランス語、アラビア語、ポルトガル語、ベンガル語、ロシア語、ウルドゥー語で表示する

設定画面は ChMate のメニューから「設定」→「ChMate ReVanced」と進んで開きます。独立したランチャーアイコンは追加しません。空欄を保存すると ChMate 本来の User-Agent に戻ります。動画情報は既定で表示し、同画面のスイッチで無効にできます。切り替え後はスレッドを開き直してください。

動画情報カードは一つのレスにつき先頭16件まで表示します。サービス側が公開しない項目は表示しません。ニコニコ動画のいいね数は公式検索APIの日次更新を反映するため、動画ページの値と一時的に差が出る場合があります。

画像掲示板は「設定」→「ChMate ReVanced」→「画像掲示板（4chan・ふたば・jschan）」から開きます。板またはスレッドのHTTPS URLを入力し、「開く」を押してください。板一覧のスレッドをタップするとレスを読めます。「投稿フォーム」は各掲示板の元ページをアプリ内で開くため、CAPTCHA、添付ファイル、削除キーなどはサイトの画面で操作します。4chanの`boards.4channel.org`形式のURLは、AQUOSのWebViewで正常に開けた`boards.4chan.org`へ切り替えて使います。jschan系では対象サイトが公式API形式の`catalog.json`と`thread/{id}.json`を公開している必要があります。

高度なNGは「設定」→「ChMate ReVanced」→「高度なNGルール」で編集します。1ルール内の条件はすべて一致したときに適用されます。文字列には本文・名前・ID・トリップ・ﾜｯﾁｮｲ・IP・BE・URL・スレッド名などを指定でき、含む・正規表現・大小無視の正規表現・あいまい一致から選べます。数値にはレス番号・改行数・文字数・同じIDのレス数・参照数・被参照数・スレッドのレス数、時間には投稿曜日・時刻・投稿からの経過分を指定できます。板またはスレッドのURLを入力すると適用範囲を限定できます。通常あぼーんは「あぼーん」行を残し、透明あぼーんは行と空間を消します。設定後はスレッドを開き直してください。既存ChMateのNG設定はそのまま使えます。

投稿の自動再試行は、サーバーのエラーに待機秒数が明記されている場合だけ動作します。通信障害や秒数のない連続投稿エラーでは再試行しません。待機中はChMateの「書き込み中…」画面が続きます。

## ダウンロード

[GitHub Releases](https://github.com/roflsunriz/chmate-revanced/releases) で配布しています。ReVanced Manager で更新を追従する場合は、Patches 画面の追加操作から「URLを入力」を選び、次の固定 URL を登録してください。

```text
https://github.com/roflsunriz/chmate-revanced/releases/latest/download/patches.json
```

手動で追加する場合は `patches-<version>.rvp` をダウンロードし、「ストレージから選択」で `.rvp` をそのまま読み込みます。ZIP への展開は不要です。

同じ Release にある `patches.json` はその版の `.rvp` を参照する ReVanced API 形式の更新情報、`.rvp.sha256` はダウンロードした patch bundle の照合用です。ChMate APK、XAPK、patch 済み APK は配布しません。

## 互換性の考え方

対象パッケージ名だけを固定し、ChMate のバージョン番号や難読化名は固定していません。

- レイアウトは広告 SDK の View クラス名と限定した広告用 ID / tag で判定する
- 通信は `InetAddress`、`URL`、WebView、一般的な HTTP ヘッダー / URL builder 呼び出しを命令参照で判定する
- 元のランチャー Activity 名はパッチ時に manifest から取得して設定画面へ記録する

この方式はバージョン固定の fingerprint より変更に強い一方、ChMate の実装によっては更新が必要です。たとえば、ネイティブ通信、独自暗号化通信、未登録の広告 SDK、Compose など別の UI 実装へ移行した場合が該当します。「通信を完全に遮断できたこと」は対象 APK と実機でのパケット確認をもって判定してください。

## ビルド

ビルドには次のものが必要です。

- JDK 17（CI と同じメジャーバージョン）
- Android SDK Platform 34
- GitHub Packages を読める GitHub Personal Access Token (`read:packages`)

ReVanced の Gradle plugin と Patcher は GitHub Packages から取得されます。トークンをリポジトリへ保存せず、PowerShell の現在のセッションへ設定してください。

```powershell
$env:ORG_GRADLE_PROJECT_githubPackagesUsername = '<GitHubユーザー名>'
$env:ORG_GRADLE_PROJECT_githubPackagesPassword = '<read:packagesトークン>'
.\gradlew.bat :patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid
```

成果物は `patches/build/libs/patches-<version>.rvp` です。`<version>` は `gradle.properties` の `version` に従います。

認証方法の詳細は [GitHub Packages の公式ドキュメント](https://docs.github.com/ja/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry)を参照してください。

## 適用と確認

1. 自分で正当に入手した ChMate APK または XAPK を用意する。XAPK は全splitを単一APKへ統合してからpatchするか、base APKをpatchした後に全split APKを同じ鍵で再署名する。base APKだけの更新インストールではsplit不足になる場合があります。
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
- 書き込み画面（2026-08-10）: AQUOS SH-R80P / Android 16、ChMate `0.8.10.241`でv1.0.2の書き込み欄を開くと`<include>`の`layout`属性欠落による`InflateException`で終了することを再現。公式ReVanced Manager 2.6.0がv1.0.3候補で生成・署名したAPKをデータ保持更新し、既存スレッドの書き込み欄と新規スレッド作成画面が終了せず開くことを確認
- APK 適用: `0.8.10.165`、`0.8.10.179`、`0.8.10.202 dev`、`0.8.10.241` の4世代で成功
- ニコニコ動画サムネイル修正（2026-10-11）: `getthumbinfo/sm9` が返す画像URLと画像本体を確認。修正したRVPは上記4世代のAPKで公式CLI 6.0.0による適用、再構築、署名に成功。AQUOS上で公開スレッドの`sm33589395`の画像表示と元動画への遷移を確認
- 動画情報と更新インストール（2026-10-11）: 0.8.10.241の4分割XAPKを単一APKへ統合後、公式Managerでパッチ・保存・署名、AQUOS SH-R80P / Android 16へのデータ保持更新と起動に成功。公開スレッドで`sm`・`so`のサムネイルとタイトル・統計値・投稿日・概要欄のカード、YouTube動画のカード、カードの展開・復帰と元動画への遷移を確認。表示スイッチをオフにして開き直すと、サムネイルを残したままカードとその空間が消えることも確認。`nm`はAPI応答と4世代のAPK適用まで確認し、実画面は未確認
- 投稿間隔エラーの再試行（2026-10-11）: 4世代へのCLI適用に成功。AQUOS上でローカル模擬掲示板への返信と新規スレッド作成を試し、2秒指定のエラー後に約3秒で1回再送し、次の別エラーでは追加送信しないことを確認。実際の掲示板への投稿は未実施
- 依存監査（2026-10-11）: Gradle lockfile 3件をOSV-Scannerで検査し、Netty・Bouncy Castleの既知脆弱性を修正版への更新で解消。再検査で検出0件
- 画像掲示板（2026-10-11）: AQUOS上で4chan `/po/`の一覧152件・公開スレッド4レスと、ふたば料理板の一覧10件・返信を含むスレッド8レスを表示。4chanの返信フォームとCloudflare CAPTCHA、ふたばの返信フォームが開くことを確認。外部掲示板への実投稿は未実施
- jschan系（2026-10-11）: 公式README掲載の`zzzchan.xyz`で`/tech/`の一覧142件、画像、スレッド3レス、返信フォームの名前・本文・添付・CAPTCHA欄をAQUOSで確認。実投稿は未実施
- 高度なNG（2026-10-11）: AQUOSのローカル模擬スレッドで、指定本文の2レス目とそれを参照する3レス目が連鎖してあぼーんになることを確認。通常あぼーんでは表示行が残り、透明あぼーんでは両行と空間が消えた。ルールを無効にすると本文が戻り、30レスをスクロール往復しても他のレスは消えなかった。数値・時間は単体テストと編集UIの切替まで確認
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

## 依存更新の自動処理

Dependabot は対象の依存関係を毎週確認します。patch／minor 更新は PR のチェック（CI）が成功した後に自動で squash merge されます。CI の失敗ジョブは 1 回だけ再実行します。再失敗した PR は残して手動で修正します。major 更新は手動で確認します。
