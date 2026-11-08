#version 150
// Mer animée (draw_sea de compose.py). Les 48 images portent trois données : R = ondulation,
// G = écume, B = scintillement. Couleurs selon l'ambiance, reflets de lune et de soleil, masque de la mer.

in vec2 vRef;
out vec4 fragColor;

uniform sampler2DArray uFrames;
uniform float uLayer0;
uniform float uLayer1;
uniform float uFrameMix;
uniform sampler2D uDepth;
uniform sampler2D uShallow;
uniform sampler2D uMask;
uniform sampler2D uGlintMoon;
uniform sampler2D uGlintSun;
uniform vec3 uFar;
uniform vec3 uNear;
uniform vec3 uShallowColor;
uniform vec3 uFoam;
uniform float uMoonPower;
uniform float uSunPower;
uniform float uHorizon;    // horizon_row : au-dessus, le masque est vide
uniform vec3 uView;
uniform vec2 uPlate;
uniform vec2 uOut;
uniform vec2 uSize;        // taille de la demi-plaque de mer

const vec3 MOON_GLINT = vec3(199.0, 214.0, 255.0) / 255.0; // #c7d6ff
const vec3 SUN_GLINT = vec3(255.0, 204.0, 122.0) / 255.0;  // #ffcc7a

void main() {
    vec2 p = (vRef - 0.5 * uOut) / uView.x + 0.5 * uPlate + uView.yz;
    vec2 q = p / 2.0;
    if (q.y < uHorizon - 1.0) {
        discard;
    }
    vec2 uv = q / uSize;
    vec3 data = mix(texture(uFrames, vec3(uv, uLayer0)).rgb, texture(uFrames, vec3(uv, uLayer1)).rgb, uFrameMix);
    float ripple = data.r;
    float foam = data.g;
    float spark = data.b;
    float depth = 1.0 - texture(uDepth, uv).r;
    vec3 base = uFar * (1.0 - depth) + uNear * depth;
    float shallow = texture(uShallow, uv).r * 0.85;
    base = base * (1.0 - shallow) + uShallowColor * shallow;
    vec3 color = base * (0.72 + 0.56 * ripple);
    color = color * (1.0 - foam * 0.86) + uFoam * foam * 0.86;
    color += texture(uGlintMoon, uv).r * spark * MOON_GLINT * uMoonPower;
    color += texture(uGlintSun, uv).r * spark * SUN_GLINT * uSunPower;
    color = clamp(color, 0.0, 1.2);
    float a = texture(uMask, uv).r;
    fragColor = vec4(color * a, a);
}
