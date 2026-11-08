#version 150
// Flash des éclairs : frame = frame * gain + couleur. Fondu (ONE, SRC_ALPHA) sur le tampon flottant de la scène,
// où ni la source ni les facteurs ne sont écrêtés : l'alpha porte le gain (1 + 2,2 * flash, plus grand que 1).

out vec4 fragColor;

uniform vec3 uAdd;
uniform float uGain;

void main() {
    fragColor = vec4(uAdd, uGain);
}
