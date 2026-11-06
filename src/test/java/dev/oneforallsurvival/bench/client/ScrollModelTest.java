package dev.oneforallsurvival.bench.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScrollModelTest {

    private static ScrollModel model(int total, int visible) {
        ScrollModel m = new ScrollModel();
        m.setSize(total, visible);
        return m;
    }

    @Test
    void lePremierIndexResteDansLesBornes() {
        ScrollModel m = model(100, 7);
        assertEquals(0, m.first());
        m.scrollBy(-5);
        assertEquals(0, m.first());
        m.scrollBy(500);
        assertEquals(93, m.first());
        assertEquals(93, m.maxFirst());
    }

    @Test
    void uneListeCourteNeDefilePas() {
        ScrollModel m = model(5, 7);
        assertFalse(m.scrollable());
        m.scrollBy(10);
        assertEquals(0, m.first());
        assertEquals(0, m.maxFirst());
        assertEquals(100, m.thumbHeight(100));
        assertEquals(0, m.thumbTop(100));
    }

    @Test
    void uneListeVideOuUneFenetreNulleNePlantentPas() {
        ScrollModel m = model(0, 0);
        assertEquals(0, m.first());
        assertEquals(1, m.visible());
        m.scrollBy(3);
        m.reveal(10);
        assertEquals(0, m.first());
    }

    @Test
    void reduireLaListeRamenePremierIndexDansLesBornes() {
        ScrollModel m = model(100, 10);
        m.scrollTo(90);
        m.setSize(20, 10);
        assertEquals(10, m.first());
        m.setSize(3, 10);
        assertEquals(0, m.first());
    }

    @Test
    void revelerFaitDefilerLeMoinsPossible() {
        ScrollModel m = model(100, 7);
        m.reveal(3);
        assertEquals(0, m.first());
        m.reveal(7);
        assertEquals(1, m.first());
        m.reveal(50);
        assertEquals(44, m.first());
        m.reveal(46);
        assertEquals(44, m.first());
        m.reveal(10);
        assertEquals(10, m.first());
    }

    @Test
    void laPoigneeEstProportionnelleEtJamaisMinuscule() {
        ScrollModel m = model(100, 10);
        assertEquals(12, m.thumbHeight(120));
        assertEquals(0, m.thumbTop(120));
        m.setSize(1_000_000, 10);
        assertTrue(m.thumbHeight(120) >= 8);
    }

    @Test
    void tirerLaPoigneeAtteintLesDeuxExtremites() {
        ScrollModel m = model(100, 10);
        m.dragThumbTo(10_000, 120);
        assertEquals(90, m.first());
        assertEquals(120 - m.thumbHeight(120), m.thumbTop(120));
        m.dragThumbTo(-50, 120);
        assertEquals(0, m.first());
        m.dragThumbTo(60 - m.thumbHeight(120) / 2.0, 120);
        assertTrue(m.first() > 30 && m.first() < 60);
    }

    @Test
    void lePositionnementEtLaPoigneeSontCoherents() {
        ScrollModel m = model(50, 5);
        for (int first = 0; first <= 45; first += 9) {
            m.scrollTo(first);
            int top = m.thumbTop(90);
            ScrollModel other = model(50, 5);
            other.dragThumbTo(top, 90);
            assertTrue(Math.abs(other.first() - first) <= 1, "poignée en " + top + " pour " + first);
        }
    }
}
