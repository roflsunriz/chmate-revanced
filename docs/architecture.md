# アーキテクチャ

## patch 時

`ChMateResourcePatch` は manifest に初期化 Provider と設定 Activity を追加し、元の launcher Activity 名を metadata へ保存します。さらに `res/layout*` を走査し、限定した広告 class / ID / tag に一致する View の高さ・最小高さ・上下 margin をゼロ、visibility を `gone` にします。

`NetworkBoundaryPatch` は難読化名ではなく method reference を走査します。通常の ChMate コードには既知広告ホストの判定を挟み、広告 SDK package 内のコードには送信先を問わない遮断 method を挟みます。同時に User-Agent が到達しやすい HTTP header、`System.getProperty("http.agent")`、WebView の各境界を置換します。

## 実行時

`BootstrapProvider` が Application より先に共有状態を初期化し、Activity lifecycle callback を登録します。動的に追加された広告 View は global layout 後にも再走査されます。

設定 Activity は `:revanced_settings` process で動作します。そのため ChMate の main process を終了してから、patch 時に記録した元の launcher Activity を安全に起動できます。設定値は同一 package の SharedPreferences へ同期保存します。

## 防御範囲

遮断対象:

- `InetAddress.getByName` / `getAllByName`
- `URL.openConnection`
- 文字列 URL を受け取る一般的な HTTP builder と Cronet builder
- WebView `loadUrl`
- 既知広告 host suffix
- 既知広告 SDK package 起点の上記通信

User-Agent 対象:

- 一般的な `header` / `addHeader` / `setHeader` / `setRequestProperty` / `addRequestProperty`
- `System.getProperty("http.agent")`
- WebSettings と WebView

この patch は OS の VPN や firewall ではありません。JNI から直接行う socket 通信、動的コードロード、未分類 SDK の独自 transport は別途境界追加が必要です。

## 依存関係の採用判断

- ReVanced Patcher `22.0.1`: 現行の matching / patch DSL と Android resource rebuild を利用するため。旧 Patcher は DSL と bundle 形式が異なるため不採用。
- ReVanced Patches Gradle plugin `1.0.0-dev.11`: `.rve` の DEX 化と `.rvp` packaging を公式方式で行うため。独自 packaging script は形式追従と署名 metadata の保守負担が大きいため不採用。
- SMALI `3.0.9`: Patcher と同じ命令 model をコンパイル時に使うため。別 dex library の併用は型不一致を起こすため不採用。
- JUnit Jupiter `5.13.4` / Platform Launcher `1.13.4`: Kotlin の patch ロジックと Java の runtime ロジックを同じ test engine で検証するため。Gradle 9 は launcher の明示的な runtime 依存を要求する。JUnit 4 は Kotlin / Java 両方で使えるが、今後の parameterized test 拡張性から不採用。

影響範囲は build と test のみで、JUnit は `.rvp` や `.rve` に含まれません。Patcher と SMALI は patch bundle のコンパイル対象です。依存 lock は正規の GitHub Packages 認証で全 configuration を解決してから生成し、解決不能な状態の不完全な lockfile はコミットしません。
