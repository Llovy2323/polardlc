#version 150

uniform float uTime;
uniform vec2 uResolution;
uniform vec3 uColor;
uniform float uAlpha;
uniform float uSpeed;
uniform float uScale;
uniform float uIntensity;
uniform vec2 uCameraDir;
uniform float uFov;

in vec3 skyPosition;
out vec4 fragColor;

mat3 rotX(float a) {
    float c = cos(a), s = sin(a);
    return mat3(1.0, 0.0, 0.0,
                0.0,   c,   s,
                0.0,  -s,   c);
}

mat3 rotY(float a) {
    float c = cos(a), s = sin(a);
    return mat3(  c, 0.0,   s,
                0.0, 1.0, 0.0,
                 -s, 0.0,   c);
}

float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453123);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash3(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash3(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash3(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash3(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash3(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash3(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash3(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash3(i + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, u.x);
    float nx10 = mix(n010, n110, u.x);
    float nx01 = mix(n001, n101, u.x);
    float nx11 = mix(n011, n111, u.x);
    float nxy0 = mix(nx00, nx10, u.y);
    float nxy1 = mix(nx01, nx11, u.y);
    return mix(nxy0, nxy1, u.z);
}

float fbm3(vec3 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += noise3(p) * amplitude;
        p = p * 1.92 + vec3(8.7, 4.9, 6.3);
        amplitude *= 0.55;
    }
    return value;
}

void main() {
    vec3 direction = normalize(rotY(uCameraDir.x) * rotX(uCameraDir.y) * normalize(skyPosition));
    vec3 p = direction * max(uScale, 0.001);
    float t = uTime * uSpeed;

    float wave = sin(p.x * 4.5 + t * 1.7);
    wave += sin(p.z * 3.2 - t * 1.2);
    wave += sin((p.x + p.z) * 1.5 + t * 1.3);
    wave *= 0.33333334;

    float detail = fbm3(direction * 6.5 + vec3(t * 0.10, -t * 0.06, t * 0.04));
    float shimmer = smoothstep(0.2, 0.9, detail);
    float crest = pow(max(wave * 0.5 + 0.5, 0.0), 3.0);
    float horizon = smoothstep(-0.28, 0.75, direction.y);

    vec3 deep = vec3(0.01, 0.06, 0.14);
    vec3 mid = vec3(0.04, 0.29, 0.52);
    vec3 foam = vec3(0.76, 0.94, 1.0);
    vec3 sky = mix(deep, mid, smoothstep(-0.85, 0.35, direction.y));
    sky = mix(sky, mix(mid, uColor, 0.45), horizon);
    sky += foam * crest * (0.10 + shimmer * 0.25);
    sky += vec3(0.05, 0.10, 0.18) * pow(max(direction.y, 0.0), 2.5);
    sky += vec3(0.16, 0.42, 0.70) * smoothstep(0.35, 0.95, shimmer) * 0.12;

    float aurora = smoothstep(0.72, 0.98, fbm3(direction * 11.0 + vec3(t * 0.15, t * 0.07, -t * 0.05)));
    sky += vec3(0.10, 0.34, 0.62) * aurora * 0.08;

    fragColor = vec4(clamp(sky, 0.0, 1.0), uAlpha);
}
