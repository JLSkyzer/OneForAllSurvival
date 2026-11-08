package dev.oneforallsurvival.grid.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.grid.MeterLedger;
import dev.oneforallsurvival.grid.SubscriberNamePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Le petit écran où l'ingénieur inscrit le nom de l'abonné d'un compteur ou d'un disjoncteur : 16 caractères au plus,
 * lettres, chiffres, espace, tiret. La saisie filtre déjà ces caractères ; le serveur revalide tout à la réception.
 */
public final class SubscriberNameScreen extends Screen {

    private static final String GUI = "gui.oneforallsurvival.subscriber.";
    private static final int FIELD_W = 160;

    private final BlockPos pos;
    private final String current;
    private EditBox field;
    private Button confirm;

    public SubscriberNameScreen(BlockPos pos, String current) {
        super(Component.translatable(GUI + "title"));
        this.pos = pos;
        this.current = current;
    }

    @Override
    protected void init() {
        int x = (width - FIELD_W) / 2;
        int y = height / 2 - 20;
        field = new EditBox(font, x, y, FIELD_W, 20, Component.translatable(GUI + "title"));
        field.setMaxLength(MeterLedger.MAX_NAME_LENGTH);
        field.setFilter(text -> text.codePoints().allMatch(MeterLedger::isNameChar));
        field.setValue(current);
        field.setResponder(text -> updateButton());
        addRenderableWidget(field);
        setInitialFocus(field);
        confirm = addRenderableWidget(Button.builder(Component.translatable(GUI + "confirm"), b -> submit())
                .bounds(x, y + 28, FIELD_W / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(GUI + "cancel"), b -> onClose())
                .bounds(x + FIELD_W / 2 + 2, y + 28, FIELD_W / 2 - 2, 20).build());
        updateButton();
    }

    private void updateButton() {
        if (confirm != null) {
            confirm.active = MeterLedger.isValidName(field.getValue());
        }
    }

    private void submit() {
        if (MeterLedger.isValidName(field.getValue())) {
            BenchNetwork.CHANNEL.sendToServer(new SubscriberNamePacket(pos, field.getValue().trim()));
            onClose();
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 52, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(GUI + "hint", MeterLedger.MAX_NAME_LENGTH),
                width / 2, height / 2 - 36, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
