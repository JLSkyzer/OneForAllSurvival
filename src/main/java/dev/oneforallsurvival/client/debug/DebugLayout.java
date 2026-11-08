package dev.oneforallsurvival.client.debug;

import dev.oneforallsurvival.client.title.TitleLayout.Rect;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Mise en page des panneaux du F3 : deux colonnes collées aux bords de l'écran.
 *
 * <p>Logique pure (aucune classe de Minecraft). Colonne de gauche : Position, Monde, Lost Island, Mods
 * (puis la vue détaillée, en vue détaillée seulement). Colonne de droite : Performances, Cible.
 * Marges et écart de 4 px. Règle « Repli des panneaux » : quand l'écran est trop petit, Position,
 * Performances et Cible restent, les autres panneaux sont retirés du résultat (ils passent dans la vue
 * détaillée, qui est un affichage à part).
 */
public final class DebugLayout {

    /** Marge à chaque bord de l'écran, en pixels d'interface. */
    public static final int MARGIN = 4;
    /** Écart entre deux panneaux, en pixels d'interface. */
    public static final int GAP = 4;

    private DebugLayout() {}

    /**
     * Emplacements de mise en page. Distinct de {@link DebugLines.Panel} car Lost Island n'y figure pas
     * (ses données viennent d'un paquet) ; l'affichage associe chaque {@code Panel} à son emplacement :
     * POSITION, WORLD, PERFORMANCE, TARGET, MODS portent le même nom ; ISLAND est le panneau Lost Island ;
     * DETAIL reçoit {@code Panel.DETAIL_ONLY} (vue détaillée uniquement).
     */
    public enum Slot {
        POSITION(true, false),
        WORLD(false, false),
        ISLAND(false, false),
        MODS(false, false),
        DETAIL(false, false),
        PERFORMANCE(true, true),
        TARGET(true, true);

        private final boolean essential;
        private final boolean right;

        Slot(boolean essential, boolean right) {
            this.essential = essential;
            this.right = right;
        }

        /** Vrai si le panneau reste quand l'écran est trop petit. */
        public boolean essential() {
            return essential;
        }

        /** Vrai si le panneau est dans la colonne de droite. */
        public boolean right() {
            return right;
        }
    }

    /** Dimensions d'un panneau en pixels d'interface. */
    public record Size(int w, int h) {}

    /**
     * Place les panneaux. Un panneau absent de {@code sizes} (ou de taille nulle) n'est pas placé ; un
     * panneau retiré par le repli est absent du résultat. {@link Slot#DETAIL} n'est placé qu'en vue détaillée.
     * Les rectangles ne se chevauchent jamais.
     *
     * @param guiW   largeur d'interface
     * @param guiH   hauteur d'interface
     * @param sizes  taille de chaque panneau
     * @param detail vrai pour la vue détaillée
     */
    public static Map<Slot, Rect> place(int guiW, int guiH, Map<Slot, Size> sizes, boolean detail) {
        List<Slot> left = new ArrayList<>();
        List<Slot> right = new ArrayList<>();
        for (Slot s : Slot.values()) {
            Size size = sizes.get(s);
            if (size == null || size.w() <= 0 || size.h() <= 0) {
                continue;
            }
            if (s == Slot.DETAIL && !detail) {
                continue;
            }
            (s.right() ? right : left).add(s);
        }

        // Les colonnes se gêneraient horizontalement : on ne garde que les panneaux essentiels à gauche.
        if (tooWide(left, right, sizes, guiW)) {
            left.removeIf(s -> !s.essential());
        }
        // Toujours trop étroit : une seule colonne, de haut en bas, sans risque de chevauchement.
        if (tooWide(left, right, sizes, guiW)) {
            List<Slot> single = new ArrayList<>(left);
            single.addAll(right);
            return stack(single, sizes, guiW, guiH, false);
        }

        Map<Slot, Rect> out = new EnumMap<>(Slot.class);
        out.putAll(stack(left, sizes, guiW, guiH, false));
        out.putAll(stack(right, sizes, guiW, guiH, true));
        return out;
    }

    private static boolean tooWide(List<Slot> left, List<Slot> right, Map<Slot, Size> sizes, int guiW) {
        return widest(left, sizes) + widest(right, sizes) + 2 * MARGIN + GAP > guiW;
    }

    private static int widest(List<Slot> slots, Map<Slot, Size> sizes) {
        int w = 0;
        for (Slot s : slots) {
            w = Math.max(w, sizes.get(s).w());
        }
        return w;
    }

    /**
     * Empile une colonne de haut en bas. Un panneau non essentiel qui ne tient pas, ou qui suit un panneau
     * retiré, est retiré (l'ordre est conservé : on ne remonte pas un petit panneau devant un grand).
     *
     * @param rightAligned colle les panneaux au bord droit
     */
    private static Map<Slot, Rect> stack(
            List<Slot> slots, Map<Slot, Size> sizes, int guiW, int guiH, boolean rightAligned) {
        Map<Slot, Rect> out = new EnumMap<>(Slot.class);
        int y = MARGIN;
        boolean dropping = false;
        for (Slot s : slots) {
            Size size = sizes.get(s);
            boolean fits = y + size.h() <= guiH - MARGIN;
            if (!s.essential() && (dropping || !fits)) {
                dropping = true;
                continue;
            }
            int x = rightAligned ? guiW - MARGIN - size.w() : MARGIN;
            out.put(s, new Rect(x, y, size.w(), size.h()));
            y += size.h() + GAP;
        }
        return out;
    }
}
