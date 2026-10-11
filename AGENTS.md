# AGENTS.md

## 作業開始前の必須手順（最優先・例外なし）

1. エージェントは、調査、計画、コマンド実行、スキル利用、ファイル編集、コミット、プッシュを始める前に、必ずリポジトリ直下の `.\COMMON-AGENTS.md` を開き、先頭から末尾まで全文を読む。
2. `COMMON-AGENTS.md` はGit管理外のシンボリックリンクである。`git`や既定のignore設定が有効な`rg --files`の検索結果だけで、ファイルが存在しないと判断してはならない。PowerShellでは最初に次を実行する。

```powershell
Get-Content -Raw -LiteralPath .\COMMON-AGENTS.md
```

3. 読み取りに失敗した場合、出力が省略された場合、または末尾まで読めたことを確認できない場合は、一切の作業を開始せず、パスとシンボリックリンク先を確認して全文を再取得する。必要なら分割して末尾まで読む。
4. 全文を読了するまで、ローカル `AGENTS.md` だけを根拠に作業を続けてはならない。読了後は `COMMON-AGENTS.md` を最優先の指針とし、読了直後の最初の進捗報告で全文を読了したことを明示する。
   このファイルでは `chmate-revanced` 固有の補足だけを記載する。

## chmate-revanced の目的
- アンドロイドアプリ「ChMate」用のReVanced Patchを作る
- 以下の特徴をもつReVanced Patch
- 5chスレッド表示時に挿入される画面上部の広告欄とスレッド内途中に表示される広告欄の高さをゼロにする
- 広告SDKの通信を完全に遮断する
- User-Agentを設定可能にする
- User-Agentを適用するためワンボタンで再起動可能にする
- 過去のChMateバージョンと未来のChMateバージョンに渡って広く適用可能（ChMateのソースコード自身はMinify・難読化が施されている）

## Environment
- Gradle実行はJDK 17を使う（CIはTemurin 17。2026-09-23時点のローカル例: `C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot` を `JAVA_HOME` に設定）。
- GitHub Packages認証が必須のため `ORG_GRADLE_PROJECT_githubPackagesUsername` / `ORG_GRADLE_PROJECT_githubPackagesPassword` を環境変数で渡す（詳細はREADME参照）。
- 依存更新時は `./gradlew --no-daemon :patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid --write-locks` でlockfileを再生成し、同コマンド（`--write-locks` なし相当のCIと同じ範囲）が成功することを確認する。
- JUnitのjupiterとlauncherは必ず同時更新する。片方だけでは `junit-bom` とDependency Lockingの厳密制約が不整合しCIのverifyが失敗する（2026-09-23のDependabot PR #2 / #4で確認）。
- 2026-10-11確認: 通常の`:patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid --write-locks`だけでは、`_internal-unified-test-platform*`と`kotlinBouncyCastleConfiguration`等の未実行configurationに旧版が残り得る。依存更新時は`:patches:resolveAdditionalLockConfigurations :extensions:chmate:resolveAdditionalLockConfigurations --write-locks`も実行し、3つのlockfileをOSV-Scannerなどで再検査する。手順は`how-to-update.md`、実測は`verification.md`を参照。
- 複数のDependabot PRが同時に成功した場合、別PRが先にmainを更新すると自動マージが`Base branch was modified`で失敗することがある（2026-10-11のPR #13）。対象PRの現在のhead SHA・全チェック・mergeable状態を再確認して取り込み、手順と記録は`how-to-update.md`と`verification.md`を参照する。
- 2026-10-11確認: 動画サムネイルは難読化されたクラス内で、URLパターンに応じてページを読み、画像URLを抽出する。少なくともChMate 0.8.10.165、.179、.202 dev、.241では`InputStreamReader(InputStream,String)`と`Pattern.compile(String,int)`を持つ静的な3引数・文字列戻り値メソッドが1件あり、`NiconicoThumbnailPatch.kt`はそこへ接続する。先に他のbytecode変換がメソッドを可変化している場合、ラベルの参照先は`firstMethod(method)`で得た可変実装の命令から取る。4世代の適用結果は`verification.md`を参照する。
- 2026-10-11確認: AQUOS SH-R80P / Android 16でChMate 0.8.10.241 XAPKのbase APKだけをManagerでpatch・署名しても、単独の更新インストールは`INSTALL_FAILED_MISSING_SPLIT`になる。Managerのpatch成功をインストール成功と同一視せず、元XAPKの4 APKを`APKEditor 1.4.9 merge -validate-modules`で統合してからpatchする経路、または全splitの同一署名を検証する。実測経緯は`verification.md`を参照する。
- 2026-10-11確認: レス内画像の親ViewGroupは0.8.10.165/.179と.202 dev/.241で難読化名が変わるが、`CharSequence[]`と2個のbooleanを受け取って画像Viewを追加・削除するメソッド、`onMeasure`、`onLayout`の組み合わせで4世代を識別できる。.241には同じ引数型の補助メソッドもあるため、名前や型だけで一意としない。`onMeasure`で元の幅引数は上書きされるため、`setMeasuredDimension`呼び出しの幅レジスタから動画カードの測定幅を得る。根拠は`VideoMetadataPatch.kt`と4世代のCLI適用結果（`verification.md`）。
- 2026-10-11確認: ニコニコ動画ページのHTML内で最初に見つかる`count.like`は対象動画の値とは限らない。公開動画`sm33589395`で単純なHTML抽出は4、ページの表示と動画ID完全一致の公式スナップショット検索APIは8だった。いいね数は`filters[contentId][0]`で動画IDを限定し、返却`contentId`も照合する。APIは日次更新のため厳密なリアルタイム値ではない（公式資料`https://site.nicovideo.jp/search-api-docs/snapshot`、検証は`verification.md`）。
- 2026-10-11確認: ReVanced CLI 6.0.0は個別patchが`failed`を出してもDEX/リソースを保存し、終了コード0になる場合がある。APKの存在やCLIの終了コードだけで適用成功とせず、ログ中の`"ChMate ReVanced" succeeded`を必ず確認する。`VideoMetadataPatch.kt`のfingerprint試行時に実測。
- 2026-10-11確認: ChMateの投稿クライアントは4世代で難読化名が異なるが、返信は`BBSUrlInfo`を先頭・booleanを3番目に持つ5引数、新規スレッドは`BBSUrlInfo`を先頭・件名Stringとbooleanを3・4番目に持つ6引数の公開メソッドとして検出できる。サーバーの書き込み拒否はメッセージを持つ`IOException`派生例外として最終catchから再送出される。`PostRetryPatch.kt`は通信callbackを解放した後の最終例外経路へ接続する。2018年の[5ch公式wiki](https://info.5ch.io/index.php/%E6%9B%B8%E3%81%8D%E8%BE%BC%E3%82%81%E3%81%AA%E3%81%84%E6%99%82%E3%81%AE%E6%97%A9%E8%A6%8B%E8%A1%A8)にある秒数付き連投エラーを元にパーサーを検証し、現行サーバーの文言は変化し得る。AQUOS実機でローカル模擬掲示板への返信・新規スレッド作成の再送を確認済み。ChMateの「書き込み中…」画面は戻る操作では中断できず、実掲示板への送信は未実施。詳細は`verification.md`を参照。
- 2026-10-11確認: Issue #9の4chanは[公式JSON API](https://github.com/4chan/4chan-API/blob/master/pages/Endpoints_and_domains.md)が読み取り専用で、代表の`https://a.4cdn.org/po/catalog.json`はHTTP 200で11ページを返した。書き込み先は公開板HTMLの`https://sys.4chan.org/po/post`で、APIとは別経路。ふたば料理板`https://dat.2chan.net/t/futaba.htm`はShift_JISのHTMLでHTTP 200を返し、投稿フォームは`/t/futaba.php?guid=on`へhiddenの動的値を含めて送る。HTMLやフォームの実装は変わり得るため、板ごとの実データで再確認する。
- 2026-10-11確認: AQUOS SH-R80P / Android 16では`boards.4channel.org`のスレッドをChMate内WebViewで開くと`ERR_SSL_PROTOCOL_ERROR`だったが、同一の`boards.4chan.org`ではフォームとCloudflare CAPTCHAを開けた。公式APIの読み込み先は両者共通の`a.4cdn.org`なので、`ImageBoardClient.parseBoard`で前者の入力も後者へ正規化する。最終実機結果は`verification.md`を参照。
- 2026-10-11確認: AQUOS上のChMate 0.8.10.241の既存NG編集はWord・Name・ID・Be・Thread・画像のタブがあり、Word追加には文字列・正規表現・アンカ数、有効、連鎖、非表示、強調、投稿者IDもNG、板範囲がある。Issue #7ではこれらを重複実装せず、VBBBの文字列フィールド指定・複数条件・数値・時間・否定・スレッド範囲など不足機能を調べる。比較元は`https://github.com/roflsunriz/VBBB/blob/main/src/types/ng.ts`。
- 2026-10-11確認: 0.8.10.241のNG判定は難読化された`SignInButton.a(setDislikeWidth, boolean): int`に集約され、`setDislikeWidth`からレス番号、本文、ID、名前としてNG判定に使う値とアンカ先を読む。返却intはNG種別ビットで、非表示は4096、Wordは16、IDは8、Nameは32。既存の連鎖処理は判定中のキャッシュを使う。Issue #7の追加条件を実際のレス表示へ接続するときは、この判定境界を4世代で構造確認し、レス表示・連鎖・既存NGとの優先関係を実機で検証する。根拠は0.8.10.241のJADX逆コンパイルと`verification.md`。
- 2026-10-11確認: 公式CLIの`-t`一時ディレクトリ内にある元APKと同名のファイルは、パッチ処理中に変化している。次の互換性テストの入力に流用すると旧パッチ適用済みresource tableを再処理して`Could not recover original custom attribute values`等で失敗する。4世代の原本はこのリポジトリ直下のGit管理外`ChMate_0.8.10.165_APKPure.apk`、`ChMate_0.8.10.179_APKPure.xapk`、`ChMate_0.8.10.202+dev_APKPure.apk`、`ChMate_0.8.10.241_APKPure.xapk`にあり、CLIには原本APKまたはXAPKから取り出したbase APKを渡す。CLIの終了コード0だけで成功扱いしない。
- 2026-10-11確認: [jschan公式API文書](https://gitgud.io/fatchan/jschan-docs)はGitGudの公開Repository APIから`source/index.html.md.erb`を取得できる。板`/{board}/catalog.json`はスレッド配列、`/{board}/thread/{id}.json`はOPと`replies`配列、投稿はサイトフォーム`/forms/board/{board}/post`。公式README記載の`zzzchan.xyz`では`/tech/catalog.json`がHTTP 200で、添付は`/file/{filename}`と`/file/thumb/{hash}{thumbextension}`。4chan・ふたばと同一エンジンではないため、Issue #9では独立した`JSCHAN`分岐で対応する。
- 2026-10-11確認: NG判定メソッドは戻り時に`p1`引数レジスタを別用途へ再利用する場合があり、終端で`p1`をレスとみなして渡すと整数を参照してしまう。`AdvancedNgPatch.kt`は入口でレス・判定器をbridgeのスタックへ保持し、早期returnを含む全returnでpopする。レスのconstructorでもパラメータレジスタが後半に再利用されるため、フィールド対応は型と最初の`iput`代入を使う。パッチ後DEXの`SignInButton`で入口と通常・早期戻りのbridge呼出しを確認した。
- 2026-10-11確認: ChMate 0.8.10.241のNG判定bit16/4096だけでは通常・透明の双方に「あぼーん」行が残った。透明NGでは`AdvancedNgViewCollapser`が、そのスレッドURLとレス番号をbridgeで確認したRecyclerView子行だけ高さ0/GONEにし、再利用された別レスのViewを復元する。AQUOSの30レス模擬スレッドで2・3レスの行と空間が消え、4〜30レスはスクロール往復後も表示された。原則として既存ChMate NG全体を折りたたまず、高度NGで透明に設定したレスだけを対象にする。詳細は`verification.md`。
- 2026-10-11確認: #7の透明NGと#8の自動再投稿を公開掲示板へ送信せず再現する`scripts/mock-chmate-board.py`を追加した。PCの127.0.0.1:8765だけで待ち受け、AQUOSでは`adb reverse tcp:80 tcp:8765`からローカル板URLを開く。実行・解除手順は`verification.md`に記載。
- 2026-10-12確認: ローカルWindowsとActions Linuxで生成した同版RVPの全体SHA-256は、manifestの`Timestamp`だけが異なるため一致しない。公開前はZIP内のエントリとmanifest以外の内容を比較し、公開後はGitHub ReleaseのRVPを同梱SHA-256ファイルと照合する。Manager 2.6.0への固定URL登録はPatchesタブの編集ボタン→「パッチを追加」→「URLを入力」で行い、自動更新を有効にする。詳細は`how-to-update.md`と`verification.md`。
