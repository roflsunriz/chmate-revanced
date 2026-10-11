# 検証手順

## 2026-10-11: ニコニコ動画サムネイル修正（実機確認前）

- 公式の `https://ext.nicovideo.jp/api/getthumbinfo/sm9` がHTTP 200のXMLを返し、`thumbnail_url` が HTTPS の `nimg.jp` 配下を指すことを確認した。画像URLもHTTP 200のJPEGを返した。
- `NiconicoThumbnailResolverTest` で動画IDのホスト・パス判定、成功応答、失敗応答、画像ホストとスキームの拒否を確認した。
- JDK 17で `:patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid` が成功した。
- 公式ReVanced CLI 6.0.0とAndroid SDK 34のAAPT2で、ChMate `0.8.10.165`、`0.8.10.179`、`0.8.10.202 dev`、`0.8.10.241` の4世代に同じRVPを適用し、DEX・リソース再構築と署名まで成功した。各APKの元DEXにサムネイル取得フックの参照が存在することを確認した。
- 実機での画像表示と元リンクのタップ導線は未確認。テスト用公開動画を表示し、画像・タイトル位置・タップ先を確認する。

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
