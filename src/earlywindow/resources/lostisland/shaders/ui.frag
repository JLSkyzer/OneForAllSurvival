#version 150
// Logo, barre et texte (draw_logo, draw_ui, Font.draw de compose.py). Sortie prémultipliée, fondu
// (ONE, ONE_MINUS_SRC_ALPHA) : uAlphaScale 1 recouvre (blit_over, blit_mask), 0 s'ajoute (blit_add).
//   uMode 0 : image (lettres, icône de l'avion, lueur du logo, halo de la tête de barre) ;
//   uMode 1 : rectangle plein ou en dégradé horizontal (rail pointillé, traînée dorée et son liseré) ;
//   uMode 2 : glyphe de la police, lu au texel près dans l'atlas (uCell = coin et taille dans l'atlas) ;
//   uMode 3 : reflet qui balaie le logo (bande gaussienne × alpha de face_mask.png).

in vec2 vUv;
in vec2 vRef;
out vec4 fragColor;

uniform int uMode;
uniform sampler2D uTex;
uniform vec3 uColor;
uniform vec3 uColor2;      // couleur au bout du dégradé (mode 1)
uniform vec2 uAlphaGrad;   // alpha au début et au bout du dégradé (mode 1)
uniform vec2 uGrad;        // première colonne et nombre de colonnes du dégradé (np.linspace(0, 1, n))
uniform float uAlpha;
uniform float uAlphaScale;
uniform bool uFromAlpha;   // mode 0 : couleur uColor × alpha de l'image (blit_add)
uniform vec4 uCell;        // mode 2 : coin et taille du glyphe ; mode 3 : taille du masque en zw
uniform float uSheen;      // mode 3 : position de la bande, -200 + (w + 400) * u

void main() {
    if (uMode == 0) {
        vec4 s = texture(uTex, vUv);
        float a = s.a * uAlpha;
        vec3 rgb = uFromAlpha ? uColor : s.rgb * uColor;
        fragColor = vec4(rgb * a, a * uAlphaScale);
    } else if (uMode == 1) {
        // Indice entier de la colonne, comme les tranches de tableau de numpy.
        float i = floor(vRef.x) - uGrad.x;
        float k = uGrad.y > 1.0 ? clamp(i / (uGrad.y - 1.0), 0.0, 1.0) : 0.0;
        float a = uAlpha * mix(uAlphaGrad.x, uAlphaGrad.y, k);
        fragColor = vec4(mix(uColor, uColor2, k) * a, a * uAlphaScale);
    } else if (uMode == 2) {
        ivec2 texel = ivec2(uCell.xy + min(floor(vUv * uCell.zw), uCell.zw - 1.0));
        float a = texelFetch(uTex, texel, 0).r * uAlpha;
        fragColor = vec4(uColor * a, a * uAlphaScale);
    } else {
        vec2 p = min(floor(vUv * uCell.zw), uCell.zw - 1.0);
        float line = p.x + p.y * 0.55 - uSheen;
        float band = exp(-(line / 46.0) * (line / 46.0)) * texture(uTex, vUv).a;
        fragColor = vec4(uColor * band * uAlpha, 0.0);
    }
}
