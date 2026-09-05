#version 150

uniform vec4 u_Color;
uniform vec4 u_Color2;
uniform vec2 u_Resolution;
uniform float u_Scale;
uniform float u_Time;
uniform float u_Fov;
uniform vec2 u_CameraDir;

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
    return fract(sin(dot(p, vec3(93.1, 47.7, 17.3))) * 43758.5453123);
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
        p = p * 1.85 + vec3(4.2, 7.1, 5.9);
        amplitude *= 0.56;
    }
    return value;
}

void main() {
    vec3 direction = normalize(rotY(u_CameraDir.x) * rotX(u_CameraDir.y) * normalize(skyPosition));
    vec3 p = direction * max(u_Scale, 0.001);
    float t = u_Time * 1.5;

    float v1 = sin(p.x + t) + sin(p.y + t) + sin(p.z + t);
    float v2 = sin(p.x * cos(t * 0.4) - p.z * sin(t * 0.4) + t)
             + sin(p.y * sin(t * 0.3) + p.z * cos(t * 0.3) + t);
    float v3 = sin(length(p * 1.2) - t * 1.2);

    float norm = clamp(((v1 + v2 + v3) / 3.0) * 0.5 + 0.5, 0.0, 1.0);
    float detail = fbm3(direction * 5.0 + vec3(t * 0.12, -t * 0.08, t * 0.05));
    float horizon = smoothstep(-0.25, 0.85, direction.y);

    vec3 bgColor = mix(u_Color.rgb, u_Color2.rgb, 0.35) * 0.14;
    vec3 plasmaColor = mix(u_Color.rgb, u_Color2.rgb, clamp(norm * 0.82 + 0.08, 0.0, 1.0));
    plasmaColor *= 0.45 + norm * 0.55;
    vec3 ridgeColor = mix(u_Color.rgb, u_Color2.rgb, 0.5) * pow(norm, 5.0) * 1.25;
    vec3 shimmer = vec3(0.95, 0.98, 1.0) * pow(detail, 3.0) * 0.2;

    vec3 finalColor = mix(bgColor, plasmaColor, norm) + ridgeColor + shimmer;
    finalColor *= 0.82 + horizon * 0.18;
    finalColor = clamp(finalColor, 0.0, 1.0);

    fragColor = vec4(finalColor, u_Color.a);
}
