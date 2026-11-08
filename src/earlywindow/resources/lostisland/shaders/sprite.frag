#version 150
// Image posée à un rectangle de l'image de référence : blit_over (lune) et blit_add (soleil, halos).
// Sprite non prémultiplié ; sortie prémultipliée, comme layer.frag.

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uTex;
uniform vec3 uTint;
uniform float uAlpha;
uniform bool uFromAlpha;   // vrai : blit_add, couleur uTint * alpha du sprite
uniform float uAlphaScale; // 1 : blit_over, 0 : blit_add

void main() {
    vec4 s = texture(uTex, vUv);
    float a = s.a * uAlpha;
    vec3 rgb = uFromAlpha ? uTint : s.rgb * uTint;
    fragColor = vec4(rgb * a, a * uAlphaScale);
}
