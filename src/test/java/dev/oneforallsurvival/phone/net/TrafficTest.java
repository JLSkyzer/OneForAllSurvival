package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * Le trafic ouvert sur les antennes d'un trajet : compté tant que la poignée est ouverte, une fois par antenne, rendu à
 * la fermeture (une seule fois), jamais écrit sur le disque. Sans monde : le registre seul.
 */
class TrafficTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static RelayPlanner.AntennaPoint point(UUID id) {
        return new RelayPlanner.AntennaPoint(id, "op", TestDimensions.OVERWORLD, BlockPos.ZERO, true);
    }

    private static RelayPlanner.Route route(UUID caller, UUID callee) {
        return new RelayPlanner.Route(point(caller), point(callee));
    }

    @Test
    void unAppelOuvertCompteSurChaqueAntenneDuTrajet() {
        PhoneOperators registry = new PhoneOperators();
        TrafficHandle h = PhoneNetwork.openTraffic(registry, route(A, B), TrafficKind.CALL);
        assertEquals(1, registry.traffic(A, TrafficKind.CALL));
        assertEquals(1, registry.traffic(B, TrafficKind.CALL));
        assertEquals(0, registry.traffic(A, TrafficKind.FACETIME));
        h.close();
        assertEquals(0, registry.traffic(A, TrafficKind.CALL));
        assertEquals(0, registry.traffic(B, TrafficKind.CALL));
    }

    @Test
    void unTrajetSousUneSeuleAntenneNeCompteQuUneFois() {
        PhoneOperators registry = new PhoneOperators();
        TrafficHandle h = PhoneNetwork.openTraffic(registry, route(A, A), TrafficKind.FACETIME);
        assertEquals(1, registry.traffic(A, TrafficKind.FACETIME));
        h.close();
        assertEquals(0, registry.traffic(A, TrafficKind.FACETIME));
    }

    @Test
    void fermerDeuxFoisNeRetireQuUneFois() {
        PhoneOperators registry = new PhoneOperators();
        TrafficHandle premier = PhoneNetwork.openTraffic(registry, route(A, B), TrafficKind.CALL);
        TrafficHandle second = PhoneNetwork.openTraffic(registry, route(A, B), TrafficKind.CALL);
        assertEquals(2, registry.traffic(A, TrafficKind.CALL));
        premier.close();
        premier.close();
        assertEquals(1, registry.traffic(A, TrafficKind.CALL), "la seconde poignée tient encore");
        second.close();
        assertEquals(0, registry.traffic(A, TrafficKind.CALL));
    }

    @Test
    void appelEtVisioSeComptentAPart() {
        PhoneOperators registry = new PhoneOperators();
        TrafficHandle appel = PhoneNetwork.openTraffic(registry, route(A, B), TrafficKind.CALL);
        TrafficHandle visio = PhoneNetwork.openTraffic(registry, route(A, B), TrafficKind.FACETIME);
        assertEquals(1, registry.traffic(B, TrafficKind.CALL));
        assertEquals(1, registry.traffic(B, TrafficKind.FACETIME));
        appel.close();
        assertEquals(0, registry.traffic(B, TrafficKind.CALL));
        assertEquals(1, registry.traffic(B, TrafficKind.FACETIME));
        visio.close();
    }

    @Test
    void leTraficNEstPasSauvegarde() {
        PhoneOperators registry = new PhoneOperators();
        TrafficHandle h = PhoneNetwork.openTraffic(registry, route(A, B), TrafficKind.CALL);
        assertFalse(registry.isDirty(), "ouvrir du trafic ne salit pas le registre");
        PhoneOperators relu = PhoneOperators.load(registry.save(new CompoundTag()),
                loc -> TestDimensions.key(loc.getPath()));
        assertEquals(0, relu.traffic(A, TrafficKind.CALL), "rien sur le disque");
        h.close();
        assertFalse(registry.isDirty(), "fermer non plus");
    }

    @Test
    void unePoigneeDUnAncienRegistreNeTouchePasAuNouveau() {
        PhoneOperators ancien = new PhoneOperators();
        TrafficHandle h = PhoneNetwork.openTraffic(ancien, route(A, B), TrafficKind.CALL);
        PhoneOperators nouveau = new PhoneOperators();
        TrafficHandle n = PhoneNetwork.openTraffic(nouveau, route(A, B), TrafficKind.CALL);
        h.close();
        assertEquals(1, nouveau.traffic(A, TrafficKind.CALL), "le monde suivant garde son trafic");
        n.close();
    }
}
