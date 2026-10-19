import { z } from "zod";
import { purposes } from "./api-contracts";

const schema = z.discriminatedUnion("status", [
  z
    .object({
      status: z.literal("authenticated"),
      mode: z.enum(["OIDC", "DEV_TEST_ONLY"]).default("OIDC"),
      subject: z.string().min(1),
      workspaceId: z.uuid(),
      workspaceIds: z.array(z.uuid()).min(1),
      purposes: z.array(z.enum(purposes)).min(1),
      scopes: z.array(z.string()),
      expiresAt: z.number().int(),
    })
    .refine((value) => value.workspaceIds.includes(value.workspaceId)),
  z.object({
    status: z.enum(["signed-out", "unconfigured"]),
    localDevAuth: z.boolean().optional(),
  }),
]);

export type BrowserSession = z.infer<typeof schema>;
export type SessionState = BrowserSession | { status: "loading" | "error" };

/** A UI isolation boundary, never an authorization grant or a persisted ID. */
export function sessionContextKey(session: SessionState): string {
  if (session.status !== "authenticated") return "anonymous";
  const set = (values: readonly string[]) => [...new Set(values)].sort();
  return JSON.stringify([
    session.mode,
    session.subject,
    session.workspaceId,
    set(session.workspaceIds),
    set(session.purposes),
    set(session.scopes),
  ]);
}

/** Serializes session mutations and rejects reads made before a context change. */
export function createSessionController(
  update: (session: SessionState) => void,
) {
  let active = true;
  let generation = 0;
  let mutating = false;
  let recheckPending = false;
  let currentRead: AbortController | null = null;
  let currentMutation: AbortController | null = null;

  const cancelRead = () => {
    generation++;
    currentRead?.abort();
    currentRead = null;
  };
  const invalidate = () => {
    cancelRead();
    if (active) update({ status: "loading" });
  };

  async function read(signal?: AbortSignal) {
    if (!active || signal?.aborted) return;
    cancelRead();
    const version = generation;
    const controller = new AbortController();
    currentRead = controller;
    const abort = () => controller.abort();
    signal?.addEventListener("abort", abort, { once: true });
    const current = () =>
      active && version === generation && !controller.signal.aborted;
    try {
      const response = await fetch("/api/session", {
        cache: "no-store",
        credentials: "same-origin",
        signal: controller.signal,
      });
      if (!response.ok) throw new Error("Session unavailable");
      const next = schema.parse(await response.json());
      if (
        next.status === "authenticated" &&
        next.expiresAt * 1000 <= Date.now()
      ) {
        throw new Error("Expired session");
      }
      if (current()) {
        update(next);
        return next;
      }
    } catch {
      if (current()) update({ status: "error" });
    } finally {
      signal?.removeEventListener("abort", abort);
      if (currentRead === controller) currentRead = null;
    }
  }

  async function reload(signal?: AbortSignal) {
    if (mutating) {
      recheckPending = true;
      return;
    }
    return read(signal);
  }

  async function mutate(
    path: string,
    method: "POST" | "DELETE",
    body?: object,
  ) {
    if (!active || mutating) throw new Error("Session change already pending");
    mutating = true;
    invalidate();
    const controller = new AbortController();
    currentMutation = controller;
    let failure: unknown;
    try {
      const response = await fetch(path, {
        method,
        credentials: "same-origin",
        cache: "no-store",
        signal: controller.signal,
        ...(body
          ? {
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify(body),
            }
          : {}),
      });
      if (!response.ok) throw new Error("Session change denied");
    } catch (cause) {
      failure = cause;
    }
    // Re-read authoritative state even if the mutation response was lost or denied.
    // DELETE's response does not include the development sign-in configuration.
    let next: BrowserSession | undefined;
    try {
      do {
        recheckPending = false;
        next = active ? await read() : undefined;
      } while (active && recheckPending);
    } finally {
      mutating = false;
      if (currentMutation === controller) currentMutation = null;
    }
    if (failure) throw failure;
    if (active && !next) throw new Error("Session verification failed");
  }

  return {
    reload,
    invalidate,
    activate: () => {
      active = true;
    },
    dispose: () => {
      active = false;
      cancelRead();
      currentMutation?.abort();
    },
    changeWorkspace: (workspaceId: string) =>
      mutate("/api/session", "POST", { workspaceId }),
    loginLocal: (fixture: string) =>
      mutate("/api/auth/local", "POST", { fixture }),
    logout: () => mutate("/api/session", "DELETE"),
  };
}
