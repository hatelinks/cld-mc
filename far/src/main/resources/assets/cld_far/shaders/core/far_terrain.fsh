#version 150

in vec4 vertexColor;
in vec3 relPos;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float ClipRadius;

out vec4 fragColor;

void main() {
    // внутри прорисовки игры рисуют обычные чанки
    float d = length(relPos.xz);
    if (d < ClipRadius) discard;
    vec3 c = vertexColor.rgb * ColorModulator.rgb;
    // туман у края плюс лёгкая дымка с расстоянием (воздушная перспектива: дальние горы светлее и мягче)
    float haze = 0.3 * smoothstep(ClipRadius, FogEnd, d);
    float f = (1.0 - (1.0 - smoothstep(FogStart, FogEnd, d)) * (1.0 - haze)) * FogColor.a;
    fragColor = vec4(mix(c, FogColor.rgb, f), vertexColor.a);
}
