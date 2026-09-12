import { describe, expect, it } from "vitest";
import { RateLimiter } from "../src/http/rateLimit";

describe("RateLimiter", () => {
  it("上限までは通す", () => {
    const limiter = new RateLimiter(3);
    expect(limiter.tryAcquire("uid", 0)).toBe(true);
    expect(limiter.tryAcquire("uid", 0)).toBe(true);
    expect(limiter.tryAcquire("uid", 0)).toBe(true);
    expect(limiter.tryAcquire("uid", 0)).toBe(false);
  });

  it("利用者ごとに数える", () => {
    const limiter = new RateLimiter(1);
    expect(limiter.tryAcquire("uid-a", 0)).toBe(true);
    expect(limiter.tryAcquire("uid-b", 0)).toBe(true);
    expect(limiter.tryAcquire("uid-a", 0)).toBe(false);
  });

  it("1分経てばまた通る", () => {
    const limiter = new RateLimiter(1);
    expect(limiter.tryAcquire("uid", 0)).toBe(true);
    expect(limiter.tryAcquire("uid", 59_999)).toBe(false);
    expect(limiter.tryAcquire("uid", 60_000)).toBe(true);
  });
});
