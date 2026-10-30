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
    vec3 c = vertexColor.rgb * ColorModulator.rgb;
    // туман у края плюс лёгкая дымка с расстоянием (воздушная перспектива: дальние горы светлее и мягче)
    float haze = 0.3 * smoothstep(ClipRadius, FogEnd, d);
    float f = (1.0 - (1.0 - smoothstep(FogStart, FogEnd, d)) * (1.0 - haze)) * FogColor.a;
    fragColor = vec4(mix(c, FogColor.rgb, f), vertexColor.a);
}
