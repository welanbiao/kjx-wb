import { describe, expect, it } from "vitest";
import { emptyMemberTerm, patchMemberTerm, validateCreateMember, validateReason, validateUpdateMember } from "./member-form";

describe("member term fields", () => {
  it("requires months, days and expire independently", () => {
    const base = emptyMemberTerm();
    expect(() => validateCreateMember(base)).toThrow(/抖音号/);
    const withId = patchMemberTerm(base, "douyinId", "zhangsan");
    expect(() => validateCreateMember(withId)).toThrow(/月数/);
    const withMonths = patchMemberTerm(withId, "subscribedMonths", 15);
    expect(() => validateCreateMember(withMonths)).toThrow(/天数/);
    const withDays = patchMemberTerm(withMonths, "subscribedDays", 435);
    expect(() => validateCreateMember(withDays)).toThrow(/到期时间/);
  });

  it("never rewrites days when months change", () => {
    const current = {
      douyinId: "zhangsan",
      subscribedMonths: 15,
      subscribedDays: 435,
      expireDate: "2027-08-09",
      remark: "",
    };
    const next = patchMemberTerm(current, "subscribedMonths", 20);
    expect(next.subscribedDays).toBe(435);
    expect(next.expireDate).toBe("2027-08-09");
    expect(next.subscribedMonths).toBe(20);
  });

  it("never rewrites months when days change", () => {
    const current = {
      douyinId: "zhangsan",
      subscribedMonths: 15,
      subscribedDays: 435,
      expireDate: "2027-08-09",
      remark: "",
    };
    const next = patchMemberTerm(current, "subscribedDays", 100);
    expect(next.subscribedMonths).toBe(15);
    expect(next.expireDate).toBe("2027-08-09");
  });

  it("can save 15 months + 435 days without deriving either", () => {
    const body = validateCreateMember({
      douyinId: "zhangsan",
      subscribedMonths: 15,
      subscribedDays: 435,
      expireDate: "2027-08-09",
      remark: "",
    });
    expect(body.subscribedMonths).toBe(15);
    expect(body.subscribedDays).toBe(435);
    expect(body.expireAt).toBe("2027-08-09T23:59:59.999+08:00");
  });

  it("rejects empty reason on key updates", () => {
    expect(() => validateReason("  ")).toThrow(/理由/);
    const body = validateUpdateMember(
      {
        douyinId: "zhangsan",
        subscribedMonths: 16,
        subscribedDays: 435,
        expireDate: "2027-08-09",
        remark: "",
      },
      "续期核对",
    );
    expect(body.reason).toBe("续期核对");
    expect(body.subscribedMonths).toBe(16);
    expect(body.subscribedDays).toBe(435);
  });
});
