const BREAKUP = "breakup-editor";
const BREAKUP_NAME = "分手结局修改器";
const BREAKUP_SUMMARY = "如果那句话没说出口，他们还会分手吗？今天，帮 TA 改写曾经的选择。";
const DREAM = "dream-phone";
const DREAM_NAME = "梦女小手机";
const DREAM_SUMMARY = "一对一、朋友圈、红包都在这部手机里。";

function looksBroken(value: string | null | undefined, needle: string): boolean {
  if (!value || !value.trim()) {
    return true;
  }
  if (value.includes(needle)) {
    return false;
  }
  const marks = Array.from(value).filter((ch) => ch === "?" || ch === "\uFFFD").length;
  return marks >= Math.max(2, Math.floor(value.length / 2));
}

function recoverLatin1(value: string): string {
  try {
    const bytes = Uint8Array.from(Array.from(value, (ch) => ch.charCodeAt(0) & 0xff));
    return new TextDecoder("utf-8").decode(bytes);
  } catch {
    return value;
  }
}

/** Page decoder: live row may already be question marks; never show those as the title. */
export function decodeWorkName(workCode: string, raw: string | null | undefined): string {
  if (workCode === BREAKUP) {
    const recovered = raw ? recoverLatin1(raw) : "";
    if (looksBroken(recovered || raw, "分手")) {
      return BREAKUP_NAME;
    }
    return recovered.includes("分手") ? recovered : raw || BREAKUP_NAME;
  }
  if (workCode === DREAM) {
    const recovered = raw ? recoverLatin1(raw) : "";
    if (looksBroken(recovered || raw, "梦女")) {
      return DREAM_NAME;
    }
    return recovered.includes("梦女") ? recovered : raw || DREAM_NAME;
  }
  return raw ?? "";
}

export function decodeWorkSummary(workCode: string, raw: string | null | undefined): string {
  if (workCode === BREAKUP) {
    const recovered = raw ? recoverLatin1(raw) : "";
    if (looksBroken(recovered || raw, "那句话")) {
      return BREAKUP_SUMMARY;
    }
    return recovered.includes("那句话") ? recovered : raw || BREAKUP_SUMMARY;
  }
  if (workCode === DREAM) {
    const recovered = raw ? recoverLatin1(raw) : "";
    if (looksBroken(recovered || raw, "朋友圈")) {
      return DREAM_SUMMARY;
    }
    return recovered.includes("朋友圈") ? recovered : raw || DREAM_SUMMARY;
  }
  return raw ?? "";
}
