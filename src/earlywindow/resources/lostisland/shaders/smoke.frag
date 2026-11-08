#version 150
// Bouffée de fumée (draw_smoke) : blit_over d'une image smoke_*.png teintée. Sprite non prémultiplié,
// sortie prémultipliée.

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uTex;
uniform vec3 uTint;
uniform float uAlpha;

void main() {
    vec4 s = texture(uTex, vUv);
    float a = s.a * uAlpha;
    fragColor = vec4(s.rgb * uTint * a, a);
}
