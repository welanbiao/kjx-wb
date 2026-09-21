import { describe, expect, it } from "vitest";
import { canTransit, workActions } from "./work-status";

describe("work status operations", () => {
  it("follows WP-12 transitions", () => {
    expect(canTransit("DRAFT", "PENDING_REVIEW")).toBe(true);
    expect(canTransit("DRAFT", "PUBLISHED")).toBe(false);
    expect(canTransit("PENDING_REVIEW", "PUBLISHED")).toBe(true);
    expect(canTransit("PENDING_REVIEW", "GRAY")).toBe(true);
    expect(canTransit("PUBLISHED", "UNPUBLISHED")).toBe(true);
    expect(canTransit("PUBLISHED", "DRAFT")).toBe(false);
    expect(canTransit("UNPUBLISHED", "PUBLISHED")).toBe(true);
    expect(canTransit("GRAY", "UNPUBLISHED")).toBe(true);
  });

  it("exposes publish and unpublish actions from live states", () => {
    const published = workActions("PUBLISHED").map((item) => item.to);
    expect(published).toContain("GRAY");
    expect(published).toContain("UNPUBLISHED");
    const draft = workActions("DRAFT").map((item) => item.to);
    expect(draft).toEqual(["PENDING_REVIEW"]);
  });
});
