#version 150
// Passe multiplicative (fondu ZERO, SRC_COLOR) : assombrissement de l'orage, puis étalonnage de compose.py
// (vignette, et assombrissement du bas de l'image sous la barre).

in vec2 vRef;
out vec4 fragColor;

uniform float uFactor;   // facteur constant (1 - 0.34 * storm, ou 1)
uniform float uVignette; // 1 : applique A.vignette
uniform float uBottom;   // k de sstep(BAR_IN, BAR_IN + 0.7, t) : mélange vers A.bottom
uniform vec2 uOut;

void main() {
    float k = uFactor;
    if (uVignette > 0.0 || uBottom > 0.0) {
        // compose.py calcule ces masques sur la grille des pixels (indices entiers).
        vec2 g = vRef - 0.5;
        float r = length((g - 0.5 * uOut) / (0.5 * uOut));
        float vignette = 1.0 - 0.42 * pow(clamp(r - 0.35, 0.0, 1.0), 1.6);
        float bottom = 1.0 - 0.72 * pow(clamp((g.y - uOut.y * 0.78) / (uOut.y * 0.22), 0.0, 1.0), 1.3);
        k *= mix(1.0, vignette, uVignette) * (1.0 - uBottom + uBottom * bottom);
    }
    fragColor = vec4(k, k, k, 1.0);
}
