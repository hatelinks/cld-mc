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
uniform float GameTime;
uniform vec2 WorldXZ;
uniform float Waves;
uniform float Glint;
uniform float Reflect;
uniform float CloudShadows;
uniform vec2 CloudOffset;
uniform float Mist;
uniform float MistTop;
uniform vec3 LodTint;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

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
        if (abs(Pass - 1.0) > 0.5 && (m & 2) != 0) discard;
    } else if (length(relPos) < ClipRadius) discard;
    vec3 c = vertexColor.rgb;
    float alpha = vertexColor.a;
    // мировые координаты (по модулю, чтобы хватало точности): для волн и теней облаков
    vec2 wp = relPos.xz + WorldXZ;
    if (Pass > 1.5) {
        // вода (Pass 2): рябь, отражение неба под острым углом, блик солнца
        vec3 n = vec3(0.0, 1.0, 0.0);
        if (Waves > 0.0) {
            float t = GameTime;
            n.x = 0.06 * (vnoise(wp / 9.0 + vec2(t * 0.4, t * 0.25)) - 0.5) + 0.04 * (vnoise(wp / 3.5 - vec2(t * 0.7, 0.0)) - 0.5);
            n.z = 0.06 * (vnoise(wp / 9.0 + vec2(13.0 - t * 0.3, t * 0.35)) - 0.5) + 0.04 * (vnoise(wp / 3.5 + vec2(0.0, t * 0.6)) - 0.5);
            n = normalize(n);
        }
        vec3 v = normalize(relPos);
        float fres = pow(1.0 - max(dot(-v, n), 0.0), 4.0) * Reflect;
        c = mix(c, HazeColor, fres);
        alpha = mix(alpha, 1.0, fres);
        float spec = pow(max(dot(reflect(v, n), SunDir), 0.0), 180.0) * Glint;
        c += vec3(1.0, 0.95, 0.82) * spec * 1.5;
        alpha = max(alpha, min(1.0, spec));
    } else if (SunShade > 0.0) {
        vec3 n = normalize(cross(dFdx(relPos), dFdy(relPos)));
        if (dot(n, relPos) > 0.0) n = -n;
        float lit = 0.55 + 0.6 * max(dot(n, SunDir), 0.0);
        c *= mix(1.0, lit, SunShade);
    }
    c *= ColorModulator.rgb * Tint;
    float grey = dot(c, vec3(0.299, 0.587, 0.114));
    c = max(mix(vec3(grey), c, Saturation), 0.0);
    // тени облаков: крупные пятна плывут по ветру
    if (CloudShadows > 0.0) {
        vec2 cp = (wp + CloudOffset) / 420.0;
        float cl = vnoise(cp) * 0.65 + vnoise(cp * 2.3) * 0.35;
        c *= 1.0 - CloudShadows * 0.45 * smoothstep(0.52, 0.72, cl);
    }
    // туман в низинах: ниже MistTop (от камеры) и не у самого игрока
    if (Mist > 0.0) {
        float low = 1.0 - smoothstep(MistTop - 28.0, MistTop, relPos.y);
        float mk = Mist * low * smoothstep(48.0, 700.0, d) * (0.6 + 0.4 * vnoise(wp / 260.0 + vec2(GameTime * 0.02, 0.0)));
        c = mix(c, mix(HazeColor, vec3(0.92), 0.5), mk * 0.8);
    }
    // туман у края плюс дымка с расстоянием (воздушная перспектива: дальние горы светлее и мягче) цвета неба
    float haze = Haze * smoothstep(ClipRadius, FogEnd, d);
    c = mix(c, HazeColor, haze);
    // отладка: тайлы раскрашены по уровню детальности
    if (LodTint.r >= 0.0) c = mix(c, LodTint, 0.45);
    float f = smoothstep(FogStart, FogEnd, d) * FogColor.a;
    fragColor = vec4(mix(c, FogColor.rgb, f), alpha);
}
