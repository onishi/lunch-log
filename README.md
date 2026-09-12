# ランチログ (Lunch Log)

毎日のランチ (と、その他の食事) を写真とともに記録し、Web で振り返り、
スプレッドシートやはてなブログへ書き出せる Android アプリ。

- [SPEC.md](./SPEC.md) — 仕様 (何を作るか)
- [PLAN.md](./PLAN.md) — 開発計画とチェックリスト (どの順で作るか)

## 現在の状態

**Phase 0 (足場) 〜 Phase 1-2 (ドメイン層)** まで。まだアプリとしては動かない。
進捗は PLAN.md のチェックボックスを参照。

## 構成

| モジュール | 内容 | Android SDK |
|------------|------|-------------|
| `core` | ドメインモデルと純粋なロジック (ID 生成、geohash、検証、食事種別の推定) | 不要 |
| `app` | Android アプリ本体 (Compose / CameraX / Room / Firebase) | 必要 |

`core` を Android から切り離しているのは、**端末や SDK なしでロジックを
テストできるようにする**ため。判断を含むコードはできるだけ `core` に置く。

## セットアップ

### 必要なもの

- JDK 17
- Android Studio (最新安定版) / Android SDK 35
- Firebase プロジェクト

### 手順

1. リポジトリを clone する。
2. Firebase コンソールで Android アプリを登録する。
   - パッケージ名: `app.lunchlog`
   - Authentication で Google プロバイダを有効化
   - Firestore (ネイティブモード) と Cloud Storage を作成 (`asia-northeast1`)
3. `google-services.json` を `app/` に置く。**このファイルはコミットしない。**
4. `app/build.gradle.kts` の `google-services` プラグインのコメントを外す。
5. ビルドする。

```bash
./gradlew :core:test          # ドメイン層のテスト (Android SDK 不要)
./gradlew :app:assembleDebug  # アプリのビルド
```

## 開発の進め方

- 作業は `main` からブランチを切る。
- 仕様を変えるときは `SPEC.md` を直す。実装順の変更は `PLAN.md` 側で行う。
- 詳細は [CLAUDE.md](./CLAUDE.md) を参照。
