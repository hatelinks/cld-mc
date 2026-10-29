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
    float f = smoothstep(FogStart, FogEnd, d) * FogColor.a;
    fragColor = vec4(mix(c, FogColor.rgb, f), 1.0);
}
