#version 150
// Lueur des feux sur le décor (fire_plate) : couleurs de <calque>_fire.png, alpha de <calque>_night.png,
// vues par la caméra du calque (View.sample), ajoutées à l'image avec un gain (feu * vacillement * 1,8 ou 0,9).

in vec2 vRef;
out vec4 fragColor;

uniform sampler2D uFire;
uniform sampler2D uMask;   // plaque de nuit (prémultipliée, seul l'alpha sert)
uniform vec3 uView;        // zoom, décalage x, décalage y (pixels de la plaque)
uniform vec2 uPlate;
uniform vec2 uOut;
uniform float uGain;

void main() {
    vec2 p = (vRef - 0.5 * uOut) / uView.x + 0.5 * uPlate + uView.yz;
    vec2 uv = p / uPlate;
    vec3 fire = texture(uFire, uv).rgb * texture(uMask, uv).a;
    fragColor = vec4(fire * uGain, 0.0);
}
