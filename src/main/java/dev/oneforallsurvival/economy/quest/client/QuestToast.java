package dev.oneforallsurvival.economy.quest.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * La notification discrète d'une quête qui vient de finir : « Quête terminée » et son titre (coupé par « … » s'il est
 * trop long), l'icône de sa cible, 5 secondes dans le coin haut droit. Rien au chat.
 */
public final class QuestToast implements Toast {

    public static final long DISPLAY_MS = 5_000L;
    private static final String TITLE = "gui.oneforallsurvival.quests.toast";

    private final Component quest;
    private final ItemStack icon;

    public QuestToast(Component quest, ItemStack icon) {
        this.quest = quest;
        this.icon = icon.copy();
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceLastVisible) {
        Font font = toasts.getMinecraft().font;
        g.blit(TEXTURE, 0, 0, 0, 32, width(), height());
        g.renderFakeItem(icon, 8, 8);
        int room = width() - 30 - 6;
        g.drawString(font, QuestScreenLayout.cut(Component.translatable(TITLE).getString(), room, font::width), 30, 7,
                0xFF500050, false);
        g.drawString(font, QuestScreenLayout.cut(quest.getString(), room, font::width), 30, 18, 0xFF000000, false);
        return timeSinceLastVisible >= DISPLAY_MS * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE
                : Visibility.SHOW;
    }
}
