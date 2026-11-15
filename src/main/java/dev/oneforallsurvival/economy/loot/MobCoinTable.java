package dev.oneforallsurvival.economy.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.oneforallsurvival.economy.Coin;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;

/**
 * Table du butin des mobs, lue d'un fichier de données : des paliers (liste de tirages indépendants), des règles
 * ordonnées (identifiant exact ou {@code #tag}, la première qui correspond gagne), un palier par défaut par catégorie
 * et un palier pour le creeper chargé. Immuable.
 */
public final class MobCoinTable {

    /** Un tirage : avec la chance donnée, entre {@code min} et {@code max} pièces de la coupure. */
    public record Roll(Coin coin, double chance, int min, int max) {}

    /** Un palier de butin ; {@code anyOrigin} vrai : l'origine du mob est ignorée (boss). */
    public record Tier(String id, List<Roll> rolls, boolean anyOrigin) {}

    private record Rule(ResourceLocation target, boolean tag, String tierId) {}

    /** Mot réservé : « rien ». */
    public static final String NONE = "none";
    public static final int MAX_COUNT = 10_000;

    public static final MobCoinTable EMPTY =
            new MobCoinTable(Map.of(), List.of(), new EnumMap<>(MobCategory.class), null);

    private final Map<String, Tier> tiers;
    private final List<Rule> rules;
    private final EnumMap<MobCategory, String> categoryDefaults;
    @Nullable
    private final String chargedCreeper;

    private MobCoinTable(Map<String, Tier> tiers, List<Rule> rules, EnumMap<MobCategory, String> categoryDefaults,
            @Nullable String chargedCreeper) {
        this.tiers = tiers;
        this.rules = rules;
        this.categoryDefaults = categoryDefaults;
        this.chargedCreeper = chargedCreeper;
    }

    public int tierCount() {
        return tiers.size();
    }

    public int ruleCount() {
        return rules.size();
    }

    /** Palier d'identifiant donné ; {@code null} pour {@code none} ou un identifiant inconnu. */
    @Nullable
    public Tier tier(String id) {
        return tiers.get(id);
    }

    /** Palier d'un mob, ou {@code null} s'il ne donne rien (palier {@code none}, aucune règle ni défaut). */
    @Nullable
    public Tier tierFor(ResourceLocation typeId, Set<ResourceLocation> typeTags, MobCategory category,
            boolean chargedCreeper) {
        if (chargedCreeper && chargedCreeper() != null) {
            return tier(chargedCreeper());
        }
        for (Rule rule : rules) {
            if (rule.tag() ? typeTags.contains(rule.target()) : rule.target().equals(typeId)) {
                return tier(rule.tierId());
            }
        }
        String fallback = categoryDefaults.get(category);
        return fallback == null ? null : tier(fallback);
    }

    @Nullable
    private String chargedCreeper() {
        return chargedCreeper;
    }

    /** Un tirage par ligne du palier, indépendants ; les coupures à zéro sont absentes. */
    public static EnumMap<Coin, Long> roll(Tier tier, RandomSource random) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (Roll roll : tier.rolls()) {
            if (random.nextDouble() < roll.chance()) {
                long count = roll.min() + random.nextInt(roll.max() - roll.min() + 1);
                if (count > 0L) {
                    out.merge(roll.coin(), count, Long::sum);
                }
            }
        }
        return out;
    }

    /**
     * Les règles dont l'identifiant ne désigne aucun type de mob, ou dont le tag est inconnu ou vide : une faute de
     * frappe laisserait sinon la règle sans effet, en silence. La table reste acceptée (un mod peut être absent).
     */
    public List<String> unknownTargets(Predicate<ResourceLocation> typeExists,
            Predicate<ResourceLocation> tagHasEntries) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (rule.tag() ? !tagHasEntries.test(rule.target()) : !typeExists.test(rule.target())) {
                out.add("rules[" + i + "].match : " + (rule.tag() ? "tag inconnu ou vide #" : "identifiant inconnu ")
                        + rule.target());
            }
        }
        return out;
    }

    /** Lit le fichier ; lève {@link JsonParseException} en nommant le champ fautif. */
    public static MobCoinTable parse(JsonObject json) {
        JsonObject tiersJson = asObject(json.get("tiers"), "tiers");
        Map<String, Tier> tiers = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : tiersJson.entrySet()) {
            String id = entry.getKey();
            String path = "tiers." + id;
            if (id.equals(NONE)) {
                throw new JsonParseException(path + " : le mot « none » est réservé");
            }
            JsonObject tierJson = asObject(entry.getValue(), path);
            boolean anyOrigin = false;
            if (tierJson.has("any_origin")) {
                anyOrigin = bool(tierJson.get("any_origin"), path + ".any_origin");
            }
            JsonArray rollsJson = asArray(tierJson.get("rolls"), path + ".rolls");
            List<Roll> rolls = new ArrayList<>();
            for (int i = 0; i < rollsJson.size(); i++) {
                String rollPath = path + ".rolls[" + i + "]";
                rolls.add(parseRoll(asObject(rollsJson.get(i), rollPath), rollPath));
            }
            tiers.put(id, new Tier(id, List.copyOf(rolls), anyOrigin));
        }

        List<Rule> rules = new ArrayList<>();
        if (json.has("rules")) {
            JsonArray rulesJson = asArray(json.get("rules"), "rules");
            for (int i = 0; i < rulesJson.size(); i++) {
                String path = "rules[" + i + "]";
                JsonObject rule = asObject(rulesJson.get(i), path);
                String match = text(rule.get("match"), path + ".match");
                boolean tag = match.startsWith("#");
                ResourceLocation target = ResourceLocation.tryParse(tag ? match.substring(1) : match);
                if (target == null) {
                    throw new JsonParseException(path + ".match : identifiant invalide « " + match + " »");
                }
                rules.add(new Rule(target, tag, knownTier(tiers, text(rule.get("tier"), path + ".tier"),
                        path + ".tier")));
            }
        }

        EnumMap<MobCategory, String> defaults = new EnumMap<>(MobCategory.class);
        if (json.has("category_defaults")) {
            JsonObject defaultsJson = asObject(json.get("category_defaults"), "category_defaults");
            for (Map.Entry<String, JsonElement> entry : defaultsJson.entrySet()) {
                String path = "category_defaults." + entry.getKey();
                MobCategory category = null;
                for (MobCategory c : MobCategory.values()) {
                    if (c.getName().equals(entry.getKey())) {
                        category = c;
                    }
                }
                if (category == null) {
                    throw new JsonParseException(path + " : catégorie inconnue");
                }
                defaults.put(category, knownTier(tiers, text(entry.getValue(), path), path));
            }
        }

        String charged = null;
        if (json.has("charged_creeper")) {
            charged = knownTier(tiers, text(json.get("charged_creeper"), "charged_creeper"), "charged_creeper");
        }
        return new MobCoinTable(Map.copyOf(tiers), List.copyOf(rules), defaults, charged);
    }

    private static Roll parseRoll(JsonObject json, String path) {
        String coinName = text(json.get("coin"), path + ".coin");
        Coin coin = null;
        for (Coin c : Coin.values()) {
            if (c.key().equals(coinName)) {
                coin = c;
            }
        }
        if (coin == null) {
            throw new JsonParseException(path + ".coin : coupure inconnue « " + coinName + " »");
        }
        double chance = number(json.get("chance"), path + ".chance");
        if (!(chance >= 0.0 && chance <= 1.0)) {
            throw new JsonParseException(path + ".chance : doit être entre 0 et 1 (« " + chance + " »)");
        }
        int min = integer(json.get("min"), path + ".min");
        int max = integer(json.get("max"), path + ".max");
        if (min < 1 || min > MAX_COUNT) {
            throw new JsonParseException(path + ".min : doit être entre 1 et " + MAX_COUNT + " (« " + min + " »)");
        }
        if (max < min || max > MAX_COUNT) {
            throw new JsonParseException(path + ".max : doit être entre min (" + min + ") et " + MAX_COUNT
                    + " (« " + max + " »)");
        }
        return new Roll(coin, chance, min, max);
    }

    private static String knownTier(Map<String, Tier> tiers, String id, String path) {
        if (!id.equals(NONE) && !tiers.containsKey(id)) {
            throw new JsonParseException(path + " : palier inconnu « " + id + " »");
        }
        return id;
    }

    // --- lecture typée : chaque erreur nomme son champ ---

    private static JsonObject asObject(@Nullable JsonElement element, String path) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException(path + " : un objet est attendu");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray asArray(@Nullable JsonElement element, String path) {
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException(path + " : une liste est attendue");
        }
        return element.getAsJsonArray();
    }

    private static boolean bool(JsonElement element, String path) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new JsonParseException(path + " : vrai ou faux attendu");
        }
        return element.getAsBoolean();
    }

    private static String text(@Nullable JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException(path + " : un texte est attendu");
        }
        return element.getAsString();
    }

    private static double number(@Nullable JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(path + " : un nombre est attendu");
        }
        return element.getAsDouble();
    }

    private static int integer(@Nullable JsonElement element, String path) {
        double value = number(element, path);
        if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
            throw new JsonParseException(path + " : un entier est attendu");
        }
        return (int) value;
    }
}
