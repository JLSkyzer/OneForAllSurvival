package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DebugViewsTest {

    @Test
    void leCycleRevientALaVueNormale() {
        int view = 0;
        view = DebugViews.next(view, 2);
        assertEquals(1, view);
        view = DebugViews.next(view, 2);
        assertEquals(2, view);
        view = DebugViews.next(view, 2);
        assertEquals(0, view);
    }

    @Test
    void sansPagePasDeVueDetaillee() {
        assertEquals(0, DebugViews.next(0, 0));
    }

    @Test
    void uneVueTropGrandeEstRamenee() {
        assertEquals(2, DebugViews.clamp(5, 2));
        assertEquals(0, DebugViews.clamp(1, 0));
        assertEquals(0, DebugViews.next(5, 2));
    }
}
