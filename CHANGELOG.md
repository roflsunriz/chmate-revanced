# 変更履歴

このプロジェクトは [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) に従い、バージョン番号は [Semantic Versioning](https://semver.org/lang/ja/) に従います。

## [Unreleased]

## [1.2.4] - 2026-10-25

### Fixed

- 0.8.10.246 devの`ResListActivity`で、`onStart`／`onResume`のintegrity trapが内部状態により発火し、スレッドを開く際に断続的に終了する問題を修正した。
- 「スレ検索」などで使われる`WebViewActivity`にも同型のライフサイクル修正を適用し、Web画面を開く際の終了を防止した。
- スレッドの書き込み欄で使われる`ResEditActivity`にも同型の修正を適用し、返信ダイアログを開く際の終了を防止した。
- 246 devの全Activityを横断確認し、ホーム・検索・スレッド一覧を含む7 Activityについて、super呼出しとthrow／divide trapが共存するライフサイクルmethodだけを構造判定して修正するよう一般化した。

## [1.2.3] - 2026-10-24

### Fixed

- Android 17のReVanced Manager別プロセスでapktoolのframework保存先が`/.local/share`となり、書き込み失敗ログがBinder上限を超えてパッチ処理が終了する問題を修正した。

## [1.2.2] - 2026-10-24

### Fixed

- Android版ReVanced Managerで0.8.10.246 devのroot配置binary XMLを復号した際、文字列poolから欠落した属性名をresource IDから復元し、`IntListPreference`などの必須属性が消えて起動不能になる問題を修正した。
- 0.8.10.246 devで追加された起動・設定・板一覧のintegrity trapを構造検出して無効化し、Manager署名版でも各画面を開けるようにした。
- Compose版のChMate設定下部へ画像掲示板と高度NGの直接入口を常設し、掲示板設定・NG設定から中央のReVanced設定を経由せず開けるようにした。

## [1.2.1] - 2026-10-24

### Fixed

- Android版ReVanced Managerで0.8.10.246 devの難読化binary XMLを処理できるよう、数値resource／attr参照、framework enum、短縮color、欠落属性を正規化した。
- 掲示板設定XMLから属性名・クラス名を復元できないCompose版でも、実行時の「掲示板設定／BBSMENU」表示から対象画面を識別し、画像掲示板項目を追加できるようにした。

## [1.2.0] - 2026-10-24

### Added

- ホームの更新で複数の板・スレッドを確認する処理を最大4ワーカーへ分割し、ChMate本来の取得・DB更新・エラー処理を保ったまま同時読み込みできるようにした。
- 4chan・ふたば・jschanの板を「設定」→「掲示板設定」から登録し、ループバック変換層を介してChMate標準の板一覧・スレッド一覧・レス画面で閲覧できるようにした。4chan・ふたばのURLと登録済みjschan URLは「URLを指定して開く」にも対応した。

### Changed

- GitHub Actions の月間利用時間を抑えるため、Dependabot version update と自動マージを毎週・即時処理から毎月28日（Asia/Tokyo）の月末一括処理へ変更した。
- 高度なNGルールの入口を「ChMate ReVanced」設定からChMate本来のNG編集画面へ移し、既存NGと同じ導線で編集できるようにした。
- 画像掲示板と高度NGを独立したReVanced設定から除き、User-Agent・再起動・動画情報だけを「ChMate ReVanced」設定に残した。
- ChMate 0.8.10.246 devのCompose設定画面でも「ChMate ReVanced」を開けるよう、XML Preferenceと実行時設定画面の両方へ互換的に入口を接続した。

- 重複するCIを自動で取り消し、独立したGradleタスクを並列実行してパッチ検証の待ち時間を短縮した。

- 作業規範と実装上の知見を分けて参照できるよう、固有の構成・環境・調査知見を `findings.md` に集約し、元文書の参照と着手前の全文読了指示を整えた。利用者向け手順と既存の検証証跡は保持した。

### Fixed

- 0.8.10.246 devで空の設定XMLを前提にしてパッチが失敗する問題を修正した。
- Android版ReVanced Managerで難読化binary XMLの属性名・値を解決できない場合も、不正XMLを生成せずresource処理を継続できるようにした。

## [1.1.0] - 2026-10-12

### Security

- ビルド時とAndroidテスト用の推移依存に残った既知脆弱性を解消するため、NettyとBouncy Castleを修正版へ更新し、通常の品質ゲートでは未解決のlockfileも再生成できるようにした。

### Fixed

- ニコニコ動画の`so`・`nm`形式リンクにも既存の動画サムネイル処理を適用できるよう、動画URLの判定を拡張した。
- ニコニコ動画ページの画像抽出に依存してサムネイルが欠ける問題を避けるため、動画情報APIからサムネイルURLを取得し、失敗時は元の取得処理へ戻すようにした。
- CI と Dependabot の分類の実行順が前後しても更新を取りこぼさないよう、同じ PR 番号と head SHA を再照合する経路を追加した。

### Changed

- 不具合・機能提案などの受付とPRの記入形式を揃え、プロジェクト固有の確認項目を残した。 READMEは既存の意味と手順を保ち、実装と異なる説明や読みにくい表現を修正した。

- 依存更新を安全に省力化するため、Dependabot の patch／minor PR を既存 CI の全チェック成功後に自動取り込みし、失敗ジョブを一度再実行する設定を追加した。
- 複数の依存更新が同時に進んだ際の取りこぼしを復旧できるよう、main 更新との競合で自動マージが失敗した場合の再確認と手動マージ手順を追記した。

### Added

- 公開掲示板への送信なしで投稿間隔エラーとNG連鎖を再現できるよう、ローカル模擬掲示板の検証スクリプトを追加した。

- 既存NGを残しながら、項目別文字列・数値・時間・否定・複数条件AND・板/スレッド範囲・連鎖を組み合わせ、透明あぼーんではレス行の空間まで消せる高度NG編集画面を追加した。

- 4chan・ふたば・jschan系サイトの板とスレッドをChMate内で画像付き表示し、サイト固有の認証・投稿フォームへ進める画面を追加した。

- 投稿間隔エラーに待機秒数が示されたとき、返信と新規スレッド作成を指定時間後に最大2回自動で再試行できるようにした。

- 動画リンクの情報をスレッド内で確認できるよう、公開メタデータからタイトル・利用できる統計値・投稿日・投稿者・概要欄を表示するカードと、表示を切り替える設定を追加した。

- ReVanced Manager が固定 URL から patch bundle の更新を追従できるように、各 GitHub Release へ同じタグの `.rvp` を参照する ReVanced API 形式の `patches.json` を追加

## [1.0.3] - 2026-08-10

### Fixed

- ChMate独自の`layout`属性と同名でも、`<include>`の必須`layout`属性を`app:`名前空間へ変更しないようにし、書き込み欄と新規スレッド作成を開く際の`InflateException`を修正

## [1.0.2] - 2026-08-09

### Fixed

- ReVanced ManagerのAndroid XML parserがPC向けJAXPのsecurity featureを未実装でもpatchを開始できるようにし、DOCTYPE／外部entityはparser依存機能を使わず事前拒否する方式へ変更

## [1.0.1] - 2026-08-09

### Fixed

- ChMate 0.8.10.241 のスレッド画面で、タイトル直上に残っていた広告領域を高さ `0dp`・`GONE` にする構造ベースの検出を追加
- 広告枠が実行時に再表示されても再度折りたためるよう、patch 時に限定的な tag を付与して runtime 側でも監視
- タイトルの全幅表示、スレッド一覧の下引き更新、スレッド末尾の上引き更新、ID直接タップとID行空白タップを維持したまま上部広告領域を除去

## [1.0.0] - 2026-08-09

### Added

- ReVanced Manager が直接読み込める `.rvp` と SHA-256 を、タグとバージョンの一致確認、テスト、Lint、再検証を経て GitHub Release へ公開する workflow
- ChMate の静的・動的広告 View を高さゼロにする resource / runtime patch
- 既知広告ホストと広告 SDK 起点の通信を遮断する bytecode patch
- HTTP、WebView、`http.agent` に適用する User-Agent 設定
- ChMate 本体の設定へ組み込む多言語設定画面とワンボタン再起動
- 広告要素分類と広告ホスト判定の単体テスト
- Android 6 以上向け拡張機能の Debug / Release build と Lint 構成

### Fixed

- APK の再署名後に ChMate の FileProvider または設定画面の多段初期化から異常終了する署名整合性チェックを、難読化名に依存しない結果配列比較と限定した失敗処理の検出で回避
- 設定画面をホストプロセス内で動かして ChMate Application の初期化漏れを防ぎ、main Activity を新しい task として起動してから現在プロセスを終了する即時再起動方式へ変更
- 実在する4世代のChMateで広告SDK通信が初期化段階から始まらないように、Google Mobile Ads内部packageとNend SDKの分類、広告SDK manifest componentの無効化、広告request entry pointの置換を追加
- 難読化された内部classの `$` を含む不正なXML要素名でresource再コンパイルが失敗しないように、Android inflater互換の `<view class="...">` 形式へ正規化
- ハイフンを含むR8生成コンポーネント名を、マニフェストとDEXの両方で再コンパイル可能な名前へ正規化
- 数値だけのresource名・参照・ファイル名を、public IDを維持したままAAPT2互換名へ正規化
- APK直下へ難読化された画像・binary XML resourceを標準resource directoryへ復元
- 実機計測で判明したFirebase Analytics / Crashlytics / Performanceの収集経路をmanifestで無効化し、関連テレメトリホストも接続前に遮断
- ChMateが組み立てた最終User-Agentを安定した`Monazilla/1.00`リテラルで検出し、設定値を接尾辞として付加するのではなくHTTPヘッダー全体へ適用
- Apktoolが名前空間なしで復号したChMate独自layout属性を`app:`名前空間へ戻し、元resource tableからenum / flagの実値を復元することで、タイトル幅、スレッド一覧の下引き更新、スレッド末尾の上引き更新、ID行の空白タップとIDタップによる同IDポップアップの退行を修正

### Security

- Android / Gradle の build・test tooling が推移的に解決していた Netty、Bouncy Castle、Protobuf、Commons Lang、Apache HttpClient を既知脆弱性の修正版へ統一
- v1.0.0 の依存解決結果を固定し、公開前の既知脆弱性検査を再現可能にする dependency lock
- GitHub Packages token を環境変数だけから受け取り、リポジトリへ保存しない構成
