# 調査知見

- JUnit Jupiterとlauncherは同時更新する。通常taskだけでは未実行configurationのlockが古いまま残るため、追加lock解決taskも実行し、3 lockfileを監査する。
- 難読化名やversion番号をfingerprintに使わず、引数型・戻り型・呼出し命令・`onMeasure/onLayout` などの構造を複数世代で照合する。別patchが先にmethodを可変化した場合は元descriptorでなく可変実装からlabelを取る。
- ReVanced CLIは個別patch失敗でも成果物を保存しexit 0になることがある。APKの存在や終了codeでなく、対象patchの `succeeded` logとpatch後DEXを確認する。
- CLI一時directory内の元APK同名fileはpatch中に変化する。次の互換性試験へ流用せず、Git管理外に保存した各世代の原本から毎回base APKを取り出す。
- XAPKのbaseだけをpatchしても `INSTALL_FAILED_MISSING_SPLIT` になり得る。全module統合後にpatchするか、全splitが同じ署名で揃うことを確認する。Managerのpatch成功とinstall成功を分ける。
- ニコニコのHTMLで最初の `count.like` は対象動画とは限らない。snapshot APIをcontentIdで絞り、返却IDも照合する。値は日次更新でリアルタイムではない。
- 投稿retryは通信callback解放後の最終 `IOException` 派生経路へ接続する。実掲示板へ送らず、ローカル模擬板と `adb reverse` で返信・新規threadを確認する。
- 高度NGは判定method入口でレスと判定器をbridge stackへ保存し、早期returnを含む全returnでpopする。終端のparameter registerは別用途へ再利用され得る。透明NGは対象レスのRecyclerView行だけを0/GONEにし、再利用viewでは必ず復元する。
- 4chan、ふたば、jschanはAPI・投稿form・encodingが別。独立engineとして扱い、4channel URLは実機SSL不具合時に4chanへ正規化しても読取API `a.4cdn.org` との役割を混同しない。
- ローカル/Actionsの同版RVPはmanifest Timestamp差で全体SHAが一致しない。公開前はZIP entryとmanifest以外を比較し、公開後はrelease RVPと同梱checksumを照合する。
- 広告通信の完全遮断はbytecode適用だけで断定せず、対象APK・実機のpacket観測で判定する。native/独自暗号/新SDK/Compose移行は追加調査対象。
