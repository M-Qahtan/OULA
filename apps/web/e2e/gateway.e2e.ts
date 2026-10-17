import { expect, test } from "@playwright/test";

test.describe("OULA five-family gateway / accessible contextual navigation", () => {
  test("Arabic main gateway opens NEXUS specialist workspace in one activation", async ({
    page,
  }) => {
    await page.goto("/gateway");
    await expect(page.locator("html")).toHaveAttribute("lang", "ar");
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByRole("heading", { level: 1 })).toContainText("عُلا");
    await expect(page.locator(".og-portal")).toHaveCount(5);
    await expect(page.getByText("تصور تفاعلي — بيانات تجريبية")).toBeVisible();
    await page.getByRole("link", { name: /وَصْل.*NEXUS/ }).click();
    await expect(page).toHaveURL(/\/gateway\/nexus$/);
    await expect(page.getByRole("heading", { level: 1 })).toContainText(
      "NEXUS",
    );
    await expect(page.locator(".ow-footer")).toContainText(
      "تصور تشغيلي — ليس خدمة حية",
    );
    await page.getByRole("button", { name: /غرفة الصفقة/ }).click();
    await expect(page.locator(".ow-task-detail")).toContainText(
      "مراحل صفقة تجريبية",
    );
    await expect(
      page.getByRole("link", { name: /افتح الرحلة التجريبية القائمة/ }),
    ).toHaveAttribute("href", "/journey");
    await page
      .getByRole("link", { name: /افتح الرحلة التجريبية القائمة/ })
      .click();
    await expect(page).toHaveURL(/\/journey$/);
    await expect(page.locator(".shell")).toBeVisible();
  });

  test("preview guide routes to a real contextual preview, without backend claims", async ({
    page,
  }) => {
    await page.goto("/gateway");
    await page
      .getByRole("textbox", { name: /أخبرني بما تحتاج/ })
      .fill("أحتاج صيانة أملاكي");
    await page.getByRole("button", { name: "وجّهني" }).click();
    await expect(page.locator(".og-selection-card")).toContainText("موئل");
    await expect(page.getByText(/إرشاد تجريبي محلي/)).toBeVisible();
    await page
      .locator(".og-selection-card")
      .getByRole("link", { name: /استكشف موئل/ })
      .click();
    await expect(page).toHaveURL(/\/gateway\/habitat$/);
  });

  for (const viewport of [
    { width: 390, height: 844, label: "phone" },
    { width: 1024, height: 768, label: "tablet" },
    { width: 1920, height: 1080, label: "desktop" },
  ]) {
    test(
      viewport.label + " keyboard entry, language and no overflow",
      async ({ page }) => {
        await page.setViewportSize(viewport);
        await page.goto("/gateway");
        await expect(page.locator(".og-portal")).toHaveCount(5);
        expect(
          await page.evaluate(
            () =>
              document.documentElement.scrollWidth -
              document.documentElement.clientWidth,
          ),
        ).toBeLessThanOrEqual(1);
        const entry = page.getByRole("link", { name: /نبض.*PULSE/ });
        await entry.focus();
        await expect(entry).toBeFocused();
        await page.keyboard.press("Enter");
        await expect(page).toHaveURL(/\/gateway\/pulse$/);
        await expect(page.getByRole("heading", { level: 1 })).toContainText(
          "PULSE",
        );
        await expect(page.locator(".ow-task")).toHaveCount(4);
        expect(
          await page.evaluate(
            () =>
              document.documentElement.scrollWidth -
              document.documentElement.clientWidth,
          ),
        ).toBeLessThanOrEqual(1);
        await page.getByRole("button", { name: "Change to English" }).click();
        await expect(page.locator("html")).toHaveAttribute("dir", "ltr");
        await expect(page.locator("html")).toHaveAttribute("lang", "en");
        await expect(page.locator(".ow-footer")).toContainText(
          "Operational concept — not a live service",
        );
        expect(
          await page.evaluate(
            () =>
              document.documentElement.scrollWidth -
              document.documentElement.clientWidth,
          ),
        ).toBeLessThanOrEqual(1);
      },
    );
  }

  test("all five families have distinct and reachable workspaces", async ({
    page,
  }) => {
    for (const [world, english, task] of [
      ["pulse", "PULSE", "احتياجي العقاري"],
      ["habitat", "HABITAT", "الحارس والصيانة"],
      ["nexus", "NEXUS", "غرفة الصفقة"],
      ["forge", "FORGE", "المحاكاة العلمية"],
      ["civic", "CIVIC", "السيناريوهات الحضرية"],
    ] as const) {
      await page.goto("/gateway/" + world);
      await expect(page.getByRole("heading", { level: 1 })).toContainText(
        english,
      );
      await expect(
        page.getByRole("button", { name: new RegExp(task) }),
      ).toBeVisible();
      await expect(page.locator(".ow-family-nav a")).toHaveCount(4);
    }
  });

  test("future engineering capability never exposes a fake live action", async ({
    page,
  }) => {
    await page.goto("/gateway/forge");
    await page.getByRole("button", { name: /المحاكاة العلمية/ }).click();
    await expect(page.locator(".ow-task-detail")).toContainText(
      "قدرة مستقبلية",
    );
    await expect(page.locator(".ow-task-detail").getByRole("link")).toHaveCount(
      0,
    );
    await expect(page.locator(".ow-task-detail")).toContainText(
      "محركات متخصصة",
    );
  });

  test("world guide preserves no-access and clarifies an unknown intent", async ({
    page,
  }) => {
    await page.goto("/gateway/civic");
    await page
      .getByRole("textbox", { name: "اكتب ما تريد إنجازه" })
      .fill("غير مفهوم");
    await page.getByRole("button", { name: "اقترح عالمًا" }).click();
    await expect(page.getByText(/لم يتضح مجال طلبك/)).toBeVisible();
    await page
      .getByRole("textbox", { name: "اكتب ما تريد إنجازه" })
      .fill("أريد منزلًا");
    await page.getByRole("button", { name: "اقترح عالمًا" }).click();
    await expect(
      page.getByRole("link", { name: /الانتقال إلى الفرع/ }),
    ).toHaveAttribute("href", "/gateway/pulse");
  });

  test("unknown family fails closed with 404", async ({ page }) => {
    const response = await page.goto("/gateway/unknown-family");
    expect(response?.status()).toBe(404);
  });

  test("reduced-motion preference removes gateway transitions", async ({
    page,
  }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/gateway");
    const duration = await page
      .locator(".og-portal")
      .first()
      .evaluate((element) => getComputedStyle(element).transitionDuration);
    expect(duration).toBe("0s");
    await page.goto("/gateway/pulse");
    const workspaceDuration = await page
      .locator(".ow-task")
      .first()
      .evaluate((element) => getComputedStyle(element).transitionDuration);
    expect(workspaceDuration).toBe("0s");
  });
});

test("the root opens the five-family gateway and connects a family to its workspace", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/gateway$/);
  await expect(page.locator(".og-portal")).toHaveCount(5);
  await page.getByRole("link", { name: /PULSE/ }).click();
  await page.getByRole("link", { name: /افتح مساحة العمل الموحدة/ }).click();
  await expect(page).toHaveURL(/\/workspace\?world=PULSE/);
  await expect(page.locator(".workspace-brand")).toContainText("PULSE");
});
