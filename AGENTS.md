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
- 複数のDependabot PRが同時に成功した場合、別PRが先にmainを更新すると自動マージが`Base branch was modified`で失敗することがある（2026-10-11のPR #13）。対象PRの現在のhead SHA・全チェック・mergeable状態を再確認して取り込み、手順と記録は`how-to-update.md`と`verification.md`を参照する。
- 2026-10-11確認: 動画サムネイルは難読化されたクラス内で、URLパターンに応じてページを読み、画像URLを抽出する。少なくともChMate 0.8.10.165、.179、.202 dev、.241では`InputStreamReader(InputStream,String)`と`Pattern.compile(String,int)`を持つ静的な3引数・文字列戻り値メソッドが1件あり、`NiconicoThumbnailPatch.kt`はそこへ接続する。先に他のbytecode変換がメソッドを可変化している場合、ラベルの参照先は`firstMethod(method)`で得た可変実装の命令から取る。4世代の適用結果は`verification.md`を参照する。
- 2026-10-11確認: AQUOS SH-R80P / Android 16でChMate 0.8.10.241 XAPKのbase APKだけをManagerでpatch・署名しても、単独の更新インストールは`INSTALL_FAILED_MISSING_SPLIT`になる。Managerのpatch成功をインストール成功と同一視せず、元XAPKの4 APKを`APKEditor 1.4.9 merge -validate-modules`で統合してからpatchする経路、または全splitの同一署名を検証する。実測経緯は`verification.md`を参照する。
- 2026-10-11確認: レス内画像の親ViewGroupは0.8.10.165/.179と.202 dev/.241で難読化名が変わるが、`CharSequence[]`と2個のbooleanを受け取って画像Viewを追加・削除するメソッド、`onMeasure`、`onLayout`の組み合わせで4世代を識別できる。.241には同じ引数型の補助メソッドもあるため、名前や型だけで一意としない。`onMeasure`で元の幅引数は上書きされるため、`setMeasuredDimension`呼び出しの幅レジスタから動画カードの測定幅を得る。根拠は`VideoMetadataPatch.kt`と4世代のCLI適用結果（`verification.md`）。
- 2026-10-11確認: ニコニコ動画ページのHTML内で最初に見つかる`count.like`は対象動画の値とは限らない。公開動画`sm33589395`で単純なHTML抽出は4、ページの表示と動画ID完全一致の公式スナップショット検索APIは8だった。いいね数は`filters[contentId][0]`で動画IDを限定し、返却`contentId`も照合する。APIは日次更新のため厳密なリアルタイム値ではない（公式資料`https://site.nicovideo.jp/search-api-docs/snapshot`、検証は`verification.md`）。
- 2026-10-11確認: ReVanced CLI 6.0.0は個別patchが`failed`を出してもDEX/リソースを保存し、終了コード0になる場合がある。APKの存在やCLIの終了コードだけで適用成功とせず、ログ中の`"ChMate ReVanced" succeeded`を必ず確認する。`VideoMetadataPatch.kt`のfingerprint試行時に実測。
