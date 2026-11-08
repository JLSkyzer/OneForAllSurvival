package dev.oneforallsurvival.role;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

/**
 * Registre des rôles, des attributions joueur → rôle, et du maire. Logique pure, sans dépendance au monde.
 *
 * <p>Le maire est un statut qui s'ajoute au métier (un Ingénieur peut être maire) : il n'est pas un
 * rôle de la liste, et un seul joueur le porte à la fois.
 */
public final class RoleBook {

    public static final String DEFAULT_ROLE_ID = "citoyen";
    public static final String ENGINEER_ROLE_ID = "ingenieur";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> BUILTIN_IDS =
            Set.of("medecin", ENGINEER_ROLE_ID, "policier", "agriculteur", DEFAULT_ROLE_ID);
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");

    private final Map<String, Role> roles = new LinkedHashMap<>();
    private final Map<UUID, String> assignments = new LinkedHashMap<>();
    private UUID mayor;

    private RoleBook() {
    }

    public static RoleBook withBuiltins() {
        RoleBook book = new RoleBook();
        book.addMissingBuiltins();
        return book;
    }

    private void addMissingBuiltins() {
        addBuiltin("medecin", "Médecin", ChatFormatting.RED);
        addBuiltin(ENGINEER_ROLE_ID, "Ingénieur", ChatFormatting.GOLD);
        addBuiltin("policier", "Policier", ChatFormatting.BLUE);
        addBuiltin("agriculteur", "Agriculteur", ChatFormatting.GREEN);
        addBuiltin(DEFAULT_ROLE_ID, "Citoyen", ChatFormatting.GRAY);
    }

    private void addBuiltin(String id, String label, ChatFormatting color) {
        roles.putIfAbsent(id, new Role(id, label, color, true));
    }

    public Collection<Role> roles() {
        return Collections.unmodifiableCollection(roles.values());
    }

    public Optional<Role> role(String id) {
        return Optional.ofNullable(roles.get(id));
    }

    /** Rôle du joueur ; citoyen s'il n'en a pas reçu. */
    public Role roleOf(UUID player) {
        String id = assignments.get(player);
        Role role = id == null ? null : roles.get(id);
        return role != null ? role : roles.get(DEFAULT_ROLE_ID);
    }

    public void assign(UUID player, String roleId) {
        if (!roles.containsKey(roleId)) {
            throw new IllegalArgumentException("Rôle inconnu : " + roleId);
        }
        assignments.put(player, roleId);
    }

    public void createRole(String id, String label, ChatFormatting color) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Identifiant invalide : " + id);
        }
        if (roles.containsKey(id)) {
            throw new IllegalArgumentException("Rôle déjà existant : " + id);
        }
        roles.put(id, new Role(id, label, color, false));
    }

    /** Change libellé et couleur ; le drapeau « codé en dur » est conservé. */
    public void editRole(String id, String label, ChatFormatting color) {
        Role old = roles.get(id);
        if (old == null) {
            throw new IllegalArgumentException("Rôle inconnu : " + id);
        }
        roles.put(id, new Role(id, label, color, old.builtin()));
    }

    /** Supprime un rôle créé ; ses porteurs redeviennent citoyens. */
    public void deleteRole(String id) {
        Role role = roles.get(id);
        if (role == null) {
            throw new IllegalArgumentException("Rôle inconnu : " + id);
        }
        if (role.builtin()) {
            throw new IllegalStateException("Un rôle codé en dur ne se supprime pas : " + id);
        }
        roles.remove(id);
        assignments.values().removeIf(id::equals);
    }

    /** Le maire en fonction ; vide tant que l'équipe du serveur n'en a nommé aucun. */
    public Optional<UUID> mayor() {
        return Optional.ofNullable(mayor);
    }

    public boolean isMayor(UUID player) {
        return player != null && player.equals(mayor);
    }

    /**
     * Nomme ce joueur maire ; il remplace le maire précédent, qui perd le statut.
     *
     * @return le maire remplacé, vide s'il n'y en avait pas ou si c'était déjà ce joueur
     */
    public Optional<UUID> appointMayor(UUID player) {
        if (player == null) {
            throw new IllegalArgumentException("Joueur absent");
        }
        UUID previous = mayor;
        mayor = player;
        return previous == null || previous.equals(player) ? Optional.empty() : Optional.of(previous);
    }

    /** Retire le statut de maire ; renvoie le maire retiré, vide s'il n'y en avait pas. */
    public Optional<UUID> removeMayor() {
        UUID previous = mayor;
        mayor = null;
        return Optional.ofNullable(previous);
    }

    public CompoundTag save() {
        ListTag roleList = new ListTag();
        for (Role role : roles.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", role.id());
            t.putString("label", role.label());
            t.putString("color", role.color().getName());
            t.putBoolean("builtin", role.builtin());
            roleList.add(t);
        }
        ListTag assignList = new ListTag();
        for (Map.Entry<UUID, String> e : assignments.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("player", e.getKey());
            t.putString("role", e.getValue());
            assignList.add(t);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("roles", roleList);
        tag.put("assignments", assignList);
        if (mayor != null) {
            tag.putUUID("mayor", mayor);
        }
        return tag;
    }

    /**
     * Relit une sauvegarde ; les rôles codés en dur absents sont recréés et gardent toujours leur
     * drapeau. Une entrée corrompue est ignorée avec un avertissement : elle ne doit pas empêcher
     * le serveur de démarrer.
     */
    public static RoleBook load(CompoundTag tag) {
        RoleBook book = new RoleBook();
        for (Tag raw : tag.getList("roles", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            String id = t.getString("id");
            if (!ID_PATTERN.matcher(id).matches()) {
                LOGGER.warn("Rôle ignoré, identifiant invalide : '{}'", id);
                continue;
            }
            ChatFormatting color = ChatFormatting.getByName(t.getString("color"));
            if (color == null) {
                LOGGER.warn("Rôle '{}' : couleur inconnue '{}', blanc utilisé", id, t.getString("color"));
                color = ChatFormatting.WHITE;
            }
            // Un rôle codé en dur ne se supprime jamais, quoi que la sauvegarde en dise.
            boolean builtin = t.getBoolean("builtin") || BUILTIN_IDS.contains(id);
            book.roles.put(id, new Role(id, t.getString("label"), color, builtin));
        }
        book.addMissingBuiltins();
        for (Tag raw : tag.getList("assignments", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("player")) {
                LOGGER.warn("Attribution ignorée, identifiant de joueur absent ou invalide");
                continue;
            }
            String roleId = t.getString("role");
            if (book.roles.containsKey(roleId)) {
                book.assignments.put(t.getUUID("player"), roleId);
            }
        }
        // Une sauvegarde d'avant le maire n'a pas cette clé : aucun maire, comme sur un monde neuf.
        if (tag.hasUUID("mayor")) {
            book.mayor = tag.getUUID("mayor");
        } else if (tag.contains("mayor")) {
            LOGGER.warn("Maire ignoré, identifiant de joueur invalide : aucun maire après chargement");
        }
        return book;
    }
}
