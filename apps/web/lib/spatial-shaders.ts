/** GLSL artwork. Uniforms are presentation-only; no numeric business data is synthesized. */
export const screenVertex = /* glsl */ `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 1.0, 1.0);
  }
`;

export const nebulaFragment = /* glsl */ `
  uniform float uTime;
  uniform float uAspect;
  uniform vec2 uPointer;
  uniform vec3 uAccent;
  uniform vec3 uGold;
  varying vec2 vUv;
  float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
  float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),
               mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.)),f.x),f.y);
  }
  void main() {
    vec2 p = (vUv - .5) * vec2(uAspect,1.);
    p += uPointer * .035;
    float cloud = noise(p*3.8+uTime*.018)*.55;
    cloud += noise(p*8.0-uTime*.012)*.24;
    cloud += noise(p*17.0+uTime*.006)*.11;
    float halo = exp(-length((p-vec2(.15,.09))*vec2(.8,1.8))*2.4);
    vec3 base = vec3(.006,.018,.039);
    vec3 color = base + uAccent * pow(cloud,3.0) * halo * .55;
    color += uGold * pow(max(0.,cloud-.28),3.0) * .25;
    vec2 stars = p*240.;
    float spark = step(.9976,hash(floor(stars))) * pow(1.-length(fract(stars)-.5),12.);
    color += vec3(.5,.8,1.) * spark * (.75+sin(uTime*.3+stars.x)*.2);
    color *= 1.-smoothstep(.3,1.2,length(p))*.45;
    gl_FragColor = vec4(color,1.);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }
`;

export const cityVertex = /* glsl */ `
  varying vec3 vLocal;
  varying vec3 vWorld;
  void main() {
    vLocal = position;
    vec4 world = modelMatrix * instanceMatrix * vec4(position,1.);
    vWorld = world.xyz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }
`;

export const cityFragment = /* glsl */ `
  uniform vec3 uAccent;
  uniform vec3 uGold;
  varying vec3 vLocal;
  varying vec3 vWorld;
  void main() {
    vec3 face = abs(vLocal);
    float edge = step(.475,min(max(face.x,face.z),face.y));
    edge = max(edge, step(.475,min(face.x,face.z)));
    float windowX = step(.56,fract((vLocal.x+vLocal.z)*9.));
    float windowY = step(.62,fract(vWorld.y*4.5));
    float randomFloor = fract(sin(floor(vWorld.y*4.5)*12.3+floor(vWorld.x*8.)*23.7+floor(vWorld.z*8.))*457.1);
    float windows = windowX * windowY * step(.45,randomFloor);
    float warm = step(.6,fract(sin(floor(vWorld.x)*31.+floor(vWorld.z)*71.)*437.));
    vec3 light = mix(uAccent,uGold,warm);
    vec3 body = vec3(.018,.055,.084) + uAccent * (.025+max(vLocal.y,0.)*.09);
    vec3 color = body + light * windows * .8 + uAccent * edge * .75;
    float fog = smoothstep(18.,38.,length(vWorld.xz));
    gl_FragColor = vec4(mix(color,vec3(.006,.018,.039),fog),1.);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }
`;

export const floorVertex = /* glsl */ `
  varying vec2 vGround;
  void main() {
    vGround = position.xy;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.);
  }
`;

export const floorFragment = /* glsl */ `
  uniform float uTime;
  uniform vec3 uAccent;
  uniform vec3 uGold;
  varying vec2 vGround;
  void main() {
    vec2 p = vGround;
    vec2 cell = abs(fract(p/1.65-.5)-.5);
    float grid = 1.-smoothstep(.007,.02,min(cell.x,cell.y));
    float boulevard = exp(-abs(p.x)*5.) + exp(-abs(p.y)*5.);
    float radius = length(p);
    float ring = exp(-abs(radius-11.85)*24.) + exp(-abs(radius-12.1)*38.)*.4;
    float scan = exp(-pow(radius-mod(uTime*.6,15.),2.)*9.);
    float reflection = pow(max(0.,sin(p.x*2.2+p.y*.4)),18.) * exp(-abs(p.y+4.)*.25);
    vec3 color = vec3(.008,.032,.05) + uAccent*(grid*.22+boulevard*.34+ring*.7+scan*.09);
    color += uGold*reflection*.11;
    float alpha = (1.-smoothstep(11.8,13.0,radius))*.93;
    gl_FragColor = vec4(color,alpha);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }
`;

export const dustVertex = /* glsl */ `
  uniform float uTime;
  uniform float uDpr;
  attribute float aSeed;
  varying float vSeed;
  void main() {
    vSeed = aSeed;
    vec3 p = position;
    p.y += sin(uTime*.14+aSeed*50.)*.08;
    vec4 view = modelViewMatrix * vec4(p,1.);
    gl_PointSize = clamp((1.6+aSeed*1.9)*uDpr*(20./-view.z),1.,6.);
    gl_Position = projectionMatrix * view;
  }
`;

export const dustFragment = /* glsl */ `
  uniform vec3 uAccent;
  uniform vec3 uGold;
  varying float vSeed;
  void main() {
    float glow = exp(-length(gl_PointCoord-.5)*7.);
    vec3 color = mix(uAccent,uGold,step(.78,vSeed));
    gl_FragColor = vec4(color,glow*.75);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }
`;
