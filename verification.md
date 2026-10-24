# 検証手順

## 2026-10-24: v1.2.0 画像掲示板・高度NG統合と並列更新

- Pixel 10aへv1.2.3をインストール後、スレッドを開くと`ResListActivity.onStart(:727)`のNPEで断続的に終了することをcrash bufferで4件確認した。246 devのdecompile結果では`onStart`と`onResume`の成功経路がsuper呼出しだけで、内部counterの分岐がnull throw等へ入るため、v1.2.4で両ライフサイクルを直接superへ委譲する。
- v1.2.4候補をPixel 10aへデータ保持更新後、したらばの同一スレッド「AA保管庫」を20回連続で開閉した。20/20回で`ResListActivity`がtop resumed activityとなり、ChMate processは同じPIDで継続し、crash bufferは空だった。
- 続くホーム全メニュー検査で「スレ検索」を開いた際に`WebViewActivity.onStart(:653)`のNPEを検出したため、同Activityもv1.2.4のライフサイクル修正対象へ追加した。
- スレッドtoolbarの「書き込む」で`ResEditActivity.onStart(:598)`のArithmeticExceptionを検出したため、同Activityもv1.2.4のライフサイクル修正対象へ追加した。
- 246 devのdecompile結果をActivity全体へ横断し、Home・Search・ThreadListにもライフサイクルtrapを確認した。対象7 Activityのうちsuper同名呼出しとthrow／divide opcodeが共存するmethodだけを修正する構造判定へ一般化した。
- 再検査で返信ダイアログは`onStart`を通過したが、`ResEditActivity.onResume(:521)`の`NegativeArraySizeException`を検出した。負数長の`NEW_ARRAY`もsuper同名呼出しと共存する場合に限ってライフサイクルtrapとして修正する。
- 最終候補v1.2.4eで、ホーム5 tab・12メニュー、設定14項目、画像掲示板／高度NG／ChMate ReVancedの3直接画面、NG編集6 tab、スレッドtoolbar 5操作、コピー／共有／次スレ作成／板へ／URL入力を開閉した。返信・次スレ作成dialog、共有chooser、検索・抽出、リストコントローラー、板一覧、WebView、Archiveも表示できた。「今すぐ再起動」でPID変更とHome復帰も確認した。
- 最終候補で同一スレッドを再度20回連続開閉し20/20回成功した。更新buttonとpull-to-refresh後もprocessは生存し、全UI回帰検査後のAndroid crash bufferは空だった。外部投稿、履歴削除、NG追加の保存など不可逆・外部副作用を伴う確定操作は行わず、画面・dialog表示とcancel／backまで確認した。
- v1.2.4のclean状態で`:patches:test`、`:extensions:chmate:test`、`:extensions:chmate:lint`、`:patches:buildAndroid`が成功した。同じ最終RVPを公式CLI 6.0.0で165、統合済み179 XAPK、202 dev、統合済み241 XAPK、244 dev、245 dev、246 devへ適用し、全7世代で`"ChMate ReVanced" succeeded`、resource・全DEX再構築、zipalign、署名済みAPK保存を確認した。ローカルRVPのSHA-256は`2a7ee15f09e391d347fbfb7acc523c8f1f6c14494c9f5405add01832946e48a2`だった。Pixel実機候補との差分は42 entry中`META-INF/MANIFEST.MF`だけだった。
- OSV-Scanner 2.7.0でpatches lockfileの68 packageとextension lockfileの176 packageを検査し、既知脆弱性0件を確認した。
- main CI run `40106014071`と公開前Release run `40106203250`が成功した。annotated tag `v1.2.4`起点のRelease run `40106265739`も成功し、公開資産はRVP・checksum・`patches.json`の3点だけだった。公開RVPのSHA-256は`787638c38202238761256bec60a4fc8ba7f14c3edc5c6962f9e120ea8e4e93c8`で同梱checksumと一致し、latest APIもv1.2.4を指した。
- Pixel 10aのManagerからローカル候補sourceを除去し、固定`releases/latest/download/patches.json`を再登録した。Managerが`ChMate ReVanced Patches v1.2.4`と1 patchを取得することを確認した。
- Pixel 10a / Android 17 / ReVanced Manager 2.6.0でv1.2.2を246 devへ適用すると、resource decode中に`/.local/share/apktool/framework`を作成できないlogが大量発生し、Binderの`DeadObjectException`を経て`ProcessRuntime.RemoteFailureException`となることを再現した。v1.2.3ではapktool framework保存先をprocess cacheへ切り替える。
- v1.2.3候補では同じPixel 10aで準備2/2、patch適用2/2、保存2/2まで完走し、全9 DEX、resource、zipalign、Manager署名済みAPKの生成を確認した。`/.local/share`のframework作成失敗、Binder `DeadObjectException`、`RemoteFailureException`はいずれも再発しなかった。端末へのインストールは利用者の明示承認待ちとした。
- v1.2.3のclean状態で`:patches:test`、`:extensions:chmate:test`、`:extensions:chmate:lint`、`:patches:buildAndroid`が成功した。同じ最終RVPを公式CLI 6.0.0で165、統合済み179 XAPK、202 dev、統合済み241 XAPK、244 dev、245 dev、246 devへ適用し、全7世代で`"ChMate ReVanced" succeeded`、resource・全DEX再構築、zipalign、署名済みAPK保存を確認した。ローカルRVPのSHA-256は`80da098cdf24b3a2d8196bfa0557b06e5c07fdb3b7d01354db7c4f79d96e32c3`だった。Pixel実機候補との差分は42 entry中`META-INF/MANIFEST.MF`だけだった。
- OSV-Scanner 2.7.0でpatches lockfileの68 packageとextension lockfileの176 packageを検査し、既知脆弱性0件を確認した。
- main CI run `40101307883`と公開前Release run `40101494225`が成功した。annotated tag `v1.2.3`起点のRelease run `40101538222`も成功し、公開資産はRVP・checksum・`patches.json`の3点だけだった。公開RVPのSHA-256は`9eb2c461da1a6ff4d9fac8a61aa7e32f929ca061d5f81c3a71ec322573056515`で同梱checksumと一致し、latest APIもv1.2.3を指した。
- Pixel 10aのManagerからローカル候補sourceを除去し、固定`releases/latest/download/patches.json`を再登録した。Managerが`ChMate ReVanced Patches v1.2.3`と1 patchを取得することを確認し、端末上の候補RVPを削除した。
- 固定URLから取得した公開v1.2.3をPixel 10aで246 devへ再適用し、候補版と同じく準備・patch・保存が各2/2で完了した。framework保存先エラーとremote process終了は再発しなかった。
- v1.2.1をManagerで246 devへデータ保持更新すると、versionCode 516、versionName `0.8.10.246 dev`、ReVanced証明書SHA-256 `cddbc1c44abb5b0bc4efab2b9e82291758db40409082c7dda55cd1d771459ab0`でインストールは成功した。一方、初回起動で`IntListPreference.<init>`がNPEとなった。patch済みAPKを復号するとroot配置binary XMLのPreference属性が消失しており、文字列pool indexが欠落した属性名をresource IDから復元するv1.2.2修正へ移行した。
- v1.2.2候補を公式Manager 2.6.0で246 devへデータ保持更新し、ホーム、Compose設定、掲示板設定、NG設定、板一覧を開けることを確認した。掲示板設定下部の「画像掲示板の板」から3種のsourceを保存するとBBSMENU URL 2へ`http://127.0.0.1:28282/bbsmenu`が入り、板一覧更新は1945板を処理した。「URLを開く」に`https://boards.4chan.org/g/`を入力するとChMate標準スレ一覧画面で同URLを開いた。NG設定下部の「高度なNGルール」からルール画面へ直接遷移した。更新ボタンとpull-to-refresh後もChMate processが継続した。
- v1.2.2のclean状態で`:patches:test`、`:extensions:chmate:test`、`:extensions:chmate:lint`、`:patches:buildAndroid`が成功した。同じ最終RVPを公式CLI 6.0.0で165、統合済み179 XAPK、202 dev、統合済み241 XAPK、244 dev、245 dev、246 devへ適用し、全7世代で`"ChMate ReVanced" succeeded`、resource・全DEX再構築、zipalign、署名済みAPK保存を確認した。ローカルRVPのSHA-256は`b76a0a889ac910371ea2fb2ff9e80cd4be65f9c8f22de42f4010fdf68afb3e0d`だった。
- OSV-Scanner 2.7.0でpatches lockfileの68 packageとextension lockfileの176 packageを検査し、既知脆弱性0件を確認した。
- main CI run `40098333136`と公開前Release run `40098525678`が成功した。annotated tag `v1.2.2`起点のRelease run `40098590879`も成功し、公開資産はRVP・checksum・`patches.json`の3点だけだった。公開RVPのSHA-256は`2301142fb3ec3b7b9ca742d795a7562e6a421f8bcf93129342f68d0389543957`で同梱checksumと一致し、latest APIもv1.2.2を指した。
- AQUOSのManagerから全ローカル候補sourceを除去し、固定`releases/latest/download/patches.json`を再登録した。Managerが`ChMate ReVanced Patches v1.2.2`と1 patchを取得することを確認し、端末上の候補RVPを削除した。
- v1.2.1候補をAQUOS SH-R80P / Android 16の公式ReVanced Manager 2.6.0へ読み込み、0.8.10.246 devで`ChMate ReVanced`適用、全10 DEXのコンパイル、resourceコンパイル、zipalign、Manager署名まで完了した。候補とclean再ビルドRVPは42 entry中`META-INF/MANIFEST.MF`だけが異なり、patch classと拡張DEXは同一だった。インストールは共通手順に従い利用者の明示承認待ちとした。
- v1.2.1のclean build後、公式CLI 6.0.0で165、統合済み179 XAPK、202 dev、統合済み241 XAPK、244 dev、245 dev、246 devへ同一RVPを適用し、全7世代で`"ChMate ReVanced" succeeded`、resource・全DEX再構築、zipalign、署名済みAPK保存を確認した。
- v1.2.1のclean状態で`:patches:test`、`:extensions:chmate:test`、`:extensions:chmate:lint`、`:patches:buildAndroid`が成功した。ローカルRVPは42 entryで、SHA-256は`41692feb214ba022e40ab24605de17406189b46061723d820f561100d8f33479`だった。
- mainへのpush後、GitHub Actions CI run `40090485509`でも同じJUnit・Android Lint・RVP buildが成功した。
- 文書更新後のCI run `40090641248`と公開前Release run `40090645487`が成功した。annotated tag `v1.2.1`起点のRelease run `40090690578`も成功し、`patches-1.2.1.rvp`、checksum、`patches.json`の3資産だけを公開した。公開RVPのSHA-256は`940835b94cf22607807e1a3ed58f182d794a0f158e78c17a99b7b5593b7e8d3c`で同梱checksumと一致し、latest APIと`patches.json`はいずれもv1.2.1を指した。
- AQUOSのManagerからローカル候補sourceを除去し、README記載の固定`releases/latest/download/patches.json`を再登録した。Managerが`ChMate ReVanced Patches v1.2.1`と1 patchを取得することを確認し、端末上の候補RVPと0 byteの保存試行fileを削除した。
- OSV-Scanner 2.7.0でpatches lockfileの68 packageとextension lockfileの176 packageを検査し、既知脆弱性0件を確認した。settings lockfileは対象packageがなくextractor対象外だった。
- 公開後の固定URL v1.2.0をManagerへ更新して246 devを再適用すると、binary XMLのnull値・不正属性名は解消したが、`android:key`を失った掲示板設定を検出できず停止した。BBSMENU Preferenceクラスによるv1.2.1候補でもManager復号結果にはクラス名が残らなかったため、旧版のXML統合を保ちつつ、Compose版は実行時の「掲示板設定／BBSMENU」表示から同画面を識別するfallbackへ変更した。

- `ParallelRefreshBridgeTest`で同一板4件を4ワーカーへ分割し、実行区間が重なることとエラー件数の集約を確認した。別板は板単位で分割する。新版`AutoCheckWork`と旧版`ReloadBookmarkTask2`の逐次method入口へ同じbridgeを注入し、worker側だけ元methodを実行するDEXを確認した。
- `ImageBoardIntegrationTest`で4chanの板・スレッドURLをloopbackの板URL・`test/read.cgi`へ変換し、ChMateが扱えない短いjschan IDを安定した9桁IDへ変換すること、一般HTTPS URLをjschanとして誤変換しないことを確認した。246 devの復号後掲示板設定XMLに`ImageBoardSourcesActivity`へのPreference、manifestに非公開ActivityとProvider、拡張DEXに`BBSMENU`・`subject.txt`・`dat`変換サーバーを確認した。
- JDK 17のclean後に`:patches:test`、`:extensions:chmate:test`、`:extensions:chmate:lint`、`:patches:buildAndroid`が成功した。ローカルRVPは42エントリ、SHA-256は`2d6e3f96f083fa547db7eac3f99e1454572292e2a575800d8d133355f84bce5d`で、Android DEXの`extensions/chmate.rve`とdeep integration／parallel refresh patchを含み、APK/XAPKを含まない。
- OSV-Scanner 2.7.0で`patches/gradle.lockfile`の68パッケージと`extensions/chmate/gradle.lockfile`の176パッケージを検査し、既知脆弱性の検出は0件だった。`settings-gradle.lockfile`は依存エントリを持たず抽出対象にならない。GitHub Dependabotの未解決アラートとopen PRも0件だった。
- 179の20 moduleと241の4 moduleはAPKEditor 1.4.9で統合した。同じ`patches-1.2.0.rvp`を165、179、202 dev、241、244 dev、245 dev、246 devへ公式CLI 6.0.0で適用し、全7世代で`ChMate ReVanced succeeded`、resource・全DEX再構築、zipalign、署名済みAPK保存を確認した。
- AQUOS SH-R80P / Android 16の公式Manager 2.6.0では、初回候補で246 devのbinary XML属性値がnullとなる失敗、次候補で属性名が空の`android:`となる失敗を再現した。型付きraw値の復元と名前なし属性だけの除外を追加し、JUnitと7世代CLIを再実行した。固定URLソースを残したまま複数の同名ローカル候補を追加するとAPKのパッチ選択画面が新しい候補を列挙しないため、公開後に固定URLをv1.2.0へ更新してManagerの最終適用を行う。
- `main`のCI [run 40087070199](https://github.com/roflsunriz/chmate-revanced/actions/runs/40087070199)と公開前Release workflow [run 40087075212](https://github.com/roflsunriz/chmate-revanced/actions/runs/40087075212)は成功した。Actions artifactのRVPはSHA-256 `5ea631c9469eebeb4bb3c2d458425c72255900c2e790c3b87d2f8d64e73fa02b`。ローカル版とZIP entry・patch classは一致した。拡張DEXからJADXで双方765ソースを抽出したところ、Windows/LinuxのD8によるlambda・nest accessor番号は異なるが、`app.revanced.extension.chmate`のmethod本体・文字列・型に実装差がないことを確認した。

## 2026-10-19: CI高速化

- 変更したGitHub Actionsをactionlint 1.7.12で検査し、エラー0件を確認した。
- GitHub上の未解決Dependabot更新PRと依存脆弱性アラートがともに0件であることを確認した。
- CIと同じGradleタスクへ`--parallel --build-cache --dry-run`を指定し、タスクグラフ生成が成功した。

## 2026-10-12: v1.1.0 公開前検証

- `main`の機能実装コミット`7429e67`に対し、`CHANGELOG.md`、`README.md`、リリースノート、`gradle.properties`のバージョンだけを更新した状態で、JDK 17の`clean :patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid`が成功した。
- ローカルの`patches-1.1.0.rvp`は38エントリで、patchクラスと`extensions/chmate.rve`を含む。`.rve`の先頭はAndroid DEXの`dex\n035`で、APK/XAPKは含まない。SHA-256は`5ed722850d566f115b0d8a631996ba3335f5a74c76a62f7b5735e76b599bc7d4`。
- OSV-Scanner 2.6.0で`patches/gradle.lockfile`の68パッケージと`extensions/chmate/gradle.lockfile`の176パッケージを再検査し、既知脆弱性の検出は0件だった。`settings-gradle.lockfile`は`empty=incomingCatalogForLibs0`のみで、同ツールの依存抽出対象にならない。GitHub Dependabotの未解決アラートも0件だった。
- 実機と4世代CLIの機能確認は2026-10-11の各節に記録した。今回の版番号変更はpatchコードを変更しない。
- `main`のCI [run 38157211750](https://github.com/roflsunriz/chmate-revanced/actions/runs/38157211750)と、公開前のRelease workflow [run 38157222467](https://github.com/roflsunriz/chmate-revanced/actions/runs/38157222467)は、同じ`4a96cd1`で成功した。ローカルとActionsのRVPは38エントリ中manifestの`Timestamp`だけが異なり、Android DEXを含む実装部分は一致した。
- 注釈付きタグ`v1.1.0`からの[公開workflow](https://github.com/roflsunriz/chmate-revanced/actions/runs/38157488780)は成功し、[Release](https://github.com/roflsunriz/chmate-revanced/releases/tag/v1.1.0)にRVP、SHA-256、`patches.json`の3資産だけを公開した。配布RVPのSHA-256は`d1d86da0f542a5f5dd3cce81d1781cc603089ba1c9a9b037e9b6ac0de9872c9d`で、同梱の照合ファイルと一致した。READMEの固定URLはHTTP 200で`v1.1.0`のJSONを返し、同じタグのRVPを指した。
- AQUOSの公式ReVanced Manager 2.6.0で固定URLを自動更新付きソースとして追加すると、エラーなく`v1.1.0`・1パッチを表示した。前回ストレージから読み込んだ`1.0.3`ソースも端末に残るため、選択時は版番号を確認する。

## 2026-10-11: 高度NGルール

ローカル模擬掲示板を再現する場合は、PCで`python .\scripts\mock-chmate-board.py`を起動し、別の端末で`adb reverse tcp:80 tcp:8765`を実行する。ChMateの「URLを指定して開く」から`http://127.0.0.1/test/read.cgi/test/1234567890/`を開く。検証後はサーバーをCtrl+Cで停止し、`adb reverse --remove tcp:80`を実行する。模擬掲示板はPCの`127.0.0.1:8765`だけで待ち受ける。

- `AdvancedNgMatcherTest`で複数項目のAND、板/スレッドURL範囲、正規表現・あいまい一致・否定、数値範囲、投稿曜日・時刻、JSON保存/復元を確認した。`AdvancedNgBridgeTest`で既存NGビットの保持、本文一致、参照連鎖、入れ子の判定と早期return時の状態解放を確認した。JDK 17のテスト・Lint・RVPビルドが成功した。
- 4世代のChMate APKへ同じRVPを公式CLIで適用した。0.8.10.241のパッチ後DEXでは、NG判定メソッドの入口でレスを保持し、通常・早期returnの双方でbridgeへ戻すこと、constructorの最初の型一致代入からレス番号・名前・メール・日付・本文・ID・アンカ配列を対応付けることを確認した。
- 公式Managerで統合した0.8.10.241 APKをpatch・署名し、AQUOS SH-R80P / Android 16へデータ保持更新した。最終保存APKと端末のインストール済みbase APKのSHA-256はともに`6F541ADAD84D11078480B4E64C40B4BBB98C4F6D164F72427AFD151363D8D5A9`、署名証明書SHA-256は`cddbc1c44abb5b0bc4efab2b9e82291758db40409082c7dda55cd1d771459ab0`。
- 実機上の`http://127.0.0.1/test/read.cgi/test/1234567890/`へだけ適用する一時ルールを高度NG画面から追加・編集し、本文`middle_target_20261011`を含む2レス目と`>>2`で参照する3レス目を連鎖NGにした。通常あぼーんは両レスに「あぼーん」行を残した。透明あぼーんは両行と空間を消し、1レス目と4レス目の間に対象レスの空白は残らなかった。ルールを無効化して開き直すと2・3レスの本文が戻った。
- 30レスの模擬スレッドで後半までスクロールして先頭へ戻しても、4〜30レスは表示され、2・3レスだけが透明NGの対象だった。数値・時間条件は実機の編集画面で選択項目が切り替わることまで確認し、実レス判定は単体テストで確認した。既存ChMateのNGルールは変更していない。テスト終了後、一時ルールと模擬スレッド履歴を削除し、ローカルサーバーとADB reverseを停止した。

## 2026-10-11: 4chan・ふたば・jschanの画像掲示板

- Issue本文にあるjschan系は4chan・ふたばとは異なるエンジンである。[jschan公式API文書](https://gitgud.io/api/v4/projects/fatchan%2Fjschan-docs/repository/files/source%2Findex.html.md.erb/raw?ref=master)の公開Repository APIから仕様を取得し、`/{board}/catalog.json`、`/{board}/thread/{id}.json`と、サイトの投稿フォーム`/forms/board/{board}/post`を確認した。公式READMEにある`https://zzzchan.xyz/tech/catalog.json`はHTTP 200でJSONを返した。`ImageBoardClientTest`でカタログ・返信・添付URLを確認した。AQUOS上では`/tech/`の一覧142件、画像、スレッド3レス、返信フォームの名前・件名・本文・添付・CAPTCHA欄を確認した。
- 代表の板を[4chan公式API](https://github.com/4chan/4chan-API/blob/master/pages/Endpoints_and_domains.md)が提供する`/po/`、[ふたば公式メニュー](https://www.2chan.net/bbsmenu.html)にある料理板`https://dat.2chan.net/t/futaba.htm`とした。前者の`catalog.json`はHTTP 200で11ページ、後者のHTMLはHTTP 200で`charset=Shift_JIS`を返した。
- `ImageBoardClientTest`で4chan/ふたばURLの検証、スレッドURL、4chan JSON、ふたばのOP・返信HTML、HTMLエスケープと画像URLを確認した。JDK 17のテスト・Lint・RVPビルドが成功し、公式CLIで0.8.10.165、.179、.202 dev、.241の4世代に適用した。patch後manifestに`ImageBoardActivity`を確認した。CLIの`-t`一時ディレクトリ内にある同名APKはパッチ処理で変化しているため、互換性テストにはリポジトリ直下の原本を使った。
- ManagerでRVPをストレージから追加し、0.8.10.241の統合APKをpatch・署名してAQUOS SH-R80P / Android 16へデータ保持更新した。署名証明書SHA-256は既存インストールと一致した。ChMate本体の設定から画像掲示板画面を開けた。
- 4chan `/po/`で一覧152件、サムネイル、公開スレッド4レスを確認した。`boards.4channel.org`は端末内WebViewで`ERR_SSL_PROTOCOL_ERROR`となった一方、同じ板の`boards.4chan.org`は正常に開き、返信フォームとCloudflare CAPTCHAが表示された。最終候補で`boards.4channel.org`形式を入力すると後者へ正規化して一覧152件を開き、板の新規スレッドフォームの件名・本文・CAPTCHA欄も表示した。
- ふたば料理板で一覧10件、サムネイル、返信を含むスレッド8レスを確認した。WebViewでサイトの返信フォームが表示された。初回候補で横にはみ出したフォームとURL入力後に残ったキーボードは、表示縮小・フォームへのスクロール・キーボードを閉じる修正を実機確認した。件名が`無題`の場合はレス本文を一覧の見出しに使い、ステータスバーの文字も背景と区別できるようにした。最終APKの照合値は高度NG節に記録した。
- 4chan・ふたば・jschan系の実投稿は行っていないため、送信成功と投稿後の更新は未確認。サイト固有のCAPTCHA・hidden値・添付は元サイトのフォームを使う。画面確認用URLの公開内容はサイト側で変わる。

## 2026-10-11: push前の依存監査

- Google OSV-Scanner 2.6.0で`settings-gradle.lockfile`、`patches/gradle.lockfile`、`extensions/chmate/gradle.lockfile`を検査した。更新前はNetty 4.1.136.Finalに3件、Bouncy Castle 1.84に2件のアドバイザリーを検出した。GitHubのDependabotアラート0件だけでは、このローカル結果を代用できなかった。
- Maven Centralのmetadataと公式リリースを確認し、推移依存のNettyを4.2.19.Final、Bouncy Castleを1.86、Protobufを4.36.2、Commons Langを3.21.0、Apache HttpClientを4.5.14へ更新した。品質ゲートの`--write-locks`だけでは未実行のAndroidテスト内部configurationに旧版が残ったため、`resolveAdditionalLockConfigurations`で解決してlockfileを再生成した。
- 再生成後の3つのlockfileに対するOSV-Scanner検査はexit code 0で、検出された既知脆弱性は0件。JDK 17で`:patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid`の`--write-locks`あり・なしが成功した。最新のReVanced `apktool-lib` 3.0.3-1は公開APIの名前空間と型が変わり、現行のbinary XML修復コードをコンパイルできないため、互換な最大の公開版2.10.1.1を維持した。

## 2026-10-11: 投稿間隔エラー後の自動再試行

- 再現用のローカル模擬掲示板は`scripts/mock-chmate-board.py`を使う。初回POSTへ秒数付きエラー、2回目以降へ別のエラーを返し、POST間隔を標準出力へ記録する。待機秒数は起動前に`$env:MOCK_WAIT_SECONDS = '10'`などと設定できる。ADB reverseとURLは上記の高度NG節と同じで、公開掲示板へは送信しない。
- `RetryCooldownTest`で秒数付きの日本語・英語エラー、Samba24の残り秒数、曖昧な文言と上限超過を確認した。`PostRetryBridgeTest`で返信・新規スレッド作成、再試行上限、元の件名保持、割り込み時の送信停止を確認した。
- JDK 17の`:patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid`が成功した。公式ReVanced CLI 6.0.0でChMate `0.8.10.165`、`0.8.10.179`、`0.8.10.202 dev`、`0.8.10.241`へ同じRVPを適用し、各ログの`"ChMate ReVanced" succeeded`と再構築・署名成功を確認した。.165と.241のpatch後DEXでは、返信と新規スレッド作成の最終例外経路が通信callback解放後に再試行処理へ入ることも確認した。
- 公式ManagerでChMate `0.8.10.241`の統合APKをpatch・署名し、AQUOS SH-R80P / Android 16へ既存データを保持して更新インストールした。保存APKのSHA-256は`A00CB2E6ED5C66922349A7304CE9215E2DCA32553AADF88EFAE7C8C4C8CEF0C0`。既存アプリと署名証明書が一致した。
- 端末の`tcp:80`をPC上のローカル模擬掲示板へ一時転送し、ChMateで`http://127.0.0.1/test/read.cgi/test/1234567890/`を開いた。返信の初回POSTに「再投稿まで2秒待ってください」を返すと、同じ宛先へ2回目のPOSTが3.024秒後に届いた。2回目に別のエラーを返すと、3回目は送られず、そのエラーが画面へ表示された。
- 同じ模擬掲示板で10秒指定の場合は11.019秒後、30秒指定の場合は31.020秒後に再送された。30秒待機中に端末の戻る操作を行っても「書き込み中…」は閉じず、再送は継続した。現行ChMateの進行画面は戻る操作では取り消せないため、この操作をキャンセル手段として案内しない。
- 新規スレッド作成画面でも初回POSTに2秒指定のエラーを返し、同じ件名・本文の2回目のPOSTが3.018秒後に届いた。2回目の別エラーで止まり、3回目は送られなかった。テストはローカルホストだけを宛先とし、実際の掲示板には投稿していない。終了後に模擬サーバーを停止し、ADB reverseを解除した。

## 2026-10-11: ニコニコ動画サムネイルと動画情報

- 公式の `https://ext.nicovideo.jp/api/getthumbinfo/sm9` がHTTP 200のXMLを返し、`thumbnail_url` が HTTPS の `nimg.jp` 配下を指すことを確認した。画像URLもHTTP 200のJPEGを返した。
- `NiconicoThumbnailResolverTest` で動画IDのホスト・パス判定、成功応答、失敗応答、画像ホストとスキームの拒否を確認した。
- JDK 17で `:patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid` が成功した。
- 公式ReVanced CLI 6.0.0とAndroid SDK 34のAAPT2で、ChMate `0.8.10.165`、`0.8.10.179`、`0.8.10.202 dev`、`0.8.10.241` の4世代に同じRVPを適用し、DEX・リソース再構築と署名まで成功した。各APKの元DEXにサムネイル取得フックの参照が存在することを確認した。
- AQUOS SH-R80P / Android 16で、元XAPKのbase APKだけを公式Managerに渡した場合、patch・保存・署名は各工程成功したが更新インストールは`INSTALL_FAILED_MISSING_SPLIT`で失敗した。`APKEditor 1.4.9 merge -validate-modules`で4 APKを単一APKへ統合後、Managerで再patchしたAPKは現行アプリと署名が一致し、データを保持して更新・起動できた。baseだけのpatch成功をインストール可能と記録しない。
- 初回実機候補では、本体「設定」→「ChMate ReVanced」で動画情報スイッチが既定オン、オフへ切り替えて画面を開き直しても保存されること、オンへ戻ることを確認した。既存のUser-Agent設定値は維持された。
- 公開[5chスレッド](https://lavender.5ch.io/test/read.cgi/net/1536932691/)のレス88にある`sm33589395`で、サムネイル画像、タイトル・再生数・コメント数・マイリスト数・投稿日・投稿者・概要欄のカードをスレッド内に確認した。カードをタップすると高さが404pxから1584pxへ展開し、再タップで404pxへ戻った。サムネイルをタップすると元のニコニコ動画ページが開いた。
- 初回候補でいいね数は表示されなかった。ページHTMLを単純に検索した値は4だった一方、対象動画ページの実表示と公式[スナップショット検索API](https://site.nicovideo.jp/search-api-docs/snapshot)の`contentId`完全一致結果は8だったため、現在のソースでは公式APIを使い、返却された動画IDを照合する。スナップショットAPIは日次更新である。
- `so44540860`と`nm6049209`の`getthumbinfo`はHTTP 200かつ`status="ok"`を返す。新しいURL判定を含むRVPは4世代のAPKでCLI適用・再構築・署名に成功した。`nm`の実画面は未確認。
- 2026-10-11の実機候補は公式Managerで準備2/2・パッチ2/2・保存2/2、署名成功後に更新インストールした。保存APKのSHA-256は`5A7D5DECFB181CAB4ECA7A153F8EDE82EF5A112F22B1C0DDBD7BC5C9EDA1214A`、署名証明書SHA-256は既存インストールと同じ`cddbc1c44abb5b0bc4efab2b9e82291758db40409082c7dda55cd1d771459ab0`。manifestにsplit必須指定は残っていない。
- この実機候補で公開スレッドの`sm33589395`は画像とカードが表示され、再生3,687・コメント22・マイリスト37・いいね8を確認した。`sp.nicovideo.jp/watch/sm...`の画像・カードも表示した。NicoCache公開スレッドの`so44540860`は画像とカードが表示され、再生31,762・コメント1,483・マイリスト59・いいね386を確認した。YouTubeリンクでもタイトル・再生数・投稿日・投稿者・概要欄のカードが表示された。`nm`の実画面は未確認。
- 動画情報カードはタップで展開し、再タップで省略表示へ戻る。設定スイッチをオフにしてスレッドを開き直すと、`so`サムネイルと元リンクを維持したままカードと余白が消え、次レスの開始位置が約579px上がった。スイッチはオンへ戻した。既存のUser-Agent値も保持された。

## Dependabot 自動処理（2026-09-23）

`.github/workflows/dependabot-automation.yml` を actionlint で検査し、PR 用 workflow 名（CI）と一致することを確認する。Dependabot の patch／minor かつ全 PR チェック成功の場合だけ取り込み、major・古い SHA・再失敗は残す。

実際の Dependabot PR では、自動化ジョブ、CI の結果、マージ先の commit をそれぞれ確認する。CI が成功しても、他の PR が先に main を更新すると自動マージが失敗する場合がある。

大量の Dependabot PR により CI 完了より分類が遅れる場合でも、分類後の `workflow_dispatch` が現在の PR 番号と head SHA を照合して再評価する。別の作成者、古い SHA、未完了の CI はマージしない。

## 2026-10-11: Dependabot PR #13

- `actions/upload-artifact` v7.0.1 → v7.0.2 の変更は Release workflow の固定 SHA 1 行のみ。PR の `verify` と分類チェックは成功した。
- 自動処理の `process-ci` は、別の PR が main を更新したため `Base branch was modified` で失敗した。PR の head SHA と全チェックを再確認してから、#13 を手動で squash merge した。マージ先は `2c6049040b2042f6b2d9d7ecaee4eae898748c13`。
- GitHub の Dependabot 脆弱性アラートは0件で、同時点に残る open な Dependabot PR も0件。マージ後の [main の CI](https://github.com/roflsunriz/chmate-revanced/actions/runs/38124872459) は成功した。

## 2026-10-05: GitHub受付・READMEの整備（公開前）

- 比較元: `3c00897e8056bdaad0be102437f51065cba18fba`（`main`）。
- 受付フォーム 2 件のYAML構造、重複キー・ID、入力型、選択肢、予約ファイル名を一括検査し、エラー0件。
- 既存の固有質問・入力例・必須条件を原文と照合。READMEのリンク・画像・コマンド・条件を確認し、裏付けがある誤記だけを訂正した。
- 既存のCI、Dependabot、labeler、ライセンスのファイル内容は比較元から変更していない。
- 製品のビルド・インストール・実機操作、GitHub上のフォーム表示、公開後CIは今回の静的検証に含めない。公開後に実際の受付表示と必要ラベルの適用を確認する。

## 2026-10-16: findings.md への知見分離

- 対象: AGENTS.md、README.md。固有の環境・構成・実行手段・調査知見を [findings.md](findings.md) に分離し、AGENTSには純粋な指示と説明、目的、禁止事項を残した。利用者向けの操作・導入手順、更新・復旧手順、既存の実測証跡は保持した。
- 実測: 移動した4節について、書式上の空白・Markdown表の区切り幅を正規化して移動前本文との一致を確認した。変更文書の相対リンク・参照アンカーはすべて解決し、コードフェンスと見出し構造、文書以外の無関係な差分がないこと、`git diff --check` 成功を確認した。
- GitHub既存のDependabot警告を読み取り、未解決警告0件を確認した。既存Open PRは0件。これはGitHubで既に算出された結果の参照であり、新たなローカル監査の成功を示すものではない。
- ローカル脆弱性監査とレジストリへの最新版照会は、自動承認審査が依存情報のOSV・npm・PyPI等への送信を拒否したため未実行。依存名・版と送信先を承認できる環境で監査・更新確認を再開する。GitHub既存警告の0件だけでは、全依存の現時点の安全性や最新版であることを保証しない。
- アプリコード・依存定義・lockfileを変更しない文書移動のため、製品のビルド・GUI操作・ブラウザ操作・実機書込みは実行していない。製品挙動を新たに検証したとは扱わない。
- この作業コピーに既存ローカルPrettierがないため、Markdownの構造・リンク・差分を読み取り検証した。

## 2026-10-20: Dependabot 月末一括処理

- `.github/dependabot.yml` は毎月28日00:00、`.github/workflows/dependabot-automation.yml` は同日18:00（いずれも `Asia/Tokyo`）に設定した。呼び出し先は検証済み共通 workflow の固定 SHA とし、現在の head SHA、Dependabot の資格状態、全 PR チェック成功を merge 前に再確認する。
- YAML 構文、変更差分、空白エラーをローカルで検査した。実際の月末 schedule と Dependabot PR の merge 経路は、次回の毎月28日実行で確認する。
