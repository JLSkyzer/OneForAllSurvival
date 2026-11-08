#version 150
// Calque vu par la caméra (View.sample de compose.py) : ciel, étoiles, plaques far/mid/fg, rayons de lune.
// Deux textures mêlées par uMix (lerp3 entre deux ambiances). Sortie prémultipliée :
// fondu = (ONE, ONE_MINUS_SRC_ALPHA), donc alpha 0 = ajout pur.

in vec2 vRef;
out vec4 fragColor;

uniform sampler2D uTexA;
uniform sampler2D uTexB;
uniform float uMix;
uniform vec3 uView;        // zoom, décalage x, décalage y (pixels de la plaque)
uniform vec3 uSrc;         // largeur, hauteur de la texture, échelle (2 pour les demi-plaques)
uniform vec2 uPlate;       // taille de la plaque
uniform vec2 uOut;         // taille de l'image de référence
uniform vec3 uTint;        // multiplie la couleur (gain du premier plan, couleur des rayons)
uniform bool uFromAlpha;   // vrai : la couleur est uTint * alpha (rayons de lune)
uniform float uAlphaScale; // 1 : recouvre (plaques), 0 : s'ajoute (étoiles, rayons)

void main() {
    vec2 p = (vRef - 0.5 * uOut) / uView.x + 0.5 * uPlate + uView.yz;
    vec2 uv = p / uSrc.z / uSrc.xy;
    vec4 s = mix(texture(uTexA, uv), texture(uTexB, uv), uMix);
    vec3 rgb = uFromAlpha ? s.a * uTint : s.rgb * uTint;
    fragColor = vec4(rgb, s.a * uAlphaScale);
}
