import { DirectionPreview } from "@/components/design-preview/DirectionPreview";
import { DIRECTIONS } from "@/lib/design-preview/content";

export const metadata = {
  title: `${DIRECTIONS.a.name} · 叩君芯`,
  description: DIRECTIONS.a.thesis,
};

export default function DesignPreviewAPage() {
  return <DirectionPreview direction="a" />;
}
