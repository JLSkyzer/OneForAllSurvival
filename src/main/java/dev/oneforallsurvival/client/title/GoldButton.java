package dev.oneforallsurvival.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Bouton doré « Rejoindre l'île », mis en avant parmi les boutons du titre. Dessin entièrement fait ici
 * (aplats et dégradés), sans texture : aucune image de Mojang n'est copiée ni réutilisée.
 */
final class GoldButton extends Button {
    // Couleurs RGB (l'opacité vient du fondu du widget).
    private static final int TOP = 0xF2C35B, BOTTOM = 0xC2882A, BORDER = 0x6E4510, TEXT = 0x3A2306;
    private static final int HOVER_TOP = 0xFFE29A, HOVER_BOTTOM = 0xDDA23C, HOVER_BORDER = 0xFFF4D2;
    private static final int OFF_TOP = 0x8C8068, OFF_BOTTOM = 0x605645, OFF_BORDER = 0x37301F, OFF_TEXT = 0xC9C0AE;

    GoldButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int a = Mth.clamp(Mth.ceil(alpha * 255f), 0, 255) << 24;
        boolean lit = active && isHoveredOrFocused();
        int top = !active ? OFF_TOP : lit ? HOVER_TOP : TOP;
        int bottom = !active ? OFF_BOTTOM : lit ? HOVER_BOTTOM : BOTTOM;
        int border = !active ? OFF_BORDER : lit ? HOVER_BORDER : BORDER;
        int x = getX(), y = getY(), r = x + width, b = y + height;

        g.fill(x, y, r, b, a | border);
        g.fillGradient(x + 1, y + 1, r - 1, b - 1, a | top, a | bottom);
        if (active) {
            // liseré clair en haut, ombre en bas : relief sans texture
            g.fill(x + 1, y + 1, r - 1, y + 2, (Mth.ceil(alpha * 0x70) << 24) | 0xFFFFFF);
            g.fill(x + 1, b - 2, r - 1, b - 1, (Mth.ceil(alpha * 0x50) << 24) | 0x000000);
        }

        Font font = Minecraft.getInstance().font;
        Component text = getMessage();
        int tw = font.width(text);
        int tx = x + (width - tw) / 2;
        int ty = y + (height - 8) / 2;
        if (tw > width - 4) {
            renderScrollingString(g, font, text, x + 2, y, r - 2, b, a | (active ? TEXT : OFF_TEXT));
        } else {
            g.drawString(font, text, tx, ty, a | (active ? TEXT : OFF_TEXT), false);
        }
    }
}
