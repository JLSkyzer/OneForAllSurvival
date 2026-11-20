package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class RelayPlannerTest {

    private static final int RANGE = 270;
    private static final ResourceKey<Level> OVERWORLD = TestDimensions.OVERWORLD;
    private static final ResourceKey<Level> NETHER = TestDimensions.NETHER;

    private static final Map<String, String> NOMS = Map.of("alpha", "Alpha", "mairie", "Mairie");

    private static UUID id(long n) {
        return new UUID(0L, n);
    }

    private static RelayPlanner.AntennaPoint antenna(long id, String operator, int x, boolean inService) {
        return new RelayPlanner.AntennaPoint(id(id), operator, OVERWORLD, new BlockPos(x, 64, 0), inService);
    }

    private static RelayPlanner.Endpoint at(String operator, int x) {
        return new RelayPlanner.Endpoint(operator, OVERWORLD, new BlockPos(x, 64, 0));
    }

    private static RelayPlanner.Snapshot net(RelayPlanner.AntennaPoint... antennas) {
        return new RelayPlanner.Snapshot(List.of(antennas), NOMS, RANGE);
    }

    private static RelayPlanner.Failed failed(RelayPlanner.Result r) {
        assertTrue(r instanceof RelayPlanner.Failed, "échec attendu, vu " + r);
        return (RelayPlanner.Failed) r;
    }

    @Test
    void memeOperateurMemeAntenne() {
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        RelayPlanner.Result r = RelayPlanner.plan(at("alpha", 10), at("alpha", 20), net(a));
        assertEquals(new RelayPlanner.Route(a, a), r);
    }

    @Test
    void deuxOperateursDeuxAntennes() {
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        RelayPlanner.AntennaPoint b = antenna(2, "mairie", 500, true);
        RelayPlanner.Result r = RelayPlanner.plan(at("alpha", 10), at("mairie", 490), net(a, b));
        assertEquals(new RelayPlanner.Route(a, b), r);
    }

    @Test
    void lAppelantSansSignal() {
        RelayPlanner.AntennaPoint loin = antenna(1, "alpha", 1000, true);
        RelayPlanner.AntennaPoint horsTension = antenna(2, "alpha", 5, false);
        RelayPlanner.AntennaPoint autreOperateur = antenna(3, "mairie", 5, true);
        RelayPlanner.Failed f = failed(RelayPlanner.plan(at("alpha", 0), at("mairie", 10),
                net(loin, horsTension, autreOperateur)));
        assertEquals(RelayFailure.Reason.CALLER_NO_SIGNAL, f.reason());
    }

    @Test
    void antenneDeLAppeleHorsTension() {
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        RelayPlanner.AntennaPoint mairie = antenna(2, "mairie", 500, false);
        RelayPlanner.Failed f = failed(RelayPlanner.plan(at("alpha", 10), at("mairie", 490), net(a, mairie)));
        assertEquals(RelayFailure.Reason.CALLEE_ANTENNA_DOWN, f.reason());
        assertEquals("Mairie", f.operatorName());
    }

    @Test
    void appeleHorsReseau() {
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        RelayPlanner.AntennaPoint mairie = antenna(2, "mairie", 500, true);
        // À 1 000 blocs de l'antenne de la mairie : rien ne couvre l'appelé.
        RelayPlanner.Failed f = failed(RelayPlanner.plan(at("alpha", 10), at("mairie", 1500), net(a, mairie)));
        assertEquals(RelayFailure.Reason.CALLEE_NO_COVERAGE, f.reason());
    }

    @Test
    void appeleCouvertSeulementParUnAutreOperateurEstHorsReseau() {
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        RelayPlanner.AntennaPoint mairie = antenna(2, "mairie", 5000, true);
        // L'appelé est sous l'antenne d'Alpha, mais sa carte est celle de la mairie.
        RelayPlanner.Failed f = failed(RelayPlanner.plan(at("alpha", 10), at("mairie", 20), net(a, mairie)));
        assertEquals(RelayFailure.Reason.CALLEE_NO_COVERAGE, f.reason());
    }

    @Test
    void operateurDeLAppeleSupprime() {
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        RelayPlanner.AntennaPoint fantome = antenna(2, "disparu", 20, true);
        RelayPlanner.Failed f = failed(RelayPlanner.plan(at("alpha", 10), at("disparu", 20), net(a, fantome)));
        assertEquals(RelayFailure.Reason.CALLEE_OPERATOR_REMOVED, f.reason());
    }

    @Test
    void operateurDeLAppelantSupprime() {
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        RelayPlanner.AntennaPoint fantome = antenna(2, "disparu", 10, true);
        RelayPlanner.Failed f = failed(RelayPlanner.plan(at("disparu", 10), at("alpha", 20), net(a, fantome)));
        assertEquals(RelayFailure.Reason.CALLER_OPERATOR_REMOVED, f.reason());
    }

    @Test
    void lAppelantPasseAvantLAppeleQuandLesDeuxOperateursSontSupprimes() {
        RelayPlanner.Failed f = failed(RelayPlanner.plan(at("un", 10), at("deux", 20), net()));
        assertEquals(RelayFailure.Reason.CALLER_OPERATOR_REMOVED, f.reason());
    }

    @Test
    void lAppelantSansSignalPasseAvantUnAppeleSupprime() {
        RelayPlanner.Failed f = failed(RelayPlanner.plan(at("alpha", 10), at("disparu", 20), net()));
        assertEquals(RelayFailure.Reason.CALLER_NO_SIGNAL, f.reason());
    }

    @Test
    void autreDimensionPasDeSignal() {
        RelayPlanner.AntennaPoint nether = new RelayPlanner.AntennaPoint(id(1), "alpha", NETHER,
                new BlockPos(0, 64, 0), true);
        RelayPlanner.Failed f = failed(RelayPlanner.plan(at("alpha", 10), at("alpha", 20), net(nether)));
        assertEquals(RelayFailure.Reason.CALLER_NO_SIGNAL, f.reason());
        // La même antenne sert dans sa dimension.
        RelayPlanner.Endpoint dansLeNether = new RelayPlanner.Endpoint("alpha", NETHER, new BlockPos(10, 64, 0));
        assertEquals(Optional.of(nether), RelayPlanner.best(dansLeNether, net(nether)));
    }

    @Test
    void laMeilleureEstLaPlusProcheEnService() {
        RelayPlanner.AntennaPoint toutPres = antenna(1, "alpha", 11, false);
        RelayPlanner.AntennaPoint pres = antenna(2, "alpha", 40, true);
        RelayPlanner.AntennaPoint loin = antenna(3, "alpha", 200, true);
        RelayPlanner.Snapshot reseau = net(loin, toutPres, pres);
        assertEquals(Optional.of(pres), RelayPlanner.best(at("alpha", 10), reseau));
        RelayPlanner.Result r = RelayPlanner.plan(at("alpha", 10), at("alpha", 190), reseau);
        assertEquals(new RelayPlanner.Route(pres, loin), r);
    }

    @Test
    void aDistanceEgaleLIdentifiantLePlusPetitGagne() {
        RelayPlanner.AntennaPoint petit = new RelayPlanner.AntennaPoint(id(1), "alpha", OVERWORLD,
                new BlockPos(-30, 64, 0), true);
        RelayPlanner.AntennaPoint grand = new RelayPlanner.AntennaPoint(id(2), "alpha", OVERWORLD,
                new BlockPos(30, 64, 0), true);
        for (List<RelayPlanner.AntennaPoint> ordre : List.of(List.of(petit, grand), List.of(grand, petit))) {
            RelayPlanner.Snapshot reseau = new RelayPlanner.Snapshot(ordre, NOMS, RANGE);
            assertEquals(Optional.of(petit), RelayPlanner.best(at("alpha", 0), reseau));
        }
    }

    @Test
    void laPorteeEstInclusiveEtAuDelaiIlNYAPlusDeSignal() {
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        assertEquals(Optional.of(a), RelayPlanner.best(at("alpha", 270), net(a)));
        assertEquals(Optional.empty(), RelayPlanner.best(at("alpha", 271), net(a)));
    }

    @Test
    void unReseauSansPorteeNeCouvreRien() {
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        RelayPlanner.Snapshot sansPortee = new RelayPlanner.Snapshot(List.of(a), NOMS, 0);
        assertEquals(Optional.empty(), RelayPlanner.best(at("alpha", 0), sansPortee));
        assertEquals(Optional.empty(), RelayPlanner.anyInService(OVERWORLD, new BlockPos(0, 64, 0), sansPortee));
    }

    @Test
    void urgenceParNimporteQuelOperateur() {
        RelayPlanner.AntennaPoint alpha = antenna(1, "alpha", 100, true);
        RelayPlanner.AntennaPoint mairie = antenna(2, "mairie", 20, true);
        RelayPlanner.Snapshot reseau = net(alpha, mairie);
        // La plus proche, quel que soit l'opérateur.
        assertEquals(Optional.of(mairie), RelayPlanner.anyInService(OVERWORLD, new BlockPos(0, 64, 0), reseau));
        // La plus proche est hors tension : la suivante prend l'appel.
        RelayPlanner.AntennaPoint eteinte = antenna(3, "mairie", 5, false);
        assertEquals(Optional.of(mairie),
                RelayPlanner.anyInService(OVERWORLD, new BlockPos(0, 64, 0), net(alpha, mairie, eteinte)));
    }

    @Test
    void urgenceIgnoreLesAntennesHorsPorteeHorsTensionOuDUneAutreDimension() {
        RelayPlanner.AntennaPoint eteinte = antenna(1, "alpha", 10, false);
        RelayPlanner.AntennaPoint loin = antenna(2, "alpha", 5000, true);
        RelayPlanner.AntennaPoint nether = new RelayPlanner.AntennaPoint(id(3), "alpha", NETHER,
                new BlockPos(10, 64, 0), true);
        assertEquals(Optional.empty(),
                RelayPlanner.anyInService(OVERWORLD, new BlockPos(0, 64, 0), net(eteinte, loin, nether)));
    }

    @Test
    void urgenceIgnoreLesAntennesDUnOperateurSupprime() {
        RelayPlanner.AntennaPoint fantome = antenna(1, "disparu", 10, true);
        assertEquals(Optional.empty(), RelayPlanner.anyInService(OVERWORLD, new BlockPos(0, 64, 0), net(fantome)));
    }

    @Test
    void appeleDansLeNetherSansAntenne() {
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        // Antenne de l'opérateur de l'appelé, hors tension, à la même position mais dans l'overworld.
        RelayPlanner.AntennaPoint mairie = antenna(2, "mairie", 500, false);
        RelayPlanner.Endpoint appele = new RelayPlanner.Endpoint("mairie", NETHER, new BlockPos(500, 64, 0));
        RelayPlanner.Failed f = failed(RelayPlanner.plan(at("alpha", 10), appele, net(a, mairie)));
        assertEquals(RelayFailure.Reason.CALLEE_NO_COVERAGE, f.reason());
        assertEquals("", f.operatorName());
    }

    @Test
    void unChampNulEstRefuseAvantDeFaireLeverLePlan() {
        BlockPos pos = new BlockPos(0, 64, 0);
        assertThrows(NullPointerException.class, () -> new RelayPlanner.Endpoint(null, OVERWORLD, pos));
        assertThrows(NullPointerException.class, () -> new RelayPlanner.Endpoint("alpha", null, pos));
        assertThrows(NullPointerException.class, () -> new RelayPlanner.Endpoint("alpha", OVERWORLD, null));
        assertThrows(NullPointerException.class,
                () -> new RelayPlanner.AntennaPoint(null, "alpha", OVERWORLD, pos, true));
        assertThrows(NullPointerException.class,
                () -> new RelayPlanner.AntennaPoint(id(1), null, OVERWORLD, pos, true));
        assertThrows(NullPointerException.class,
                () -> new RelayPlanner.AntennaPoint(id(1), "alpha", null, pos, true));
        assertThrows(NullPointerException.class,
                () -> new RelayPlanner.AntennaPoint(id(1), "alpha", OVERWORLD, null, true));
        RelayPlanner.AntennaPoint a = antenna(1, "alpha", 0, true);
        assertThrows(NullPointerException.class, () -> new RelayPlanner.Route(null, a));
        assertThrows(NullPointerException.class, () -> new RelayPlanner.Route(a, null));
        assertThrows(NullPointerException.class, () -> new RelayPlanner.Failed(null, ""));
        assertThrows(NullPointerException.class,
                () -> new RelayPlanner.Failed(RelayFailure.Reason.CALLER_NO_SIGNAL, null));
        assertThrows(NullPointerException.class, () -> new RelayPlanner.Snapshot(null, NOMS, RANGE));
        assertThrows(NullPointerException.class, () -> new RelayPlanner.Snapshot(List.of(), null, RANGE));
    }
}
