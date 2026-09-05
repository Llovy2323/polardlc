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
    float amp = 0.55;
    for (int i = 0; i < 5; i++) {
        value += noise(p) * amp;
        p = p * 2.02 + vec2(8.1, 4.7);
        amp *= 0.5;
    }
    return value;
}

float edgeMask(vec2 uv, float s) {
    float a0 = texture(Sampler0, uv).a;
    float ax1 = texture(Sampler0, uv + vec2(texelSize.x * s, 0.0)).a;
    float ax2 = texture(Sampler0, uv - vec2(texelSize.x * s, 0.0)).a;
    float ay1 = texture(Sampler0, uv + vec2(0.0, texelSize.y * s)).a;
    float ay2 = texture(Sampler0, uv - vec2(0.0, texelSize.y * s)).a;
    float diag1 = texture(Sampler0, uv + vec2(texelSize.x * s, texelSize.y * s)).a;
    float diag2 = texture(Sampler0, uv + vec2(-texelSize.x * s, texelSize.y * s)).a;
    float diag3 = texture(Sampler0, uv + vec2(texelSize.x * s, -texelSize.y * s)).a;
    float diag4 = texture(Sampler0, uv + vec2(-texelSize.x * s, -texelSize.y * s)).a;

    float edge = abs(a0 - ax1) + abs(a0 - ax2) + abs(a0 - ay1) + abs(a0 - ay2);
    edge += 0.6 * (abs(a0 - diag1) + abs(a0 - diag2) + abs(a0 - diag3) + abs(a0 - diag4));
    return clamp(edge * 2.0, 0.0, 1.0);
}

void main() {
    vec2 uv = TexCoord;
    float mask = texture(Sampler0, uv).a;
    if (mask <= 0.0) discard;

    float e = edgeMask(uv, outline);
    float edgeBand = smoothstep(0.02, 0.38, e);
    if (outlineOnly > 0.5) {
        float edgeAlpha = clamp(edgeBand * alpha, 0.0, 1.0) * mask;
        if (edgeAlpha <= 0.001) discard;
        OutColor = vec4(mix(color, color2, 0.7), edgeAlpha);
        return;
    }

    vec2 p = (uv - 0.5) * mix(6.0, 16.0, clamp(scale / 3.0, 0.0, 1.0));
    float t = time * max(speed, 0.001);
    float ang = atan(p.y, p.x + 1e-6) + t * 0.35;
    float rad = length(p);

    vec2 warp = vec2(
        fbm(p * 0.85 + vec2(t * 0.25, -t * 0.17)),
        fbm(p * 0.72 - vec2(t * 0.19, t * 0.23))
    );
    vec2 q = p + (warp - 0.5) * 1.7;

    float rings = 1.0 - abs(sin(rad * 2.8 - t * 1.2 + fbm(q * 0.85) * 3.2));
    rings = pow(clamp(rings, 0.0, 1.0), 4.5);

    float cards = 1.0 - abs(sin((ang * 3.0 + rad * 0.8) * 2.2 + t * 0.8));
    cards = pow(clamp(cards, 0.0, 1.0), 6.0);

    float grain = fbm(q * 2.2 + vec2(t * 0.35, -t * 0.28));
    float pulse = smoothstep(0.08, 0.95, rings + cards * 0.85 + grain * 0.35);

    vec3 base = mix(color, color2, clamp(0.25 + cards * 0.6 + grain * 0.2, 0.0, 1.0));
    vec3 highlight = mix(color2, vec3(1.0, 0.95, 0.75), clamp(rings * 0.85 + pulse * 0.25, 0.0, 1.0));
    float innerMask = clamp(mask - edgeBand * 0.6, 0.0, 1.0);
    float fillStrength = fill * innerMask * (0.22 + pulse * 0.92);
    float glowStrength = edgeBand * (0.34 + glow * 0.14);

    vec3 rgb = base * fillStrength + highlight * glowStrength;
    float outAlpha = clamp(alpha * (fillStrength * 0.96 + edgeBand * 0.42) * mask, 0.0, 1.0);
    if (outAlpha <= 0.001) discard;

    OutColor = vec4(rgb, outAlpha);
}
