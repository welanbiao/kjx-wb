const SENSITIVE = /phone|mobile|token|password|captcha|authorization|sms|verifycode|verify_code/i;

function redactValue(key: string, value: unknown): unknown {
  if (SENSITIVE.test(key)) {
    return "[redacted]";
  }
  if (Array.isArray(value)) {
    return value.map((item, index) => redactValue(String(index), item));
  }
  if (value && typeof value === "object") {
    return redactRecord(value as Record<string, unknown>);
  }
  return value;
}

export function redactRecord(input: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(input)) {
    out[key] = redactValue(key, value);
  }
  return out;
}

/** Never print phone numbers, tokens or captcha in logs. */
export function safeDebug(): void {
  // Intentionally empty: admin UI must not log secrets.
}
