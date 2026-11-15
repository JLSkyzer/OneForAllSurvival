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
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.fml.ModList;
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
    /** Les tags de cible inconnus ou vides au dernier chargement des données (« GENRE:identifiant »). */
    private static volatile Set<String> deadTags = Set.of();
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
        Set<String> dead = deadTags;
        List<QuestTemplate> extra = staff;
        if (extra.isEmpty() && dead.isEmpty()) {
            return loaded;
        }
        List<QuestTemplate> all = new ArrayList<>(loaded);
        Set<String> ids = new HashSet<>();
        loaded.forEach(t -> ids.add(t.id()));
        for (QuestTemplate template : extra) {
            if (ids.add(template.id())) {
                all.add(template);
            } else {
                LOGGER.warn("Quetes : modele de l'equipe {} ecarte, son identifiant existe deja dans le fichier",
                        template.id());
            }
        }
        return List.copyOf(usable(all, dead));
    }

    /** Les modèles dont la cible n'est pas un tag mort : un tag inconnu ou vide ne peut jamais être rempli. */
    public static List<QuestTemplate> usable(List<QuestTemplate> templates, Set<String> deadTags) {
        if (deadTags.isEmpty()) {
            return templates;
        }
        return templates.stream().filter(t -> t.target() == null || !t.target().tag()
                || !deadTags.contains(tagKey(t.category().targetKind(), t.target().id()))).toList();
    }

    /** Les tags de cible des modèles dont {@code tagHasEntries} dit qu'ils ne contiennent rien. */
    public static Set<String> findDeadTags(List<QuestTemplate> templates,
            BiPredicate<QuestCategory.TargetKind, ResourceLocation> tagHasEntries) {
        Set<String> dead = new HashSet<>();
        for (QuestTemplate t : templates) {
            if (t.target() != null && t.target().tag()
                    && !tagHasEntries.test(t.category().targetKind(), t.target().id())) {
                dead.add(tagKey(t.category().targetKind(), t.target().id()));
            }
        }
        return dead;
    }

    private static String tagKey(QuestCategory.TargetKind kind, ResourceLocation id) {
        return kind + ":" + id;
    }

    /** Vrai si le tag existe dans le registre du jeu et contient quelque chose. */
    public static boolean tagHasEntries(QuestCategory.TargetKind kind, ResourceLocation id) {
        return switch (kind) {
            case BLOCK -> hasEntries(BuiltInRegistries.BLOCK, Registries.BLOCK, id);
            case ENTITY -> hasEntries(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, id);
            case ITEM -> hasEntries(BuiltInRegistries.ITEM, Registries.ITEM, id);
            case ACTION -> false;
        };
    }

    private static <T> boolean hasEntries(Registry<T> registry, ResourceKey<Registry<T>> key, ResourceLocation id) {
        return registry.getTag(TagKey.create(key, id)).map(set -> set.size() > 0).orElse(false);
    }

    /** Après chaque chargement des données : les modèles à tag inconnu ou vide ne sont plus tirables. */
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        refreshDeadTags();
    }

    private static void refreshDeadTags() {
        List<QuestTemplate> all = new ArrayList<>(loaded);
        all.addAll(staff);
        Set<String> dead = findDeadTags(all, QuestTemplates::tagHasEntries);
        deadTags = dead;
        for (String tag : dead) {
            LOGGER.warn("Quetes : tag de cible inconnu ou vide {}, les modeles qui le visent ne sont pas tires", tag);
        }
    }

    /** Remplace les modèles de l'équipe (commande, tâche 4). */
    public static void setStaffTemplates(List<QuestTemplate> templates) {
        staff = List.copyOf(templates);
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
            refreshDeadTags();
        }
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
            deadTags = Set.of();
            LOGGER.info("Quetes : {} modeles charges", templates.size());
        } catch (RuntimeException e) {
            LOGGER.error("Quetes : quests.json invalide, reserve precedente conservee : {}", e.getMessage());
        }
    }

    /** Lit le fichier ; lève {@link JsonParseException} en nommant le modèle et le champ fautif. */
    public static List<QuestTemplate> parse(JsonObject json) {
        return parse(json, QuestTemplates::inRegistry, QuestTemplates::modLoaded);
    }

    private static boolean modLoaded(String modId) {
        return ModList.get() != null && ModList.get().isLoaded(modId);
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
    public static List<QuestTemplate> parse(JsonObject json, RegistryCheck registry,
            Predicate<String> modLoaded) {
        JsonArray array = array(json.get("templates"), "templates");
        List<QuestTemplate> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            QuestTemplate template = parseOne(array.get(i), i, ids, registry, modLoaded);
            if (template != null) {
                out.add(template);
            }
        }
        return List.copyOf(out);
    }

    @Nullable
    private static QuestTemplate parseOne(JsonElement element, int index, Set<String> ids,
            RegistryCheck registry, Predicate<String> modLoaded) {
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
        if (ids.contains(id)) {
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
            checkTarget(category, target, where + ".target");
        } else if (category == QuestCategory.JOB) {
            throw new JsonParseException(where + ".target : obligatoire pour un modele de metier");
        }
        String requires = o.has("requires") ? string(o.get("requires"), where + ".requires") : null;
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
        // La structure est valide ; reste ce qui dépend des mods et du registre : le modèle est alors ignoré.
        if (requires != null && !modLoaded.test(requires)) {
            LOGGER.debug("Quetes : modele {} ignore, mod {} absent", id, requires);
            return null;
        }
        if (target != null && !target.tag() && category.targetKind() != QuestCategory.TargetKind.ACTION
                && !registry.contains(category.targetKind(), target.id())) {
            LOGGER.warn("Quetes : modele {} ignore, {} absent du registre", id, target.text());
            return null;
        }
        ids.add(id);
        return new QuestTemplate(id, category, target, min, max, reward, weight, roleWeights, weeklyFactor, title);
    }

    /** Une action de métier doit être enregistrée (faute de structure : le fichier est refusé). */
    private static void checkTarget(QuestCategory category, QuestTarget target, String path) {
        if (category.targetKind() == QuestCategory.TargetKind.ACTION
                && (target.tag() || QuestJobs.roleOf(target.id()).isEmpty())) {
            throw new JsonParseException(path + " : action de metier inconnue « " + target.text() + " »");
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
