#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float alpha;
in vec2 TexCoord;
out vec4 OutColor;
void main() {
    float mask = texture(Sampler0, TexCoord).a;
    vec4 source = texture(Sampler1, TexCoord);
    OutColor = vec4(source.rgb, mask * alpha);
}
