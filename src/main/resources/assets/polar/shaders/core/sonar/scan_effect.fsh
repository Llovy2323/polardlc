#version 150

uniform mat4 invViewMat;
uniform mat4 invProjMat;
uniform vec3 pos;
uniform vec3 center;
uniform float radius;
uniform sampler2D depthTex;

uniform float width;
uniform float sharpness;
uniform vec4 outerColor;
uniform vec4 midColor;
uniform vec4 innerColor;
uniform vec4 scanlineColor;
uniform int DebugMode; // 0 = 3D World Grid, 1 = Cyberpunk Tech, 2 = Clean Wave, 3 = Topography
uniform vec4 ColorModulator;

in vec2 texCoord;
out vec4 OutColor;

vec3 worldpos(vec2 uv, float depth) {
    float z = depth * 2.0 - 1.0;
    vec4 clipSpacePosition = vec4(uv * 2.0 - 1.0, z, 1.0);
    vec4 viewSpacePosition = invProjMat * clipSpacePosition;
    viewSpacePosition /= max(viewSpacePosition.w, 1e-6);
    vec4 worldSpacePosition = invViewMat * viewSpacePosition;
    return pos + worldSpacePosition.xyz;
}

// 3D block-edge wireframe in world space
float calcWorldGrid(vec3 p) {
    vec3 d = abs(fract(p - 0.5) - 0.5);
    vec3 edge = 1.0 - smoothstep(vec3(0.0), vec3(0.065), d);
    return clamp(max(max(edge.x, edge.y), edge.z), 0.0, 1.0);
}

// Topographic height contour lines along Y axis
float calcTopo(vec3 p) {
    float h = abs(fract(p.y * 1.0) - 0.5);
    float line = 1.0 - smoothstep(0.0, 0.06, h);
    float major = 1.0 - smoothstep(0.0, 0.09, abs(fract(p.y * 0.2) - 0.5));
    return clamp(line * 0.70 + major * 0.65, 0.0, 1.0);
}

// Cyberpunk tech matrix
float calcTechPattern(vec3 p) {
    vec3 grid = abs(fract(p) - 0.5);
    float lines = 1.0 - smoothstep(0.0, 0.07, min(min(grid.x, grid.y), grid.z));
    vec3 sub = abs(fract(p * 2.0) - 0.5);
    float sublines = (1.0 - smoothstep(0.0, 0.05, min(min(sub.x, sub.y), sub.z))) * 0.6;
    return clamp(lines + sublines, 0.0, 1.0);
}

void main() {
    vec2 uv = texCoord;
    float depth = texture(depthTex, uv).r;

    if (depth >= 1.0) {
        OutColor = vec4(0.0);
        return;
    }

    vec3 p = worldpos(uv, depth);
    float dist = distance(p, center);

    if (dist <= radius && dist >= radius - width) {
        // Normalized 0.0 -> 1.0 across wave thickness (1.0 at front leading edge)
        float norm = clamp((dist - (radius - width)) / max(width, 1e-5), 0.0, 1.0);

        // Leading edge front glow peak (razor-sharp neon leading line)
        float frontLine = smoothstep(0.88, 1.0, norm);
        float frontGlow = smoothstep(0.60, 1.0, norm);

        // Soft wave body envelope at the tail
        float bodyFade = smoothstep(0.0, 0.20, norm);

        // Concentric acoustic ripples
        float ripple = sin((1.0 - norm) * 18.849) * 0.5 + 0.5;

        // Pattern selection
        float pattern = 0.0;
        if (DebugMode == 0) {
            // Mode 0: 3D Holographic World Grid (block wireframe)
            float grid = calcWorldGrid(p);
            pattern = grid * 0.85 + ripple * 0.20;
        } else if (DebugMode == 1) {
            // Mode 1: Cyberpunk Tech
            float tech = calcTechPattern(p);
            pattern = tech * 0.80 + ripple * 0.25;
        } else if (DebugMode == 2) {
            // Mode 2: Clean Wave
            pattern = ripple * 0.70 + 0.30;
        } else {
            // Mode 3: Topography
            float topo = calcTopo(p);
            pattern = topo * 0.80 + ripple * 0.20;
        }

        // Base wave color
        vec4 col = mix(innerColor, midColor, norm);
        col = mix(col, outerColor, frontGlow);

        // Vibrant pattern & front edge illumination
        vec3 neonRgb = col.rgb;
        neonRgb = mix(neonRgb, scanlineColor.rgb, clamp(pattern, 0.0, 1.0) * 0.80);
        neonRgb = mix(neonRgb, vec3(1.0), frontLine * 0.90);

        // Solid, clearly visible alpha that stands out on any terrain/daylight
        float alphaVal = (0.35 + pattern * 0.55 + frontLine * 0.45) * bodyFade;
        float totalAlpha = clamp(col.a * alphaVal, 0.0, 1.0);

        OutColor = vec4(neonRgb, totalAlpha);
    } else {
        OutColor = vec4(0.0);
    }
}
