import { DirectionPreview } from "@/components/design-preview/DirectionPreview";
import { DIRECTIONS } from "@/lib/design-preview/content";

export const metadata = {
  title: `${DIRECTIONS.c.name} · 叩君芯`,
  description: DIRECTIONS.c.thesis,
};

export default function DesignPreviewCPage() {
  return <DirectionPreview direction="c" />;
}
