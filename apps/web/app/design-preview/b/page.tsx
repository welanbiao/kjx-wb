import { DirectionPreview } from "@/components/design-preview/DirectionPreview";
import { DIRECTIONS } from "@/lib/design-preview/content";

export const metadata = {
  title: `${DIRECTIONS.b.name} · 叩君芯`,
  description: DIRECTIONS.b.thesis,
};

export default function DesignPreviewBPage() {
  return <DirectionPreview direction="b" />;
}
