#version 150
// Plaque du décor vue par la caméra (comme layer.frag), feuillage ondulant au vent : là où le masque des
// feuilles est blanc, la lecture de la plaque est décalée de quelques pixels par un bruit lent. Hors du
// masque, le décalage est nul et l'image est exactement celle de layer.frag.

in vec2 vRef;
out vec4 fragColor;

uniform sampler2D uTexA;
uniform sampler2D uTexB;
uniform sampler2D uMask;   // foliage_<plan>.png, même géométrie que la plaque
uniform float uMix;
uniform vec3 uView;        // zoom, décalage x, décalage y (pixels de la plaque)
uniform vec2 uPlate;       // taille de la plaque
uniform vec2 uOut;         // taille de l'image de référence
uniform vec3 uTint;
uniform float uTime;       // temps réel : le vent ne s'arrête pas sur l'aube tenue
uniform float uAmplitude;  // déplacement maximal, en pixels de la plaque

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// Bruit de valeur lissé, de -1 à 1.
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y) * 2.0 - 1.0;
}

void main() {
    vec2 p = (vRef - 0.5 * uOut) / uView.x + 0.5 * uPlate + uView.yz;
    float m = texture(uMask, p / uPlate).r;
    if (m > 0.0) {
        // Rafales : un front lent qui traverse l'île de gauche à droite, et des feuilles qui frémissent.
        float gust = 0.6 + 0.4 * sin(uTime * 0.7 - p.x * 0.004);
        vec2 q = p / 46.0 + vec2(uTime * 0.45, uTime * 0.2);
        vec2 d = vec2(noise(q), 0.45 * noise(q + vec2(17.3, 5.1)));
        p -= d * (uAmplitude * gust * m);
    }
    vec2 uv = p / uPlate;
    vec4 s = mix(texture(uTexA, uv), texture(uTexB, uv), uMix);
    fragColor = vec4(s.rgb * uTint, s.a);
}
