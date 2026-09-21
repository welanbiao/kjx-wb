export function isMockToolsEnabled(): boolean {
  return process.env.NEXT_PUBLIC_ENABLE_MOCK_TOOLS === "true";
}
