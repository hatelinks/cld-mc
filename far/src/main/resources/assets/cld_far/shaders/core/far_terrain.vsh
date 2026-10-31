#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform float Curv;
uniform float Pass;

out vec4 vertexColor;
out vec3 relPos;

void main() {
    // ChunkOffset = угол тайла относительно камеры
    vec3 pos = Position + ChunkOffset;
    // кривизна земли: вдали поверхность уходит вниз
    pos.y -= Curv * dot(pos.xz, pos.xz);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    // Pass 3: грубый рельеф под блочными тайлами на дальней плоскости, он виден только там, где блочный ничего не нарисовал
    if (Pass > 2.5) gl_Position.z = gl_Position.w;
    vertexColor = Color;
    relPos = pos;
}
