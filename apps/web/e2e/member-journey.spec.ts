import { expect, test, type Page } from "@playwright/test";
import { captchaTestDoubleInit } from "./captcha";

const PASSWORD = "Passw0rd1";
const SMS = "123456";

async function installCaptchaDouble(page: Page) {
  await page.addInitScript(captchaTestDoubleInit);
}

async function resetMock(page: Page) {
  await page.goto("/health");
  const result = await page.evaluate(async () => {
    const csrfRes = await fetch("/app/auth/csrf", { credentials: "include" });
    const csrfJson = (await csrfRes.json()) as { data?: { csrfToken?: string } };
    const token = csrfJson.data?.csrfToken ?? "";
    const res = await fetch("/app/_mock/reset", {
      method: "POST",
      credentials: "include",
      headers: {
        "X-CSRF-Token": token,
        "Content-Type": "application/json",
      },
    });
    return { ok: res.ok, status: res.status, body: await res.text() };
  });
  expect(result.ok, result.body).toBeTruthy();
}

function uniquePhone() {
  const tail = String(Date.now()).slice(-8);
  return `138${tail}`;
}

test.describe.configure({ mode: "default" });

test("register → captcha → bind → mock review → refresh → unlock", async ({ page, context }) => {
  await installCaptchaDouble(page);
  await resetMock(page);
  const phone = uniquePhone();

  await page.goto("/register");
  await page.getByRole("textbox", { name: "手机号" }).fill(phone);
  await page.getByRole("textbox", { name: "密码" }).fill(PASSWORD);
  await expect(page.getByText("已完成图形验证")).toBeVisible();
  await page.getByRole("button", { name: "发送验证码" }).click();
  await expect(page.getByText("验证码已发送")).toBeVisible();
  await page.getByLabel("短信验证码").fill(SMS);
  await page.getByLabel("我已阅读并同意用户协议与隐私政策").check();
  await page.getByRole("button", { name: "注册" }).click();
  await page.waitForURL("**/me");
  await expect(page.getByRole("heading", { name: "个人主页" })).toBeVisible();

  await page.getByLabel("抖音号").fill("zhangsan");
  await page.getByLabel("会员时长（已订阅月数）").fill("15");
  await page.getByLabel("会员编码").fill("KJX-A3F9K2B7");
  await page.getByRole("button", { name: "提交核对" }).click();
  await expect(page.getByText("资料审核中")).toBeVisible();

  await page.getByRole("button", { name: "模拟审核通过" }).click();
  await page.getByRole("button", { name: "刷新绑定状态" }).click();
  await expect(page.getByText("状态 有效")).toBeVisible();
  await expect(page.getByText("剩余可解锁数")).toBeVisible();

  await page.goto("/works/breakup-editor");
  await page.getByRole("button", { name: "解锁" }).click();
  const dialog = page.getByRole("dialog", { name: "解锁二次确认" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText("前端不计算额度")).toBeVisible();
  await dialog.getByRole("button", { name: "确认解锁" }).click();
  await page.waitForURL("**/runtime/breakup-editor");
  await page.goto("/unlocks");
  await expect(page.getByRole("heading", { name: "分手编辑部" })).toBeVisible();
  await expect(page.getByRole("link", { name: "进入" })).toBeVisible();

  const storage = await page.evaluate(() => ({
    local: { ...localStorage },
    session: { ...sessionStorage },
    documentCookie: document.cookie,
  }));
  expect(JSON.stringify(storage.local)).not.toMatch(/token|satoken|authorization/i);
  expect(JSON.stringify(storage.session)).not.toMatch(/token|satoken|authorization/i);
  expect(storage.documentCookie).not.toMatch(/kjx_app_token=/);
  const cookies = await context.cookies();
  const session = cookies.find((item) => item.name === "kjx_app_token");
  expect(session?.httpOnly).toBe(true);
  expect(session?.value).toBeTruthy();
});

test("expired member can open purchased works but not unlock new ones", async ({ page }) => {
  await installCaptchaDouble(page);
  await resetMock(page);
  await page.goto("/login");
  await page.getByRole("textbox", { name: "手机号" }).fill("13900139000");
  await page.getByRole("textbox", { name: "密码" }).fill(PASSWORD);
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForURL("**/me");
  await expect(page.getByText("状态 已过期")).toBeVisible();

  await page.goto("/works/breakup-editor");
  await expect(page.getByText("不能再解锁新作品")).toBeVisible();
  await expect(page.getByRole("button", { name: "解锁" })).toBeDisabled();

  await page.goto("/unlocks");
  await expect(page.getByRole("heading", { name: "已下架剧场" })).toBeVisible();
  await page.getByRole("link", { name: "进入" }).first().click();
  await expect(page.getByText("运行时由 M5 提供")).toBeVisible();
});

test("task pages stay within viewport at required widths", async ({ page }) => {
  await installCaptchaDouble(page);
  await resetMock(page);
  const widths = [320, 375, 414, 768, 1440];
  for (const width of widths) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/login");
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow, `login overflow at ${width}`).toBeLessThanOrEqual(1);
    await page.goto("/register");
    const registerOverflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(registerOverflow, `register overflow at ${width}`).toBeLessThanOrEqual(1);
  }
});
