package dev.oneforallsurvival.bench.client;

import dev.oneforallsurvival.bench.EngineerBenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Écran provisoire : la Task 5 le remplace par l'écran principal. Il évite seulement un menu sans écran. */
public final class EngineerBenchScreen extends AbstractContainerScreen<EngineerBenchMenu> {

    public EngineerBenchScreen(EngineerBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Provisoire : rien à dessiner.
    }
}
