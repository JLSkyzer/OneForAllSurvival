package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.EconomyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * « Motif de la dépense » : la petite fenêtre qu'ouvre un retrait à l'écran du trésor. Un champ (1 à
 * {@link EconomyConfig#treasuryReasonMax()} caractères, sans saut de ligne), « Confirmer » et « Annuler » ; l'un comme
 * l'autre (et Échap) reviennent à l'écran du trésor, dont le menu est resté ouvert sur le serveur. Le serveur revérifie
 * le motif ({@code TreasuryActionPacket.validReason}) : les bornes d'ici ne servent qu'à griser « Confirmer ». Les
 * textes dont la longueur dépend du contenu sont mesurés et rétrécis pour tenir.
 */
public final class TreasuryReasonScreen extends Screen {

    private static final String GUI = "gui.oneforallsurvival.treasury.reason.";
    private static final int PANEL_W = 220;
    private static final int PAD = 10;
    private static final int FIELD_H = 16;
    private static final int BUTTON_W = 94;
    private static final int BUTTON_H = 16;

    private static final int WHAT_Y = 24;
    private static final int LABEL_Y = WHAT_Y + 14;
    private static final int FIELD_Y = LABEL_Y + 11;
    private static final int BUTTONS_Y = FIELD_Y + FIELD_H + 12;
    private static final int PANEL_H = BUTTONS_Y + BUTTON_H + PAD;

    private final TreasuryScreen parent;
    private final CoinRequest request;
    private final String initial;
    private EditBox reason;
    private int left;
    private int top;

    public TreasuryReasonScreen(TreasuryScreen parent, CoinRequest request, String initial) {
        super(Component.translatable(GUI + "title"));
        this.parent = parent;
        this.request = request;
        this.initial = initial;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        String kept = reason == null ? initial : reason.getValue();
        reason = new EditBox(font, left + PAD + 4, top + FIELD_Y + 4, PANEL_W - 2 * PAD - 8, FIELD_H - 6,
                Component.translatable(GUI + "field"));
        reason.setBordered(false);
        reason.setTextColor(BenchTheme.TEXT);
        reason.setMaxLength(EconomyConfig.treasuryReasonMax());
        reason.setFilter(text -> text.chars().noneMatch(c -> Character.isISOControl(c) || c == '§'));
        reason.setValue(kept);
        addRenderableWidget(reason);
        setInitialFocus(reason);
    }

    private boolean valid() {
        String text = reason == null ? "" : reason.getValue().strip();
        return !text.isEmpty() && text.length() <= EconomyConfig.treasuryReasonMax();
    }

    private int confirmX() {
        return left + PAD;
    }

    private int cancelX() {
        return left + PANEL_W - PAD - BUTTON_W;
    }

    private void confirm() {
        if (valid()) {
            parent.sendWithReason(request, reason.getValue().strip());
            onClose();
        }
    }

    /** Retour à l'écran du trésor (Annuler, Échap, ou après Confirmer). */
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int y = top + BUTTONS_Y;
            if (BenchTheme.inside(mx, my, confirmX(), y, BUTTON_W, BUTTON_H) && valid()) {
                click();
                confirm();
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
            confirm();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void tick() {
        reason.tick();
        // Le serveur a fermé ou remplacé l'écran du trésor (trésor déplacé, droit perdu) : plus rien à confirmer.
        if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu != parent.getMenu()) {
            minecraft.setScreen(null);
        }
    }

    /** Un texte d'une ligne, rétréci (jamais coupé) pour tenir dans {@code room} pixels. */
    private void fitted(GuiGraphics g, Component text, int x, int y, int room, int color) {
        float scale = Math.min(1.0F, room / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - scale) * 4.0F, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        int inner = PANEL_W - 2 * PAD;
        BenchTheme.plate(g, left, top, PANEL_W, PANEL_H);
        BenchTheme.rivets(g, left, top, PANEL_W, PANEL_H);
        fitted(g, title, left + PAD, top + 8, inner, BenchTheme.TEXT);
        BenchTheme.rule(g, left + PAD, top + 19, inner);

        Coin coin = Coin.values()[Math.max(0, Math.min(Coin.values().length - 1, request.coin()))];
        Component what = Component.translatable(GUI + "what", request.amount(),
                Component.translatable(coin.unitKey(request.amount())));
        fitted(g, what, left + PAD, top + WHAT_Y, inner, BenchTheme.BRASS_LIGHT);

        String counter = (reason == null ? 0 : reason.getValue().length()) + "/" + EconomyConfig.treasuryReasonMax();
        int counterW = font.width(counter);
        fitted(g, Component.translatable(GUI + "field"), left + PAD, top + LABEL_Y, inner - counterW - 6,
                BenchTheme.TEXT_DIM);
        g.drawString(font, counter, left + PANEL_W - PAD - counterW, top + LABEL_Y, BenchTheme.TEXT_DIM, false);
        BenchTheme.well(g, left + PAD, top + FIELD_Y, inner, FIELD_H);

        int y = top + BUTTONS_Y;
        boolean ok = valid();
        StallShopScreen.fittedButton(g, font, confirmX(), y, BUTTON_W, BUTTON_H,
                Component.translatable(GUI + "confirm").getString(),
                ok && BenchTheme.inside(mx, my, confirmX(), y, BUTTON_W, BUTTON_H), ok, true);
        StallShopScreen.fittedButton(g, font, cancelX(), y, BUTTON_W, BUTTON_H,
                Component.translatable(GUI + "cancel").getString(),
                BenchTheme.inside(mx, my, cancelX(), y, BUTTON_W, BUTTON_H), true, false);
        super.render(g, mx, my, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
