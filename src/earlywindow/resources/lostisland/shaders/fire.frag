#version 150
// Flamme (draw_fires) : une image 16x16 de la bande fire_*_strip.png, agrandie en bilinéaire sans déborder sur
// les images voisines de la bande (resized de compose.py), puis blit_over (uAlpha) et blit_add de uAdd
// (uAddAlpha) en un seul tracé : sortie = rgb * a + uAdd * alpha * uAddAlpha, alpha a.

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uStrip;
uniform int uTile;      // côté d'une image de la bande
uniform int uIndex;     // image courante
uniform float uAlpha;
uniform vec3 uAdd;
uniform float uAddAlpha;

vec4 texel(int x, int y) {
    return texelFetch(uStrip, ivec2(x, uIndex * uTile + y), 0);
}

void main() {
    float last = float(uTile - 1);
    vec2 src = clamp(vUv * float(uTile) - 0.5, vec2(0.0), vec2(last));
    ivec2 i0 = ivec2(floor(src));
    ivec2 i1 = min(i0 + 1, ivec2(uTile - 1));
    vec2 f = src - vec2(i0);
    vec4 top = mix(texel(i0.x, i0.y), texel(i1.x, i0.y), f.x);
    vec4 low = mix(texel(i0.x, i1.y), texel(i1.x, i1.y), f.x);
    vec4 s = mix(top, low, f.y);
    float a = s.a * uAlpha;
    fragColor = vec4(s.rgb * a + uAdd * s.a * uAddAlpha, a);
}
