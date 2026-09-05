#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 texelSize;
uniform float width;
uniform float alpha;
uniform float rainbowTime;
uniform float rainbowSpeed;
uniform float rainbowScale;
uniform float saturation;
uniform int colorMode;
uniform vec3 solidColor;
in vec2 TexCoord;
out vec4 OutColor;
vec3 hue2rgb(float h) {
    vec3 p = abs(fract(vec3(h) + vec3(0.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0);
    return clamp(p - 1.0, 0.0, 1.0);
}
void main() {
    if (texture(Sampler0, TexCoord).a > 0.01) discard;
    float maxA = 0.0;
    for (int x=-2; x<=2; x++) for (int y=-2; y<=2; y++) {
        if (x != 0 || y != 0) maxA = max(maxA, texture(Sampler0, TexCoord + vec2(x,y)*texelSize*width).a);
    }
    if (maxA < 0.01) discard;
    vec3 c = solidColor;
    if (colorMode == 1) c = hue2rgb(fract((1.0-TexCoord.y)*rainbowScale + rainbowTime*rainbowSpeed));
    else if (colorMode == 2) {
        vec4 b = texture(Sampler1, TexCoord);
        c = b.rgb / max(b.a, 0.001);
        float m = max(c.r,max(c.g,c.b)); if (m > 0.001) c /= m;
        float l = dot(c,vec3(0.299,0.587,0.114)); c = clamp(mix(vec3(l),c,saturation),0.0,1.0);
    }
    OutColor = vec4(c, maxA*alpha);
}
