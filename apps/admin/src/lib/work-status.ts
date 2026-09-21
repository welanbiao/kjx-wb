import type { WorkStatus } from "./types";

const ALLOWED: Record<WorkStatus, WorkStatus[]> = {
  DRAFT: ["PENDING_REVIEW"],
  PENDING_REVIEW: ["PUBLISHED", "GRAY", "REJECTED", "DRAFT"],
  REJECTED: ["DRAFT", "PENDING_REVIEW"],
  PUBLISHED: ["GRAY", "UNPUBLISHED"],
  GRAY: ["PUBLISHED", "UNPUBLISHED"],
  UNPUBLISHED: ["PUBLISHED", "GRAY"],
};

export const WORK_STATUS_LABEL: Record<WorkStatus, string> = {
  DRAFT: "草稿",
  PENDING_REVIEW: "待审",
  PUBLISHED: "已发布",
  GRAY: "灰度",
  UNPUBLISHED: "已下架",
  REJECTED: "已驳回",
};

export function canTransit(from: WorkStatus, to: WorkStatus): boolean {
  if (from === to) {
    return true;
  }
  return ALLOWED[from]?.includes(to) ?? false;
}

export type WorkAction = {
  to: WorkStatus;
  label: string;
  danger?: boolean;
};

export function workActions(from: WorkStatus): WorkAction[] {
  const next = ALLOWED[from] ?? [];
  const labels: Record<WorkStatus, string> = {
    DRAFT: "退回草稿",
    PENDING_REVIEW: "提交审核",
    PUBLISHED: "发布",
    GRAY: "灰度发布",
    UNPUBLISHED: "下架",
    REJECTED: "驳回",
  };
  return next.map((to) => ({
    to,
    label: labels[to],
    danger: to === "UNPUBLISHED" || to === "REJECTED",
  }));
}
