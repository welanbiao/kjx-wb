import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { defineConfig, loadEnv } from "vite";
import { normalizePublicBase } from "./src/lib/public-base";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const base = normalizePublicBase(env.VITE_BASE);

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
      proxy: {
        "/admin-api": {
          target: env.VITE_API_BASE_URL || "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
  };
});
