"use client";
import { useCallback, useEffect, useState } from "react";
import { z } from "zod";
import { purposes } from "./api-contracts";

const schema = z.discriminatedUnion("status", [
  z.object({
    status: z.literal("authenticated"),
    mode: z.enum(["OIDC", "DEV_TEST_ONLY"]).default("OIDC"),
    subject: z.string(),
    workspaceId: z.uuid(),
    workspaceIds: z.array(z.uuid()),
    purposes: z.array(z.enum(purposes)),
    scopes: z.array(z.string()),
    expiresAt: z.number(),
  }),
  z.object({
    status: z.enum(["signed-out", "unconfigured"]),
    localDevAuth: z.boolean().optional(),
  }),
]);
export type BrowserSession = z.infer<typeof schema>;
export type SessionState = BrowserSession | { status: "loading" | "error" };
export function useOulaSession() {
  const [session, setSession] = useState<SessionState>({ status: "loading" });
  const load = useCallback(async (signal?: AbortSignal) => {
    try {
      const response = await fetch("/api/session", {
        cache: "no-store",
        credentials: "same-origin",
        signal,
      });
      if (!response.ok) throw new Error("Session unavailable");
      const next = schema.parse(await response.json());
      if (!signal?.aborted) setSession(next);
    } catch {
      if (!signal?.aborted) setSession({ status: "error" });
    }
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);
  useEffect(() => {
    if (session.status !== "authenticated") return;
    const timer = setTimeout(
      () => setSession({ status: "signed-out" }),
      Math.max(0, session.expiresAt * 1000 - Date.now()),
    );
    return () => clearTimeout(timer);
  }, [session]);
  const changeWorkspace = async (workspaceId: string) => {
    const response = await fetch("/api/session", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ workspaceId }),
    });
    if (!response.ok) throw new Error("Workspace change denied");
    setSession(schema.parse(await response.json()));
  };
  const loginLocal = async (fixture: string) => {
    const response = await fetch("/api/auth/local", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ fixture }),
    });
    if (!response.ok) throw new Error("Local session unavailable");
    setSession(schema.parse(await response.json()));
  };
  const logout = async () => {
    const response = await fetch("/api/session", { method: "DELETE" });
    if (!response.ok) throw new Error("Sign-out failed");
    setSession({ status: "signed-out" });
  };
  return { session, reload: load, changeWorkspace, logout, loginLocal };
}
