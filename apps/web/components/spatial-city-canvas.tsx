"use client";

import {
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  type ComponentRef,
  type RefObject,
} from "react";
import { Canvas, useFrame, useThree } from "@react-three/fiber";
import { OrbitControls } from "@react-three/drei";
import {
  AdditiveBlending,
  Color,
  InstancedMesh,
  Object3D,
  ShaderMaterial,
  Vector2,
  Vector3,
} from "three";
import {
  createIllustrativeDistrict,
  spatialRenderBudget,
} from "../lib/spatial-city-model";
import {
  cityFragment,
  cityVertex,
  dustFragment,
  dustVertex,
  floorFragment,
  floorVertex,
  nebulaFragment,
  screenVertex,
} from "../lib/spatial-shaders";

export type SpatialCityControls = {
  rotate: (direction: number) => void;
  zoom: (direction: number) => void;
  reset: () => void;
};

export type SpatialCityCanvasProps = {
  accent: string;
  gold: string;
  width: number;
  pixelRatio: number;
  animated: boolean;
  revealed: boolean;
  pointer: RefObject<{ x: number; y: number }>;
  controlsRef: RefObject<SpatialCityControls | null>;
  onReady: () => void;
  onFailure: () => void;
  onFrame: (frame: {
    count: number;
    calls: number;
    azimuth: number;
    distance: number;
  }) => void;
};

function RenderClock({ active, fps }: { active: boolean; fps: number }) {
  const invalidate = useThree((state) => state.invalidate);
  useEffect(() => {
    if (!active) return;
    let frame = 0;
    let previous = 0;
    const tick = (time: number) => {
      if (time - previous >= 1000 / fps) {
        previous = time;
        invalidate();
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [active, fps, invalidate]);
  return null;
}

function DistrictScene(props: SpatialCityCanvasProps) {
  const { accent, gold, animated, pointer, controlsRef, onFrame, onReady } =
    props;
  const budget = spatialRenderBudget(props.width, props.pixelRatio);
  const camera = useThree((state) => state.camera);
  const invalidate = useThree((state) => state.invalidate);
  const gl = useThree((state) => state.gl);
  const viewport = useThree((state) => state.size);
  const controls = useRef<ComponentRef<typeof OrbitControls>>(null);
  const bodies = useRef<InstancedMesh>(null);
  const reflections = useRef<InstancedMesh>(null);
  const sky = useRef<ShaderMaterial>(null);
  const floor = useRef<ShaderMaterial>(null);
  const dust = useRef<ShaderMaterial>(null);
  const time = useRef(0);
  const frames = useRef(0);
  const mouse = useRef(new Vector2());
  const buildings = useMemo(createIllustrativeDistrict, []);
  const colors = useMemo(
    () => ({
      uAccent: { value: new Color(accent) },
      uGold: { value: new Color(gold) },
    }),
    [accent, gold],
  );
  const skyUniforms = useMemo(
    () => ({
      ...colors,
      uTime: { value: 0 },
      uAspect: { value: 1 },
      uPointer: { value: new Vector2() },
    }),
    [colors],
  );
  const floorUniforms = useMemo(
    () => ({ ...colors, uTime: { value: 0 } }),
    [colors],
  );
  const dustUniforms = useMemo(
    () => ({ ...colors, uTime: { value: 0 }, uDpr: { value: budget.dpr } }),
    [colors, budget.dpr],
  );
  const edges = useMemo(() => {
    const values: number[] = [];
    const pairs = [
      [0, 1],
      [1, 2],
      [2, 3],
      [3, 0],
      [4, 5],
      [5, 6],
      [6, 7],
      [7, 4],
      [0, 4],
      [1, 5],
      [2, 6],
      [3, 7],
    ];
    for (const b of buildings) {
      const corners = [
        [b.x - b.width / 2, 0.08, b.z - b.depth / 2],
        [b.x + b.width / 2, 0.08, b.z - b.depth / 2],
        [b.x + b.width / 2, 0.08, b.z + b.depth / 2],
        [b.x - b.width / 2, 0.08, b.z + b.depth / 2],
        [b.x - b.width / 2, b.height, b.z - b.depth / 2],
        [b.x + b.width / 2, b.height, b.z - b.depth / 2],
        [b.x + b.width / 2, b.height, b.z + b.depth / 2],
        [b.x - b.width / 2, b.height, b.z + b.depth / 2],
      ];
      for (const [a, bIndex] of pairs)
        values.push(...corners[a!]!, ...corners[bIndex!]!);
    }
    return new Float32Array(values);
  }, [buildings]);
  const particles = useMemo(() => {
    const positions = new Float32Array(budget.particles * 3);
    const seeds = new Float32Array(budget.particles);
    const hash = (n: number) => {
      const value = Math.sin(n * 127.1 + 311.7) * 43758.5453;
      return value - Math.floor(value);
    };
    for (let i = 0; i < budget.particles; i++) {
      const b = buildings[i % buildings.length]!;
      const face = i % 4;
      const u = hash(i + 2) - 0.5;
      const v = hash(i + 9);
      positions[i * 3] =
        b.x + (face < 2 ? u * b.width : (face === 2 ? -0.5 : 0.5) * b.width);
      positions[i * 3 + 1] =
        v * b.height + (i % 7 === 0 ? hash(i + 3) * 4 : 0.04);
      positions[i * 3 + 2] =
        b.z + (face < 2 ? (face === 0 ? -0.5 : 0.5) * b.depth : u * b.depth);
      seeds[i] = hash(i + 51);
    }
    return { positions, seeds };
  }, [buildings, budget.particles]);

  useEffect(() => {
    const item = new Object3D();
    buildings.forEach((b, index) => {
      item.position.set(b.x, b.height / 2 + 0.06, b.z);
      item.scale.set(b.width, b.height, b.depth);
      item.updateMatrix();
      bodies.current?.setMatrixAt(index, item.matrix);
      item.position.y = -b.height / 2;
      item.updateMatrix();
      reflections.current?.setMatrixAt(index, item.matrix);
    });
    for (const mesh of [bodies.current, reflections.current]) {
      if (mesh) {
        mesh.instanceMatrix.needsUpdate = true;
        mesh.computeBoundingSphere();
      }
    }
    invalidate();
  }, [buildings, invalidate]);

  useImperativeHandle(
    controlsRef,
    () => ({
      rotate(direction) {
        const control = controls.current;
        if (!control) return;
        control.setAzimuthalAngle(
          control.getAzimuthalAngle() + (direction * Math.PI) / 8,
        );
        control.update();
        invalidate();
      },
      zoom(direction) {
        const control = controls.current;
        if (!control) return;
        const offset = camera.position.clone().sub(control.target);
        offset.setLength(
          Math.min(
            45,
            Math.max(16, offset.length() * (direction > 0 ? 0.83 : 1.2)),
          ),
        );
        camera.position.copy(control.target).add(offset);
        control.update();
        invalidate();
      },
      reset() {
        camera.position.set(22, 17, 24);
        controls.current?.target.set(0, 0.8, 0);
        controls.current?.update();
        invalidate();
      },
    }),
    [camera, invalidate],
  );

  useFrame((_, delta) => {
    if (animated) time.current += Math.min(delta, 0.08);
    if (sky.current) {
      sky.current.uniforms.uTime!.value = time.current;
      sky.current.uniforms.uAspect!.value =
        viewport.width / Math.max(viewport.height, 1);
      const point = sky.current.uniforms.uPointer!.value as Vector2;
      if (animated)
        point.lerp(
          mouse.current.set(pointer.current.x, pointer.current.y),
          0.06,
        );
    }
    if (floor.current) floor.current.uniforms.uTime!.value = time.current;
    if (dust.current) dust.current.uniforms.uTime!.value = time.current;
    frames.current++;
    if (frames.current < 2) invalidate();
    if (frames.current === 2) onReady();
    onFrame({
      count: frames.current,
      calls: gl.info.render.calls,
      azimuth: controls.current?.getAzimuthalAngle() ?? 0,
      distance: camera.position.distanceTo(
        controls.current?.target ?? new Vector3(),
      ),
    });
  });

  // Demand rendering still needs a second frame to verify a completed first GPU draw.
  useEffect(() => {
    invalidate();
    const frame = requestAnimationFrame(() => invalidate());
    return () => cancelAnimationFrame(frame);
  }, [invalidate]);
  useEffect(() => {
    if (!props.revealed) return;
    // The first GPU draws happen behind the loading poster. Paint once more after
    // the canvas is revealed, including when reduced motion leaves the loop idle.
    const frame = requestAnimationFrame(() => invalidate());
    return () => cancelAnimationFrame(frame);
  }, [props.revealed, invalidate]);
  return (
    <>
      <RenderClock active={animated} fps={budget.fps} />
      <mesh frustumCulled={false} renderOrder={-100}>
        <planeGeometry args={[2, 2]} />
        <shaderMaterial
          ref={sky}
          uniforms={skyUniforms}
          vertexShader={screenVertex}
          fragmentShader={nebulaFragment}
          depthWrite={false}
          depthTest={false}
        />
      </mesh>
      <group position={[0, -0.3, 0]}>
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.17, 0]}>
          <circleGeometry args={[12.8, 96]} />
          <meshBasicMaterial color={accent} transparent opacity={0.07} />
        </mesh>
        <instancedMesh
          ref={reflections}
          args={[undefined, undefined, buildings.length]}
        >
          <boxGeometry />
          <meshBasicMaterial
            color={accent}
            transparent
            opacity={0.055}
            depthWrite={false}
          />
        </instancedMesh>
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0, 0]}>
          <planeGeometry args={[28, 28]} />
          <shaderMaterial
            ref={floor}
            uniforms={floorUniforms}
            vertexShader={floorVertex}
            fragmentShader={floorFragment}
            transparent
            depthWrite={false}
          />
        </mesh>
        <instancedMesh
          ref={bodies}
          args={[undefined, undefined, buildings.length]}
        >
          <boxGeometry />
          <shaderMaterial
            uniforms={colors}
            vertexShader={cityVertex}
            fragmentShader={cityFragment}
          />
        </instancedMesh>
        <lineSegments>
          <bufferGeometry>
            <bufferAttribute attach="attributes-position" args={[edges, 3]} />
          </bufferGeometry>
          <lineBasicMaterial
            color={accent}
            transparent
            opacity={0.42}
            toneMapped={false}
          />
        </lineSegments>
        <points>
          <bufferGeometry>
            <bufferAttribute
              attach="attributes-position"
              args={[particles.positions, 3]}
            />
            <bufferAttribute
              attach="attributes-aSeed"
              args={[particles.seeds, 1]}
            />
          </bufferGeometry>
          <shaderMaterial
            ref={dust}
            uniforms={dustUniforms}
            vertexShader={dustVertex}
            fragmentShader={dustFragment}
            transparent
            depthWrite={false}
            blending={AdditiveBlending}
          />
        </points>
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.05, 0]}>
          <ringGeometry args={[1.6, 1.63, 64]} />
          <meshBasicMaterial
            color={gold}
            transparent
            opacity={0.85}
            toneMapped={false}
          />
        </mesh>
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0.055, 0]}>
          <ringGeometry args={[1.63, 2.15, 64]} />
          <meshBasicMaterial
            color={gold}
            transparent
            opacity={0.055}
            depthWrite={false}
          />
        </mesh>
      </group>
      <OrbitControls
        ref={controls}
        makeDefault
        target={[0, 0.8, 0]}
        enablePan={false}
        enableZoom={false}
        enabled={!budget.compact}
        enableDamping={animated}
        dampingFactor={0.08}
        minDistance={16}
        maxDistance={45}
        minPolarAngle={0.2}
        maxPolarAngle={Math.PI / 2.25}
      />
    </>
  );
}

export default function SpatialCityCanvas(props: SpatialCityCanvasProps) {
  const budget = spatialRenderBudget(props.width, props.pixelRatio);
  return (
    <Canvas
      camera={{ position: [22, 17, 24], fov: 39, near: 0.1, far: 100 }}
      dpr={budget.dpr}
      frameloop="demand"
      gl={{
        alpha: false,
        antialias: !budget.compact,
        powerPreference: "low-power",
      }}
      style={{ pointerEvents: budget.compact ? "none" : "auto" }}
      fallback={null}
      onCreated={({ gl }) => {
        gl.domElement.setAttribute("aria-hidden", "true");
        gl.debug.onShaderError = () => props.onFailure();
      }}
    >
      <DistrictScene {...props} />
    </Canvas>
  );
}
