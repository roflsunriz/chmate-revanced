# 検証手順

## 2026-10-11: push前の依存監査

- Google OSV-Scanner 2.6.0で`settings-gradle.lockfile`、`patches/gradle.lockfile`、`extensions/chmate/gradle.lockfile`を検査した。更新前はNetty 4.1.136.Finalに3件、Bouncy Castle 1.84に2件のアドバイザリーを検出した。GitHubのDependabotアラート0件だけでは、このローカル結果を代用できなかった。
- Maven Centralのmetadataと公式リリースを確認し、推移依存のNettyを4.2.19.Final、Bouncy Castleを1.86、Protobufを4.36.2、Commons Langを3.21.0、Apache HttpClientを4.5.14へ更新した。品質ゲートの`--write-locks`だけでは未実行のAndroidテスト内部configurationに旧版が残ったため、`resolveAdditionalLockConfigurations`で解決してlockfileを再生成した。
- 再生成後の3つのlockfileに対するOSV-Scanner検査はexit code 0で、検出された既知脆弱性は0件。JDK 17で`:patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid`の`--write-locks`あり・なしが成功した。最新のReVanced `apktool-lib` 3.0.3-1は公開APIの名前空間と型が変わり、現行のbinary XML修復コードをコンパイルできないため、互換な最大の公開版2.10.1.1を維持した。

## 2026-10-11: 投稿間隔エラー後の自動再試行

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
