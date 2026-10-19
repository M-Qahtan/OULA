import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
test("five native arches open a scoped world and protected workspace", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/gateway$/);
  await expect(page.locator(".og-reference-image")).toHaveAttribute(
    "src",
    "/visuals/reference/gateway-original.png",
  );
  await expect(page.locator(".og-portal")).toHaveCount(5);
  await page.getByRole("link", { name: /وَصْل.*NEXUS/ }).click();
  await expect(page).toHaveURL(/\/gateway\/nexus/);
  await expect(page.locator(".ow-task")).toHaveCount(6);
  await page.getByRole("link", { name: /افتح المسار في مساحة العمل/ }).click();
  await expect(page.locator(".workspace-origin")).toContainText("غرفة الصفقة");
});
test("local Arabic search recognizes names and restores opener focus", async ({
  page,
}) => {
  await page.goto("/gateway");
  const open = page.getByRole("button", { name: "ابحث في عُلا", exact: true });
  await open.click();
  await page.getByLabel("ابحث عن عالم أو قسم أو مهمة").fill("وصل");
  await expect(page.locator(".og-search-results a").first()).toHaveAttribute(
    "href",
    /\/gateway\/nexus\?/,
  );
  await page.keyboard.press("Escape");
  await expect(open).toBeFocused();
  await page.getByLabel("أخبرني بما تحتاج إليه داخل عُلا").fill("صيانة");
  await page.getByRole("button", { name: "وجّهني", exact: true }).click();
  await expect(page.locator(".og-search-results a").first()).toHaveAttribute(
    "href",
    /\/gateway\/habitat\?/,
  );
});
for (const viewport of [
  { width: 390, height: 844 },
  { width: 1024, height: 768 },
  { width: 1536, height: 864 },
  { width: 1920, height: 1080 },
])
  test(`keyboard, AR/EN and accessibility at ${viewport.width}px`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.goto("/gateway");
    await page.getByRole("link", { name: /نبض.*PULSE/ }).focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("heading", { level: 1 })).toContainText(
      "PULSE",
    );
    await expect(page.locator(".ow-branch")).toHaveCount(3);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth - innerWidth,
      ),
    ).toBeLessThanOrEqual(1);
    await page
      .getByRole("button", { name: "Change to English", exact: true })
      .click();
    await expect(page.locator("html")).toHaveAttribute("lang", "en");
    await expect(page.locator("html")).toHaveAttribute("dir", "ltr");
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth - innerWidth,
      ),
    ).toBeLessThanOrEqual(1);
    expect(
      (
        await new AxeBuilder({ page })
          .include(".ow-root")
          .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
          .analyze()
      ).violations,
    ).toEqual([]);
  });
for (const world of ["pulse", "habitat", "nexus", "forge", "civic"])
  test(`${world} opens six sections and eighteen parent-scoped branches`, async ({
    page,
  }) => {
    await page.goto("/gateway/" + world);
    await expect(page.locator(".ow-task")).toHaveCount(6);
    for (let s = 0; s < 6; s++) {
      await page.locator(".ow-task").nth(s).click();
      await expect(page.locator(".ow-task").nth(s)).toHaveAttribute(
        "aria-pressed",
        "true",
      );
      for (let b = 0; b < 3; b++) {
        const link = page.locator(".ow-branch").nth(b);
        const name = await link.locator("strong").innerText();
        await link.click();
        await expect(page.locator(".ow-task-detail h3")).toHaveText(name);
        const query = new URL(page.url()).searchParams;
        expect(query.get("section")).toBeTruthy();
        expect(query.get("branch")).toBeTruthy();
      }
    }
  });
test("planned simulation exposes requirements without an execution action", async ({
  page,
}) => {
  const calls: string[] = [];
  page.on("request", (r) => {
    if (r.url().includes("/api/oula/")) calls.push(r.url());
  });
  await page.goto("/gateway/forge?section=simulation&branch=wind");
  await expect(page.locator(".ow-task-detail")).toContainText("قدرة مستقبلية");
  await expect(page.locator(".ow-task-detail")).toContainText("محركات متخصصة");
  await expect(page.locator(".ow-primary")).toHaveCount(0);
  expect(calls).toEqual([]);
});
test("invalid world and mismatched parent contexts cannot open services", async ({
  page,
}) => {
  expect((await page.goto("/gateway/missing-world"))?.status()).toBe(404);
  await page.goto("/gateway/pulse?section=simulation&branch=wind");
  await expect(page.locator(".ow-invalid")).toBeVisible();
  await expect(page.locator(".ow-primary")).toHaveCount(0);
  await page.goto(
    "/gateway/pulse?section=passport&branch=register&role=administrator",
  );
  await expect(page.locator(".ow-task-detail")).toContainText(
    "الفرع المطلوب غير موجود",
  );
  await expect(page.locator(".ow-primary")).toHaveCount(0);
});
test("gateway and mobile search pass accessibility and reduced-motion checks", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/gateway");
  expect(
    await page
      .locator(".og-portal")
      .first()
      .evaluate((e) => getComputedStyle(e).transitionDuration),
  ).toBe("0s");
  expect(
    (
      await new AxeBuilder({ page })
        .include(".og-root")
        .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
  await page.getByRole("button", { name: "ابحث في عُلا", exact: true }).click();
  expect(
    (
      await new AxeBuilder({ page })
        .include(".og-search-dialog")
        .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
});
