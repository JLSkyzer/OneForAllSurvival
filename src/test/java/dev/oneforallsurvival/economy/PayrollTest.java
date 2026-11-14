package dev.oneforallsurvival.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.economy.payroll.SalaryMath;
import dev.oneforallsurvival.economy.payroll.SalaryRates;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PayrollTest {

    private static final UUID POLICIER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final LocalDate J = LocalDate.of(2026, 11, 14);
    private static final long HUIT_HEURES = 8L * 3_600L;

    @Test
    void leCreditSurvitALaSauvegarde() {
        Payroll paie = new Payroll();
        assertNull(paie.lastClosed());
        paie.credit(POLICIER, "Agent", J, "policier", 600L, 60L, HUIT_HEURES);
        paie.credit(POLICIER, "Agent", J.plusDays(1), "policier", 5L, 60L, HUIT_HEURES);
        paie.setLastClosed(J.minusDays(1));
        assertTrue(paie.isDirty());

        Payroll relu = Payroll.load(paie.save(new CompoundTag()));
        assertEquals(Map.of("policier", 600L), relu.secondsFor(POLICIER, J));
        assertEquals(Map.of("policier", 600L * 60L), relu.rateSecondsFor(POLICIER, J));
        assertEquals(Map.of("policier", 5L), relu.secondsFor(POLICIER, J.plusDays(1)));
        assertEquals(J.minusDays(1), relu.lastClosed());
        assertEquals("Agent", relu.nameOf(POLICIER, J));
        assertFalse(relu.isDirty(), "une relecture n'est pas une modification");
        // Le compte continue après la relecture : additionné, pas remis à zéro.
        relu.credit(POLICIER, "Agent", J, "policier", 10L, 60L, HUIT_HEURES);
        assertEquals(Map.of("policier", 610L), relu.secondsFor(POLICIER, J));
        // Un joueur ou un jour inconnus : rien.
        assertEquals(Map.of(), relu.secondsFor(UUID.randomUUID(), J));
        assertEquals(Map.of(), relu.secondsFor(POLICIER, J.minusDays(5)));
    }

    @Test
    void deuxClesLeMemeJour() {
        Payroll paie = new Payroll();
        paie.credit(POLICIER, "Agent", J, "policier", 100L, 60L, HUIT_HEURES);
        paie.credit(POLICIER, "Agent", J, SalaryRates.MAYOR_KEY, 100L, 30L, HUIT_HEURES);
        assertEquals(Map.of("policier", 100L, "mayor", 100L), paie.secondsFor(POLICIER, J));
        assertEquals(Map.of("policier", 6_000L, "mayor", 3_000L), paie.rateSecondsFor(POLICIER, J));
        // Le plafond vaut par salaire, pas pour le joueur entier.
        paie.credit(POLICIER, "Agent", J, "policier", HUIT_HEURES, 60L, HUIT_HEURES);
        paie.credit(POLICIER, "Agent", J, SalaryRates.MAYOR_KEY, 50L, 30L, HUIT_HEURES);
        assertEquals(Map.of("policier", HUIT_HEURES, "mayor", 150L), paie.secondsFor(POLICIER, J));
    }

    @Test
    void unTauxChangeEnCoursDeJournee() {
        Payroll paie = new Payroll();
        paie.credit(POLICIER, "Agent", J, "policier", 1_800L, 60L, HUIT_HEURES);
        paie.credit(POLICIER, "Agent", J, "policier", 1_800L, 120L, HUIT_HEURES);
        assertEquals(Map.of("policier", 3_600L), paie.secondsFor(POLICIER, J));
        assertEquals(90L, SalaryMath.amount(paie.rateSecondsFor(POLICIER, J).get("policier")));
    }

    @Test
    void unCreditNulOuPlafonneNeSalitRien() {
        Payroll paie = new Payroll();
        paie.credit(POLICIER, "Agent", J, "policier", 0L, 60L, HUIT_HEURES);
        assertFalse(paie.isDirty());
        assertEquals(Map.of(), paie.secondsFor(POLICIER, J));
        paie.credit(POLICIER, "Agent", J, "policier", HUIT_HEURES + 100L, 60L, HUIT_HEURES);
        assertEquals(Map.of("policier", HUIT_HEURES), paie.secondsFor(POLICIER, J));
        paie.setDirty(false);
        paie.credit(POLICIER, "Agent", J, "policier", 5L, 60L, HUIT_HEURES);
        assertFalse(paie.isDirty(), "rien d'ajouté au-delà du plafond");
    }
}
