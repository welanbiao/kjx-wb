import { textProp } from "@/runtime/form-a/components";
import type { LayoutNode } from "@/runtime/types";

function NodeFrame({ type, title, text }: { type: string; title: string; text: string }) {
  return (
    <article className="kjx-runtime-node" data-type={type}>
      <p className="kjx-kicker">{title}</p>
      <p>{text}</p>
    </article>
  );
}

export function FormANode({ node }: { node: LayoutNode }) {
  const props = node.props ?? {};
  const text = textProp(props, "text") || textProp(props, "title") || textProp(props, "label");
  switch (node.type) {
    case "chat.bubble":
      return <NodeFrame type={node.type} title={textProp(props, "role", "对话")} text={text || "…"} />;
    case "chat.system":
      return <NodeFrame type={node.type} title="系统" text={text || "提示"} />;
    case "empty-state":
      return <NodeFrame type={node.type} title="空状态" text={text || "暂无内容"} />;
    default:
      return (
        <NodeFrame
          type={node.type}
          title={node.type.startsWith("custom.slot:") ? node.type.slice("custom.slot:".length) : node.type}
          text={text || "固定组件"}
        />
      );
  }
}
