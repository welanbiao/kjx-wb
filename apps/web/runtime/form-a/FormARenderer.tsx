import "@/runtime/form-a/form-a.css";
import { FormANode } from "@/runtime/form-a/FormANode";
import { isFormAType, parseJsonArray, parseJsonObject } from "@/runtime/form-a/components";
import type { LayoutDoc, LayoutNode, StageView } from "@/runtime/types";

function asNode(value: unknown): LayoutNode | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const type = "type" in value && typeof value.type === "string" ? value.type : "";
  if (!isFormAType(type)) {
    return null;
  }
  const props =
    "props" in value && value.props && typeof value.props === "object" && !Array.isArray(value.props)
      ? (value.props as Record<string, unknown>)
      : {};
  return { type, props };
}

export function FormARenderer({ stage }: { stage: StageView }) {
  const layout = parseJsonObject(stage.layoutJson) as LayoutDoc;
  const screens = Array.isArray(layout.screens) ? layout.screens : [];
  const extraNodes = parseJsonArray(stage.nodesJson)
    .map((item) => {
      if (!item || typeof item !== "object") {
        return null;
      }
      const record = item as Record<string, unknown>;
      const type = typeof record.type === "string" ? record.type : "chat.bubble";
      return asNode({ type, props: { text: record.text, title: record.title } });
    })
    .filter((node): node is LayoutNode => node !== null);

  return (
    <div className="kjx-runtime-phone">
      <div className="kjx-runtime-status">
        <span>叩君芯</span>
        <span>{stage.stage}</span>
      </div>
      <div className="kjx-runtime-screen">
        {screens.length === 0 && extraNodes.length === 0 ? (
          <p className="kjx-runtime-unknown">这一幕没有可渲染的固定组件。</p>
        ) : null}
        {screens.flatMap((screen, screenIndex) =>
          (screen.nodes ?? [])
            .map((node, index) => {
              const safe = asNode(node);
              if (!safe) {
                return (
                  <p key={`${screen.id ?? screenIndex}-${index}`} className="kjx-runtime-unknown">
                    未登记组件已跳过
                  </p>
                );
              }
              return <FormANode key={`${screen.id ?? screenIndex}-${safe.type}-${index}`} node={safe} />;
            }),
        )}
        {screens.length === 0
          ? extraNodes.map((node, index) => <FormANode key={`node-${index}`} node={node} />)
          : null}
      </div>
    </div>
  );
}
