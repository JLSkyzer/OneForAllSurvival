package dev.oneforallsurvival.role;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Registre des rôles et des attributions joueur → rôle. Logique pure, sans dépendance au monde. */
public final class RoleBook {

    public static final String DEFAULT_ROLE_ID = "citoyen";
    public static final String ENGINEER_ROLE_ID = "ingenieur";

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");

    private final Map<String, Role> roles = new LinkedHashMap<>();
    private final Map<UUID, String> assignments = new LinkedHashMap<>();

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
        return tag;
    }

    /** Relit une sauvegarde ; les rôles codés en dur absents sont recréés. */
    public static RoleBook load(CompoundTag tag) {
        RoleBook book = new RoleBook();
        List<Role> loaded = new ArrayList<>();
        for (Tag raw : tag.getList("roles", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            ChatFormatting color = ChatFormatting.getByName(t.getString("color"));
            loaded.add(new Role(t.getString("id"), t.getString("label"),
                    color != null ? color : ChatFormatting.WHITE, t.getBoolean("builtin")));
        }
        for (Role role : loaded) {
            book.roles.put(role.id(), role);
        }
        book.addMissingBuiltins();
        for (Tag raw : tag.getList("assignments", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            String roleId = t.getString("role");
            if (book.roles.containsKey(roleId)) {
                book.assignments.put(t.getUUID("player"), roleId);
            }
        }
        return book;
    }
}
