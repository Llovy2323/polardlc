#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec3 multiplier;
uniform float mixFactor;
in vec2 TexCoord;
out vec4 OutColor;
void main() {
    vec4 source = texture(Sampler0, TexCoord);
    if (source.a < 0.01) discard;
    vec3 glass = texture(Sampler1, TexCoord).rgb * multiplier;
    OutColor = vec4(mix(glass, source.rgb, mixFactor), source.a);
}
