import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig, loadEnv } from "vite";
import { normalizePublicBase } from "./src/lib/public-base";

function hmrClient(origin: string | undefined): { clientPort: number } | undefined {
  const raw = origin?.trim();
  if (!raw) {
    return undefined;
  }
  const url = new URL(raw);
  if (url.port) {
    return { clientPort: Number(url.port) };
  }
  return { clientPort: url.protocol === "https:" ? 443 : 80 };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const publicBase = env.VITE_BASE || process.env.VITE_BASE;
  const devOrigin = env.KJX_DEV_ORIGIN || process.env.KJX_DEV_ORIGIN;
  const base = normalizePublicBase(publicBase);

  return {
    base,
    plugins: [vue()],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
    server: {
      port: 3001,
      allowedHosts: ["localhost", "127.0.0.1"],
      origin: devOrigin || undefined,
      hmr: hmrClient(devOrigin),
      proxy: {
        "/admin-api": {
          target: env.VITE_API_BASE_URL || "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
  };
});
