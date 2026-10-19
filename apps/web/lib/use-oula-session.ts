"use client";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createSessionController, type SessionState } from "./session-state";
export type { BrowserSession, SessionState } from "./session-state";

const changeSignal = "oula-session-changed";

export function useOulaSession() {
  const [session, setSession] = useState<SessionState>({ status: "loading" });
  const controller = useMemo(() => createSessionController(setSession), []);
  const channel = useRef<BroadcastChannel | null>(null);

  useEffect(() => {
    controller.activate();
    void controller.reload();
    const revalidate = () => {
      if (document.visibilityState === "visible") void controller.reload();
    };
    const changed = () => {
      controller.invalidate();
      void controller.reload();
    };
    const storageChanged = (event: StorageEvent) => {
      if (event.key === changeSignal && event.newValue) changed();
    };
    let broadcast: BroadcastChannel | null = null;
    try {
      if (typeof BroadcastChannel !== "undefined") {
        broadcast = new BroadcastChannel(changeSignal);
        broadcast.addEventListener("message", changed);
      }
    } catch {
      /* Focus and visible-tab polling remain available. */
    }
    channel.current = broadcast;
    window.addEventListener("focus", revalidate);
    window.addEventListener("pageshow", revalidate);
    window.addEventListener("storage", storageChanged);
    document.addEventListener("visibilitychange", revalidate);
    const poll = setInterval(revalidate, 30_000);
    return () => {
      controller.dispose();
      clearInterval(poll);
      window.removeEventListener("focus", revalidate);
      window.removeEventListener("pageshow", revalidate);
      window.removeEventListener("storage", storageChanged);
      document.removeEventListener("visibilitychange", revalidate);
      broadcast?.close();
      channel.current = null;
    };
  }, [controller]);

  useEffect(() => {
    if (session.status !== "authenticated") return;
    const timer = setTimeout(
      () => {
        controller.invalidate();
        void controller.reload();
      },
      Math.max(0, session.expiresAt * 1000 - Date.now()),
    );
    return () => clearTimeout(timer);
  }, [controller, session]);

  const publishChange = useCallback(() => {
    // Signals contain no identity, token, record or assistant text.
    try {
      channel.current?.postMessage("changed");
    } catch {
      /* Channel was closed. */
    }
    // This fallback also supports browsers without BroadcastChannel. Remove the
    // temporary signal immediately; no private state is persisted in storage.
    if (!channel.current) {
      try {
        localStorage.setItem(changeSignal, crypto.randomUUID());
        localStorage.removeItem(changeSignal);
      } catch {
        /* Storage may be disabled. Focus/poll revalidation still works. */
      }
    }
  }, []);

  const mutate = async (work: () => Promise<void>) => {
    try {
      await work();
    } finally {
      publishChange();
    }
  };
  return {
    session,
    reload: controller.reload,
    changeWorkspace: (workspaceId: string) =>
      mutate(() => controller.changeWorkspace(workspaceId)),
    loginLocal: (fixture: string) =>
      mutate(() => controller.loginLocal(fixture)),
    logout: () => mutate(controller.logout),
  };
}
