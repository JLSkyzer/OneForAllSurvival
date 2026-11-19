package dev.oneforallsurvival.client.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackGateTest {

    private final List<WebFailure> appels = new ArrayList<>();
    private final List<Runnable> differes = new ArrayList<>();
    private final FallbackGate gate = new FallbackGate(appels::add, differes::add);

    @Test
    void horsDInitLeReplisEstImmediatEtUnique() {
        assertTrue(gate.fail(WebFailure.NO_READY));
        assertFalse(gate.fail(WebFailure.LOAD_ERROR));
        assertEquals(List.of(WebFailure.NO_READY), appels);
        assertTrue(differes.isEmpty());
    }

    @Test
    void pendantInitLeReplisEstDiffere() {
        gate.beginInit();
        assertTrue(gate.fail(WebFailure.CREATE_FAILED));
        gate.endInit();
        assertTrue(appels.isEmpty(), "rien pendant init (setScreen en cours)");
        assertEquals(1, differes.size());
        assertTrue(gate.fellBack(), "le repli est pris tout de suite, même différé");
        differes.get(0).run();
        assertEquals(List.of(WebFailure.CREATE_FAILED), appels);
    }

    @Test
    void unReplisDiffereNeSertPasUnEcranFerme() {
        gate.beginInit();
        gate.fail(WebFailure.ABSENT);
        gate.endInit();
        gate.close();
        differes.get(0).run();
        assertTrue(appels.isEmpty());
    }

    @Test
    void apresFermetureRien() {
        gate.close();
        assertFalse(gate.fail(WebFailure.NO_READY));
        assertTrue(appels.isEmpty());
        assertTrue(differes.isEmpty());
    }
}
