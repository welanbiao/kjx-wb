import { AppShell } from "@/components/product/AppShell";
import { RuntimeScreen } from "@/runtime/RuntimeScreen";

export default async function RuntimePage({
  params,
}: {
  params: Promise<{ workCode: string }>;
}) {
  const { workCode } = await params;
  return (
    <AppShell variant="task">
      <RuntimeScreen workCode={workCode} />
    </AppShell>
  );
}
