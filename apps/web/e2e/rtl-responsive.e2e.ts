import { expect, test } from "@playwright/test";

// UI-only assertions. The Gateway anchor is intentionally not an experience stage.
for (const viewport of [
  { name: "phone", width: 390, height: 844 },
  { name: "tablet", width: 1024, height: 900 },
  { name: "expo", width: 1920, height: 1080 },
]) {
  for (const language of ["ar", "en"] as const) {
    test(`${language.toUpperCase()} layout and all eight stages at ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      const errors: string[] = [];
      page.on("pageerror", error => errors.push(error.message));
      await page.goto("/journey");

      if (language === "en") {
        await page.getByRole("button", { name: "Switch to English" }).click();
      }
      await expect(page.locator("html")).toHaveAttribute("lang", language);
      await expect(page.locator("html")).toHaveAttribute("dir", language === "ar" ? "rtl" : "ltr");

      const stages = page.locator(".stage-nav > button.stage-link");
      await expect(stages).toHaveCount(8);
      await expect(page.locator('.stage-nav > a[href="/gateway"]')).toHaveCount(1);
      for (let index = 0; index < 8; index++) {
        if (viewport.width <= 780) {
          const toggle = page.getByRole("button", {
            name: language === "ar" ? "فتح أو إغلاق قائمة المراحل" : "Open or close stage navigation",
          });
          await toggle.click();
          await expect(toggle).toHaveAttribute("aria-expanded", "true");
        }
        await stages.nth(index).click();
        await expect(stages.nth(index)).toHaveAttribute("aria-current", "step");
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        );
        expect(overflow, `${language} overflow at stage ${index} / ${viewport.name}`).toBeLessThanOrEqual(1);
      }
      expect(errors).toEqual([]);
    });
  }
}
