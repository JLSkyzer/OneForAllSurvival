package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                        for (boolean detail : new boolean[] {false, true}) {
                            Map<Slot, Rect> r = DebugLayout.place(gw, gh, tailles(pw, ph), detail);
                            List<Rect> rects = List.copyOf(r.values());
                            for (int i = 0; i < rects.size(); i++) {
                                for (int j = i + 1; j < rects.size(); j++) {
                                    assertFalse(rects.get(i).intersects(rects.get(j)),
                                            gw + "x" + gh + " " + pw + "x" + ph + " " + detail);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void colonnesEtMarges() {
        Map<Slot, Rect> r = DebugLayout.place(854, 480, tailles(120, 50), false);
        assertEquals(new Rect(4, 4, 120, 50), r.get(Slot.POSITION));
        assertEquals(new Rect(4, 58, 120, 50), r.get(Slot.WORLD));
        assertEquals(new Rect(854 - 4 - 120, 4, 120, 50), r.get(Slot.PERFORMANCE));
        assertEquals(new Rect(854 - 4 - 120, 58, 120, 50), r.get(Slot.TARGET));
        assertTrue(r.containsKey(Slot.ISLAND) && r.containsKey(Slot.MODS));
        assertFalse(r.containsKey(Slot.DETAIL));
        assertTrue(DebugLayout.place(854, 480, tailles(120, 50), true).containsKey(Slot.DETAIL));
    }

    @Test
    void petiteFenetre() {
        // 320x240 : quatre panneaux de 100x60 à gauche ne tiennent pas, seuls les essentiels restent.
        Map<Slot, Rect> r = DebugLayout.place(320, 240, tailles(100, 60), false);
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
        Map<Slot, Rect> r = DebugLayout.place(200, 400, tailles(120, 40), false);
        assertTrue(r.containsKey(Slot.POSITION) && r.containsKey(Slot.PERFORMANCE) && r.containsKey(Slot.TARGET));
        assertFalse(r.containsKey(Slot.WORLD));
    }
}
