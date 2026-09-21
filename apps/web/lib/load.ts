"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type LoadState<T> =
  | { kind: "loading" }
  | { kind: "error"; error: unknown }
  | { kind: "ok"; data: T };

export function useLoadOnMount<T>(loader: () => Promise<T>) {
  const [state, setState] = useState<LoadState<T>>({ kind: "loading" });
  const loaderRef = useRef(loader);

  useEffect(() => {
    loaderRef.current = loader;
  });

  const reload = useCallback(() => {
    setState({ kind: "loading" });
    return loaderRef
      .current()
      .then((data) => setState({ kind: "ok", data }))
      .catch((error: unknown) => setState({ kind: "error", error }));
  }, []);

  useEffect(() => {
    let cancelled = false;
    loaderRef
      .current()
      .then((data) => {
        if (!cancelled) {
          setState({ kind: "ok", data });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setState({ kind: "error", error });
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return { state, reload };
}
