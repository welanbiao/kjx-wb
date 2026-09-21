/**
 * Display catalog copied from contracts/errors.yaml.
 * Frontend never invents new business codes.
 */
export const ERROR_MESSAGES: Record<number, string> = {
  1001: "信息不匹配，请核对后重试",
  1002: "该会员信息已被使用，如有疑问请联系客服",
  1003: "每个账号只能绑定一个会员",
  1004: "尝试次数过多，请 30 分钟后再试",
  1005: "会员尚未生效，生效时间 {time}",
  1006: "会员已过期，请续费",
  1007: "可解锁数量已用完",
  1008: "作品已下架",
  1009: "请先完成会员绑定",
  1010: "已解锁，正在进入",
  1011: "验证码错误或已过期",
  1012: "发送过于频繁，请稍后再试",
  1013: "账号已锁定，请联系客服",
  1014: "账号注销中，暂时无法操作",
  1090: "重复请求与首次参数不一致",
};

export const ERROR_HTTP_STATUS: Record<number, number> = {
  1001: 400,
  1002: 409,
  1003: 409,
  1004: 429,
  1005: 403,
  1006: 403,
  1007: 403,
  1008: 403,
  1009: 403,
  1010: 200,
  1011: 400,
  1012: 429,
  1013: 403,
  1014: 403,
  1090: 409,
};

export const ALREADY_UNLOCKED = 1010;

export function displayErrorMessage(code: number, msg: string, data?: unknown): string {
  const fallback = ERROR_MESSAGES[code] ?? msg ?? "请求失败";
  const text = msg?.trim() ? msg : fallback;
  if (code === 1005 && data && typeof data === "object" && data !== null && "time" in data) {
    return text.replace("{time}", String((data as { time: unknown }).time));
  }
  return text;
}

export class ApiError extends Error {
  readonly code: number;
  readonly requestId: string;
  readonly httpStatus: number;
  readonly data: unknown;

  constructor(options: {
    code: number;
    msg: string;
    requestId: string;
    httpStatus: number;
    data?: unknown;
  }) {
    super(displayErrorMessage(options.code, options.msg, options.data));
    this.name = "ApiError";
    this.code = options.code;
    this.requestId = options.requestId;
    this.httpStatus = options.httpStatus;
    this.data = options.data;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
