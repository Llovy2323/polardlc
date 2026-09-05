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
    return fract(sin(dot(p, vec3(12.1, 78.7, 41.3))) * 43758.5453123);
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
    for (int i = 0; i < 5; i++) {
        value += noise3(p) * amplitude;
        p = p * 1.88 + vec3(5.7, 3.4, 7.9);
        amplitude *= 0.56;
    }
    return value;
}

void main() {
    vec3 direction = normalize(rotY(uCameraDir.x) * rotX(uCameraDir.y) * normalize(skyPosition));
    vec3 p = direction * max(uScale, 0.001);
    float t = uTime * uSpeed;

    float band = sin((p.x + p.z) * 3.5 + t * 1.9);
    band += sin(p.y * 8.0 - t * 2.8);
    band *= 0.5;

    float cell = fbm3(direction * 7.0 + vec3(t * 0.15, -t * 0.12, t * 0.07));
    float caustic = pow(max(band * 0.5 + 0.5, 0.0), 2.6);
    float glow = smoothstep(0.12, 0.92, cell);

    vec3 base = mix(vec3(0.01, 0.05, 0.10), uColor, 0.58);
    vec3 sparkle = vec3(0.14, 0.56, 0.96) * (0.3 + glow * 0.7);

    vec3 color = base * (0.34 + glow * 0.26);
    color += sparkle * caustic;
    color += vec3(0.03, 0.09, 0.15) * pow(max(direction.y, 0.0), 2.0);

    fragColor = vec4(clamp(color, 0.0, 1.0), uAlpha);
}
