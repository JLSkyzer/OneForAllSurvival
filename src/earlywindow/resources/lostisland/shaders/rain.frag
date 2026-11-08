#version 150
// Pluie (draw_rain) : deux couches de rain.png fixes à l'écran, qui défilent, lues au texel près comme les
// index entiers de numpy, et ajoutées à l'image (frame += masque * couleur).

in vec2 vRef;
out vec4 fragColor;

uniform sampler2D uTex;
uniform ivec2 uShift0;   // (ox, oy) de la couche rapide
uniform ivec2 uShift1;   // (ox, oy) de la couche lente
uniform float uAlpha0;   // alpha * storm * (0.75 + flash)
uniform float uAlpha1;
uniform vec3 uColor;     // #a9bce0 * 0.5

float layer(ivec2 p, ivec2 shift, ivec2 size) {
    // xs = (x + ox) % 1024, ys = (y - oy) % 1024 : reste positif, comme numpy. Le % de GLSL n'est défini que
    // pour des opérandes positifs : on décale de quelques tours avant (0 <= shift < size, p >= -4 * size).
    int x = (p.x + shift.x + 4 * size.x) % size.x;
    int y = (p.y - shift.y + 4 * size.y) % size.y;
    return texelFetch(uTex, ivec2(x, y), 0).a;
}

void main() {
    ivec2 size = textureSize(uTex, 0);
    ivec2 p = ivec2(floor(vRef));
    float a = layer(p, uShift0, size) * uAlpha0 + layer(p, uShift1, size) * uAlpha1;
    fragColor = vec4(uColor * a, 0.0);
}
