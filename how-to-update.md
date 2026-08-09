# 更新手順

ChMate、ReVanced Patcher、広告 SDK のいずれかが更新されたときに行う手順です。互換性を推測だけで宣言せず、最低でも現在版と一つ前の入手可能な版で確認します。

## 1. 依存関係を更新する

1. ReVanced の公式 patch template、Gradle plugin、Patcher の release / main を確認する。
2. `settings.gradle.kts` の plugin バージョンと `gradle/libs.versions.toml` の Patcher バージョンを互換な組み合わせで更新する。
3. Gradle wrapper と Android compile SDK は plugin が要求する版に合わせる。
4. JUnit、SMALI を更新した理由と代替案を変更履歴またはコミット本文へ残す。
5. GitHub Packages の資格情報を環境変数へ設定し、次を実行する。

```powershell
.\gradlew.bat --refresh-dependencies :patches:test :extensions:chmate:test :extensions:chmate:lint :patches:buildAndroid
```

6. 全 configuration を解決できる状態で Gradle dependency locking を有効化し、`--write-locks` で lockfile を生成・差分確認する。
7. 利用可能な依存監査を実行し、検出事項、影響、対応を記録する。

依存解決エラーを無視したり、不完全な lockfile を作ったり、Lint baseline で新しい警告を隠したりしません。

## 2. 新しい ChMate APK を調査する

APK はリポジトリ外で管理します。

1. package が `jp.co.airfront.android.a2chMate` であること、versionName / versionCode、SHA-256 を記録する。
2. manifest に MAIN / LAUNCHER Activity が一つ以上あることを確認する。
3. DEX 内の広告 SDK package と、利用している通信 API を列挙する。
4. `res/layout*` の広告領域について View class、resource ID、tag を列挙する。
5. 新しい SDK を発見した場合は、SDK class marker と固有ホストを追加する。一般サイトまで遮断する広すぎるドメインは追加しない。
6. XAPK の場合は base APK だけでなく全 split APK の一覧も記録する。patch 後は全 split を同じ鍵で再署名し、`adb install-multiple` または対応する split installer で一括導入する。

## 3. patch 適用を検証する

現在版と一つ前の版へ同じ `.rvp` を適用し、それぞれで次を確認します。

- patch が例外なく完了し、署名後の APK をインストールできる
- XAPK の全 split が base APK と同じ証明書で署名され、欠落なく一括インストールできる
- 通常起動と、ChMate の「設定」→「ChMate ReVanced」からの設定画面起動が成功する
- `ChMate ReVanced` が独立した launcher Activity として公開されていない
- スレッド上部と途中の広告領域が `0px` で、余白も残らない
- スレッド一覧、閲覧、書き込み、画像表示など広告以外の通信が壊れていない
- 空欄では元の User-Agent、設定後は指定値が HTTP / WebView で送られる
- 「保存して再起動」で ChMate の main process が入れ替わり、設定値が反映される
- 広告 SDK package から外部通信が出ない
- 既知広告ホストへの DNS / TCP / TLS 接続が出ない

通信確認は少なくとも、コールドスタート、スレッド一覧、スレッド表示を数分ずつ行います。端末の Private DNS や別の広告ブロッカーは無効にし、この patch 単独の結果を測ります。

### Pixelでの実測手順

1. AdGuardなどのVPN、Private DNS、システムHTTP proxyを停止する。過去にADBでproxyを設定した場合は、設定DBを削除するだけでなく`settings put global http_proxy :0`でConnectivityサービスへ無効状態を通知し、ChMateをforce-stopして新しいprocessで測る。
2. 初期状態のChMateでは「掲示板設定」へBBSMENUを登録し、板一覧の読み込みテストが成功することを先に確認する。proxyが残っている場合は`Unexpected response code for CONNECT`になるため、その状態の結果をpatchの通信失敗として扱わない。
3. 画面解像度を記録し、実在スレッドの先頭と途中を`uiautomator dump`と`screencap`で取得する。広告の前後にある通常Viewのboundsを比較し、広告由来の追加高が`0px`であることを数値で確認する。
4. 識別可能なUser-Agentを保存して「保存して再起動」を押す。再起動前後のPIDを確認し、ADB reverseなどで端末から到達できる一時HTTPサーバーをBBSMENU読み込みテストの送信先にして、生の`User-Agent`ヘッダーが入力値と完全一致することを確認する。値がChMate既定UAの接頭辞・接尾辞として付加されるだけなら不合格。確認後は既定値へ戻す。
5. コールドスタート、板一覧、スレッド表示、十分なスクロールを行い、広告SDK要求がDNS・socket・URL接続より前で終了することを確認する。`adb logcat -s FA:V FA-SVC:V`では`App measurement deactivated via the manifest`が記録され、upload失敗を含む`FA-SVC`通信試行が出ないことを合格条件とする。
6. QA用ログを一時追加した場合は製品buildから必ず除去し、通常buildを再適用してから端末を返す。proxy、stay-on、ADB reverseなど検証用の端末設定も元へ戻す。

## 4. 失敗時の切り分け

- 広告の空白が残る: 対象 View の class / ID / tag を取得し、限定的な classifier を追加する。
- 広告通信が残る: 呼び出し元 class と最初の Android / Java / HTTP API 境界を特定する。ホスト追加だけで済ませず、SDK class 境界で遮断できるかを先に検討する。
- 正常通信まで止まる: 広すぎる host suffix または class marker を取り除き、SDK 固有の条件へ狭める。
- User-Agent が変わらない: 実際のクライアントが使う header setter / request builder の method reference を追加する。
- 再起動できない: patch 後 manifest の SettingsActivity metadata と元の launcher Activity 名を照合する。
- 再署名後の起動直後や設定表示時に FileProvider の `onCreate`、不自然な `NullPointerException` / ゼロ除算、巨大配列確保の `OutOfMemoryError` で終了する: 署名整合性チェックの結果配列比較と、その直後の限定的な失敗処理が変化していないか確認し、難読化クラス名ではなく命令構造を限定して更新する。

## 5. リリース前

1. `README.md` の検証状況と対応範囲を更新する。
2. `CHANGELOG.md` の `Unreleased` をリリース日付きバージョンへ移す。
3. `gradle.properties` の version を Semantic Versioning に従って更新する。
4. クリーンビルドと全検証を再実行する。
5. APK のハッシュ、端末 / Android 版、検証結果を release note に残す。ただし APK 自体は添付しない。
