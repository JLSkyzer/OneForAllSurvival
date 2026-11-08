#version 150
// Carré unité placé dans l'image de référence (1920x1080, y vers le bas), puis recadré sur la fenêtre.

in vec2 aPos;

uniform vec4 uRect;       // x, y, largeur, hauteur en pixels de l'image de référence
uniform vec2 uRefCenter;  // centre de l'image de référence
uniform vec2 uRefToNdc;   // 2 * échelle / taille de la fenêtre (recadrage centré, sans bandes)

out vec2 vUv;   // 0..1 sur le carré
out vec2 vRef;  // position en pixels de l'image de référence

void main() {
    vUv = aPos;
    vRef = uRect.xy + aPos * uRect.zw;
    vec2 ndc = (vRef - uRefCenter) * uRefToNdc;
    gl_Position = vec4(ndc.x, -ndc.y, 0.0, 1.0);
}
