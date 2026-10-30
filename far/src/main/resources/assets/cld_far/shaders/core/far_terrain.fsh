#version 150

in vec4 vertexColor;
in vec3 relPos;

uniform sampler2D Mask;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float ClipRadius;
uniform vec2 MaskOrigin;
uniform float MaskSize;
uniform float VertRange;
uniform float Pass;
uniform vec3 SunDir;
uniform float SunShade;
uniform float Saturation;
uniform float Haze;
uniform vec3 HazeColor;
uniform vec3 Tint;

out vec4 fragColor;

void main() {
    float d = length(relPos.xz);
    if (MaskSize > 0.5) {
        // маска по чанкам: где игра уже нарисовала чанк (и по высоте он в её прорисовке), дальний рельеф не нужен;
        // где готов блочный тайл, грубый рельеф под ним не рисуется (Pass 0, грубый, 1, блочный)
        ivec2 cell = ivec2(floor((relPos.xz - MaskOrigin) / 16.0));
        int m = 0;
        if (cell.x >= 0 && cell.y >= 0 && cell.x < int(MaskSize) && cell.y < int(MaskSize)) m = int(texelFetch(Mask, cell, 0).r * 255.0 + 0.5);
        if ((m & 1) != 0 && abs(relPos.y) < VertRange) discard;
        if (Pass < 0.5 && (m & 2) != 0) discard;
    } else if (length(relPos) < ClipRadius) discard;
    vec3 c = vertexColor.rgb;
    // солнце: нормаль грани по производным позиции, повёрнута к камере
    if (SunShade > 0.0) {
        vec3 n = normalize(cross(dFdx(relPos), dFdy(relPos)));
        if (dot(n, relPos) > 0.0) n = -n;
        float lit = 0.55 + 0.6 * max(dot(n, SunDir), 0.0);
        c *= mix(1.0, lit, SunShade);
    }
    c *= ColorModulator.rgb * Tint;
    float grey = dot(c, vec3(0.299, 0.587, 0.114));
    c = max(mix(vec3(grey), c, Saturation), 0.0);
    // туман у края плюс дымка с расстоянием (воздушная перспектива: дальние горы светлее и мягче) цвета неба
    float haze = Haze * smoothstep(ClipRadius, FogEnd, d);
    c = mix(c, HazeColor, haze);
    float f = smoothstep(FogStart, FogEnd, d) * FogColor.a;
    fragColor = vec4(mix(c, FogColor.rgb, f), vertexColor.a);
}
