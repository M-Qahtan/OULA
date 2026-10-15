import { describe, expect, it, vi } from "vitest";
import { fetchOulaExperienceManifest } from "../lib/experience-manifest";

const ctx = {
  accessToken: "test-only-token",
  workspaceId: "11111111-1111-4111-8111-111111111111",
  purpose: "PROPERTY_MANAGEMENT" as const,
};

describe("Authenticated contextual experience boundary", () => {
  it("sends bearer, workspace and purpose and accepts only matching manifest", async () => {
    const fetcher = vi.fn(async () => ({
      ok: true, status: 200, json: async () => ({
        workspaceId: ctx.workspaceId, purpose: ctx.purpose, lens: "HABITAT",
        capabilities: [], source: "AUTHENTICATED_SCOPE_PROJECTION",
      }),
    })) as unknown as typeof fetch;
    const result = await fetchOulaExperienceManifest("https://localhost:8080", ctx, fetcher);
    expect(result.lens).toBe("HABITAT");
    expect(fetcher).toHaveBeenCalledWith(
      expect.any(URL), expect.objectContaining({
        cache: "no-store",
        headers: expect.objectContaining({
          Authorization: "Bearer test-only-token",
          "X-OULA-Workspace-ID": ctx.workspaceId,
          "X-OULA-Purpose": "PROPERTY_MANAGEMENT",
        }),
      }),
    );
  });

  it("refuses a manifest for a different workspace", async () => {
    const fetcher = vi.fn(async () => ({
      ok: true, status: 200, json: async () => ({
        workspaceId: "other", purpose: ctx.purpose, lens: "HABITAT",
        capabilities: [], source: "AUTHENTICATED_SCOPE_PROJECTION",
      }),
    })) as unknown as typeof fetch;
    await expect(fetchOulaExperienceManifest("https://localhost:8080", ctx, fetcher))
      .rejects.toThrow("context mismatch");
  });

  it("has no silent DEMO fallback on failed HTTP request", async () => {
    const fetcher = vi.fn(async () => ({ok: false, status: 403})) as unknown as typeof fetch;
    await expect(fetchOulaExperienceManifest("https://localhost:8080", ctx, fetcher))
      .rejects.toMatchObject({status: 403});
  });
});
