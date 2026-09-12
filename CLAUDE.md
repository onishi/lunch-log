# 開発メモ

このリポジトリで作業するときの約束ごと。

## 文書の役割

| 文書 | 役割 |
|------|------|
| `SPEC.md` | 仕様。何を作るか。**仕様を変えたくなったらここを直す。** |
| `PLAN.md` | 計画。どの順で作るか。進捗はここのチェックボックスで管理する。 |
| `README.md` | セットアップ手順。 |

実装中に仕様の判断が必要になったら、コードにコメントで埋めずに SPEC.md を更新する。

## コマンド

```bash
./gradlew :core:test            # ドメイン層のテスト。Android SDK なしで動く
./gradlew :app:assembleDebug    # デバッグビルド
./gradlew :app:testDebugUnitTest

cd functions && npm test        # サーバ処理のテスト
cd functions && npm run typecheck

scripts/ktlint.sh               # Kotlin のスタイル検査 (Android SDK 不要)
scripts/ktlint.sh --fix         # 自動修正
```

## モジュールの方針

- **`core` は Android に依存させない。** `android.*` と `androidx.*` を import しない。
  判断を含むロジック (検証、推定、変換、ID 生成) はここに置き、ユニットテストを書く。
- **`app` は薄く保つ。** UI と、Android の API を叩く部分だけ。
- **`functions` も同じ考え方で組む。** 外部 API の呼び出しは関数として注入し、
  判断 (半径を広げる、並べ替える、キャッシュを使う) はネットワークなしでテストする。

## コーディング規約

- Kotlin 公式スタイル (`kotlin.code.style=official`)。
- コメントと KDoc は日本語で書く。**何をしているかではなく、なぜそうしたかを書く。**
  特に SPEC のどの判断に由来するのかを残す (例: `SPEC §9.1`, `F-116`)。
- 例外を投げるのは、プログラマの誤りのときだけ。利用者の入力は
  `ValidationResult` のような戻り値で扱う。
- 日時は `Instant` (UTC) で保持し、表示のときだけ利用者のタイムゾーンへ変換する。

## テストの方針

- テスト名は日本語のバッククォート記法にする (`fun \`11時から15時まではランチ\`()`)。
- **仕様に由来する値はテストで固定する。** 例: ランチの時間帯、保存に必須な項目、
  写真の上限枚数。SPEC を変えたらテストも変わるべき、という関係にしておく。
- 外部 API (Places / はてな) を叩くテストは書かない。境界のデータ変換だけ検証する。

## やらないこと

- MVP に入っていない機能を「ついでに」実装しない (PLAN.md の「やらないことリスト」)。
- 監視 (`monitor`) のコードは Phase 6 まで書かない (SPEC §6.6)。
- `google-services.json` と署名鍵をコミットしない。
