package dev.oneforallsurvival.bench;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Surcharges de données de l'établi : {@code data/<espace>/bench_overrides/<nom>.json}.
 *
 * <pre>{"result":"mod:objet","ingredients":[{"item":"minecraft:iron_ingot"}],"seconds":120,"hidden":false}</pre>
 *
 * Seul {@code result} est obligatoire. {@code ingredients} suit le format vanilla des ingrédients et
 * remplace ceux de la recette ; {@code seconds} impose la durée ; {@code hidden} retire la recette du
 * catalogue (elle quitte quand même la table). La surcharge vaut pour toutes les recettes du résultat.
 *
 * <p>Ce chargeur s'applique pendant le rechargement des données, donc avant le {@code TagsUpdatedEvent}
 * qui construit le catalogue : celui-ci lit toujours des surcharges à jour.
 */
public final class BenchOverrides extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Une surcharge lue.
     *
     * @param file        fichier d'origine, pour les journaux
     * @param ingredients ingrédients de remplacement, ou null s'ils ne changent pas
     * @param seconds     durée imposée, ou null
     * @param hidden      true pour cacher, false pour montrer, null si non précisé
     */
    public record Surcharge(ResourceLocation file, ResourceLocation result, List<Ingredient> ingredients,
            Integer seconds, Boolean hidden) {}

    /** Surcharges par résultat, dans l'ordre des fichiers. */
    private static volatile Map<ResourceLocation, List<Surcharge>> current = Map.of();

    public BenchOverrides() {
        super(GSON, "bench_overrides");
    }

    public static Map<ResourceLocation, List<Surcharge>> current() {
        return current;
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new BenchOverrides());
    }

    @java.lang.Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
            ProfilerFiller profiler) {
        List<Surcharge> parsed = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            try {
                parsed.add(parse(entry.getKey(), entry.getValue()));
            } catch (RuntimeException e) {
                // Un fichier invalide ne doit jamais arrêter le chargement.
                LOGGER.warn("Etabli : surcharge {} ignoree (fichier invalide) : {}", entry.getKey(), e.getMessage());
            }
        }
        parsed.sort(Comparator.comparing(o -> o.file().toString()));
        Map<ResourceLocation, List<Surcharge>> byResult = new TreeMap<>();
        for (Surcharge o : parsed) {
            byResult.computeIfAbsent(o.result(), k -> new ArrayList<>()).add(o);
        }
        current = Map.copyOf(byResult);
        LOGGER.info("Etabli : {} surcharges lues, {} fichiers invalides", parsed.size(), files.size() - parsed.size());
    }

    /** Lit un fichier de surcharge ; lève une exception explicite s'il est invalide. */
    static Surcharge parse(ResourceLocation file, JsonElement json) {
        JsonObject obj = GsonHelper.convertToJsonObject(json, "surcharge");
        String rawResult = GsonHelper.getAsString(obj, "result");
        ResourceLocation result = ResourceLocation.tryParse(rawResult);
        if (result == null) {
            throw new JsonParseException("identifiant de resultat invalide : " + rawResult);
        }
        List<Ingredient> ingredients = null;
        if (obj.has("ingredients")) {
            JsonArray array = GsonHelper.getAsJsonArray(obj, "ingredients");
            ingredients = new ArrayList<>();
            for (JsonElement element : array) {
                Ingredient ingredient = Ingredient.fromJson(element);
                if (!ingredient.isEmpty()) {
                    ingredients.add(ingredient);
                }
            }
        }
        Integer seconds = null;
        if (obj.has("seconds")) {
            seconds = GsonHelper.getAsInt(obj, "seconds");
            if (seconds < 1) {
                throw new JsonParseException("seconds doit valoir au moins 1 : " + seconds);
            }
        }
        Boolean hidden = obj.has("hidden") ? GsonHelper.getAsBoolean(obj, "hidden") : null;
        return new Surcharge(file, result, ingredients == null ? null : List.copyOf(ingredients), seconds, hidden);
    }
}
