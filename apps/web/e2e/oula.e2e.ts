import { expect, test, type Locator, type Page } from "@playwright/test";

async function activateWithKeyboard(page: Page, locator: Locator) {
  await locator.focus();
  await expect(locator).toBeFocused();
  await page.keyboard.press("Enter");
}

async function switchToEnglish(page: Page) {
  const html = page.locator("html");
  if ((await html.getAttribute("dir")) === "rtl") {
    await activateWithKeyboard(page, page.getByRole("button", { name: "Switch to English" }));
  }
  await expect(html).toHaveAttribute("dir", "ltr");
  await expect(html).toHaveAttribute("lang", "en");
}

test.describe("OULA real frontend browser gates", () => {
  test("Arabic is native default, skip link works, and language direction switches", async ({ page }) => {
    await page.goto("/");

    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.locator("html")).toHaveAttribute("lang", "ar");

    await page.keyboard.press("Tab");
    const skip = page.getByRole("link", { name: "تجاوز إلى المحتوى" });
    await expect(skip).toBeFocused();
    await expect(skip).toBeVisible();
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/#main-content$/);

    await switchToEnglish(page);
    await expect(page.getByRole("heading", { level: 1 })).toContainText("A built world that understands people.");
  });

  test("critical journey is keyboard-operable and browser approval never advances authoritative state", async ({ page }) => {
    await page.goto("/");
    await switchToEnglish(page);

    await activateWithKeyboard(page, page.getByRole("button", { name: /Begin the journey/ }));
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Start with your life");
    await expect(page.getByRole("heading", { level: 1 })).toBeFocused();

    await activateWithKeyboard(page, page.getByRole("button", { name: /Explore candidates/ }));
    await expect(page.getByRole("heading", { level: 1 })).toContainText("A recommendation you can challenge");
    await expect(page.getByRole("heading", { level: 1 })).toBeFocused();
    await expect(page.getByRole("heading", { level: 3, name: /Apartment — Al Malqa/ })).toBeVisible();
    await expect(page.getByText(/Preferred district matches/)).toBeVisible();

    const eligiblePassport = page.locator("button.minor-button:not(:disabled)").first();
    await activateWithKeyboard(page, eligiblePassport);
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Truth before impression");
    await expect(page.getByRole("heading", { level: 2 })).toContainText("Apartment — Al Malqa");
    await expect(page.getByText("Flexible spaces for a family seeking stability and everyday comfort.")).toBeVisible();
    await expect(page.locator(".status-tag", { hasText: /DECLARED/ }).first()).toBeVisible();
    await expect(page.locator(".status-tag", { hasText: /SANDBOX/ }).first()).toBeVisible();
    await expect(page.locator(".status-tag", { hasText: /VERIFIED/ })).toHaveCount(0);

    await activateWithKeyboard(page, page.getByRole("button", { name: /Evaluate this choice/ }));
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Intelligence proposes");

    await activateWithKeyboard(page, page.getByRole("button", { name: /Open simulated Deal Room/ }));
    await expect(page.getByRole("heading", { level: 1 })).toContainText("A transaction built around human authority");
    await expect(page.getByText("QUALIFIED", { exact: true })).toBeVisible();

    await activateWithKeyboard(page, page.getByRole("button", { name: /Request demo viewing/ }));
    await expect(page.getByText("VIEWING", { exact: true })).toBeVisible();

    await activateWithKeyboard(page, page.getByRole("button", { name: /Prepare a demo offer/ }));
    await expect(page.getByText("OFFERING", { exact: true })).toBeVisible();

    await activateWithKeyboard(page, page.getByRole("button", { name: /Approve simulated offer/ }));
    await expect(page.getByText("OFFERING", { exact: true })).toBeVisible();
    await expect(page.getByText(/Presentation only; does not mutate backend state/)).toBeVisible();

    await activateWithKeyboard(page, page.getByRole("button", { name: /Property Guardian/ }));
    await expect(page.getByRole("heading", { level: 1 })).toContainText("The asset journey continues after the deal");
    await activateWithKeyboard(page, page.getByRole("button", { name: /Review hypothetical signal/ }));
    await expect(page.getByText(/Browser-only simulated approval/)).toBeVisible();

    await activateWithKeyboard(page, page.getByRole("button", { name: /Reality Intelligence/ }));
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Remember reality");

    await activateWithKeyboard(page, page.getByRole("button", { name: /Restart the experience/ }));
    await expect(page.getByRole("heading", { level: 1 })).toContainText("A built world that understands people");
  });

  for (const viewport of [
    { name: "expo", width: 1920, height: 1080 },
    { name: "tablet", width: 1024, height: 900 },
    { name: "phone", width: 390, height: 844 },
  ]) {
    test(`${viewport.name} viewport has no unintended horizontal overflow across all stages`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      const errors: string[] = [];
      page.on("pageerror", (error) => errors.push(error.message));

      await page.goto("/");
      await switchToEnglish(page);

      const labels = [
        "Vision",
        "Understand Me",
        "Smart Match",
        "Property Passport",
        "Informed Decision",
        "Deal Room",
        "Property Guardian",
        "Reality Intelligence",
      ];

      for (const label of labels) {
        if (viewport.width <= 780) {
          const menu = page.getByRole("button", { name: "Open or close stage navigation" });
          if ((await menu.getAttribute("aria-expanded")) !== "true") {
            await menu.click();
          }
        }

        await page.getByRole("button", { name: new RegExp(label) }).first().click();
        await expect(page.locator(".stage-link.active")).toHaveAttribute("aria-current", "step");

        const overflow = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
        );
        expect(overflow, `horizontal overflow at ${label} / ${viewport.name}`).toBeLessThanOrEqual(1);
      }

      expect(errors).toEqual([]);
    });
  }

  test("mobile stage navigation closes with Escape", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/");

    const menu = page.getByRole("button", { name: "فتح أو إغلاق قائمة المراحل" });
    await menu.click();
    await expect(menu).toHaveAttribute("aria-expanded", "true");
    await page.keyboard.press("Escape");
    await expect(menu).toHaveAttribute("aria-expanded", "false");
  });

  test("reduced-motion preference disables nonessential transitions", async ({ page }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/");

    const transition = await page.locator(".stage-link").first().evaluate(
      (element) => getComputedStyle(element).transitionDuration,
    );
    expect(transition).toBe("0s");
  });
});
