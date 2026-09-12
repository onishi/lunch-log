/**
 * 利用者ごとのレート制限 (SPEC §8「1 ユーザーあたり 60 req/分」)。
 *
 * 関数インスタンスのメモリ上で数えるだけの簡易な実装。インスタンスが
 * 複数あれば上限はその数だけ緩くなるが、狙いは「壊れた端末が無限に
 * 叩いて課金が膨らむ」のを止めることなので、これで足りる。
 */
export const RATE_LIMIT_PER_MINUTE = 60;
const WINDOW_MS = 60_000;

export class RateLimiter {
  private readonly hits = new Map<string, number[]>();

  constructor(
    private readonly limit: number = RATE_LIMIT_PER_MINUTE,
    private readonly windowMs: number = WINDOW_MS,
  ) {}

  /** 呼び出しを 1 回記録し、上限内なら true。 */
  tryAcquire(key: string, now: number = Date.now()): boolean {
    const recent = (this.hits.get(key) ?? []).filter((at) => now - at < this.windowMs);

    if (recent.length >= this.limit) {
      this.hits.set(key, recent);
      return false;
    }
    recent.push(now);
    this.hits.set(key, recent);
    return true;
  }
}
