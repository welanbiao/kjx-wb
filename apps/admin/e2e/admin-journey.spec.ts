import { expect, test, type Page } from "@playwright/test";

async function login(page: Page, username: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("账号").fill(username);
  await page.getByLabel("密码").fill(password);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.getByRole("heading", { name: "工作台" })).toBeVisible();
}

test("HOST A has no OPERATOR review entry and is blocked by direct URL", async ({ page }) => {
  await login(page, "host-a", "mock-host-a");
  await expect(page.getByRole("menuitem", { name: "我的会员" })).toBeVisible();
  await expect(page.getByRole("menuitem", { name: "绑定审核" })).toHaveCount(0);
  await expect(page.getByRole("menuitem", { name: "跨部门会员" })).toHaveCount(0);
  await page.goto("/ops/bindings");
  await expect(page.getByRole("heading", { name: "没有权限" })).toBeVisible();
});

test("OPERATOR sees review pages and does not use HOST self path", async ({ page }) => {
  await login(page, "operator", "mock-operator");
  await expect(page.getByRole("menuitem", { name: "绑定审核" })).toBeVisible();
  await expect(page.getByRole("menuitem", { name: "我的会员" })).toHaveCount(0);
  await page.goto("/self/members");
  await expect(page.getByRole("heading", { name: "没有权限" })).toBeVisible();
  await page.getByRole("menuitem", { name: "绑定审核" }).click();
  await expect(page.getByRole("heading", { name: "绑定审核" })).toBeVisible();
  await expect(page.getByRole("button", { name: "查询" })).toBeVisible();
  await expect(page.getByText("PENDING_REVIEW").first()).toBeVisible();
});

test("member create keeps months and days independent", async ({ page }) => {
  await login(page, "host-a", "mock-host-a");
  await page.getByRole("menuitem", { name: "我的会员" }).click();
  await page.getByRole("button", { name: "录入会员" }).click();
  await page.getByLabel("抖音号").fill("e2euser");
  await page.getByLabel("已订阅月数").fill("15");
  await expect(page.getByLabel("累计已订阅天数")).toHaveValue("");
  await page.getByLabel("累计已订阅天数").fill("435");
  await expect(page.getByLabel("已订阅月数")).toHaveValue("15");
});
