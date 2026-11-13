package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import java.util.EnumMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Un prix dessiné en pièces : « 12 [rubis] 3 [émeraude] 5 [cuivre] », chaque nombre suivi de l'icône de sa pièce à
 * demi-taille. Bien plus court que le texte (« 12 rubis 3 émeraudes 5 cuivres ») : le prix le plus long permis
 * (100 000 000 cuivres, et 9 999 rubis 9 émeraudes 9 or 9 argent 9 cuivres) tient en une centaine de pixels. Si la
 * place manque malgré tout, l'ensemble est rétréci pour tenir ({@link #draw}) ; le texte complet est pour l'infobulle.
 */
public final class PriceLabel {

    /** Côté de l'icône d'une pièce, en pixels (l'objet à demi-taille). */
    private static final int ICON = 8;
    private static final int GAP_AFTER_NUMBER = 1;
    private static final int GAP_AFTER_ICON = 3;
    /**
     * Échelle minimale. Aux bornes permises (1 000 000 000 cuivres au plus par le réglage) le prix le plus large fait
     * environ 111 pixels : dans 87 pixels, l'échelle reste au-dessus de 0,75.
     */
    private static final float MIN_SCALE = 0.5F;

    private PriceLabel() {}

    /** La largeur naturelle du prix, en pixels. */
    public static int width(Font font, long amount) {
        EnumMap<Coin, Long> parts = parts(amount);
        int width = 0;
        for (Long count : parts.values()) {
            width += font.width(String.valueOf(count)) + GAP_AFTER_NUMBER + ICON + GAP_AFTER_ICON;
        }
        return Math.max(0, width - GAP_AFTER_ICON);
    }

    /**
     * Dessine le prix à partir de {@code (x, y)} (haut du texte), rétréci s'il dépasse {@code maxWidth}.
     *
     * @return la largeur dessinée
     */
    public static int draw(GuiGraphics g, Font font, long amount, int x, int y, int maxWidth, int color) {
        int natural = width(font, amount);
        float scale = natural <= maxWidth || natural == 0 ? 1.0F : Math.max(MIN_SCALE, maxWidth / (float) natural);
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - scale) * 4.0F, 0);
        g.pose().scale(scale, scale, 1.0F);
        int cx = 0;
        Coin[] coins = Coin.values();
        EnumMap<Coin, Long> parts = parts(amount);
        for (int i = coins.length - 1; i >= 0; i--) {
            Long count = parts.get(coins[i]);
            if (count == null) {
                continue;
            }
            String text = String.valueOf(count);
            g.drawString(font, text, cx, 0, color, false);
            cx += font.width(text) + GAP_AFTER_NUMBER;
            BenchTheme.item(g, font, new ItemStack(coins[i].item()), cx, 0, ICON / 16.0F, false);
            cx += ICON + GAP_AFTER_ICON;
        }
        g.pose().popPose();
        return Math.min(maxWidth, Math.round(natural * scale));
    }

    /** Les pièces du prix, de la plus grande à la plus petite ; « 0 cuivre » pour un montant nul. */
    private static EnumMap<Coin, Long> parts(long amount) {
        EnumMap<Coin, Long> parts = CoinMath.decompose(amount);
        if (parts.isEmpty()) {
            parts.put(Coin.COPPER, 0L);
        }
        return parts;
    }
}
