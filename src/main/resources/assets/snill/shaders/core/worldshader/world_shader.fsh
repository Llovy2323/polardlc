#version 150

uniform sampler2D Sampler0;
uniform vec2 resolution;
uniform vec3 color;
uniform vec3 color2;
uniform float time;
uniform float speed;
uniform float scale;
uniform float strength;
uniform float mode;
uniform float waveSpeed;
uniform float waveScale;

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;
out vec4 OutColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        value += noise(p) * amplitude;
        p = p * 2.02 + vec2(8.4, 5.7);
        amplitude *= 0.5;
    }
    return value;
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

vec3 applyCloud(vec2 uv, vec3 scene, float t) {
    vec2 p = uv * mix(1.6, 3.6, clamp(scale / 4.0, 0.0, 1.0));
    vec2 drift = vec2(t * 0.05, -t * 0.03);
    float n1 = fbm(p + drift);
    float n2 = fbm(p * 1.7 - drift * 1.2 + vec2(4.1, 7.3));
    float clouds = smoothstep(0.30, 0.95, n1 * 0.7 + n2 * 0.5);
    float glow = pow(clouds, 1.8);
    vec3 sky = mix(color, color2, clamp(uv.y * 0.55 + 0.5, 0.0, 1.0));
    sky += vec3(0.10, 0.12, 0.16) * glow;
    sky += vec3(0.06, 0.06, 0.08) * pow(fbm(p * 2.9 + drift * 1.5), 2.0);
    return mix(scene, sky, 0.30 + strength * 0.35 + glow * 0.20);
}

vec3 applyRainbow(vec2 uv, vec3 scene, float t) {
    vec2 p = uv * mix(1.4, 3.8, clamp(scale / 4.0, 0.0, 1.0));
    float flow = fbm(p * 1.8 + vec2(t * 0.12, -t * 0.10));
    float hue = fract(uv.x * 0.72 + uv.y * 0.24 + t * 0.08 + flow * 0.12);
    vec3 rainbow = hsv2rgb(vec3(hue, 0.88, 1.0));
    float bands = 0.5 + 0.5 * sin((uv.x + uv.y) * 10.0 + t * 2.0 + flow * 5.0);
    vec3 effect = scene * 0.55 + rainbow * (0.40 + bands * 0.20);
    return mix(scene, effect, 0.35 + strength * 0.35);
}

vec3 applyWater(vec2 uv, vec3 scene, float t) {
    vec2 p = uv * mix(2.0, 5.0, clamp(scale / 4.0, 0.0, 1.0));
    float wave = sin((p.x * 7.0 + p.y * 11.0) + t * 1.9) * 0.010;
    float ripple = cos((p.x * 12.0 - p.y * 8.0) - t * 1.6) * 0.008;
    vec2 warped = uv + vec2(wave, ripple);
    warped += (vec2(fbm(p + t), fbm(p - t * 0.6)) - 0.5) * 0.018;
    vec3 sampleColor = texture(Sampler0, clamp(warped, vec2(0.0), vec2(1.0))).rgb;
    float caustic = pow(max(0.0, sin((uv.x * 14.0 + uv.y * 9.0) + t * 2.2)), 2.0);
    vec3 tint = mix(color, color2, 0.5);
    vec3 effect = sampleColor + tint * 0.18 + vec3(caustic * 0.14, caustic * 0.18, caustic * 0.24);
    return mix(scene, effect, 0.38 + strength * 0.28);
}

vec3 applyOcean(vec2 uv, vec3 scene, float t) {
    vec2 p = uv * mix(1.8, 4.2, clamp(scale / 4.0, 0.0, 1.0));
    float depth = smoothstep(-0.85, 0.75, uv.y);
    vec2 wave = vec2(
        sin((p.x * 5.5 + p.y * 4.2) * waveScale + t * waveSpeed),
        cos((p.x * 4.0 - p.y * 5.1) * waveScale - t * waveSpeed)
    ) * 0.014;
    vec2 warped = uv + wave + (vec2(fbm(p + t * 0.25), fbm(p - t * 0.35)) - 0.5) * 0.020;
    vec3 sampleColor = texture(Sampler0, clamp(warped, vec2(0.0), vec2(1.0))).rgb;
    float foam = smoothstep(0.60, 0.95, fbm(p * 3.0 + t * 0.8));
    vec3 deep = mix(color, color2, depth);
    vec3 effect = sampleColor * 0.72 + deep * 0.28 + foam * vec3(0.06, 0.10, 0.14);
    return mix(scene, effect, 0.40 + strength * 0.30);
}

vec3 applyWave(vec2 uv, vec3 scene, float t) {
    vec2 p = uv * mix(2.2, 5.4, clamp(scale / 4.0, 0.0, 1.0));
    float wavePhase = t * waveSpeed;
    float ridged = 1.0 - abs(sin((p.x * 1.3 + p.y * 0.8) * waveScale + wavePhase + fbm(p * 1.2) * 4.0));
    ridged = pow(clamp(ridged, 0.0, 1.0), 4.2);
    float mist = fbm(p * 1.7 + vec2(wavePhase * 0.6, -wavePhase * 0.4));
    vec3 energy = mix(color, color2, clamp(mist * 0.65 + ridged * 0.35, 0.0, 1.0));
    vec3 effect = scene * 0.48 + energy * 0.52 + energy * ridged * 0.22;
    return mix(scene, effect, 0.42 + strength * 0.34);
}

void main() {
    vec2 uv = TexCoord;
    vec3 scene = texture(Sampler0, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
    float t = time * max(speed, 0.001);

    vec3 result = scene;
    if (mode < 0.5) {
        result = applyCloud(uv, scene, t);
    } else if (mode < 1.5) {
        result = applyRainbow(uv, scene, t);
    } else if (mode < 2.5) {
        result = applyWater(uv, scene, t);
    } else if (mode < 3.5) {
        result = applyOcean(uv, scene, t);
    } else {
        result = applyWave(uv, scene, t);
    }

    vec3 finalColor = mix(scene, result, clamp(strength, 0.0, 1.0));
    OutColor = vec4(finalColor, 1.0);
}
