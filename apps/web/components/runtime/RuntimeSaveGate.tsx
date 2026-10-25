"use client";

/**
 * Cross-work server save gate: detect / continue / new / reset.
 * Copy is work-configured; session truth comes from listSessions + optional reset action.
 */
export type RuntimeSaveGateCopy = {
  title?: string;
  detected?: string;
  empty?: string;
  continueLabel?: string;
  newLabel?: string;
  resetLabel?: string;
};

export type RuntimeSaveGateProps = {
  hasSave: boolean;
  busy?: boolean;
  copy?: RuntimeSaveGateCopy;
  onContinue: () => void;
  onNew: () => void;
  onReset?: () => void;
};

const DEFAULTS: Required<RuntimeSaveGateCopy> = {
  title: "存档",
  detected: "检测到服务器存档。",
  empty: "尚无存档。开始后会写入服务器会话。",
  continueLabel: "继续",
  newLabel: "新开一局",
  resetLabel: "重置",
};

export function RuntimeSaveGate({ hasSave, busy, copy, onContinue, onNew, onReset }: RuntimeSaveGateProps) {
  const c = { ...DEFAULTS, ...copy };
  return (
    <div className="kjx-save-gate" role="region" aria-label={c.title}>
      <p className="kjx-hint">{hasSave ? c.detected : c.empty}</p>
      <div className="kjx-actions" style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
        {hasSave ? (
          <button type="button" className="kjx-btn" disabled={busy} onClick={onContinue}>
            {c.continueLabel}
          </button>
        ) : null}
        <button type="button" className="kjx-btn kjx-btn-ghost" disabled={busy} onClick={onNew}>
          {c.newLabel}
        </button>
        {hasSave && onReset ? (
          <button type="button" className="kjx-btn kjx-btn-ghost" disabled={busy} onClick={onReset}>
            {c.resetLabel}
          </button>
        ) : null}
      </div>
    </div>
  );
}
