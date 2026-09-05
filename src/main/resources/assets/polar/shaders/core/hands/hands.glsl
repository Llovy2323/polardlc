#version 120

uniform sampler2D originalTexture;
uniform sampler2D blurredTexture;
uniform vec4 multiplier;
uniform vec2 viewOffset;
uniform vec2 resolution;

void main() {
    vec2 pos = gl_FragCoord.xy + viewOffset;
    vec4 srcColor = texture2D(originalTexture, gl_TexCoord[0].xy);
    vec2 blurredPos = pos / resolution;
    pos.y = resolution.y - pos.y;
    gl_FragColor = texture2D(blurredTexture, blurredPos) * multiplier * srcColor.a;
}
