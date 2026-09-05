#version 150
uniform sampler2D Sampler0;
uniform vec3 color;
uniform float fillAlpha;
uniform float shadingStrength;
uniform int keepShading;
uniform int rainbow;
uniform float rainbowTime;
uniform float rainbowSpeed;
uniform float rainbowScale;
in vec2 TexCoord;
out vec4 OutColor;
vec3 hue2rgb(float h) {
    vec3 p = abs(fract(vec3(h) + vec3(0.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0);
    return clamp(p - 1.0, 0.0, 1.0);
}
void main() {
    vec4 source = texture(Sampler0, TexCoord);
    if (source.a < 0.01) discard;
    vec3 fill = rainbow == 1
        ? hue2rgb(fract((1.0 - TexCoord.y) * rainbowScale + rainbowTime * rainbowSpeed))
        : color;
    if (keepShading == 1) {
        fill *= mix(1.0, dot(source.rgb, vec3(0.299, 0.587, 0.114)), shadingStrength);
    }
    OutColor = vec4(mix(source.rgb, fill, fillAlpha), source.a);
}
