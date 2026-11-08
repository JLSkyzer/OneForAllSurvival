#version 150
// Dernière passe : l'image de la scène (tampon flottant) vers la fenêtre. Ouverture depuis le noir,
// grain fin contre les aplats en escalier (le grain de compose.py), puis écrêtage 0..1.

out vec4 fragColor;

uniform sampler2D uScene;
uniform float uFade;   // sstep(0, 1.2, t)
uniform float uSeed;   // change à chaque image, comme le décalage du grain de compose.py

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec3 c = texelFetch(uScene, ivec2(gl_FragCoord.xy), 0).rgb * uFade;
    // Bruit triangulaire d'écart-type proche de 1.3 / 255, au lieu du bruit gaussien de compose.py.
    vec2 p = gl_FragCoord.xy + uSeed;
    float n = hash(p) + hash(p + 17.31) - 1.0;
    c += n * (3.2 / 255.0);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
