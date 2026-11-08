package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.debug.DebugLayout.Placement;
import dev.oneforallsurvival.client.debug.DebugLayout.Size;
import dev.oneforallsurvival.client.debug.DebugLayout.Slot;
import dev.oneforallsurvival.client.title.TitleLayout.Rect;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DebugLayoutTest {

    private static Map<Slot, Size> tailles(int w, int h) {
        Map<Slot, Size> m = new EnumMap<>(Slot.class);
        for (Slot s : Slot.values()) {
            m.put(s, new Size(w, h));
        }
        return m;
    }

    @Test
    void aucunChevauchement() {
        for (int gw = 100; gw <= 900; gw += 37) {
            for (int gh = 60; gh <= 500; gh += 29) {
                for (int pw : new int[] {60, 120, 170}) {
                    for (int ph : new int[] {30, 70, 110}) {
                        Map<Slot, Rect> r = DebugLayout.place(gw, gh, tailles(pw, ph));
                        List<Rect> rects = List.copyOf(r.values());
                        for (int i = 0; i < rects.size(); i++) {
                            for (int j = i + 1; j < rects.size(); j++) {
                                assertFalse(rects.get(i).intersects(rects.get(j)),
                                        gw + "x" + gh + " " + pw + "x" + ph);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void colonnesEtMarges() {
        Map<Slot, Rect> r = DebugLayout.place(854, 480, tailles(120, 50));
        assertEquals(new Rect(4, 4, 120, 50), r.get(Slot.POSITION));
        assertEquals(new Rect(4, 58, 120, 50), r.get(Slot.WORLD));
        assertEquals(new Rect(854 - 4 - 120, 4, 120, 50), r.get(Slot.PERFORMANCE));
        assertEquals(new Rect(854 - 4 - 120, 58, 120, 50), r.get(Slot.TARGET));
        assertTrue(r.containsKey(Slot.ISLAND) && r.containsKey(Slot.MODS));
        assertFalse(r.containsKey(Slot.DETAIL));
    }

    @Test
    void petiteFenetre() {
        // 320x240 : quatre panneaux de 100x60 à gauche ne tiennent pas, Mods est retiré.
        Map<Slot, Rect> r = DebugLayout.place(320, 240, tailles(100, 60));
        assertTrue(r.containsKey(Slot.POSITION));
        assertTrue(r.containsKey(Slot.PERFORMANCE));
        assertTrue(r.containsKey(Slot.TARGET));
        assertFalse(r.containsKey(Slot.MODS));
        for (Rect rect : r.values()) {
            assertTrue(rect.x() >= 4 && rect.right() <= 316 && rect.y() >= 4 && rect.bottom() <= 236);
        }
    }

    @Test
    void ecranTresEtroitEmpileEnUneColonne() {
        Map<Slot, Rect> r = DebugLayout.place(200, 400, tailles(120, 40));
        assertTrue(r.containsKey(Slot.POSITION) && r.containsKey(Slot.PERFORMANCE) && r.containsKey(Slot.TARGET));
        assertFalse(r.containsKey(Slot.WORLD));
    }

    @Test
    void unEssentielTropGrandEstRogne() {
        Rect p = DebugLayout.place(320, 240, tailles(100, 500)).get(Slot.POSITION);
        assertTrue(p.y() >= 4 && p.bottom() <= 236);
        Rect large = DebugLayout.place(100, 240, tailles(500, 30)).get(Slot.POSITION);
        assertTrue(large.x() >= 4 && large.right() <= 96);
    }

    @Test
    void rienNEstPerduEnVueDetaillee() {
        // Panneaux de 100x60 (chrome 12 + 6 lignes de 8) et un panneau Détail de 40 lignes, large de 110.
        for (int[] ecran : new int[][] {{320, 240}, {200, 120}, {100, 60}, {854, 480}}) {
            Map<Slot, Size> sizes = tailles(100, 60);
            sizes.put(Slot.DETAIL, new Size(110, 12 + 40 * 8));
            List<List<Placement>> pages = DebugLayout.placeDetail(ecran[0], ecran[1], sizes, 8, 12);
            Map<Slot, Integer> lignes = new EnumMap<>(Slot.class);
            for (List<Placement> page : pages) {
                for (int i = 0; i < page.size(); i++) {
                    Placement p = page.get(i);
                    lignes.merge(p.slot(), p.lineCount(), Integer::sum);
                    assertTrue(p.rect().x() >= 4 && p.rect().y() >= 4 && p.rect().bottom() <= Math.max(ecran[1] - 4, 5));
                    for (int j = i + 1; j < page.size(); j++) {
                        assertFalse(p.rect().intersects(page.get(j).rect()), ecran[0] + "x" + ecran[1]);
                    }
                }
            }
            for (Slot s : Slot.values()) {
                int attendu = s == Slot.DETAIL ? 40 : 6;
                assertEquals(attendu, lignes.get(s), s + " " + ecran[0] + "x" + ecran[1]);
            }
        }
    }

    @Test
    void laVueDetailleeDebordeSurUneSecondePage() {
        assertTrue(DebugLayout.placeDetail(320, 240, tailles(100, 120), 8, 12).size() >= 2);
        assertTrue(DebugLayout.placeDetail(320, 240, new EnumMap<>(Slot.class), 8, 12).isEmpty());
    }
}
