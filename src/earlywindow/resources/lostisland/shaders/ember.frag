#version 150
// Braise (draw_embers) : carré de couleur ajouté à l'image (frame += couleur * alpha).

out vec4 fragColor;

uniform vec3 uColor;
uniform float uAlpha;

void main() {
    fragColor = vec4(uColor * uAlpha, 0.0);
}
