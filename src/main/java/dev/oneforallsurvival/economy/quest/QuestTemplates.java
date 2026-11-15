package dev.oneforallsurvival.economy.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Réserve de modèles de quêtes en service : le fichier de données ({@code economy/quests.json}, rechargé avec
 * {@code /reload}) puis les modèles ajoutés par l'équipe. Un fichier absent ou invalide garde la réserve précédente
 * (vide au premier chargement) et écrit l'erreur au journal.
 */
public final class QuestTemplates extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final ResourceLocation FILE = new ResourceLocation("oneforallsurvival", "quests");
    private static final ResourceLocation RESOURCE = new ResourceLocation("oneforallsurvival", "economy/quests.json");
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final int LIMIT = 10_000;

    private static volatile List<QuestTemplate> loaded = List.of();
    private static volatile List<QuestTemplate> staff = List.of();
    @Nullable
    private static volatile List<QuestTemplate> forTest;

    public QuestTemplates() {
        super(GSON, "economy");
    }

    /** Fichier, puis modèles de l'équipe dont l'identifiant n'est pas déjà pris. */
    public static List<QuestTemplate> current() {
        List<QuestTemplate> test = forTest;
        if (test != null) {
            return test;
        }
        List<QuestTemplate> extra = staff;
        if (extra.isEmpty()) {
            return loaded;
        }
        List<QuestTemplate> all = new ArrayList<>(loaded);
        Set<String> ids = new HashSet<>();
        loaded.forEach(t -> ids.add(t.id()));
        for (QuestTemplate template : extra) {
            if (ids.add(template.id())) {
                all.add(template);
            }
        }
        return List.copyOf(all);
    }

    /** Remplace les modèles de l'équipe (commande, tâche 4). */
    public static void setStaffTemplates(List<QuestTemplate> templates) {
        staff = List.copyOf(templates);
    }

    /** Impose une réserve (tests) tant qu'elle n'est pas rendue à {@code null}. */
    public static void setForTest(@Nullable List<QuestTemplate> templates) {
        forTest = templates == null ? null : List.copyOf(templates);
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new QuestTemplates());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        JsonElement file = files.get(FILE);
        if (file == null) {
            LOGGER.error("Quetes : {}, reserve precedente conservee", manager.getResource(RESOURCE).isPresent()
                    ? "economy/quests.json illisible (JSON mal forme, voir l'erreur de chargement juste avant)"
                    : "economy/quests.json absent");
            return;
        }
        try {
            List<QuestTemplate> templates = parse(GsonHelper.convertToJsonObject(file, "quests.json"));
            loaded = templates;
            LOGGER.info("Quetes : {} modeles charges", templates.size());
        } catch (RuntimeException e) {
            LOGGER.error("Quetes : quests.json invalide, reserve precedente conservee : {}", e.getMessage());
        }
    }

    /** Lit le fichier ; lève {@link JsonParseException} en nommant le modèle et le champ fautif. */
    public static List<QuestTemplate> parse(JsonObject json) {
        return parse(json, QuestTemplates::inRegistry);
    }

    /** Ce que sait dire un registre : l'identifiant existe-t-il pour ce genre de cible ? */
    @FunctionalInterface
    public interface RegistryCheck {
        boolean contains(QuestCategory.TargetKind kind, ResourceLocation id);
    }

    private static boolean inRegistry(QuestCategory.TargetKind kind, ResourceLocation id) {
        return switch (kind) {
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(id);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            case ITEM -> BuiltInRegistries.ITEM.containsKey(id);
            case ACTION -> false;
        };
    }

    /** Comme {@link #parse(JsonObject)} avec un registre donné (les tests n'ont pas celui du jeu). */
    public static List<QuestTemplate> parse(JsonObject json, RegistryCheck registry) {
        JsonArray array = array(json.get("templates"), "templates");
        List<QuestTemplate> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            out.add(parseOne(array.get(i), i, ids, registry));
        }
        return List.copyOf(out);
    }

    private static QuestTemplate parseOne(JsonElement element, int index, Set<String> ids,
            RegistryCheck registry) {
        String slot = "templates[" + index + "]";
        if (!element.isJsonObject()) {
            throw new JsonParseException(slot + " : objet attendu");
        }
        JsonObject o = element.getAsJsonObject();
        String id = string(o.get("id"), slot + ".id");
        final String where = slot + " (" + id + ")";
        if (!ID.matcher(id).matches()) {
            throw new JsonParseException(where + ".id : [a-z0-9_]{1,32} attendu");
        }
        if (!ids.add(id)) {
            throw new JsonParseException(where + ".id : identifiant en double");
        }
        String categoryKey = string(o.get("category"), where + ".category");
        QuestCategory category = QuestCategory.byKey(categoryKey)
                .orElseThrow(() -> new JsonParseException(where + ".category : categorie inconnue « " + categoryKey + " »"));
        if (!category.counted()) {
            throw new JsonParseException(where + ".category : « " + categoryKey
                    + " » n'a aucun moyen d'etre comptee (aucun grenier communal)");
        }
        QuestTarget target = null;
        if (o.has("target") && !o.get("target").isJsonNull()) {
            String text = string(o.get("target"), where + ".target");
            target = QuestTarget.parse(text)
                    .orElseThrow(() -> new JsonParseException(where + ".target : identifiant invalide « " + text + " »"));
            checkTarget(category, target, where + ".target", registry);
        } else if (category == QuestCategory.JOB) {
            throw new JsonParseException(where + ".target : obligatoire pour un modele de metier");
        }
        int min = integer(o.get("min"), where + ".min", 1, LIMIT);
        int max = integer(o.get("max"), where + ".max", 1, LIMIT);
        if (min > max) {
            throw new JsonParseException(where + ".max : doit etre au moins min (" + min + " > " + max + ")");
        }
        long reward = integer(o.get("reward"), where + ".reward", 1, LIMIT);
        double weight = 1.0D;
        if (o.has("weight")) {
            weight = number(o.get("weight"), where + ".weight");
            if (!(weight > 0.0D)) {
                throw new JsonParseException(where + ".weight : strictement positif attendu");
            }
        }
        Map<String, Double> roleWeights = new LinkedHashMap<>();
        if (o.has("role_weights")) {
            JsonElement rolesElement = o.get("role_weights");
            if (!rolesElement.isJsonObject()) {
                throw new JsonParseException(where + ".role_weights : objet attendu");
            }
            for (Map.Entry<String, JsonElement> entry : rolesElement.getAsJsonObject().entrySet()) {
                String path = where + ".role_weights." + entry.getKey();
                double value = number(entry.getValue(), path);
                if (!(value >= 0.0D)) {
                    throw new JsonParseException(path + " : positif ou nul attendu");
                }
                roleWeights.put(entry.getKey(), value);
            }
        }
        OptionalInt weeklyFactor = OptionalInt.empty();
        if (o.has("weekly_factor")) {
            weeklyFactor = OptionalInt.of(integer(o.get("weekly_factor"), where + ".weekly_factor", 1, 20));
        }
        String title = o.has("title") ? string(o.get("title"), where + ".title") : null;
        return new QuestTemplate(id, category, target, min, max, reward, weight, roleWeights, weeklyFactor, title);
    }

    /** Une cible qui n'est pas un tag doit exister dans le registre de son genre ; une action, être enregistrée. */
    private static void checkTarget(QuestCategory category, QuestTarget target, String path,
            RegistryCheck registry) {
        QuestCategory.TargetKind kind = category.targetKind();
        if (kind == QuestCategory.TargetKind.ACTION) {
            if (target.tag() || QuestJobs.roleOf(target.id()).isEmpty()) {
                throw new JsonParseException(path + " : action de metier inconnue « " + target.text() + " »");
            }
            return;
        }
        if (target.tag()) {
            return;
        }
        boolean known = registry.contains(kind, target.id());
        if (!known) {
            throw new JsonParseException(path + " : « " + target.text() + " » absent du registre des "
                    + switch (kind) {
                        case BLOCK -> "blocs";
                        case ENTITY -> "creatures";
                        default -> "objets";
                    });
        }
    }

    private static JsonArray array(@Nullable JsonElement element, String path) {
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException(path + " : tableau attendu");
        }
        return element.getAsJsonArray();
    }

    private static String string(@Nullable JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException(path + " : texte attendu");
        }
        return element.getAsString();
    }

    private static double number(@Nullable JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(path + " : nombre attendu");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        double value = primitive.getAsDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new JsonParseException(path + " : nombre fini attendu");
        }
        return value;
    }

    private static int integer(@Nullable JsonElement element, String path, int min, int max) {
        double value = number(element, path);
        if (value != Math.rint(value) || value < min || value > max) {
            throw new JsonParseException(path + " : entier de " + min + " a " + max + " attendu");
        }
        return (int) value;
    }
}
