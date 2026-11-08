package dev.oneforallsurvival.grid.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.grid.MeterLedger;
import dev.oneforallsurvival.grid.SubscriberNamePacket;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * Le petit écran où l'ingénieur inscrit le nom de l'abonné d'un compteur ou d'un disjoncteur : 16 caractères au plus,
 * lettres, chiffres, espace, tiret. La saisie filtre déjà ces caractères ; le serveur revalide tout à la réception.
 * Au décor de l'établi ({@link BenchTheme}) : une tôle rivetée, le champ dans sa cuvette, deux boutons biseautés.
 */
public final class SubscriberNameScreen extends Screen {

    private static final String GUI = "gui.oneforallsurvival.subscriber.";
    private static final int PANEL_W = 200;
    private static final int PAD = 10;
    private static final int FIELD_H = 16;
    private static final int BUTTON_W = 86;
    private static final int BUTTON_H = 16;

    private final BlockPos pos;
    private final String current;
    private EditBox field;
    private List<FormattedCharSequence> hint = List.of();
    private int left;
    private int top;
    private int panelH;
    private int fieldY;
    private int buttonsY;

    public SubscriberNameScreen(BlockPos pos, String current) {
        super(Component.translatable(GUI + "title"));
        this.pos = pos;
        this.current = current;
    }

    @Override
    protected void init() {
        hint = font.split(Component.translatable(GUI + "hint", MeterLedger.MAX_NAME_LENGTH), PANEL_W - 2 * PAD);
        fieldY = 24 + hint.size() * 10 + 4;
        buttonsY = fieldY + FIELD_H + 10;
        panelH = buttonsY + BUTTON_H + PAD;
        left = (width - PANEL_W) / 2;
        top = (height - panelH) / 2;
        String keep = field == null ? current : field.getValue();
        field = new EditBox(font, left + PAD + 4, top + fieldY + 4, PANEL_W - 2 * PAD - 8, FIELD_H - 6,
                Component.translatable(GUI + "title"));
        field.setBordered(false);
        field.setTextColor(BenchTheme.TEXT);
        field.setMaxLength(MeterLedger.MAX_NAME_LENGTH);
        field.setFilter(text -> text.codePoints().allMatch(MeterLedger::isNameChar));
        field.setValue(keep);
        addRenderableWidget(field);
        setInitialFocus(field);
    }

    private boolean valid() {
        return field != null && MeterLedger.isValidName(field.getValue());
    }

    private int confirmX() {
        return left + PAD;
    }

    private int cancelX() {
        return left + PANEL_W - PAD - BUTTON_W;
    }

    private void submit() {
        if (valid()) {
            BenchNetwork.CHANNEL.sendToServer(new SubscriberNamePacket(pos, field.getValue().trim()));
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int y = top + buttonsY;
            if (BenchTheme.inside(mx, my, confirmX(), y, BUTTON_W, BUTTON_H) && valid()) {
                click();
                submit();
                return true;
            }
            if (BenchTheme.inside(mx, my, cancelX(), y, BUTTON_W, BUTTON_H)) {
                click();
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
    public void tick() {
        field.tick();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        BenchTheme.plate(g, left, top, PANEL_W, panelH);
        BenchTheme.rivets(g, left, top, PANEL_W, panelH);
        g.drawString(font, BenchTheme.ellipsize(font, title.getString(), PANEL_W - 2 * PAD), left + PAD, top + 8,
                BenchTheme.TEXT, false);
        BenchTheme.rule(g, left + PAD, top + 19, PANEL_W - 2 * PAD);
        for (int i = 0; i < hint.size(); i++) {
            g.drawString(font, hint.get(i), left + PAD, top + 24 + 10 * i, BenchTheme.TEXT_DIM, false);
        }
        BenchTheme.well(g, left + PAD, top + fieldY, PANEL_W - 2 * PAD, FIELD_H);
        int y = top + buttonsY;
        boolean ok = valid();
        BenchTheme.button(g, font, confirmX(), y, BUTTON_W, BUTTON_H,
                Component.translatable(GUI + "confirm").getString(),
                ok && BenchTheme.inside(mx, my, confirmX(), y, BUTTON_W, BUTTON_H), ok, true);
        BenchTheme.button(g, font, cancelX(), y, BUTTON_W, BUTTON_H, Component.translatable(GUI + "cancel").getString(),
                BenchTheme.inside(mx, my, cancelX(), y, BUTTON_W, BUTTON_H), true, false);
        super.render(g, mx, my, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
