#version 150

uniform sampler2D Sampler0;
uniform float swing;
uniform float smearIntensity;
uniform float glowBoost;
uniform vec2 swipeDir;

in vec2 TexCoord;
out vec4 OutColor;

void main() {
    vec2 uv = TexCoord;

    vec4 color = texture(Sampler0, uv);

    if (swing > 0.01) {
        vec2 smearUv = swipeDir * smearIntensity * swing * 0.05;

        vec4 smeared = vec4(0.0);
        float totalWeight = 0.0;

        for (int i = 0; i < 8; i++) {
            float t = float(i) / 7.0;
            vec2 sampleUv = uv + smearUv * t;
            float weight = 1.0 - t * 0.5;
            smeared += texture(Sampler0, sampleUv) * weight;
            totalWeight += weight;
        }

        smeared /= totalWeight;

        color = mix(color, smeared, swing * 0.7);

        float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        color.rgb += color.rgb * lum * glowBoost * swing;
    }

    OutColor = color;
}
