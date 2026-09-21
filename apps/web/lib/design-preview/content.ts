export const PRODUCT_NAME = "叩君芯";

export const PRODUCT_DEFINITION =
  "一个会员制自营平台：用户注册平台账号并绑定会员身份，用解锁额度解锁平台自研的 AI 文游 与 多轮对话式 UI 布局；两类界面都支持平台计费 API 或自带 API 网关运行。";

export const NAV_ITEMS = [
  { href: "#hero", label: "作品入口" },
  { href: "#member", label: "会员与解锁" },
  { href: "#admin", label: "后台样例" },
] as const;

export const HERO_ACTIONS = {
  browse: "浏览作品列表",
  register: "注册",
  login: "登录",
} as const;

export const BIND_COPY = {
  unboundTitle: "你还没有绑定会员",
  unboundBody:
    "请在下方填写你的抖音号与会员时长，系统将与你购买的会员记录进行核对。",
  douyin: "抖音号",
  months: "会员时长（已订阅月数）",
  memberCode: "会员编码",
  submit: "提交核对",
  appeal: "提交申诉",
  pending: "资料审核中，预计 1 个工作日内完成",
  rejectedPrefix: "审核未通过",
  unbinding: "解绑审核中",
  notStarted: "会员尚未生效",
  expired: "已过期，不能再解锁新作品；已解锁作品仍可访问",
  loginRequired: "未登录用户不能解锁作品，请先登录",
} as const;

export const QUOTA_COPY = {
  formula: "可解锁作品数 = floor(已订阅天数 / 30)",
  floorNote: "已订阅天数不足 30 天时不可解锁",
  buyout: "已解锁作品为买断制：不消耗额度，可一直使用，不再校验会员是否有效",
  remaining: "剩余可解锁数",
  available: "可解锁作品数",
  unlocked: "已解锁数量",
  subscribedDays: "已订阅天数",
  expireAt: "到期时间",
  startAt: "开通时间",
} as const;

export const WORK_TYPES = [
  {
    id: "layout",
    type: "layout" as const,
    typeLabel: "多轮对话式 UI 布局",
    surface: "会话界面",
    enterLabel: "进入会话界面",
    status: "已上架" as const,
    channels: ["平台通道", "自备通道 BYOK"] as const,
    entitlement: "locked" as const,
  },
  {
    id: "game",
    type: "game" as const,
    typeLabel: "AI 文游",
    surface: "游戏游玩界面",
    enterLabel: "进入游戏游玩界面",
    status: "已上架" as const,
    channels: ["平台通道", "自备通道 BYOK"] as const,
    entitlement: "unlocked" as const,
  },
] as const;

export const UNLOCK_COPY = {
  confirmTitle: "解锁二次确认",
  confirm: "确认解锁",
  cancel: "取消",
  afterUnlock: "解锁后剩余可解锁数",
  demoMode: "演示模式说明",
  demoBody:
    "未绑定会员可进入演示模式：服务端预置剧本，不消耗额度、不调真实模型。",
  guestUnlock: "未登录用户不能解锁作品，请先登录",
  needValid: "新解锁要求会员有效",
  buyoutNote: "已解锁作品为买断制：不消耗额度，可一直使用，不再校验会员是否有效",
} as const;

export const SKIP_TO_CONTENT = "跳到主要内容";

export const WORK_ENTRY_NOTE =
  "游客可浏览作品列表与详情。解锁需已登录、已绑定，且会员状态为有效。";

export const MEMBER_SECTION = {
  title: "登录后：会员状态与解锁任务",
  account: "账号信息",
  member: "会员信息",
  bind: "绑定操作",
  tasks: "解锁路径",
  loggedIn: "预览按已登录、已绑定、会员有效展示",
} as const;

export const BIND_HINTS = {
  douyin: "用户在抖音的个人主页可见",
  months: "用户填「我买/订阅了几个月」，仅用于绑定核对",
  memberCode: "由主播或客服发放，半私密",
} as const;

export const DEFAULT_DIRECTION = "a" as const;

export const COMPARE = {
  kicker: "视觉方向探索",
  title: "三套方向同页对照",
  lead: "这不是演示文稿。每套都是响应式应用页：公开 Hero 与作品入口、登录后会员任务、以及 RuoYi / Element Plus 高密度后台样例。文案只使用规格已确认表述，不编写客户指标或奖项。",
  recommendedLabel: "建议默认方向",
  recommendedName: "瑞士网格",
  recommendedWhy:
    "会员绑定与额度解锁是高风险任务流，后台必须保持运营熟悉的表格交互。瑞士网格能同时覆盖公开浏览、登录后任务页，并与 RuoYi 后台共存；另外两套更适合作为公开落地的品牌层。",
  open: "打开整页预览",
  back: "返回对照",
  a11y: "三套均使用语义 HTML、可见焦点、44px 触控、prefers-reduced-motion。请在 320 / 375 / 414 / 768 / 1440 检查，页面不得横向溢出。",
} as const;

export const MEMBER_SAMPLE = {
  caption: "预览样例：字段与状态来自规格，编码格式取自已确认示例",
  nickname: "已登录账号",
  phoneMasked: "138****0000",
  memberStatus: "有效",
  memberStatusCode: "VALID",
  bindStatus: "已绑定",
  bindStatusCode: "MATCHED",
  memberCode: "KJX-A3F9K2B7",
  subscribedDays: 90,
  subscribedMonths: 3,
  availableCount: 3,
  unlockedCount: 1,
  remainingCount: 2,
  startAt: "2026-06-23 00:00:00.000",
  expireAt: "2026-12-31 23:59:59.999",
} as const;

export const TASKS = [
  {
    id: "bind",
    title: "绑定会员",
    detail: "个人主页提交三要素：抖音号 + 已订阅月数 + 会员编码",
  },
  {
    id: "quota",
    title: "核对解锁额度",
    detail: "额度只按已订阅天数计算，月数仅用于绑定核对，互不换算",
  },
  {
    id: "unlock",
    title: "解锁作品",
    detail: "新解锁要求会员有效；解锁原子扣减剩余额度",
  },
  {
    id: "enter",
    title: "进入 AI 互动内容",
    detail: "layout 进入会话界面，game 进入游戏游玩界面",
  },
] as const;

export const ADMIN_FILTERS = [
  { id: "all", label: "全部状态" },
  { id: "VALID", label: "有效" },
  { id: "EXPIRED", label: "已过期" },
  { id: "NOT_STARTED", label: "未生效" },
] as const;

export const BIND_FILTERS = [
  { id: "all", label: "全部绑定" },
  { id: "PENDING_REVIEW", label: "待审核" },
  { id: "MATCHED", label: "已绑定" },
  { id: "NONE", label: "未绑定" },
] as const;

export type AdminMemberRow = {
  memberCode: string;
  douyin: string;
  status: "VALID" | "EXPIRED" | "NOT_STARTED";
  statusLabel: string;
  expireAt: string;
  subscribedDays: number;
  availableCount: number;
  unlockedCount: number;
  remainingCount: number;
  bindStatus: "NONE" | "PENDING_REVIEW" | "MATCHED" | "REJECTED" | "UNBINDING";
  bindLabel: string;
  workType: string;
};

export const ADMIN_ROWS: AdminMemberRow[] = [
  {
    memberCode: "KJX-A3F9K2B7",
    douyin: "（预览）",
    status: "VALID",
    statusLabel: "有效",
    expireAt: "2026-12-31 23:59:59.999",
    subscribedDays: 90,
    availableCount: 3,
    unlockedCount: 1,
    remainingCount: 2,
    bindStatus: "MATCHED",
    bindLabel: "已绑定",
    workType: "AI 文游",
  },
  {
    memberCode: "KJX-C4D8E2F6",
    douyin: "（预览）",
    status: "EXPIRED",
    statusLabel: "已过期",
    expireAt: "2026-08-31 23:59:59.999",
    subscribedDays: 60,
    availableCount: 2,
    unlockedCount: 2,
    remainingCount: 0,
    bindStatus: "MATCHED",
    bindLabel: "已绑定",
    workType: "多轮对话式 UI 布局",
  },
  {
    memberCode: "KJX-H7K3M9P2",
    douyin: "（预览）",
    status: "NOT_STARTED",
    statusLabel: "未生效",
    expireAt: "2026-12-31 23:59:59.999",
    subscribedDays: 30,
    availableCount: 1,
    unlockedCount: 0,
    remainingCount: 1,
    bindStatus: "PENDING_REVIEW",
    bindLabel: "资料审核中",
    workType: "—",
  },
  {
    memberCode: "KJX-Q1R5T8W4",
    douyin: "（预览）",
    status: "VALID",
    statusLabel: "有效",
    expireAt: "2027-03-31 23:59:59.999",
    subscribedDays: 29,
    availableCount: 0,
    unlockedCount: 0,
    remainingCount: 0,
    bindStatus: "NONE",
    bindLabel: "未绑定会员",
    workType: "—",
  },
];

export const ADMIN_COLUMNS = [
  { key: "memberCode", label: "会员编码" },
  { key: "douyin", label: "抖音号" },
  { key: "statusLabel", label: "会员状态" },
  { key: "expireAt", label: "到期时间" },
  { key: "subscribedDays", label: "已订阅天数" },
  { key: "availableCount", label: "可解锁数" },
  { key: "unlockedCount", label: "已解锁数" },
  { key: "remainingCount", label: "剩余数" },
  { key: "bindLabel", label: "绑定状态" },
  { key: "workType", label: "已解锁作品类型" },
] as const;

export const ADMIN_COPY = {
  title: "会员授权管理",
  subtitle: "RuoYi / Element Plus 高密度表格：筛选、状态标签、行操作。后台不做营销动效。",
  module: "会员中心",
  pages: "会员授权管理 · 会员绑定审核",
  search: "编码 / 抖音号",
  query: "查询",
  reset: "重置",
  approve: "通过",
  reject: "驳回",
  reason: "驳回原因",
  export: "导出",
  empty: "没有符合筛选条件的会员记录",
  loading: "正在查询…",
  success: "审核已记录（预览，不写库）",
  error: "请填写驳回原因",
  role: "OPERATOR / ADMIN",
  note: "列表字段来自 B-07：会员编码、抖音ID、到期时间、已订阅天数、可解锁数、已解锁数、剩余数、状态、绑定。额度公式 floor(已订阅天数 / 30)。",
} as const;

export const DIRECTIONS = {
  a: {
    id: "a" as const,
    name: "瑞士网格",
    source: "safe" as const,
    sourceLabel: "克制安全",
    preset: "Swiss Modern",
    from: "STYLE_PRESETS.md · Swiss Modern",
    thesis: "白底、黑字、单一正红。可见网格与不对称栏宽，把产品说清楚，不把情绪做满。",
    fit: "适合作为全站默认：公开浏览、登录后任务页、以及需要与 RuoYi 后台共存的克制界面。",
    tradeoff: "辨识度低于另外两套；公开 Hero 不会形成强烈品牌记忆。",
  },
  b: {
    id: "b" as const,
    name: "墨火大报",
    source: "bold" as const,
    sourceLabel: "大胆模板",
    preset: "Broadside",
    from: "bold-template-pack · Broadside（MIT）",
    thesis: "墨黑画布与火橙色块对打。Barlow 超大字重把 koujunxin 当图形，中文标题保持零字距。",
    fit: "适合公开落地与作品入口的品牌表达，尤其需要中英双语、编辑气质时。",
    tradeoff: "暗色后台偏离运营已熟悉的 Element Plus 浅色表格；登录后任务页会偏戏剧。",
  },
  c: {
    id: "c" as const,
    name: "分幕通行证",
    source: "custom" as const,
    sourceLabel: "自创",
    preset: "Key & Curtain",
    from: "针对「会员绑定 → 作品解锁 → AI 互动内容」自创",
    thesis: "会员编码是通行证，额度是打孔，作品是分幕。幕布与脚灯色只服务这条解锁路径。",
    fit: "适合 C 端从公开浏览走到绑定、解锁、进入会话/游玩的主路径。",
    tradeoff: "装饰语义强，不适合直接铺到整个管理端；后台仍应回到高密度表格。",
  },
} as const;

export function quotaFromDays(subscribedDays: number): number {
  return Math.floor(subscribedDays / 30);
}
