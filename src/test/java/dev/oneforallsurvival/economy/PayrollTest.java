package dev.oneforallsurvival.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.CommerceDelivery.Destination;

import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.economy.payroll.SalaryMath;
import dev.oneforallsurvival.economy.payroll.SalaryRates;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
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
    private static final UUID MEDECIN = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Test
    void unCreditSurUnJourDejaCloEstReporteAuLendemainDuDernierClos() {
        Payroll paie = new Payroll();
        paie.setLastClosed(J);
        // L'horloge a reculé au-delà d'une frontière close : le temps n'est ni perdu ni laissé sur un jour clos.
        paie.credit(POLICIER, "Agent", J, "policier", 30L, 60L, HUIT_HEURES);
        paie.credit(POLICIER, "Agent", J.minusDays(3), "policier", 20L, 60L, HUIT_HEURES);
        assertEquals(Map.of(), paie.secondsFor(POLICIER, J));
        assertEquals(Map.of(), paie.secondsFor(POLICIER, J.minusDays(3)));
        assertEquals(Map.of("policier", 50L), paie.secondsFor(POLICIER, J.plusDays(1)));
        // Le jour d'après le dernier clos, lui, se crédite normalement.
        paie.credit(POLICIER, "Agent", J.plusDays(2), "policier", 5L, 60L, HUIT_HEURES);
        assertEquals(Map.of("policier", 5L), paie.secondsFor(POLICIER, J.plusDays(2)));
    }

    @Test
    void laClotureFaitDesDettesParJoueurPuisCle() {
        Payroll paie = new Payroll();
        paie.credit(MEDECIN, "Docteur", J, "medecin", 3_600L, 60L, HUIT_HEURES);
        paie.credit(POLICIER, "Agent", J, SalaryRates.MAYOR_KEY, 1_800L, 60L, HUIT_HEURES);
        paie.credit(POLICIER, "Agent", J, "policier", 3_600L, 60L, HUIT_HEURES);
        // Moins d'un cuivre : pas de dette.
        paie.credit(POLICIER, "Agent", J, "ingenieur", 59L, 60L, HUIT_HEURES);
        paie.credit(POLICIER, "Agent", J.plusDays(1), "policier", 10L, 60L, HUIT_HEURES);

        List<Payroll.Debt> dettes = paie.closeDay(J);
        assertEquals(3, dettes.size());
        // Ordre : joueur (UUID) puis clé ; ids croissants.
        assertEquals(List.of("mayor", "policier", "medecin"), dettes.stream().map(Payroll.Debt::key).toList());
        assertEquals(List.of(30L, 60L, 60L), dettes.stream().map(Payroll.Debt::amount).toList());
        assertTrue(dettes.get(0).id() < dettes.get(1).id() && dettes.get(1).id() < dettes.get(2).id());
        assertEquals(1_800L, dettes.get(0).seconds());
        assertEquals("Agent", dettes.get(0).playerName());
        assertEquals(J, dettes.get(0).day());
        // Les compteurs du jour sont retirés, ceux du lendemain restent.
        assertEquals(Map.of(), paie.secondsFor(POLICIER, J));
        assertEquals(Map.of("policier", 10L), paie.secondsFor(POLICIER, J.plusDays(1)));
        assertEquals(150L, paie.owedTotal());
        assertEquals(2, paie.owedTo(POLICIER).size());
        assertEquals(dettes, paie.debts());
        // Une seconde clôture du même jour ne fait rien.
        assertTrue(paie.closeDay(J).isEmpty());

        paie.removeDebt(dettes.get(1).id());
        assertEquals(90L, paie.owedTotal());
        // Les ids ne reviennent pas après une relecture.
        Payroll relu = Payroll.load(paie.save(new CompoundTag()));
        assertEquals(paie.debts(), relu.debts());
        relu.credit(MEDECIN, "Docteur", J.plusDays(1), "medecin", 3_600L, 60L, HUIT_HEURES);
        List<Payroll.Debt> suivantes = relu.closeDay(J.plusDays(1));
        assertTrue(suivantes.get(0).id() > dettes.get(2).id(), "ids croissants après relecture : " + suivantes);
    }

    @Test
    void lesSalairesVersesPendantLAbsenceSeLisentUneFois() {
        Payroll paie = new Payroll();
        paie.addUnseenPaid(POLICIER, 60L, Destination.SAFE);
        paie.addUnseenPaid(POLICIER, 10L, Destination.SAFE);
        paie.addUnseenPaid(POLICIER, 5L, Destination.PENDING);
        Payroll relu = Payroll.load(paie.save(new CompoundTag()));
        EnumMap<Destination, Long> vus = relu.takeUnseenPaid(POLICIER);
        assertEquals(Map.of(Destination.SAFE, 70L, Destination.PENDING, 5L), vus);
        assertTrue(relu.isDirty());
        assertTrue(relu.takeUnseenPaid(POLICIER).isEmpty(), "lu une seule fois");
    }
}
