import { expect, test } from "@playwright/test";

for (const language of ["ar", "en"] as const) {
  test(`mobile drawer traps focus through Gateway, closes on Escape, and restores focus (${language})`, async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/");
    if (language === "en") {
      await page.getByRole("button", { name: "Switch to English" }).click();
    }
    await expect(page.locator("html")).toHaveAttribute("dir", language === "ar" ? "rtl" : "ltr");
    const menu = page.getByRole("button", {
      name: language === "ar" ? "فتح أو إغلاق قائمة المراحل" : "Open or close stage navigation",
    });
    const sidebar = page.locator(".sidebar");
    const brand = sidebar.locator(".brand-button");
    const stages = sidebar.locator(".stage-nav > button.stage-link");
    const gateway = sidebar.locator('.stage-nav > a[href="/gateway"]');

    await expect(sidebar).toBeHidden();
    await expect(menu).toHaveAttribute("aria-expanded", "false");
    await menu.focus();
    await page.keyboard.press("Enter");
    await expect(menu).toHaveAttribute("aria-expanded", "true");
    await expect(sidebar).toBeVisible();
    await expect(stages.first()).toBeFocused();

    await page.keyboard.press("Shift+Tab");
    await expect(brand).toBeFocused();
    await page.keyboard.press("Shift+Tab");
    await expect(gateway).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(brand).toBeFocused();

    await page.keyboard.press("Escape");
    await expect(menu).toHaveAttribute("aria-expanded", "false");
    await expect(menu).toBeFocused();
    await expect(sidebar).toBeHidden();
    await page.keyboard.press("Tab");
    await expect(page.locator(".lang-switch")).toBeFocused();
  });
}

test("reduced motion disables programmatic smooth scrolling", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await page.evaluate(() => {
    window.scrollTo = ((options: ScrollToOptions) => {
      document.documentElement.dataset.scrollBehavior = options.behavior ?? "";
    }) as typeof window.scrollTo;
  });
  await page.getByRole("button", { name: /ابدأ الرحلة/ }).click();
  await expect(page.locator("html")).toHaveAttribute("data-scroll-behavior", "auto");
});
