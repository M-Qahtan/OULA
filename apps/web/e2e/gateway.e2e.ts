import { expect, test } from "@playwright/test";

test.describe("OULA five-family gateway / visual and semantic contracts", () => {
  test("Arabic main gateway presents exactly five selectable families and transparent preview semantics", async ({ page }) => {
    await page.goto("/gateway");
    await expect(page.locator("html")).toHaveAttribute("lang", "ar");
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByRole("heading", { level: 1 })).toContainText("عُلا");
    await expect(page.locator(".og-portal")).toHaveCount(5);
    await expect(page.getByText("تصور تفاعلي — بيانات تجريبية")).toBeVisible();
    await expect(page.getByRole("button", { name: /وَصْل.*NEXUS/ })).toBeVisible();
    await page.getByRole("button", { name: /وَصْل.*NEXUS/ }).click();
    await expect(page.locator(".og-selection-card")).toContainText("الوسطاء والشركاء");
    await expect(page.getByText(/الخدمات الحقيقية تخضع لإتاحة النظام/)).toBeVisible();
    await expect(page.getByRole("link", { name: /استكشف رحلة عُلا التجريبية/ })).toHaveAttribute("href", "/");
  });

  test("preview guide suggests a world without pretending to perform backend operations", async ({ page }) => {
    await page.goto("/gateway");
    await page.getByRole("textbox", { name: /أخبرني بما تحتاج/ }).fill("أحتاج صيانة أملاكي");
    await page.getByRole("button", { name: "وجّهني" }).click();
    await expect(page.locator(".og-selection-card")).toContainText("موئل");
    await expect(page.getByText(/إرشاد تجريبي محلي/)).toBeVisible();
  });

  for (const viewport of [
    { width: 390, height: 844, label: "phone" },
    { width: 1024, height: 768, label: "tablet" },
    { width: 1920, height: 1080, label: "desktop" },
  ]) {
    test(viewport.label + " no unintended overflow, accessible switches", async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.goto("/gateway");
      await expect(page.locator(".og-portal")).toHaveCount(5);
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
      const button = page.getByRole("button", { name: /نبض.*PULSE/ });
      await button.focus();
      await expect(button).toBeFocused();
      await page.keyboard.press("Enter");
      await expect(button).toHaveAttribute("aria-pressed", "true");
      await expect(page.locator(".og-selection-card")).toContainText("نبض");
      await page.getByRole("button", { name: "التبديل إلى الإنجليزية" }).click();
      await expect(page.locator("html")).toHaveAttribute("dir", "ltr");
      await expect(page.locator("html")).toHaveAttribute("lang", "en");
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1);
    });
  }

  test("reduced motion eliminates arch movement", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/gateway");
    const duration = await page.locator(".og-portal").first().evaluate(
      element => getComputedStyle(element).transitionDuration,
    );
    expect(duration).toBe("0s");
  });
});
