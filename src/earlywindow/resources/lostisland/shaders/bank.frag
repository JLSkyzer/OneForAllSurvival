#version 150
// Banc de nuages (draw_bank de compose.py) : fixe à l'écran, défile horizontalement, étiré au plus proche
// sur les 56 % du haut de l'image.

in vec2 vRef;
out vec4 fragColor;

uniform sampler2D uTex;
uniform int uShift;     // int(t * vitesse) % largeur
uniform int uRows;      // int(OUT_H * 0.56)
uniform vec3 uColor;    // lerp3 de la palette
uniform float uOpacity;

void main() {
    int x = int(floor(vRef.x));
    int y = int(floor(vRef.y));
    if (y < 0 || y >= uRows) {
        discard;
    }
    ivec2 size = textureSize(uTex, 0);
    int col = x + uShift;
    col -= size.x * int(floor(float(col) / float(size.x)));
    // np.linspace(0, h - 1, rows).astype(int32)
    int row = int(float(y) * float(size.y - 1) / float(uRows - 1));
    vec4 s = texelFetch(uTex, ivec2(col, row), 0);
    float a = s.a * uOpacity * clamp(1.2 - float(y) / float(uRows - 1) * 0.6, 0.0, 1.0);
    vec3 shade = uColor * (0.75 + 0.5 * s.r);
    fragColor = vec4(shade * a, a);
}
