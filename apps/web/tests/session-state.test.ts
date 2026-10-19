import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createSessionController,
  sessionContextKey,
  type BrowserSession,
  type SessionState,
} from "../lib/session-state";

const workspace = "123e4567-e89b-42d3-a456-426614174001";
const otherWorkspace = "123e4567-e89b-42d3-a456-426614174002";
const authenticated: Extract<BrowserSession, { status: "authenticated" }> = {
  status: "authenticated",
  mode: "OIDC",
  subject: "fixture-person-a",
  workspaceId: workspace,
  workspaceIds: [workspace, otherWorkspace],
  purposes: ["PROPERTY_DECISION_SUPPORT", "PROPERTY_MANAGEMENT"],
  scopes: ["oula.intent.read", "oula.intent.write"],
  expiresAt: 2_000_000_000,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-10-19T04:00:00Z"));
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("private session context", () => {
  it("retains drafts across successful renewal and reordered equivalent grants", () => {
    expect(
      sessionContextKey({
        ...authenticated,
        expiresAt: authenticated.expiresAt + 100,
        workspaceIds: [...authenticated.workspaceIds].reverse(),
        purposes: [...authenticated.purposes].reverse(),
        scopes: ["oula.intent.write", "oula.intent.read", "oula.intent.read"],
      }),
    ).toBe(sessionContextKey(authenticated));
  });
  it("isolates accounts, workspaces, modes and permission changes", () => {
    for (const change of [
      { subject: "fixture-person-b" },
      { workspaceId: otherWorkspace },
      { workspaceIds: [workspace] },
      { mode: "DEV_TEST_ONLY" as const },
      { scopes: ["oula.intent.read"] },
      { purposes: ["PROPERTY_MANAGEMENT" as const] },
    ])
      expect(sessionContextKey({ ...authenticated, ...change })).not.toBe(
        sessionContextKey(authenticated),
      );
  });
  it("keeps the anonymous shell stable while its initial session loads", () => {
    for (const status of ["signed-out", "unconfigured", "error"] as const)
      expect(sessionContextKey({ status })).toBe(
        sessionContextKey({ status: "loading" }),
      );
  });
});

describe("session request ordering", () => {
  it("never restores an authenticated read that finishes after sign-out", async () => {
    const slow = deferred<Response>();
    const update = vi.fn<(session: SessionState) => void>();
    const fetch = vi
      .fn()
      .mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce(Response.json({ status: "signed-out" }))
      .mockResolvedValueOnce(
        Response.json({ status: "signed-out", localDevAuth: true }),
      );
    vi.stubGlobal("fetch", fetch);
    const session = createSessionController(update);
    const reading = session.reload();
    await session.logout();
    slow.resolve(Response.json(authenticated));
    await reading;
    expect(update.mock.calls.map(([value]) => value.status)).toEqual([
      "loading",
      "signed-out",
    ]);
    expect(update).toHaveBeenLastCalledWith({
      status: "signed-out",
      localDevAuth: true,
    });
    expect((fetch.mock.calls[0]![1] as RequestInit).signal?.aborted).toBe(true);
  });
  it("ignores an older account response even when a transport ignores cancellation", async () => {
    const slow = deferred<Response>();
    const update = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockReturnValueOnce(slow.promise)
        .mockResolvedValueOnce(
          Response.json({ ...authenticated, subject: "fixture-person-b" }),
        ),
    );
    const session = createSessionController(update);
    const older = session.reload();
    await session.reload();
    slow.resolve(Response.json(authenticated));
    await older;
    expect(update).toHaveBeenCalledTimes(1);
    expect(update.mock.lastCall?.[0].subject).toBe("fixture-person-b");
  });
  it("keeps session writes exclusive and re-reads the server after a change", async () => {
    const saving = deferred<Response>();
    const update = vi.fn();
    const fetch = vi
      .fn()
      .mockReturnValueOnce(saving.promise)
      .mockResolvedValueOnce(
        Response.json({ ...authenticated, workspaceId: otherWorkspace }),
      );
    vi.stubGlobal("fetch", fetch);
    const session = createSessionController(update);
    const changing = session.changeWorkspace(otherWorkspace);
    await expect(session.logout()).rejects.toThrow("already pending");
    await session.reload();
    expect(fetch).toHaveBeenCalledTimes(1);
    saving.resolve(Response.json(authenticated));
    await changing;
    expect(update.mock.lastCall?.[0].workspaceId).toBe(otherWorkspace);
    expect(fetch.mock.calls.map(([url]) => url)).toEqual([
      "/api/session",
      "/api/session",
    ]);
  });
  it("keeps writes exclusive until the authoritative verification finishes", async () => {
    const verifying = deferred<Response>();
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(Response.json(authenticated))
      .mockReturnValueOnce(verifying.promise);
    vi.stubGlobal("fetch", fetch);
    const session = createSessionController(vi.fn());
    const changing = session.changeWorkspace(otherWorkspace);
    await Promise.resolve();
    try {
      expect(fetch).toHaveBeenCalledTimes(2);
      await expect(session.logout()).rejects.toThrow("already pending");
    } finally {
      verifying.resolve(
        Response.json({ ...authenticated, workspaceId: otherWorkspace }),
      );
      await changing.catch(() => undefined);
    }
  });
  it("rechecks a context changed while a mutation is being verified", async () => {
    const verifying = deferred<Response>();
    const update = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(Response.json(authenticated))
        .mockReturnValueOnce(verifying.promise)
        .mockResolvedValueOnce(
          Response.json({ ...authenticated, subject: "fixture-person-b" }),
        ),
    );
    const session = createSessionController(update);
    const changing = session.changeWorkspace(workspace);
    await Promise.resolve();
    session.invalidate();
    await session.reload();
    verifying.resolve(Response.json(authenticated));
    await changing;
    expect(
      update.mock.calls.map(([value]) => value.subject).filter(Boolean),
    ).toEqual(["fixture-person-b"]);
  });
  it("rechecks the cookie after a lost mutation response without retaining private state", async () => {
    const update = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockRejectedValueOnce(new Error("network lost"))
        .mockResolvedValueOnce(
          Response.json({ status: "signed-out", localDevAuth: true }),
        ),
    );
    const session = createSessionController(update);
    await expect(session.logout()).rejects.toThrow("network lost");
    expect(update.mock.calls.map(([value]) => value.status)).toEqual([
      "loading",
      "signed-out",
    ]);
  });
  it.each([
    { ...authenticated, workspaceId: "123e4567-e89b-42d3-a456-426614174099" },
    { ...authenticated, expiresAt: 1 },
    { ...authenticated, subject: "" },
    { fiction: true },
  ])("fails closed on an invalid or expired public session", async (value) => {
    const update = vi.fn();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(value)));
    await createSessionController(update).reload();
    expect(update).toHaveBeenLastCalledWith({ status: "error" });
  });
  it("does not publish responses after the workspace unmounts", async () => {
    const slow = deferred<Response>();
    const update = vi.fn();
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(slow.promise));
    const session = createSessionController(update);
    const reading = session.reload();
    session.dispose();
    slow.resolve(Response.json(authenticated));
    await reading;
    expect(update).not.toHaveBeenCalled();
  });
});
