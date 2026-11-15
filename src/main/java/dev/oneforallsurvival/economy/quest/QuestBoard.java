package dev.oneforallsurvival.economy.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * Ce que l'équipe ajoute aux quêtes : des modèles (tirables comme ceux du fichier de données) et des événements
 * (quêtes communes à tous, à durée limitée). Écrit avec le monde, aucune écriture forcée ; chaque changement met à jour
 * la réserve en service ({@link QuestTemplates#setStaffTemplates}). Les modèles sont gardés sous leur forme de fichier
 * de données (JSON) et relus par les mêmes contrôles que lui : un modèle devenu invalide (mod retiré, version
 * différente) est écarté avec un avertissement, jamais une réserve cassée. Ne se touche que depuis le fil du serveur.
 */
public final class QuestBoard extends SavedData {

    public static final String KEY = "oneforallsurvival_quests";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final String LANG = "command.oneforallsurvival.eco.quest.";

    /** Résultat d'un ajout ou d'un lancement : accepté, ou refusé avec la clé de langue de la raison. */
    public record Result(boolean ok, String key, List<Object> args) {

        public static Result accepted() {
            return new Result(true, "", List.of());
        }

        public static Result refused(String key, Object... args) {
            return new Result(false, key, List.of(args));
        }
    }

    private final Map<String, QuestTemplate> templates = new LinkedHashMap<>();
    private final Map<String, QuestEvent> events = new LinkedHashMap<>();

    public QuestBoard() {}

    /** Le tableau de l'équipe du serveur ; il met en service ses modèles à son premier chargement. */
    public static QuestBoard get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(QuestBoard::load, QuestBoard::fresh, KEY);
    }

    private static QuestBoard fresh() {
        QuestBoard board = new QuestBoard();
        QuestTemplates.setStaffTemplates(List.of());
        return board;
    }

    // ---------------------------------------------------------------------------------------------
    // Modèles
    // ---------------------------------------------------------------------------------------------

    public List<QuestTemplate> templates() {
        return List.copyOf(templates.values());
    }

    /**
     * Ajoute un modèle. Refusé si son identifiant est déjà pris (dans le fichier de données ou dans la liste), ou si
     * les contrôles du fichier de données le refusent (catégorie sans moyen d'être comptée, {@code min > max}, cible
     * inconnue, tag vide, récompense nulle…) ; la réserve reste alors telle quelle.
     */
    public Result addTemplate(QuestTemplate template) {
        if (QuestTemplates.fileIds().contains(template.id()) || templates.containsKey(template.id())) {
            return Result.refused(LANG + "refused.taken", template.id());
        }
        Result checked = check(template);
        if (!checked.ok()) {
            return checked;
        }
        templates.put(template.id(), template);
        changed();
        return Result.accepted();
    }

    /** Retire un modèle de la liste de l'équipe (jamais un modèle du fichier) ; vrai s'il y était. */
    public boolean removeTemplate(String id) {
        if (templates.remove(id) == null) {
            return false;
        }
        changed();
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Événements
    // ---------------------------------------------------------------------------------------------

    /**
     * Lance un événement. Refusé si un événement du même identifiant est encore en cours (un événement terminé ou
     * arrêté se relance, sous une autre date de début : c'en est un autre pour les joueurs), si l'identifiant, la
     * catégorie, la cible, la quantité ou la récompense ne passent pas les contrôles du fichier de données, ou si la
     * fin n'est pas après le début.
     */
    public Result startEvent(QuestEvent event) {
        if (event.endMillis() <= event.startMillis()) {
            return Result.refused(LANG + "refused.invalid", "fin avant le début");
        }
        QuestEvent previous = events.get(event.id());
        if (previous != null && previous.runningAt(event.startMillis())) {
            return Result.refused(LANG + "refused.running", event.id());
        }
        Result checked = check(new QuestTemplate(event.id(), event.category(), event.target(), event.goal(),
                event.goal(), event.reward(), 1.0D, Map.of(), OptionalInt.empty(), null));
        if (!checked.ok()) {
            return checked;
        }
        events.put(event.id(), event);
        changed();
        return Result.accepted();
    }

    /** Arrête un événement ; vrai s'il existait. Les joueurs qui l'avaient fini le gardent en attente. */
    public boolean stopEvent(String id) {
        if (events.remove(id) == null) {
            return false;
        }
        changed();
        return true;
    }

    /** Les événements en cours à cet instant (pas encore finis), par identifiant. */
    public List<QuestEvent> activeEvents(long now) {
        return events.values().stream().filter(e -> e.runningAt(now)).sorted(Comparator.comparing(QuestEvent::id))
                .toList();
    }

    private void changed() {
        QuestTemplates.setStaffTemplates(templates());
        setDirty();
    }

    // ---------------------------------------------------------------------------------------------
    // Contrôles
    // ---------------------------------------------------------------------------------------------

    /** Passe le modèle par les contrôles du fichier de données : sa forme de fichier doit être relue à l'identique. */
    private static Result check(QuestTemplate template) {
        if (!ID.matcher(template.id()).matches()) {
            return Result.refused(LANG + "refused.invalid", "identifiant : [a-z0-9_]{1,32} attendu");
        }
        List<QuestTemplate> parsed;
        try {
            JsonObject file = new JsonObject();
            JsonArray array = new JsonArray();
            array.add(toJson(template));
            file.add("templates", array);
            parsed = QuestTemplates.parse(file);
        } catch (JsonParseException e) {
            return Result.refused(LANG + "refused.invalid", e.getMessage());
        }
        if (parsed.isEmpty()) {
            return Result.refused(LANG + "refused.absent", template.target() == null ? "?" : template.target().text());
        }
        QuestTarget target = template.target();
        if (target != null && target.tag() && !QuestTemplates.tagHasEntries(template.category().targetKind(),
                target.id())) {
            return Result.refused(LANG + "refused.invalid", "tag inconnu ou vide : " + target.text());
        }
        return Result.accepted();
    }

    /** La forme de fichier de données d'un modèle ({@code economy/quests.json}). */
    public static JsonObject toJson(QuestTemplate t) {
        JsonObject o = new JsonObject();
        o.addProperty("id", t.id());
        o.addProperty("category", t.category().key());
        if (t.target() != null) {
            o.addProperty("target", t.target().text());
        }
        o.addProperty("min", t.min());
        o.addProperty("max", t.max());
        o.addProperty("reward", t.reward());
        o.addProperty("weight", t.weight());
        if (!t.roleWeights().isEmpty()) {
            JsonObject roles = new JsonObject();
            t.roleWeights().forEach(roles::addProperty);
            o.add("role_weights", roles);
        }
        t.weeklyFactor().ifPresent(f -> o.addProperty("weekly_factor", f));
        if (t.titleKey() != null) {
            o.addProperty("title", t.titleKey());
        }
        return o;
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        templates.values().forEach(t -> list.add(StringTag.valueOf(toJson(t).toString())));
        tag.put("templates", list);
        ListTag running = new ListTag();
        for (QuestEvent e : events.values()) {
            CompoundTag line = new CompoundTag();
            line.putString("id", e.id());
            line.putString("category", e.category().key());
            if (e.target() != null) {
                line.putString("target", e.target().text());
            }
            line.putInt("goal", e.goal());
            line.putLong("reward", e.reward());
            line.putLong("start", e.startMillis());
            line.putLong("end", e.endMillis());
            line.putString("title", e.title());
            line.putUUID("author", e.author());
            running.add(line);
        }
        tag.put("events", running);
        return tag;
    }

    /** Relit le tableau ; ce qui n'est plus valable est écarté avec un avertissement. */
    public static QuestBoard load(CompoundTag tag) {
        QuestBoard board = new QuestBoard();
        for (Tag raw : tag.getList("templates", Tag.TAG_STRING)) {
            try {
                JsonObject file = new JsonObject();
                JsonArray array = new JsonArray();
                array.add(JsonParser.parseString(raw.getAsString()));
                file.add("templates", array);
                List<QuestTemplate> parsed = QuestTemplates.parse(file);
                if (parsed.isEmpty()) {
                    LOGGER.warn("Quetes : modele de l'equipe ecarte a la relecture (cible ou mod absent) : {}",
                            raw.getAsString());
                }
                parsed.forEach(t -> board.templates.put(t.id(), t));
            } catch (RuntimeException e) {
                LOGGER.warn("Quetes : modele de l'equipe illisible ecarte ({}) : {}", e.getMessage(), raw.getAsString());
            }
        }
        for (Tag raw : tag.getList("events", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) raw;
            QuestCategory category = QuestCategory.byKey(line.getString("category")).orElse(null);
            QuestTarget target = line.contains("target", Tag.TAG_STRING)
                    ? QuestTarget.parse(line.getString("target")).orElse(null) : null;
            String id = line.getString("id");
            if (category == null || !ID.matcher(id).matches() || line.getInt("goal") < 1 || line.getLong("reward") < 1L
                    || line.getLong("end") <= line.getLong("start") || !line.hasUUID("author")
                    || line.contains("target", Tag.TAG_STRING) && target == null) {
                LOGGER.warn("Quetes : evenement de l'equipe illisible ecarte : {}", line);
                continue;
            }
            board.events.put(id, new QuestEvent(id, category, target, line.getInt("goal"), line.getLong("reward"),
                    line.getLong("start"), line.getLong("end"), line.getString("title"), line.getUUID("author")));
        }
        QuestTemplates.setStaffTemplates(board.templates());
        return board;
    }
}
