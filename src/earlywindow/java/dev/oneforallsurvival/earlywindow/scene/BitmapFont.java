package dev.oneforallsurvival.earlywindow.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Police du texte de la scène : port de la classe {@code Font} de compose.py (« la police bitmap du jar, comme
 * le ferait le jeu »). Logique pure, sans GL : les glyphes et leur placement ; le dessin est fait par les effets.
 *
 * <p>Les images viennent du jar du client de Minecraft, jamais copiées dans le nôtre :
 * {@code font/include/default.json} liste les fournisseurs bitmap ({@code nonlatin_european.png} pour « … »,
 * {@code accented.png} pour « Î », « é », « à », {@code ascii.png}), lus dans cet ordre ; le premier qui
 * fournit un caractère le garde. La source est injectable ({@link #load}) : en développement, le classpath ; en
 * jeu, le jar du jeu trouvé par la fenêtre anticipée.
 *
 * <p>Repli ({@link #fromTrueType}) : {@code Monocraft.ttf} de fmlearlydisplay, rastérisé à la même hauteur de
 * capitale (7 px), en pixels pleins.
 *
 * <p>Mise en page de compose.py : glyphe aussi large que sa dernière colonne non vide (3 si vide), avance de
 * largeur + 1, espace de 4, ligne de 18 px où la ligne de base est à la rangée 11 ({@code y = 11 - ascent}),
 * puis agrandissement entier ({@code np.kron}).
 */
public final class BitmapFont {
    /** Hauteur de la ligne rendue ({@code canvas = np.zeros((18, …))}). */
    public static final int LINE = 18;
    /** Rangée de la ligne de base : {@code y = 11 - ascent}. */
    public static final int BASELINE = 11;
    private static final int SPACE = 4;
    private static final int CAP_HEIGHT = 7;

    /** Alpha d'une image décodée, une valeur 0..255 par pixel, rangées de haut en bas. */
    public record Alpha(int width, int height, byte[] alpha) {
        int at(int x, int y) {
            return alpha[y * width + x] & 0xff;
        }
    }

    /** Décode un PNG en alpha (STB en jeu, injectable pour les tests). */
    @FunctionalInterface
    public interface Decoder {
        Alpha decode(byte[] png) throws IOException;
    }

    /**
     * Glyphe : {@code width} colonnes utiles, {@code height} rangées, posé à la rangée {@code top} de la ligne.
     * {@code alpha} : {@code width * height} valeurs 0..255.
     */
    public record Glyph(int width, int height, int top, byte[] alpha) {
    }

    /** Glyphe placé dans la ligne (avant agrandissement) : colonne {@code x}, rangée {@code glyph.top()}. */
    public record Placed(Glyph glyph, int x) {
    }

    private final Map<Integer, Glyph> glyphs;
    private final String origin;

    private BitmapFont(Map<Integer, Glyph> glyphs, String origin) {
        if (!glyphs.containsKey((int) '?')) {
            throw new IllegalArgumentException("police sans « ? » (glyphe de remplacement)");
        }
        this.glyphs = glyphs;
        this.origin = origin;
    }

    /** D'où vient la police (pour le journal). */
    public String origin() {
        return origin;
    }

    /** Glyphes connus, par point de code. */
    public Map<Integer, Glyph> glyphs() {
        return glyphs;
    }

    /** Glyphe d'un caractère, ou celui de « ? » s'il manque (comme compose.py). */
    public Glyph glyph(int codePoint) {
        Glyph g = glyphs.get(codePoint);
        return g != null ? g : glyphs.get((int) '?');
    }

    /** Placement des glyphes d'un texte, espaces exclus. */
    public List<Placed> layout(String text) {
        List<Placed> out = new ArrayList<>();
        int x = 0;
        for (int cp : text.codePoints().toArray()) {
            if (cp == ' ') {
                x += SPACE;
                continue;
            }
            Glyph g = glyph(cp);
            out.add(new Placed(g, x));
            x += g.width() + 1;
        }
        return out;
    }

    /** Largeur de la ligne rendue, en pixels avant agrandissement ({@code width + 2} de compose.py). */
    public int width(String text) {
        int w = 0;
        for (int cp : text.codePoints().toArray()) {
            w += cp == ' ' ? SPACE : glyph(cp).width() + 1;
        }
        return w + 2;
    }

    /** Largeur à l'échelle {@code scale} ({@code font.width(text, scale)}). */
    public int width(String text, int scale) {
        return width(text) * scale;
    }

    // --- Police bitmap du jar ----------------------------------------------------------------------------

    /**
     * Lit la police bitmap de Minecraft.
     *
     * @param source ouvre un fichier par son chemin sous {@code assets/minecraft/} (« font/include/default.json »,
     *               « textures/font/ascii.png ») ; {@code null} s'il n'existe pas
     * @throws IOException si la description ou une image manque ou est illisible
     */
    public static BitmapFont load(Function<String, InputStream> source, Decoder decoder) throws IOException {
        JsonObject root;
        try (InputStream in = source.apply("font/include/default.json")) {
            if (in == null) {
                throw new IOException("font/include/default.json introuvable dans le jar du client");
            }
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Map<Integer, Glyph> glyphs = new HashMap<>();
        for (JsonElement e : root.getAsJsonArray("providers")) {
            JsonObject provider = e.getAsJsonObject();
            if (!provider.has("type") || !"bitmap".equals(provider.get("type").getAsString())) {
                continue;
            }
            String file = provider.get("file").getAsString();
            String path = "textures/" + file.substring(file.indexOf(':') + 1);
            byte[] png;
            try (InputStream in = source.apply(path)) {
                if (in == null) {
                    throw new IOException(path + " introuvable dans le jar du client");
                }
                png = in.readAllBytes();
            }
            Alpha image = decoder.decode(png);
            JsonArray rows = provider.getAsJsonArray("chars");
            int[][] chars = new int[rows.size()][];
            for (int r = 0; r < rows.size(); r++) {
                chars[r] = rows.get(r).getAsString().codePoints().toArray();
            }
            int ch = image.height() / chars.length;
            int cw = image.width() / chars[0].length;
            int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
            int ascent = provider.has("ascent") ? provider.get("ascent").getAsInt() : 7;
            // compose.py ne garde que les fournisseurs à l'échelle 1 (hauteur = hauteur de cellule).
            if (height != ch) {
                continue;
            }
            for (int r = 0; r < chars.length; r++) {
                for (int c = 0; c < chars[r].length; c++) {
                    int cp = chars[r][c];
                    if (cp == 0 || glyphs.containsKey(cp)) {
                        continue;
                    }
                    glyphs.put(cp, cell(image, c * cw, r * ch, cw, ch, BASELINE - ascent));
                }
            }
        }
        return new BitmapFont(glyphs, "police bitmap du jar du client");
    }

    /** Cellule d'un glyphe, rognée à sa dernière colonne non vide (3 colonnes si elle est vide). */
    static Glyph cell(Alpha image, int x0, int y0, int cw, int ch, int top) {
        int last = -1;
        for (int x = 0; x < cw; x++) {
            for (int y = 0; y < ch; y++) {
                if (image.at(x0 + x, y0 + y) > 0) {
                    last = x;
                    break;
                }
            }
        }
        int width = last >= 0 ? last + 1 : 3;
        byte[] alpha = new byte[width * ch];
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < width && x < cw; x++) {
                alpha[y * width + x] = (byte) image.at(x0 + x, y0 + y);
            }
        }
        return new Glyph(width, ch, top, alpha);
    }

    /** Décodeur STB (bibliothèque native de LWJGL, déjà chargée par la fenêtre). */
    public static Alpha decodeWithStb(byte[] png) throws IOException {
        ByteBuffer file = MemoryUtil.memAlloc(png.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            file.put(png).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(file, w, h, c, 4);
            if (pixels == null) {
                throw new IOException("image de police illisible : " + STBImage.stbi_failure_reason());
            }
            try {
                byte[] alpha = new byte[w.get(0) * h.get(0)];
                for (int i = 0; i < alpha.length; i++) {
                    alpha[i] = pixels.get(i * 4 + 3);
                }
                return new Alpha(w.get(0), h.get(0), alpha);
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        } finally {
            MemoryUtil.memFree(file);
        }
    }

    // --- Repli : Monocraft.ttf -----------------------------------------------------------------------------

    /**
     * Police de repli rastérisée depuis une police TrueType (Monocraft.ttf de fmlearlydisplay) : capitales de
     * 7 px comme {@code ascii.png}, pixels pleins (seuil à mi-hauteur) pour garder l'aspect bitmap.
     */
    public static BitmapFont fromTrueType(byte[] ttf) throws IOException {
        ByteBuffer data = MemoryUtil.memAlloc(ttf.length);
        STBTTFontinfo info = STBTTFontinfo.malloc();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            data.put(ttf).flip();
            if (!STBTruetype.stbtt_InitFont(info, data)) {
                throw new IOException("police TrueType illisible");
            }
            IntBuffer x0 = stack.mallocInt(1);
            IntBuffer y0 = stack.mallocInt(1);
            IntBuffer x1 = stack.mallocInt(1);
            IntBuffer y1 = stack.mallocInt(1);
            if (!STBTruetype.stbtt_GetCodepointBox(info, 'H', x0, y0, x1, y1) || y1.get(0) <= 0) {
                throw new IOException("police TrueType sans « H »");
            }
            float scale = CAP_HEIGHT / (float) y1.get(0);
            Map<Integer, Glyph> glyphs = new HashMap<>();
            List<Integer> wanted = new ArrayList<>();
            for (int cp = 0x21; cp <= 0x7e; cp++) {
                wanted.add(cp);
            }
            for (int cp = 0xa0; cp <= 0x17f; cp++) {
                wanted.add(cp);
            }
            wanted.add(0x2019); // apostrophe typographique
            wanted.add(0x2026); // points de suspension
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer xo = stack.mallocInt(1);
            IntBuffer yo = stack.mallocInt(1);
            for (int cp : wanted) {
                if (STBTruetype.stbtt_FindGlyphIndex(info, cp) == 0) {
                    continue;
                }
                ByteBuffer bitmap = STBTruetype.stbtt_GetCodepointBitmap(info, scale, scale, cp, w, h, xo, yo);
                if (bitmap == null) {
                    continue;
                }
                try {
                    int left = Math.max(0, xo.get(0));
                    int bw = w.get(0);
                    int bh = h.get(0);
                    Alpha a = new Alpha(left + bw, bh, new byte[(left + bw) * bh]);
                    for (int y = 0; y < bh; y++) {
                        for (int x = 0; x < bw; x++) {
                            a.alpha()[y * (left + bw) + left + x] = (bitmap.get(y * bw + x) & 0xff) >= 128
                                    ? (byte) 255 : 0;
                        }
                    }
                    glyphs.put(cp, cell(a, 0, 0, left + bw, bh, BASELINE + yo.get(0)));
                } finally {
                    STBTruetype.stbtt_FreeBitmap(bitmap);
                }
            }
            return new BitmapFont(glyphs, "Monocraft.ttf (repli)");
        } finally {
            info.free();
            MemoryUtil.memFree(data);
        }
    }

    /**
     * Police du jeu si le jar du client est lisible, sinon Monocraft.ttf ; {@code null} si aucune ne se lit (le
     * texte est alors omis, la barre reste). {@code log} reçoit une ligne par repli.
     *
     * @param minecraft ouvre un fichier sous {@code assets/minecraft/} du jar du client
     * @param fallback  ouvre {@code Monocraft.ttf} (ressource de fmlearlydisplay)
     */
    public static BitmapFont loadOrFallback(Function<String, InputStream> minecraft,
                                            java.util.function.Supplier<InputStream> fallback,
                                            java.util.function.Consumer<String> log) {
        try {
            return load(minecraft, BitmapFont::decodeWithStb);
        } catch (IOException | RuntimeException | LinkageError e) {
            log.accept("police du jeu illisible (" + e.getMessage() + "), repli sur Monocraft.ttf");
        }
        try (InputStream in = fallback.get()) {
            if (in == null) {
                throw new IOException("Monocraft.ttf introuvable");
            }
            return fromTrueType(in.readAllBytes());
        } catch (IOException | RuntimeException | LinkageError e) {
            log.accept("aucune police lisible (" + e.getMessage() + ") : le texte de la scène est omis");
            return null;
        }
    }
}
