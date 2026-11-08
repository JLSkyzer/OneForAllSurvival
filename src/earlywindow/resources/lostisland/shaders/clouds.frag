#version 150
// Nuages voxel vanilla (draw_clouds de compose.py) : la texture des nuages du jeu projetée sur quatre
// plafonds (118, 122, 126, 130 blocs) vus par la caméra de la scène. Dessous sombre, flancs plus clairs.
// Le calque est calculé sur la grille de la demi-plaque puis filtré (bilinéaire) par la vue du ciel,
// comme dans compose.py.

in vec2 vRef;
out vec4 fragColor;

uniform sampler2D uClouds;  // 256x256, alpha > 0.5 = nuage
uniform vec3 uFwd;
uniform vec3 uRight;
uniform vec3 uUp;
uniform float uFocal;
uniform vec2 uCam;          // taille de la demi-plaque (caméra des nuages)
uniform int uRows;          // lignes où le ciel monte (cloud_rows) ; en dessous, rien
uniform float uDrift;       // t * 1.3
uniform vec3 uUnder;
uniform vec3 uWall;
uniform float uOpacity;     // night_opacity + 0.3 * storm
uniform vec3 uView;
uniform vec2 uPlate;
uniform vec2 uOut;

bool cloud(vec3 d, float up, float level) {
    float t = level / up;
    // astype(int32) tronque vers zéro, puis % 256 de numpy (toujours positif) : & 255 en complément à deux.
    int iu = int((d.x * t + 40.0 + uDrift) / 12.0) & 255;
    int iv = int((d.z * t + 10.0 - uDrift * 0.18) / 12.0) & 255;
    return texelFetch(uClouds, ivec2(iu, iv), 0).a > 0.5;
}

vec4 cell(ivec2 p) {
    if (p.y >= uRows) {
        return vec4(0.0);
    }
    vec2 c = vec2(p) + 0.5;
    vec3 d = normalize(uFwd + (c.x - 0.5 * uCam.x) / uFocal * uRight + (0.5 * uCam.y - c.y) / uFocal * uUp);
    float up = max(d.y, 1e-3);
    bool bottom = cloud(d, up, 118.0);
    bool side = !bottom && (cloud(d, up, 122.0) || cloud(d, up, 126.0) || cloud(d, up, 130.0));
    if (!bottom && !side) {
        return vec4(0.0);
    }
    float a = clamp((up - 0.012) / 0.10, 0.0, 1.0) * uOpacity;
    return vec4((bottom ? uUnder : uWall) * a, a);
}

void main() {
    vec2 p = (vRef - 0.5 * uOut) / uView.x + 0.5 * uPlate + uView.yz;
    vec2 q = clamp(p / 2.0 - 0.5, vec2(0.0), uCam - 1.001);
    ivec2 i = ivec2(q);
    vec2 f = q - vec2(i);
    vec4 top = mix(cell(i), cell(i + ivec2(1, 0)), f.x);
    vec4 low = mix(cell(i + ivec2(0, 1)), cell(i + ivec2(1, 1)), f.x);
    fragColor = mix(top, low, f.y);
}
