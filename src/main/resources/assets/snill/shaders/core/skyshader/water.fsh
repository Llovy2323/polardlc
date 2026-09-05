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
        p = p * 1.9 + vec3(3.1, 7.7, 5.4);
        amplitude *= 0.55;
    }
    return value;
}

void main() {
    vec3 direction = normalize(rotY(uCameraDir.x) * rotX(uCameraDir.y) * normalize(skyPosition));
    vec3 p = direction * max(uScale, 0.001);
    float t = uTime * uSpeed;

    float wave = sin(p.x * 3.6 + t);
    wave += sin(p.z * 2.9 - t * 0.8);
    wave += sin((p.x + p.z) * 1.7 + t * 1.2);
    wave *= 0.33333334;

    float detail = fbm3(direction * 6.0 + vec3(t * 0.12, -t * 0.08, t * 0.05));
    float foam = pow(max(wave * 0.5 + 0.5, 0.0), 3.2);
    float shimmer = smoothstep(0.3, 0.95, detail);

    vec3 deep = vec3(0.02, 0.10, 0.18);
    vec3 shallow = mix(deep, uColor, 0.62);
    vec3 caustic = vec3(0.26, 0.62, 0.98) * (0.35 + shimmer * 0.65);

    vec3 color = shallow * (0.38 + wave * 0.45 + detail * 0.12);
    color += caustic * foam;
    color += vec3(0.05, 0.11, 0.18) * pow(max(direction.y, 0.0), 2.5);

    fragColor = vec4(clamp(color, 0.0, 1.0), uAlpha);
}
