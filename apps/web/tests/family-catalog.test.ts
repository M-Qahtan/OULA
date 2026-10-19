import { describe, expect, it } from "vitest";
import {
  FAMILY_SECTIONS,
  familyHref,
  readFamilyContext,
  safeWorkspaceReturnTo,
  searchFamilies,
  workspaceHref,
} from "../lib/family-catalog";
describe("OULA world-to-task navigation", () => {
  it("has five families, thirty sections, ninety bilingual branches and thirty workspace entries", () => {
    expect(Object.keys(FAMILY_SECTIONS)).toEqual([
      "pulse",
      "habitat",
      "nexus",
      "forge",
      "civic",
    ]);
    let connected = 0;
    for (const sections of Object.values(FAMILY_SECTIONS)) {
      expect(sections).toHaveLength(6);
      expect(new Set(sections.map((s) => s.id)).size).toBe(6);
      for (const section of sections) {
        expect(section.branches).toHaveLength(3);
        expect(new Set(section.branches.map((b) => b.id)).size).toBe(3);
        for (const branch of section.branches) {
          expect(branch.name.ar).toBeTruthy();
          expect(branch.name.en).toBeTruthy();
          if (branch.target) connected++;
        }
      }
    }
    expect(connected).toBe(30);
  });
  it("keeps engineering, financing and city scenarios explicitly planned", () => {
    for (const world of ["forge", "civic"] as const)
      expect(
        FAMILY_SECTIONS[world].flatMap((s) => s.branches).some((b) => b.target),
      ).toBe(false);
    expect(
      FAMILY_SECTIONS.pulse
        .find((s) => s.id === "financing")!
        .branches.some((b) => b.target),
    ).toBe(false);
  });
  it("keeps a care task's world, parent, role and language in both directions", () => {
    const context = readFamilyContext(
      new URLSearchParams("section=obligations&branch=add&role=owner&lang=en"),
      "habitat",
    );
    const entry = new URL(workspaceHref(context)!, "https://oula.invalid");
    expect(Object.fromEntries(entry.searchParams)).toMatchObject({
      world: "HABITAT",
      view: "guardian",
      section: "obligations",
      branch: "add",
      role: "owner",
      lang: "en",
      connection: "server",
    });
    expect(new URL(familyHref(context), "https://oula.invalid").pathname).toBe(
      "/gateway/habitat",
    );
  });
  it("rejects unrelated branches and arbitrary role claims", () => {
    for (const query of [
      "section=simulation&branch=wind",
      "section=passport&branch=register",
      "section=passport&branch=javascript:alert(1)",
    ])
      expect(
        workspaceHref(readFamilyContext(new URLSearchParams(query), "pulse")),
      ).toBeNull();
    expect(
      readFamilyContext(
        new URLSearchParams("role=administrator&scope=all"),
        "pulse",
      ).role,
    ).toBe("individual");
  });
  it("recognizes Arabic names without diacritics and finds maintenance", () => {
    for (const query of ["وصل", "وَصْل"])
      expect(searchFamilies(query)[0]?.world.id).toBe("nexus");
    expect(searchFamilies("صيانة")[0]?.world.id).toBe("habitat");
    expect(searchFamilies("bim")[0]?.world.id).toBe("forge");
  });
  it("round-trips only navigation and discards credentials and authority", () => {
    const url = new URL(
      safeWorkspaceReturnTo(
        "/workspace?world=HABITAT&view=guardian&section=obligations&branch=add&role=owner&lang=en&accessToken=secret&workspaceId=other&scope=all&focus=body",
      ),
      "https://oula.invalid",
    );
    expect(Object.fromEntries(url.searchParams)).toEqual({
      world: "HABITAT",
      role: "owner",
      lang: "en",
      connection: "server",
      view: "guardian",
      section: "obligations",
      branch: "add",
    });
  });
  it.each([
    "https://evil.example/workspace",
    "//evil.example/workspace",
    "/\\evil/workspace",
    "javascript:alert(1)",
    "/workspace/../api/session",
    "/workspace%2f..%2fapi/session",
    " /workspace",
    "/workspace\n",
    "/workspace.evil",
    "/journey",
    "x".repeat(2000),
  ])("rejects unsafe login return %s", (value) => {
    expect(safeWorkspaceReturnTo(value)).toBe("/workspace?connection=server");
  });
});
