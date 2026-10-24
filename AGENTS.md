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

## プロジェクト固有の実現手段と知見

- 作業開始前に [COMMON-AGENTS.md](COMMON-AGENTS.md) と [findings.md](findings.md) を先頭から末尾まで全文読む。
- ファイル配置、使用ツール、実行コマンド、対応環境、文書構成、共通方針の例外と調査知見は `findings.md` に記録する。
- ユーザーの指示、変更禁止、秘密情報の保護、検証・変更履歴を残す義務は共通指針に従い維持する。

## リリース前の実機UI回帰検査

- ChMateのpatchを変更したreleaseは、公開前に接続済み検証端末へ候補RVPをManagerで適用し、必要な明示承認を得てデータ保持更新する。
- ホームの全tab・toolbar button・overflow menu、板一覧、スレッド一覧、スレッド画面、設定の全項目と子画面、ChMate ReVancedが追加する全画面を実際に開く。各画面の操作可能なbutton、menu、dialog、入力欄について、開閉、戻る、cancel、保存を副作用のない範囲で確認する。投稿、削除など外部・破壊的な確定操作は実行せず、直前まで開いてcancelする。
- 更新buttonとpull-to-refreshを確認し、同一スレッドを20回以上連続で開閉する。各回で対象Activityとprocess生存を確認する。
- 検査直前にAndroidのcrash bufferをclearし、検査後にChMate由来のFATAL EXCEPTIONが0件であることを確認する。主要画面はscreenshotまたはUI hierarchyも保存して表示崩れを確認する。
- 旧版で存在しない画面は対象外理由を記録する。実機UI回帰検査、clean build、JUnit、Lint、全対応APK/XAPKのCLI適用がすべて成功するまでtag・releaseを作成しない。結果は`verification.md`へ記録する。
