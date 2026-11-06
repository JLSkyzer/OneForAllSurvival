package dev.oneforallsurvival.role;

import static net.minecraft.ChatFormatting.BLUE;
import static net.minecraft.ChatFormatting.DARK_GREEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleBookTest {

    private RoleBook book;
    private UUID joueur;

    @BeforeEach
    void init() {
        book = RoleBook.withBuiltins();
        joueur = UUID.randomUUID();
    }

    private static Set<String> ids(RoleBook b) {
        Collection<Role> roles = b.roles();
        return roles.stream().map(Role::id).collect(Collectors.toSet());
    }

    @Test
    void unJoueurInconnuEstCitoyen() {
        assertEquals("citoyen", RoleBook.withBuiltins().roleOf(UUID.randomUUID()).id());
    }

    @Test
    void lesCinqRolesExistent() {
        assertEquals(Set.of("medecin", "ingenieur", "policier", "agriculteur", "citoyen"), ids(RoleBook.withBuiltins()));
    }

    @Test
    void unRoleCodeEnDurNeSeSupprimePas() {
        assertThrows(IllegalStateException.class, () -> book.deleteRole("ingenieur"));
    }

    @Test
    void supprimerUnRoleRendCitoyenSesPorteurs() {
        book.createRole("pecheur", "Pêcheur", BLUE);
        book.assign(joueur, "pecheur");
        book.deleteRole("pecheur");
        assertEquals("citoyen", book.roleOf(joueur).id());
    }

    @Test
    void attribuerUnRoleInconnuEstRefuse() {
        assertThrows(IllegalArgumentException.class, () -> book.assign(joueur, "inconnu"));
    }

    @Test
    void unIdentifiantInvalideOuDoubleEstRefuse() {
        assertThrows(IllegalArgumentException.class, () -> book.createRole("Pêcheur", "Pêcheur", BLUE));
        assertThrows(IllegalArgumentException.class, () -> book.createRole("", "Vide", BLUE));
        assertThrows(IllegalArgumentException.class, () -> book.createRole("citoyen", "Doublon", BLUE));
    }

    @Test
    void editerUnRoleChangeLibelleEtCouleurEtGardeLeDrapeauCodeEnDur() {
        book.editRole("medecin", "Docteur", DARK_GREEN);
        Role role = book.role("medecin").orElseThrow();
        assertEquals("Docteur", role.label());
        assertEquals(DARK_GREEN, role.color());
        assertTrue(role.builtin());
        assertThrows(IllegalArgumentException.class, () -> book.editRole("inconnu", "X", BLUE));
    }

    @Test
    void unRoleEtSonLibelleAccentueSurviventALaSauvegarde() {
        book.createRole("pecheur", "Pêcheur", BLUE);
        book.assign(joueur, "pecheur");
        RoleBook relu = RoleBook.load(book.save());
        Role role = relu.role("pecheur").orElseThrow();
        assertEquals("Pêcheur", role.label());
        assertEquals(BLUE, role.color());
        assertEquals("pecheur", relu.roleOf(joueur).id());
    }

    @Test
    void lesRolesCodesEnDurReapparaissentSiAbsentsDeLaSauvegarde() {
        assertTrue(RoleBook.load(new CompoundTag()).role("ingenieur").isPresent());
    }

    private static CompoundTag roleTag(String id, String label, String color, boolean builtin) {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putString("label", label);
        t.putString("color", color);
        t.putBoolean("builtin", builtin);
        return t;
    }

    private static CompoundTag assignTag(UUID player, String role) {
        CompoundTag t = new CompoundTag();
        if (player != null) {
            t.putUUID("player", player);
        }
        t.putString("role", role);
        return t;
    }

    private static CompoundTag sauvegarde(ListTag roles, ListTag assignments) {
        CompoundTag tag = new CompoundTag();
        tag.put("roles", roles);
        tag.put("assignments", assignments);
        return tag;
    }

    @Test
    void unCitoyenStockeNonCodeEnDurChargeCommeCodeEnDurEtGardeSonLibelle() {
        ListTag roles = new ListTag();
        roles.add(roleTag("citoyen", "Habitant", "gold", false));
        RoleBook relu = RoleBook.load(sauvegarde(roles, new ListTag()));
        Role citoyen = relu.role("citoyen").orElseThrow();
        assertTrue(citoyen.builtin());
        assertEquals("Habitant", citoyen.label());
        assertEquals(net.minecraft.ChatFormatting.GOLD, citoyen.color());
        assertThrows(IllegalStateException.class, () -> relu.deleteRole("citoyen"));
    }

    @Test
    void uneAttributionSansUuidEstIgnoreeEtLesAutresSurvivent() {
        ListTag roles = new ListTag();
        roles.add(roleTag("pecheur", "Pêcheur", "blue", false));
        ListTag assignments = new ListTag();
        assignments.add(assignTag(null, "pecheur"));
        assignments.add(assignTag(joueur, "pecheur"));
        RoleBook relu = RoleBook.load(sauvegarde(roles, assignments));
        assertEquals("pecheur", relu.roleOf(joueur).id());
    }

    @Test
    void unRoleSansIdentifiantValideEstIgnoreSansEmpecherLeChargement() {
        ListTag roles = new ListTag();
        roles.add(roleTag("", "Vide", "blue", false));
        roles.add(roleTag("Pêcheur", "Accent", "blue", false));
        roles.add(roleTag("pecheur", "Pêcheur", "couleur_inconnue", false));
        RoleBook relu = RoleBook.load(sauvegarde(roles, new ListTag()));
        assertEquals(Set.of("medecin", "ingenieur", "policier", "agriculteur", "citoyen", "pecheur"), ids(relu));
        assertEquals(net.minecraft.ChatFormatting.WHITE, relu.role("pecheur").orElseThrow().color());
    }

    @Test
    void uneAttributionVersUnRoleAbsentRetombeSurCitoyen() {
        ListTag assignments = new ListTag();
        assignments.add(assignTag(joueur, "disparu"));
        RoleBook relu = RoleBook.load(sauvegarde(new ListTag(), assignments));
        assertEquals("citoyen", relu.roleOf(joueur).id());
    }
}
