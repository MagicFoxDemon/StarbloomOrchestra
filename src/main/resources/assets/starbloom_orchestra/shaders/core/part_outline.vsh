#version 150

// 描边的全屏四边形（见 PartOutline）：Position 是 (0, 0) 到 (1, 1)，texCoord 从屏幕左下角到右上角。
// 四边形放在远平面上（深度 1），配合 LEQUAL 深度测试：这时主画面的深度里只剩手，手挡在前面的像素不画。

in vec3 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 1.0, 1.0);
    texCoord = Position.xy;
}
