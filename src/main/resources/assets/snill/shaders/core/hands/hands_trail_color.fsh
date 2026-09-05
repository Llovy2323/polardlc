#version 150
uniform sampler2D Sampler0;
uniform vec3 color;
uniform vec3 color2;
uniform float exposure;
uniform int autoColor;
uniform float saturation;
in vec2 TexCoord;
out vec4 OutColor;
void main(){
    vec4 b=texture(Sampler0,TexCoord); float a=clamp(b.a*exposure,0.0,1.0); vec3 c;
    if(autoColor==1){c=b.rgb/max(b.a,0.001);float m=max(c.r,max(c.g,c.b));if(m>0.001)c/=m;float l=dot(c,vec3(0.299,0.587,0.114));c=clamp(mix(vec3(l),c,saturation),0.0,1.0);}
    else c=mix(color,color2,TexCoord.y); OutColor=vec4(c*a,a);
}
