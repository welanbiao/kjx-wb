const DATE = /^\d{4}-\d{2}-\d{2}$/;
const DATE_TIME =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,3})?\+08:00$/;

/** Expand a calendar date to the contract expireAt instant. Never derived from months/days. */
export function expandExpireDate(dateYmd: string): string {
  const trimmed = dateYmd.trim();
  if (!DATE.test(trimmed)) {
    throw new Error("到期日期格式应为 YYYY-MM-DD");
  }
  return `${trimmed}T23:59:59.999+08:00`;
}

export function expireDatePart(expireAt: string | undefined): string {
  if (!expireAt) {
    return "";
  }
  return expireAt.slice(0, 10);
}

export function isExpireAt(value: string): boolean {
  return DATE_TIME.test(value.trim());
}
