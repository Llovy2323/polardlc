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
    return fract(sin(dot(p, vec3(74.7, 127.1, 311.7))) * 43758.5453123);
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
        p = p * 1.84 + vec3(3.3, 7.1, 5.5);
        amplitude *= 0.56;
    }
    return value;
}

void main() {
    vec3 direction = normalize(rotY(uCameraDir.x) * rotX(uCameraDir.y) * normalize(skyPosition));
    vec3 p = direction * max(uScale, 0.001);
    float t = uTime * uSpeed;

    float cloudA = fbm3(direction * 5.5 + vec3(t * 0.06, -t * 0.04, t * 0.03));
    float cloudB = fbm3(direction * 8.0 + vec3(-t * 0.09, t * 0.05, -t * 0.02));
    float clouds = smoothstep(0.48, 0.92, cloudA * 0.72 + cloudB * 0.42);
    float haze = smoothstep(-0.15, 0.75, direction.y);
    float sun = pow(max(dot(direction, normalize(vec3(-0.32, 0.66, 0.68))), 0.0), 650.0);
    float sunGlow = pow(max(dot(direction, normalize(vec3(-0.32, 0.66, 0.68))), 0.0), 24.0);

    vec3 skyTop = mix(vec3(0.33, 0.56, 1.00), uColor, 0.15);
    vec3 skyBottom = vec3(0.92, 0.72, 0.45);
    vec3 sky = mix(skyBottom, skyTop, smoothstep(-0.45, 0.95, direction.y));

    vec3 cloudColor = mix(vec3(1.0, 0.98, 0.92), uColor, 0.08);
    vec3 cloudShade = vec3(0.82, 0.69, 0.52) * 0.75;
    vec3 cloudLayer = mix(cloudShade, cloudColor, haze);
    sky = mix(sky, cloudLayer, clouds * (0.28 + uIntensity * 0.72));

    sky += vec3(1.0, 0.93, 0.67) * sunGlow * 0.28;
    sky += vec3(1.0, 0.87, 0.51) * sun * 1.35;
    sky += vec3(0.10, 0.18, 0.25) * pow(clouds, 2.0) * 0.12;

    float horizon = smoothstep(-0.3, 0.8, direction.y);
    sky *= 0.88 + horizon * 0.12;

    fragColor = vec4(clamp(sky, 0.0, 1.0), uAlpha);
}
