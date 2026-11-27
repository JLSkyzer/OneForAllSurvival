package dev.oneforallsurvival.dev.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchRecipe;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

/**
 * Les exports de l'atelier pour le wiki ({@code captures/_export/}) : une icône par objet rendue par le jeu (PNG de
 * 128 px à fond transparent), les noms français ({@code objets.json}) et les recettes ({@code recettes.json}).
 *
 * <p>Recettes : celles de la table (gestionnaire de recettes du serveur, après le passage de l'établi) qui fabriquent
 * un objet du mod, un objet cité par une fiche du wiki ({@code wiki/data/*.json}) ou un objet de
 * {@code wiki/tools/recettes-en-plus.json} ; et tout le catalogue de l'établi ({@link BenchCatalog}). Format : liste
 * de {@code {"recette", "sortie", "nombre", "poste": "table"|"etabli", "grille"?, "ingredients", "secondes"?}} ; la
 * grille (3 × 3, recette en forme seulement) porte l'identifiant de l'objet ou {@code null} ; un ingrédient donné par
 * une étiquette prend son premier objet et le dit ({@code "etiquette"}). Réglages par défaut du dossier
 * {@code run-wiki} (configuration de l'établi comprise).
 *
 * <p>Icônes : l'objet est dessiné comme dans un inventaire, à 256 px sur fond noir puis sur fond blanc ; l'écart
 * entre les deux donne l'opacité exacte de chaque pixel (objets translucides compris), puis l'image est réduite à
 * 128 px.
 */
public final class WikiExports implements Chapitre {

    private static final String MOD = "oneforallsurvival";
    private static final int RENDU = 256;
    private static final int TAILLE = 128;
    private static final Pattern IDENTIFIANT = Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");
    /** Les fichiers de {@code wiki/data/} écrits par {@code build_visuals.py} : ce ne sont pas des fiches. */
    private static final Set<String> PRODUITS = Set.of("objets.json", "recettes.json", "reperes.json");

    private static volatile JsonArray recettes;
    private static volatile Set<ResourceLocation> objets;

    @Override
    public String id() {
        return "_export";
    }

    @Override
    public void scenes(Atelier atelier) {
        atelier.scene("exports")
                .serveur("recettes", lieu -> lireRecettes(lieu.serveur()))
                .client("icônes et noms", WikiExports::ecrire);
    }

    // --- recettes (fil du serveur) ---------------------------------------------------------------------------------

    private static boolean essai(ResourceLocation id) {
        return id.getNamespace().equals(MOD) && id.getPath().startsWith("test_");
    }

    private static void lireRecettes(MinecraftServer srv) {
        RegistryAccess acces = srv.registryAccess();
        Set<ResourceLocation> voulus = objetsVoulus();
        Set<ResourceLocation> tous = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
            if (id.getNamespace().equals(MOD) && !essai(id)) {
                tous.add(id);
            }
        }
        List<JsonObject> liste = new ArrayList<>();
        int ignorees = 0;
        for (CraftingRecipe r : srv.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack sortie = r.getResultItem(acces);
            if (sortie.isEmpty()) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(sortie.getItem());
            if (essai(r.getId()) || essai(id) || !(id.getNamespace().equals(MOD) || voulus.contains(id))) {
                continue;
            }
            if (!(r instanceof ShapedRecipe) && !(r instanceof ShapelessRecipe)) {
                WikiStudio.journal("recette de table spéciale ignorée : " + r.getId() + " (" + r.getClass().getName()
                        + ")");
                ignorees++;
                continue;
            }
            JsonObject o = entete(r.getId(), sortie, "table");
            if (r instanceof ShapedRecipe forme) {
                JsonArray grille = new JsonArray();
                for (int y = 0; y < 3; y++) {
                    JsonArray ligne = new JsonArray();
                    for (int x = 0; x < 3; x++) {
                        Ingredient i = x < forme.getWidth() && y < forme.getHeight()
                                ? forme.getIngredients().get(y * forme.getWidth() + x) : Ingredient.EMPTY;
                        ItemStack premier = premier(i);
                        if (premier.isEmpty()) {
                            ligne.add(JsonNull.INSTANCE);
                        } else {
                            ligne.add(cle(premier.getItem()).toString());
                            tous.add(cle(premier.getItem()));
                        }
                    }
                    grille.add(ligne);
                }
                o.add("grille", grille);
            }
            o.add("ingredients", ingredients(r.getIngredients(), tous));
            tous.add(id);
            liste.add(o);
        }
        int etabli = 0;
        for (BenchRecipe r : BenchCatalog.current().recipes()) {
            ResourceLocation id = cle(r.result().getItem());
            if (essai(r.id()) || essai(id)) {
                continue;
            }
            JsonObject o = entete(r.id(), r.result(), "etabli");
            o.add("ingredients", ingredients(r.ingredients(), tous));
            o.addProperty("secondes", r.seconds());
            tous.add(id);
            liste.add(o);
            etabli++;
        }
        liste.sort(Comparator.comparing((JsonObject o) -> o.get("poste").getAsString())
                .thenComparing(o -> o.get("sortie").getAsString()).thenComparing(o -> o.get("recette").getAsString()));
        JsonArray a = new JsonArray();
        liste.forEach(a::add);
        tous.addAll(voulus);
        tous.removeIf(id -> ForgeRegistries.ITEMS.getValue(id) == null || ForgeRegistries.ITEMS.getValue(id) == Items.AIR);
        recettes = a;
        objets = tous;
        WikiStudio.journal("recettes : " + (liste.size() - etabli) + " de table, " + etabli + " de l'établi, "
                + ignorees + " spéciale(s) ignorée(s) ; " + tous.size() + " objets à exporter ; objets voulus hors du mod "
                + voulus);
    }

    private static JsonObject entete(ResourceLocation recette, ItemStack sortie, String poste) {
        JsonObject o = new JsonObject();
        o.addProperty("recette", recette.toString());
        o.addProperty("sortie", cle(sortie.getItem()).toString());
        o.addProperty("nombre", sortie.getCount());
        o.addProperty("poste", poste);
        return o;
    }

    /** Les ingrédients regroupés (même objet, même étiquette), dans l'ordre de première apparition. */
    private static JsonArray ingredients(List<Ingredient> liste, Set<ResourceLocation> tous) {
        Map<String, JsonObject> groupes = new LinkedHashMap<>();
        for (Ingredient i : liste) {
            ItemStack premier = premier(i);
            if (premier.isEmpty()) {
                continue;
            }
            String etiquette = etiquette(i);
            ResourceLocation id = cle(premier.getItem());
            tous.add(id);
            JsonObject g = groupes.computeIfAbsent(id + "|" + etiquette, k -> {
                JsonObject n = new JsonObject();
                n.addProperty("item", id.toString());
                n.addProperty("nombre", 0);
                if (etiquette != null) {
                    n.addProperty("etiquette", etiquette);
                }
                return n;
            });
            g.addProperty("nombre", g.get("nombre").getAsInt() + Math.max(1, premier.getCount()));
        }
        JsonArray a = new JsonArray();
        groupes.values().forEach(a::add);
        return a;
    }

    private static ItemStack premier(Ingredient i) {
        if (i == null || i.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] piles = i.getItems();
        return piles.length == 0 ? ItemStack.EMPTY : piles[0];
    }

    /** L'étiquette d'un ingrédient donné par une étiquette (la première s'il y a plusieurs choix), ou {@code null}. */
    private static String etiquette(Ingredient i) {
        try {
            JsonElement j = i.toJson();
            JsonElement premier = j.isJsonArray() && j.getAsJsonArray().size() > 0 ? j.getAsJsonArray().get(0) : j;
            if (premier.isJsonObject() && premier.getAsJsonObject().has("tag")) {
                return premier.getAsJsonObject().get("tag").getAsString();
            }
        } catch (RuntimeException e) {
            WikiStudio.journal("ingrédient illisible en JSON : " + e);
        }
        return null;
    }

    private static ResourceLocation cle(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    /**
     * Les objets d'autres mods dont la recette de table est exportée : ceux cités par les fiches du wiki (hors
     * Minecraft) et ceux de {@code wiki/tools/recettes-en-plus.json} (motifs {@code espace:*partie*}).
     */
    private static Set<ResourceLocation> objetsVoulus() {
        Set<ResourceLocation> voulus = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        Path wiki = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize().getParent()
                .resolve("wiki");
        try (Stream<Path> fiches = Files.list(wiki.resolve("data"))) {
            // Les fichiers produits par build_visuals.py ne sont pas des fiches : les lire ferait grossir l'export à
            // chaque passe (tous les objets de la passe précédente redeviendraient « cités »).
            for (Path f : fiches.filter(p -> p.toString().endsWith(".json") && !PRODUITS.contains(
                    p.getFileName().toString())).toList()) {
                Matcher m = IDENTIFIANT.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    ResourceLocation id = ResourceLocation.tryParse(m.group(1));
                    if (id != null && !id.getNamespace().equals("minecraft") && !id.getNamespace().equals(MOD)
                            && ForgeRegistries.ITEMS.containsKey(id)) {
                        voulus.add(id);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("fiches du wiki illisibles : " + wiki, e);
        }
        Path enPlus = wiki.resolve("tools").resolve("recettes-en-plus.json");
        if (Files.isRegularFile(enPlus)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(enPlus, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement e : o.getAsJsonArray("objets")) {
                    String motif = e.getAsString();
                    Pattern p = Pattern.compile(("\\Q" + motif + "\\E").replace("*", "\\E.*\\Q"));
                    int avant = voulus.size();
                    for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
                        if (p.matcher(id.toString()).matches()) {
                            voulus.add(id);
                        }
                    }
                    if (voulus.size() == avant && !motif.contains("*") && !ForgeRegistries.ITEMS.containsKey(
                            new ResourceLocation(motif))) {
                        WikiStudio.journal("recettes-en-plus : objet inconnu « " + motif + " »");
                    }
                }
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("recettes-en-plus.json illisible", e);
            }
        }
        return voulus;
    }

    // --- icônes et noms (fil du rendu) -----------------------------------------------------------------------------

    private static void ecrire(Minecraft mc) {
        if (recettes == null || objets == null) {
            throw new IllegalStateException("recettes non lues");
        }
        Path dossier = WikiStudio.racine().resolve("_export");
        Path icones = dossier.resolve("icones");
        try {
            Files.createDirectories(icones);
            try (Stream<Path> vieux = Files.list(icones)) {
                for (Path p : vieux.toList()) {
                    Files.delete(p);
                }
            }
            Files.writeString(dossier.resolve("recettes.json"), WikiStudio.gson().toJson(recettes) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("dossier des exports", e);
        }
        Map<String, JsonObject> noms = new TreeMap<>();
        List<String> vides = new ArrayList<>();
        long debut = System.nanoTime();
        TextureTarget cible = new TextureTarget(RENDU, RENDU, true, Minecraft.ON_OSX);
        Matrix4f projectionAvant = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting triAvant = RenderSystem.getVertexSorting();
        PoseStack vue = RenderSystem.getModelViewStack();
        vue.pushPose();
        try {
            float loin = ForgeHooksClient.getGuiFarPlane();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 16, 16, 0, 1000, loin),
                    VertexSorting.ORTHOGRAPHIC_Z);
            vue.setIdentity();
            vue.translate(0, 0, 1000 - loin);
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
            for (ResourceLocation id : objets) {
                ItemStack pile = new ItemStack(ForgeRegistries.ITEMS.getValue(id));
                int[] noir = dessiner(mc, cible, pile, 0F);
                int[] blanc = dessiner(mc, cible, pile, 1F);
                BufferedImage icone = reduire(detourer(noir, blanc));
                if (vide(icone)) {
                    vides.add(id.toString());
                }
                ImageIO.write(icone, "png", icones.resolve(id.getNamespace() + "__" + id.getPath().replace('/', '-')
                        + ".png").toFile());
                JsonObject n = new JsonObject();
                n.addProperty("nom", pile.getHoverName().getString());
                noms.put(id.toString(), n);
            }
        } catch (IOException e) {
            throw new IllegalStateException("icône non écrite", e);
        } finally {
            vue.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projectionAvant, triAvant);
            cible.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
        JsonObject o = new JsonObject();
        noms.forEach(o::add);
        try {
            Files.writeString(dossier.resolve("objets.json"), WikiStudio.gson().toJson(o) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("objets.json non écrit", e);
        }
        WikiStudio.journal(String.format(java.util.Locale.ROOT, "exports : %d icônes en %.1f s, %d recettes, langue %s",
                noms.size(), (System.nanoTime() - debut) / 1e9, recettes.size(), mc.getLanguageManager().getSelected()));
        if (!vides.isEmpty()) {
            throw new IllegalStateException("icônes vides (rien de dessiné) : " + vides);
        }
    }

    /** L'objet dessiné sur un fond uni de gris {@code fond} (0 noir, 1 blanc) ; pixels RGBA en ordre ABGR. */
    private static int[] dessiner(Minecraft mc, TextureTarget cible, ItemStack pile, float fond) {
        cible.setClearColor(fond, fond, fond, 1F);
        cible.clear(Minecraft.ON_OSX);
        cible.bindWrite(true);
        GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        g.renderItem(pile, 0, 0);
        g.flush();
        try (NativeImage image = new NativeImage(RENDU, RENDU, false)) {
            RenderSystem.bindTexture(cible.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            int[] p = new int[RENDU * RENDU];
            for (int y = 0; y < RENDU; y++) {
                for (int x = 0; x < RENDU; x++) {
                    p[y * RENDU + x] = image.getPixelRGBA(x, y);
                }
            }
            return p;
        }
    }

    /**
     * L'opacité de chaque pixel d'après ses deux rendus : sur noir {@code C·a}, sur blanc {@code C·a + (1 - a)} ; d'où
     * {@code a = 1 - (blanc - noir)} et {@code C = noir / a}. Rend des pixels ARGB à alpha simple (non prémultiplié).
     */
    private static int[] detourer(int[] noir, int[] blanc) {
        int[] argb = new int[noir.length];
        for (int i = 0; i < noir.length; i++) {
            int n = noir[i];
            int b = blanc[i];
            int[] cn = {n & 0xFF, (n >> 8) & 0xFF, (n >> 16) & 0xFF};
            int[] cb = {b & 0xFF, (b >> 8) & 0xFF, (b >> 16) & 0xFF};
            double ecart = ((cb[0] - cn[0]) + (cb[1] - cn[1]) + (cb[2] - cn[2])) / (3 * 255.0);
            double a = Math.max(0, Math.min(1, 1 - ecart));
            if (a < 1.5 / 255) {
                argb[i] = 0;
                continue;
            }
            int r = (int) Math.min(255, Math.round(cn[0] / a));
            int v = (int) Math.min(255, Math.round(cn[1] / a));
            int bl = (int) Math.min(255, Math.round(cn[2] / a));
            argb[i] = ((int) Math.round(a * 255) << 24) | (r << 16) | (v << 8) | bl;
        }
        return argb;
    }

    /** De 256 à 128 px : moyenne de chaque carré de 2 × 2, couleurs pondérées par l'opacité. */
    private static BufferedImage reduire(int[] argb) {
        BufferedImage img = new BufferedImage(TAILLE, TAILLE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < TAILLE; y++) {
            for (int x = 0; x < TAILLE; x++) {
                double sa = 0;
                double sr = 0;
                double sv = 0;
                double sb = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int p = argb[(2 * y + dy) * RENDU + 2 * x + dx];
                        double a = (p >>> 24) / 255.0;
                        sa += a;
                        sr += a * ((p >> 16) & 0xFF);
                        sv += a * ((p >> 8) & 0xFF);
                        sb += a * (p & 0xFF);
                    }
                }
                if (sa <= 0) {
                    continue;
                }
                int a = (int) Math.round(sa / 4 * 255);
                int r = (int) Math.round(sr / sa);
                int v = (int) Math.round(sv / sa);
                int b = (int) Math.round(sb / sa);
                img.setRGB(x, y, (a << 24) | (r << 16) | (v << 8) | b);
            }
        }
        return img;
    }

    private static boolean vide(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) > 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
