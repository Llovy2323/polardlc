#version 150

uniform sampler2D Sampler0;
uniform vec2 texelSize;
uniform vec3 color;
uniform vec3 color2;
uniform float time;
uniform float speed;
uniform float scale;
uniform float outline;
uniform float glow;
uniform float fill;
uniform float alpha;
uniform float outlineOnly;

in vec2 TexCoord;
out vec4 OutColor;

float hash(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 19.19);
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
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        value += noise(p) * amp;
        p = p * 2.05 + vec2(4.2, 7.1);
        amp *= 0.55;
    }
    return value;
}

float edgeMask(vec2 uv, float s) {
    float a0 = texture(Sampler0, uv).a;
    float ax1 = texture(Sampler0, uv + vec2(texelSize.x * s, 0.0)).a;
    float ax2 = texture(Sampler0, uv - vec2(texelSize.x * s, 0.0)).a;
    float ay1 = texture(Sampler0, uv + vec2(0.0, texelSize.y * s)).a;
    float ay2 = texture(Sampler0, uv - vec2(0.0, texelSize.y * s)).a;
    float edge = abs(a0 - ax1) + abs(a0 - ax2) + abs(a0 - ay1) + abs(a0 - ay2);
    return clamp(edge * 1.9, 0.0, 1.0);
}

void main() {
    vec2 uv = TexCoord;
    float mask = texture(Sampler0, uv).a;
    if (mask <= 0.0) discard;

    float e = edgeMask(uv, outline);
    float edgeBand = smoothstep(0.02, 0.42, e);
    if (outlineOnly > 0.5) {
        float edgeAlpha = clamp(edgeBand * alpha, 0.0, 1.0) * mask;
        if (edgeAlpha <= 0.001) discard;
        OutColor = vec4(mix(color, color2, 0.45), edgeAlpha);
        return;
    }

    vec2 p = (uv - 0.5) * mix(5.5, 14.0, clamp(scale / 3.0, 0.0, 1.0));
    float t = time * max(speed, 0.001);

    vec2 drift = vec2(t * 0.28, -t * 0.21);
    vec2 warp = vec2(
        fbm(p * 0.92 + drift * 0.8 + vec2(0.0, 2.7)),
        fbm(p * 0.78 - drift * 0.6 + vec2(3.4, 1.1))
    );
    vec2 q = p + (warp - 0.5) * 2.0;

    float waveA = 1.0 - abs(sin((q.x + q.y * 0.62) * 2.2 + t * 1.15));
    float waveB = 1.0 - abs(sin((q.x * -0.58 + q.y * 1.08) * 2.6 - t * 0.8));
    waveA = pow(clamp(waveA, 0.0, 1.0), 5.0);
    waveB = pow(clamp(waveB, 0.0, 1.0), 5.4);

    float mist = fbm(q * 0.75 + vec2(t * 0.12, -t * 0.09));
    float veins = fbm(q * 1.95 + vec2(mist * 2.6, mist * 1.7) - drift * 0.45);
    veins = pow(clamp(veins, 0.0, 1.0), 2.3);

    float pulse = clamp(mist * 0.22 + veins * 0.86 + waveA * 0.52 + waveB * 0.34, 0.0, 1.0);
    float core = smoothstep(0.16, 0.98, pulse);
    float sparkle = pow(clamp(max(veins, waveA), 0.0, 1.0), 1.3);

    vec3 outlineColor = mix(color, color2, 0.35);
    vec3 shaderColor = mix(color, color2, clamp(core * 0.78 + waveB * 0.22, 0.0, 1.0));
    vec3 sheen = mix(color2, vec3(0.95, 0.98, 1.0), clamp(sparkle * 0.8 + core * 0.2, 0.0, 1.0));

    float innerMask = clamp(mask - edgeBand * 0.58, 0.0, 1.0);
    float fillStrength = fill * innerMask * (0.24 + core * 0.82 + sparkle * 0.22);
    float edgeStrength = edgeBand * (0.34 + glow * 0.12);

    vec3 rgb = shaderColor * fillStrength + outlineColor * edgeStrength + sheen * sparkle * 0.18;
    float outAlpha = clamp(alpha * (fillStrength * 0.9 + edgeBand * 0.48) * mask, 0.0, 1.0);
    if (outAlpha <= 0.001) discard;

    OutColor = vec4(rgb, outAlpha);
}
