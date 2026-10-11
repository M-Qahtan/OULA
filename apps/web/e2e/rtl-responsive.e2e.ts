import { expect, test } from "@playwright/test";

// Complements the existing EN viewport smoke and the mobile drawer focus test.
// AR/RTL is checked across every screen without claiming backend integration.
for (const viewport of [
  { name: "phone", width: 390, height: 844 },
  { name: "tablet", width: 1024, height: 900 },
  { name: "expo", width: 1920, height: 1080 },
]) {
  test(`Arabic RTL layout remains navigable at ${viewport.name} width`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto("/");

    await expect(page.locator("html")).toHaveAttribute("lang", "ar");
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");

    const stages = page.locator(".stage-link");
    await expect(stages).toHaveCount(8);
    for (let index = 0; index < 8; index++) {
      if (viewport.width <= 780) {
        const toggle = page.getByRole("button", { name: "فتح أو إغلاق قائمة المراحل" });
        await toggle.click();
        await expect(toggle).toHaveAttribute("aria-expanded", "true");
      }

      await stages.nth(index).click();
      await expect(stages.nth(index)).toHaveAttribute("aria-current", "step");
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow, `RTL overflow at stage ${index} / ${viewport.name}`).toBeLessThanOrEqual(1);
    }

    expect(errors).toEqual([]);
  });
}
