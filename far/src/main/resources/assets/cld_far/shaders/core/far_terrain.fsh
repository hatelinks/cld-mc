#version 150

in vec4 vertexColor;
in vec3 relPos;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float ClipRadius;
uniform float ScopeMode;
uniform vec2 ScopeDir;
uniform float ScopeCos;
uniform float ScopeFar;

out vec4 fragColor;

void main() {
    // внутри прорисовки игры рисуют обычные чанки; расстояние с высотой: высоко над землёй игра чанки под
    // игроком не рисует, а дальний рельеф — рисует (по настоящей высоте, грубо)
    float d = length(relPos.xz);
    if (length(relPos) < ClipRadius) discard;
    vec2 dir = relPos.xz / max(d, 0.001);
    bool cone = d < ScopeFar && dot(dir, ScopeDir) > ScopeCos;
    // подзорная труба: 1 — гладкий рельеф уступает блочному в конусе, 2 — блочный рисуется только в конусе
    if (ScopeMode > 0.5 && ScopeMode < 1.5 && cone) discard;
    if (ScopeMode > 1.5 && !cone) discard;
    vec3 c = vertexColor.rgb * ColorModulator.rgb;
    // туман у края плюс лёгкая дымка с расстоянием (воздушная перспектива: дальние горы светлее и мягче)
    float haze = 0.3 * smoothstep(ClipRadius, FogEnd, d);
    float f = (1.0 - (1.0 - smoothstep(FogStart, FogEnd, d)) * (1.0 - haze)) * FogColor.a;
    fragColor = vec4(mix(c, FogColor.rgb, f), vertexColor.a);
}
