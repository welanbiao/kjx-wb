import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const appsRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const failures = [];

function fail(message) {
  failures.push(message);
}

function walk(dir, acc = []) {
  if (!existsSync(dir)) {
    return acc;
  }
  for (const name of readdirSync(dir)) {
    if (name === "node_modules" || name === ".next" || name === "dist") {
      continue;
    }
    const fullPath = join(dir, name);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) {
      walk(fullPath, acc);
    } else {
      acc.push(fullPath);
    }
  }
  return acc;
}

function required(file, label) {
  if (!existsSync(join(appsRoot, file))) {
    fail(`missing ${label}: ${file}`);
  }
}

required("web/package.json", "web package");
required("web/app/page.tsx", "web home");
required("web/app/health/page.tsx", "web health page");
required("web/lib/api.ts", "web api client");
required("web/.env.example", "web env example");
required("admin/package.json", "admin package");
required("admin/src/views/AdminLogin.vue", "admin login");
required("admin/src/views/AdminHealth.vue", "admin health page");
required("admin/src/lib/api.ts", "admin api client");
required("admin/README.md", "admin README");

const webPkg = JSON.parse(readFileSync(join(appsRoot, "web/package.json"), "utf8"));
for (const [name, field] of [
  ["next", "dependencies"],
  ["react", "dependencies"],
  ["tailwindcss", "devDependencies"],
  ["typescript", "devDependencies"],
]) {
  if (!webPkg[field]?.[name]) {
    fail(`web ${field} missing ${name}`);
  }
}
for (const script of ["lint", "typecheck", "test"]) {
  if (!webPkg.scripts?.[script]) {
    fail(`web missing script: ${script}`);
  }
}

const adminPkg = JSON.parse(readFileSync(join(appsRoot, "admin/package.json"), "utf8"));
for (const [name, field] of [
  ["vue", "dependencies"],
  ["vue-router", "dependencies"],
  ["element-plus", "dependencies"],
  ["vite", "devDependencies"],
  ["typescript", "devDependencies"],
]) {
  if (!adminPkg[field]?.[name]) {
    fail(`admin ${field} missing ${name}`);
  }
}
for (const script of ["lint", "typecheck", "test"]) {
  if (!adminPkg.scripts?.[script]) {
    fail(`admin missing script: ${script}`);
  }
}

const adminReadme = readFileSync(join(appsRoot, "admin/README.md"), "utf8");
if (!adminReadme.includes("RuoYi-Vue-Plus v5.2.3")) {
  fail("admin README must name RuoYi-Vue-Plus v5.2.3");
}
if (!/不是|而非|不会伪造|不伪造/.test(adminReadme)) {
  fail("admin README must state this shell does not vendor RuoYi source");
}

const webApi = readFileSync(join(appsRoot, "web/lib/api.ts"), "utf8");
if (!webApi.includes('"/app"') && !webApi.includes("'/app'")) {
  fail("web api client must use frozen /app prefix");
}
if (webApi.includes('"/api/') || webApi.includes("'/api/")) {
  fail("web api client must not use retired /api/** paths");
}

const adminApi = readFileSync(join(appsRoot, "admin/src/lib/api.ts"), "utf8");
if (!adminApi.includes('"/admin-api"') && !adminApi.includes("'/admin-api'")) {
  fail("admin api client must use /admin-api prefix");
}

const webCodeFiles = walk(join(appsRoot, "web")).filter((file) =>
  /\.(ts|tsx|js|mjs|cjs)$/.test(file),
);
for (const file of webCodeFiles) {
  const text = readFileSync(file, "utf8");
  if (/\blocalStorage\s*\./.test(text) || /\bsessionStorage\s*\./.test(text)) {
    fail(`web must not touch Web Storage: ${relative(appsRoot, file)}`);
  }
}

if (failures.length > 0) {
  console.error("static-check failed:");
  for (const message of failures) {
    console.error(`- ${message}`);
  }
  process.exit(1);
}

console.log("static-check passed");
