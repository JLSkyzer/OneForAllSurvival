#version 150
// Bloom léger de compose.py (« c'est ce qui fait respirer les feux et le soleil ») :
//   small = frame[::4, ::4] ; bright = clip(small - 0.72, 0, 2) ; blur = gaussian_filter(bright, 9) ;
//   frame += blur agrandi × 4 × 0.9.
// uMode 0 : seuil, un pixel sur 4 de l'image ; 1 : flou gaussien sur un axe (bords en miroir, comme le mode
// « reflect » de scipy) ; 2 : ajout à l'image, agrandi par filtrage bilinéaire.

out vec4 fragColor;

uniform int uMode;
uniform sampler2D uSource;
uniform ivec2 uSourceSize;
uniform ivec2 uAxis;      // (1, 0) ou (0, 1)
uniform float uSigma;     // écart-type, en pixels du tampon réduit
uniform int uRadius;      // int(4 * sigma + 0.5), comme truncate=4.0 de scipy
uniform float uGain;

int mirror(int i, int n) {
    if (i < 0) {
        i = -i - 1;
    }
    if (i >= n) {
        i = 2 * n - i - 1;
    }
    return clamp(i, 0, n - 1);
}

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    if (uMode == 0) {
        ivec2 src = min(p * 4, uSourceSize - 1);
        vec3 c = texelFetch(uSource, src, 0).rgb;
        fragColor = vec4(clamp(c - 0.72, 0.0, 2.0), 1.0);
    } else if (uMode == 1) {
        vec3 sum = vec3(0.0);
        float total = 0.0;
        for (int k = -uRadius; k <= uRadius; k++) {
            float w = exp(-0.5 * float(k * k) / (uSigma * uSigma));
            ivec2 q = p + uAxis * k;
            q = ivec2(mirror(q.x, uSourceSize.x), mirror(q.y, uSourceSize.y));
            sum += texelFetch(uSource, q, 0).rgb * w;
            total += w;
        }
        fragColor = vec4(sum / total, 1.0);
    } else {
        vec2 uv = ((gl_FragCoord.xy - 0.5) / 4.0 + 0.5) / vec2(uSourceSize);
        fragColor = vec4(texture(uSource, uv).rgb * uGain, 0.0);
    }
}
