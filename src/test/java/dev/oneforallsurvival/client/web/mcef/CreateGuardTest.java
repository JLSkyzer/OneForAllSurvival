package dev.oneforallsurvival.client.web.mcef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CreateGuardTest {

    @Test
    void creationReussieNeDefaitRien() {
        AtomicInteger defait = new AtomicInteger();
        CreateGuard.run(() -> {}, defait::incrementAndGet);
        assertEquals(0, defait.get());
    }

    @Test
    void creationQuiLeveDefaitPuisRelance() {
        AtomicInteger defait = new AtomicInteger();
        IllegalStateException erreur = new IllegalStateException("création refusée");
        IllegalStateException vue = assertThrows(IllegalStateException.class,
                () -> CreateGuard.run(() -> { throw erreur; }, defait::incrementAndGet));
        assertSame(erreur, vue, "l'erreur d'origine repart");
        assertEquals(1, defait.get(), "inscription et navigateur défaits une fois");
    }

    @Test
    void uneErreurDuRangementEstAttacheeSansRemplacer() {
        IllegalStateException erreur = new IllegalStateException("création refusée");
        IllegalStateException vue = assertThrows(IllegalStateException.class,
                () -> CreateGuard.run(() -> { throw erreur; }, () -> { throw new IllegalArgumentException("rangement"); }));
        assertSame(erreur, vue);
        assertEquals(1, vue.getSuppressed().length);
    }
}
