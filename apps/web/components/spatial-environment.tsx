"use client";

import dynamic from "next/dynamic";
import {
  Component,
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import type { Language } from "./workspace-primitives";
import type { SpatialCityControls } from "./spatial-city-canvas";
import { useSpatialExperience } from "./spatial-experience";

// Three/Fiber/Drei stay out of the initial shell and all server bundles.
const CityCanvas = dynamic(() => import("./spatial-city-canvas"), {
  ssr: false,
});

class SceneBoundary extends Component<
  { children: ReactNode; onFailure: () => void },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  componentDidCatch() {
    this.props.onFailure();
  }
  render() {
    return this.state.failed ? null : this.props.children;
  }
}

/** A visual enhancement: API records and forms never depend on GPU availability. */
export function SpatialEnvironment({ language }: { language: Language }) {
  const { tone, motionEnabled } = useSpatialExperience();
  const w = (ar: string, en: string) => (language === "ar" ? ar : en);
  const host = useRef<HTMLDivElement>(null);
  const controls = useRef<SpatialCityControls>(null);
  const pointer = useRef({ x: 0, y: 0 });
  const [palette, setPalette] = useState<{ accent: string; gold: string }>();
  const [width, setWidth] = useState(0);
  const [pixelRatio, setPixelRatio] = useState(1);
  const [nearby, setNearby] = useState(false);
  const [visible, setVisible] = useState(false);
  const [pageVisible, setPageVisible] = useState(true);
  const [simple, setSimple] = useState(false);
  const [failed, setFailed] = useState(false);
  const [ready, setReady] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const active = !simple && !failed && nearby && !!palette && width > 0;
  const animated = active && visible && pageVisible && motionEnabled;
  const onFailure = useCallback(() => {
    setFailed(true);
    setReady(false);
  }, []);
  const onReady = useCallback(() => setReady(true), []);
  const onFrame = useCallback(
    (frame: {
      count: number;
      calls: number;
      azimuth: number;
      distance: number;
    }) => {
      if (!host.current) return;
      host.current.dataset.frames = String(frame.count);
      host.current.dataset.drawCalls = String(frame.calls);
      host.current.dataset.azimuth = frame.azimuth.toFixed(3);
      host.current.dataset.distance = frame.distance.toFixed(3);
    },
    [],
  );

  useEffect(() => {
    const node = host.current;
    if (!node) return;
    const styles = getComputedStyle(node);
    setPalette({
      accent: styles.getPropertyValue("--world-accent").trim(),
      gold: styles.getPropertyValue("--os-gold").trim(),
    });
  }, [tone]);
  useEffect(() => {
    const node = host.current;
    if (!node) return;
    const resize = new ResizeObserver(([entry]) => {
      if (!entry) return;
      setWidth(entry.contentRect.width);
      setPixelRatio(window.devicePixelRatio || 1);
    });
    resize.observe(node);
    const loading = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) setNearby(true);
      },
      { rootMargin: "200px" },
    );
    const visibility = new IntersectionObserver(([entry]) =>
      setVisible(!!entry?.isIntersecting),
    );
    loading.observe(node);
    visibility.observe(node);
    const onVisibility = () =>
      setPageVisible(document.visibilityState === "visible");
    onVisibility();
    document.addEventListener("visibilitychange", onVisibility);
    // Saving data and high contrast start with an explicit quiet presentation.
    const connection = (
      navigator as Navigator & { connection?: { saveData?: boolean } }
    ).connection;
    if (connection?.saveData || matchMedia("(forced-colors: active)").matches)
      setSimple(true);
    return () => {
      resize.disconnect();
      loading.disconnect();
      visibility.disconnect();
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, []);
  useEffect(() => {
    const node = host.current;
    if (!node || !active) return;
    const lost = (event: Event) => {
      event.preventDefault();
      onFailure();
    };
    // Capture because a WebGL loss originates on the descendant canvas.
    node.addEventListener("webglcontextlost", lost, true);
    return () => node.removeEventListener("webglcontextlost", lost, true);
  }, [active, onFailure]);

  const mode = simple
    ? "simple"
    : failed
      ? "unavailable"
      : ready
        ? "interactive"
        : "loading";
  return (
    <div
      ref={host}
      className="spatial-environment"
      data-testid="spatial-environment"
      data-renderer={mode}
      data-motion={animated ? "running" : "paused"}
      onPointerMove={(event) => {
        if (!motionEnabled || event.pointerType === "touch") return;
        const box = event.currentTarget.getBoundingClientRect();
        pointer.current = {
          x: ((event.clientX - box.left) / box.width) * 2 - 1,
          y: 1 - ((event.clientY - box.top) / box.height) * 2,
        };
      }}
      onPointerLeave={() => {
        pointer.current = { x: 0, y: 0 };
      }}
    >
      <div className="spatial-environment-poster" aria-hidden="true" />
      <div className="spatial-webgl" aria-hidden="true">
        {active && palette && (
          <SceneBoundary key={attempt} onFailure={onFailure}>
            <CityCanvas
              accent={palette.accent}
              gold={palette.gold}
              width={width}
              pixelRatio={pixelRatio}
              animated={animated}
              revealed={ready}
              pointer={pointer}
              controlsRef={controls}
              onReady={onReady}
              onFailure={onFailure}
              onFrame={onFrame}
            />
          </SceneBoundary>
        )}
      </div>
      <div className="spatial-scene-shade" aria-hidden="true" />
      <div className="spatial-environment-status" role="status">
        <span className="spatial-live-dot" aria-hidden="true" />
        {simple
          ? w("عرض هادئ", "Quiet view")
          : failed
            ? w(
                "العرض المبسط متاح؛ بياناتك لم تتأثر",
                "Simplified view available; your data is unchanged",
              )
            : ready
              ? w(
                  "مشهد تفاعلي · هندسة تصورية",
                  "INTERACTIVE · ILLUSTRATIVE GEOMETRY",
                )
              : w("تجهيز المشهد المكاني…", "Preparing spatial view…")}
      </div>
      <div
        className="spatial-scene-toolbar"
        role="group"
        aria-label={w("التحكم بالمشهد المكاني", "Spatial view controls")}
      >
        <button
          type="button"
          disabled={!ready || simple || failed}
          onClick={() => controls.current?.rotate(-1)}
          aria-label={w("تدوير المشهد يسارًا", "Rotate view left")}
          title={w("تدوير يسارًا", "Rotate left")}
        >
          ↶
        </button>
        <button
          type="button"
          disabled={!ready || simple || failed}
          onClick={() => controls.current?.rotate(1)}
          aria-label={w("تدوير المشهد يمينًا", "Rotate view right")}
          title={w("تدوير يمينًا", "Rotate right")}
        >
          ↷
        </button>
        <span className="spatial-toolbar-divider" aria-hidden="true" />
        <button
          type="button"
          disabled={!ready || simple || failed}
          onClick={() => controls.current?.zoom(1)}
          aria-label={w("تقريب المشهد", "Zoom view in")}
          title={w("تقريب", "Zoom in")}
        >
          +
        </button>
        <button
          type="button"
          disabled={!ready || simple || failed}
          onClick={() => controls.current?.zoom(-1)}
          aria-label={w("إبعاد المشهد", "Zoom view out")}
          title={w("إبعاد", "Zoom out")}
        >
          −
        </button>
        <button
          type="button"
          disabled={!ready || simple || failed}
          onClick={() => controls.current?.reset()}
          aria-label={w("إعادة ضبط المشهد", "Reset spatial view")}
          title={w("إعادة الضبط", "Reset view")}
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="6" />
            <path d="M12 2v5m0 10v5M2 12h5m10 0h5" />
          </svg>
        </button>
        <span className="spatial-toolbar-divider" aria-hidden="true" />
        <button
          type="button"
          className="spatial-view-mode"
          aria-pressed={simple}
          onClick={() => {
            if (failed) {
              setFailed(false);
              setAttempt((n) => n + 1);
              setReady(false);
            } else {
              setSimple((value) => !value);
              setReady(false);
            }
          }}
        >
          {failed
            ? w("أعد المحاولة", "Retry scene")
            : simple
              ? w("عرض ثلاثي الأبعاد", "3D view")
              : w("عرض هادئ", "Quiet view")}
        </button>
      </div>
    </div>
  );
}
