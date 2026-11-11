package dev.oneforallsurvival.explore.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.bench.client.ScrollModel;
import dev.oneforallsurvival.explore.ReceiverBlockEntity;
import dev.oneforallsurvival.explore.ReceiverFrequencyPacket;
import dev.oneforallsurvival.explore.ReceiverMenu;
import dev.oneforallsurvival.explore.SignalMessage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * L'écran du récepteur : un champ de fréquence avec son bouton « Régler », la réserve et l'état d'écoute, puis
 * l'archive qui défile (le message le plus récent en haut). Au décor de l'établi ({@link BenchTheme}). Le texte des
 * messages est affiché tel quel, jamais interprété : le serveur l'a déjà nettoyé. Le serveur revalide la fréquence.
 */
public final class ReceiverScreen extends AbstractContainerScreen<ReceiverMenu> {

    private static final String GUI = "gui.oneforallsurvival.receiver.";
    private static final int WIDTH = 220;
    private static final int HEIGHT = 176;
    private static final int PAD = 8;
    private static final int FIELD_X = 70;
    private static final int FIELD_Y = 20;
    private static final int FIELD_W = 44;
    private static final int FIELD_H = 16;
    private static final int BUTTON_X = 120;
    private static final int BUTTON_W = 60;
    private static final int ENERGY_Y = 42;
    private static final int ENERGY_W = 70;
    private static final int ENERGY_H = 9;
    private static final int LIST_Y = 58;
    private static final int LIST_H = HEIGHT - LIST_Y - PAD;
    private static final int BAR_W = 6;
    private static final int LINE_H = 10;

    private record Line(FormattedCharSequence text, int color) {}

    private final ScrollModel scroll = new ScrollModel();
    private final List<Line> lines = new ArrayList<>();
    private EditBox field;
    /** La fréquence du serveur n'arrive qu'après l'ouverture : le champ la reprend une fois, à son arrivée. */
    private boolean fieldSynced;
    private int builtFor = -1;

    public ReceiverScreen(ReceiverMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = PAD;
        titleLabelY = 5;
        String keep = field == null ? "" : field.getValue();
        field = new EditBox(font, leftPos + FIELD_X + 4, topPos + FIELD_Y + 4, FIELD_W - 8, FIELD_H - 6,
                Component.translatable(GUI + "frequency"));
        field.setBordered(false);
        field.setTextColor(BenchTheme.TEXT);
        field.setMaxLength(String.valueOf(ReceiverBlockEntity.MAX_FREQUENCY).length());
        field.setFilter(text -> text.chars().allMatch(c -> c >= '0' && c <= '9'));
        field.setValue(keep);
        addRenderableWidget(field);
        setInitialFocus(field);
        buildLines();
    }

    private int textWidth() {
        return WIDTH - 2 * PAD - BAR_W - 6;
    }

    private int visibleLines() {
        return (LIST_H - 4) / LINE_H;
    }

    private void buildLines() {
        lines.clear();
        for (SignalMessage message : menu.archive()) {
            lines.add(new Line(Component.translatable(GUI + "entry", message.gameTime() / 24000L + 1,
                    String.format("%02d", clockHour(message.gameTime())),
                    String.format("%02d", clockMinute(message.gameTime())), message.frequency())
                    .getVisualOrderText(), BenchTheme.TEXT_DIM));
            for (FormattedCharSequence part : font.split(Component.literal(message.text()), textWidth())) {
                lines.add(new Line(part, BenchTheme.TEXT));
            }
        }
        scroll.setSize(lines.size(), visibleLines());
        builtFor = menu.archive().size();
    }

    /** L'heure du jeu : 0 tick, c'est 6 h du matin. */
    private static int clockHour(long gameTime) {
        return (int) ((gameTime % 24000L) / 1000L + 6) % 24;
    }

    private static int clockMinute(long gameTime) {
        return (int) ((gameTime % 1000L) * 60L / 1000L);
    }

    private int typedFrequency() {
        String value = field == null ? "" : field.getValue();
        if (value.isEmpty()) {
            return -1;
        }
        return Integer.parseInt(value);
    }

    private boolean valid() {
        return ReceiverBlockEntity.validFrequency(typedFrequency());
    }

    private void submit() {
        if (valid()) {
            BenchNetwork.CHANNEL.sendToServer(new ReceiverFrequencyPacket(menu.pos(), typedFrequency()));
        }
    }

    @Override
    protected void containerTick() {
        field.tick();
        if (!fieldSynced && menu.frequency() > 0) {
            field.setValue(String.valueOf(menu.frequency()));
            fieldSynced = true;
        }
        if (builtFor != menu.archive().size()) {
            buildLines();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && BenchTheme.inside(mx, my, leftPos + BUTTON_X, topPos + FIELD_Y, BUTTON_W, FIELD_H) && valid()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            submit();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (BenchTheme.inside(mx, my, leftPos + PAD, topPos + LIST_Y, WIDTH - 2 * PAD, LIST_H)) {
            scroll.scrollBy(-delta * 3);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (field.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submit();
                return true;
            }
            // Le champ garde toutes les touches : la touche d'inventaire ne ferme pas l'écran pendant la saisie.
            field.keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        super.render(g, mx, my, partialTick);
        if (BenchTheme.inside(mx, my, leftPos + PAD, topPos + ENERGY_Y, ENERGY_W, ENERGY_H)) {
            g.renderTooltip(font, Component.translatable(GUI + "energy", menu.energy(), ReceiverBlockEntity.CAPACITY),
                    mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        BenchTheme.plate(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rivets(g, leftPos, topPos, imageWidth, imageHeight);
        BenchTheme.rule(g, leftPos + PAD, topPos + 16, WIDTH - 2 * PAD);
        BenchTheme.well(g, leftPos + FIELD_X, topPos + FIELD_Y, FIELD_W, FIELD_H);
        boolean ok = valid();
        BenchTheme.button(g, font, leftPos + BUTTON_X, topPos + FIELD_Y, BUTTON_W, FIELD_H,
                Component.translatable(GUI + "set").getString(),
                ok && BenchTheme.inside(mx, my, leftPos + BUTTON_X, topPos + FIELD_Y, BUTTON_W, FIELD_H), ok, true);
        BenchTheme.progress(g, leftPos + PAD, topPos + ENERGY_Y, ENERGY_W, ENERGY_H,
                (double) menu.energy() / ReceiverBlockEntity.CAPACITY);
        int listX = leftPos + PAD;
        int listY = topPos + LIST_Y;
        BenchTheme.well(g, listX, listY, WIDTH - 2 * PAD, LIST_H);
        g.enableScissor(listX + 2, listY + 2, listX + WIDTH - 2 * PAD - BAR_W - 2, listY + LIST_H - 2);
        int first = scroll.first();
        for (int i = 0; i < visibleLines() && first + i < lines.size(); i++) {
            Line line = lines.get(first + i);
            g.drawString(font, line.text(), listX + 4, listY + 4 + i * LINE_H, line.color(), false);
        }
        g.disableScissor();
        BenchTheme.scrollBar(g, listX + WIDTH - 2 * PAD - BAR_W - 1, listY + 1, BAR_W, LIST_H - 2, scroll, false);
        if (lines.isEmpty()) {
            g.drawString(font, Component.translatable(GUI + "empty"), listX + 4, listY + 4, BenchTheme.TEXT_DIM, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, titleLabelX, titleLabelY, BenchTheme.TEXT, false);
        g.drawString(font, Component.translatable(GUI + "frequency"), PAD, FIELD_Y + 4, BenchTheme.TEXT_DIM, false);
        boolean on = menu.listening();
        g.drawString(font, Component.translatable(GUI + (on ? "listening" : "no_power")), PAD + ENERGY_W + 6,
                ENERGY_Y + 1, on ? BenchTheme.TEXT_OK : BenchTheme.TEXT_BAD, false);
    }
}
