import { AppShell } from "@/components/product/AppShell";
import { WorldHome } from "@/components/product/WorldHome";

export default function HomePage() {
  return (
    <AppShell variant="public">
      <WorldHome />
    </AppShell>
  );
}
