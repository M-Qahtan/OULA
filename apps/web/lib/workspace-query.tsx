"use client";
import {
  QueryClient,
  QueryClientProvider,
  useQueryClient,
} from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { createBrowserOulaApi } from "./browser-api";

/** Mounted inside the existing authority-keyed private journey, never persisted. */
export function WorkspaceQueryBoundary({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 20_000,
            gcTime: 120_000,
            retry: false,
            refetchOnWindowFocus: false,
          },
          mutations: { retry: false },
        },
      }),
  );
  useEffect(
    () => () => {
      void client.cancelQueries();
      client.clear();
    },
    [client],
  );
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

export function useWorkspaceReads() {
  const client = useQueryClient();
  return {
    passport: (id: string, fresh = false) => {
      if (fresh) client.removeQueries({ queryKey: ["passport", id] });
      return client.fetchQuery({
        queryKey: ["passport", id],
        queryFn: ({ signal }) =>
          createBrowserOulaApi(signal).getPropertyPassport(id),
        staleTime: fresh ? 0 : 20_000,
      });
    },
    dealRoom: (id: string, fresh = false) => {
      if (fresh) client.removeQueries({ queryKey: ["deal-room", id] });
      return client.fetchQuery({
        queryKey: ["deal-room", id],
        queryFn: ({ signal }) => createBrowserOulaApi(signal).getDealRoom(id),
        staleTime: fresh ? 0 : 20_000,
      });
    },
  };
}
