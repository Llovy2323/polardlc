#version 150
uniform sampler2D Sampler0;
uniform vec2 offset;
uniform vec2 texSize;
uniform float fade;
uniform float time;
uniform float dt;
uniform float turb;
uniform float flickAmp;
in vec2 TexCoord;
out vec4 OutColor;
float wob(float y,float t){return sin(y*9.0+t*4.3)*0.6+sin(y*17.0-t*2.1)*0.3+sin(y*4.0+t*1.3)*0.4+sin(y*28.0+t*5.7)*0.15;}
vec4 blurSample(vec2 p){
    vec2 px=1.0/max(texSize,vec2(1.0)); vec4 c=texture(Sampler0,p)*0.36;
    c+=texture(Sampler0,p+vec2(px.x,0))*0.16; c+=texture(Sampler0,p-vec2(px.x,0))*0.16;
    c+=texture(Sampler0,p+vec2(0,px.y))*0.16; c+=texture(Sampler0,p-vec2(0,px.y))*0.16; return c;
}
void main(){
    float dx=(wob(TexCoord.y,time)-wob(TexCoord.y,time-dt))*turb;
    vec2 disp=vec2(offset.x+dx,offset.y*(0.7+TexCoord.y*0.8)); vec4 c=blurSample(TexCoord-disp);
    float flick=1.0-flickAmp+flickAmp*(0.5+0.5*sin(time*14.0)+0.25*sin(time*9.3));
    float a=max(0.0,c.a*(0.985*flick)-fade); float scale=c.a>0.001?a/c.a:0.0; OutColor=vec4(c.rgb*scale,a);
}
