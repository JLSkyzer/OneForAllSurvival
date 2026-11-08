#version 150
// Lueurs (draw_halos, lueur du temple) : halo.png ajouté en couleur (blit_add), ou carré plein recouvrant
// (foyer des feux de camp). Sortie prémultipliée : alpha 0 = ajout pur.

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uTex;
uniform bool uSolid;       // vrai : carré plein (alpha 1 partout)
uniform vec3 uColor;
uniform float uAlpha;
uniform float uAlphaScale; // 0 : s'ajoute (halo), 1 : recouvre (foyer)

void main() {
    float a = (uSolid ? 1.0 : texture(uTex, vUv).a) * uAlpha;
    fragColor = vec4(uColor * a, a * uAlphaScale);
}
