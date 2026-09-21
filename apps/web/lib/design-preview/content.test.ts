import { describe, expect, it } from "vitest";
import {
  ADMIN_ROWS,
  DEFAULT_DIRECTION,
  DIRECTIONS,
  PRODUCT_DEFINITION,
  PRODUCT_NAME,
  quotaFromDays,
} from "./content";
import { DIRECTION_TOKENS } from "./tokens";

describe("design preview shared content", () => {
  it("keeps the confirmed product name and definition", () => {
    expect(PRODUCT_NAME).toBe("叩君芯");
    expect(PRODUCT_DEFINITION).toMatch(/会员制自营平台/);
    expect(PRODUCT_DEFINITION).not.toMatch(/获奖|第一|百万用户/);
  });

  it("uses floor(days / 30) for quota", () => {
    expect(quotaFromDays(29)).toBe(0);
    expect(quotaFromDays(30)).toBe(1);
    expect(quotaFromDays(90)).toBe(3);
  });

  it("exposes one safe, one bold, and one custom direction", () => {
    expect(DIRECTIONS.a.source).toBe("safe");
    expect(DIRECTIONS.a.preset).toBe("Swiss Modern");
    expect(DIRECTIONS.b.source).toBe("bold");
    expect(DIRECTIONS.b.preset).toBe("Broadside");
    expect(DIRECTIONS.c.source).toBe("custom");
    expect(DEFAULT_DIRECTION).toBe("a");
  });

  it("keeps admin sample remaining counts aligned with available minus unlocked", () => {
    for (const row of ADMIN_ROWS) {
      expect(row.remainingCount).toBe(row.availableCount - row.unlockedCount);
      expect(quotaFromDays(row.subscribedDays)).toBe(row.availableCount);
    }
  });

  it("gives each direction a distinct palette", () => {
    expect(DIRECTION_TOKENS.a.accent).toBe("#ff3300");
    expect(DIRECTION_TOKENS.b.accent).toBe("#e85d26");
    expect(DIRECTION_TOKENS.c.accent).toBe("#f4520d");
    expect(DIRECTION_TOKENS.c.surface).toBe("#1387c0");
    expect(DIRECTION_TOKENS.c.ticket).toBe("#faedd1");
  });
});
