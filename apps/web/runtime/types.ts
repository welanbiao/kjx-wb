export type RuntimeForm = "A" | "B" | "C";

export type SessionView = {
  sessionId: string;
  workCode: string;
  form: RuntimeForm;
  stage: string;
  schemaVersion: string;
  contentVersion?: string;
  checksum?: string;
  status: "ACTIVE" | "ENDED";
  version: number;
  payloadJson?: string;
};

export type StageView = {
  sessionId: string;
  workCode: string;
  form: RuntimeForm;
  stage: string;
  gated: boolean;
  layoutJson: string;
  tokensJson: string;
  nodesJson: string;
  assets: Record<string, string>;
};

export type SandboxBoot = {
  sessionId: string;
  workCode: string;
  form: RuntimeForm;
  iframeSrc: string;
  nonce: string;
  origin: string;
  sandbox: string;
  methods: string[];
  expiresAt: string;
};

export type HostResult = {
  ok: boolean;
  method: string;
  payloadJson: string;
};

export type LayoutNode = {
  type: string;
  props?: Record<string, unknown>;
  stage?: string | number;
};

export type LayoutScreen = {
  id?: string;
  stage?: string | number;
  nodes?: LayoutNode[];
};

export type LayoutDoc = {
  type?: string;
  screens?: LayoutScreen[];
};
