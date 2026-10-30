#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform float Curv;

out vec4 vertexColor;
out vec3 relPos;

void main() {
    // ChunkOffset = угол тайла относительно камеры
    vec3 pos = Position + ChunkOffset;
    // кривизна земли: вдали поверхность уходит вниз
    pos.y -= Curv * dot(pos.xz, pos.xz);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexColor = Color;
    relPos = pos;
}
