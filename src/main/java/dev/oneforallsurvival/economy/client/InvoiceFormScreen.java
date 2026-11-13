package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.invoice.InvoiceIssuePacket;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Le formulaire d'une facture, ouvert par le carnet sur un joueur proche : montant en cuivres (avec son aperçu en
 * pièces), motif, bouton « Émettre ». Le serveur revérifie tout à la réception ({@link InvoiceIssuePacket}) ; les
 * bornes vérifiées ici ne servent qu'à griser le bouton. Les textes dont la longueur dépend du contenu (nom, aperçu
 * du montant) sont mesurés et rétrécis pour tenir.
 */
public final class InvoiceFormScreen extends Screen {

    private static final String GUI = "gui.oneforallsurvival.invoice.form.";
    private static final int PANEL_W = 220;
    private static final int PAD = 10;
    private static final int FIELD_H = 16;
    private static final int BUTTON_W = 94;
    private static final int BUTTON_H = 16;

    private static final int AMOUNT_LABEL_Y = 24;
    private static final int AMOUNT_Y = AMOUNT_LABEL_Y + 11;
    private static final int PREVIEW_Y = AMOUNT_Y + FIELD_H + 4;
    private static final int REASON_LABEL_Y = PREVIEW_Y + 14;
    private static final int REASON_Y = REASON_LABEL_Y + 11;
    private static final int BUTTONS_Y = REASON_Y + FIELD_H + 12;
    private static final int PANEL_H = BUTTONS_Y + BUTTON_H + PAD;

    private final UUID target;
    private EditBox amount;
    private EditBox reason;
    private int left;
    private int top;

    public InvoiceFormScreen(UUID target, String targetName) {
        super(Component.translatable(GUI + "title", targetName));
        this.target = target;
    }

    /** Ouvert par {@code InvoiceFormPacket}, côté client seulement. */
    public static void open(UUID target, String targetName) {
        Minecraft.getInstance().setScreen(new InvoiceFormScreen(target, targetName));
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        String keptAmount = amount == null ? "" : amount.getValue();
        String keptReason = reason == null ? "" : reason.getValue();
        amount = field(top + AMOUNT_Y, String.valueOf(EconomyConfig.invoiceMaxAmount()).length(), GUI + "amount");
        amount.setFilter(text -> text.chars().allMatch(c -> c >= '0' && c <= '9'));
        amount.setValue(keptAmount);
        reason = field(top + REASON_Y, EconomyConfig.invoiceReasonMax(), GUI + "reason");
        reason.setFilter(text -> text.chars().noneMatch(c -> Character.isISOControl(c) || c == '§'));
        reason.setValue(keptReason);
        addRenderableWidget(amount);
        addRenderableWidget(reason);
        setInitialFocus(amount);
    }

    private EditBox field(int y, int maxLength, String key) {
        EditBox box = new EditBox(font, left + PAD + 4, y + 4, PANEL_W - 2 * PAD - 8, FIELD_H - 6, Component.translatable(key));
        box.setBordered(false);
        box.setTextColor(BenchTheme.TEXT);
        box.setMaxLength(maxLength);
        return box;
    }

    /** Le montant saisi, ou -1 s'il est vide ou illisible. */
    private long amountValue() {
        String text = amount == null ? "" : amount.getValue();
        if (text.isEmpty()) {
            return -1L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private boolean amountValid() {
        long value = amountValue();
        return value >= 1L && value <= EconomyConfig.invoiceMaxAmount();
    }

    private boolean reasonValid() {
        String text = reason == null ? "" : reason.getValue().strip();
        return !text.isEmpty() && text.length() <= EconomyConfig.invoiceReasonMax();
    }

    private boolean valid() {
        return amountValid() && reasonValid();
    }

    private int issueX() {
        return left + PAD;
    }

    private int cancelX() {
        return left + PANEL_W - PAD - BUTTON_W;
    }

    private void submit() {
        if (valid()) {
            BenchNetwork.CHANNEL.sendToServer(new InvoiceIssuePacket(target, amountValue(), reason.getValue().strip()));
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int y = top + BUTTONS_Y;
            if (BenchTheme.inside(mx, my, issueX(), y, BUTTON_W, BUTTON_H) && valid()) {
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
        if (key == GLFW.GLFW_KEY_TAB) {
            boolean toReason = amount.isFocused();
            amount.setFocused(!toReason);
            reason.setFocused(toReason);
            setFocused(toReason ? reason : amount);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void tick() {
        amount.tick();
        reason.tick();
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

        fitted(g, Component.translatable(GUI + "amount"), left + PAD, top + AMOUNT_LABEL_Y, inner, BenchTheme.TEXT_DIM);
        BenchTheme.well(g, left + PAD, top + AMOUNT_Y, inner, FIELD_H);
        long value = amountValue();
        Component preview;
        int previewColor;
        if (value < 0L) {
            preview = Component.translatable(GUI + "amount.hint", CoinMath.format(EconomyConfig.invoiceMaxAmount()));
            previewColor = BenchTheme.TEXT_DIM;
        } else if (!amountValid()) {
            preview = Component.translatable(GUI + "amount.bad", CoinMath.format(EconomyConfig.invoiceMaxAmount()));
            previewColor = BenchTheme.TEXT_BAD;
        } else {
            preview = Component.translatable(GUI + "amount.preview", CoinMath.format(value));
            previewColor = BenchTheme.BRASS_LIGHT;
        }
        fitted(g, preview, left + PAD, top + PREVIEW_Y, inner, previewColor);

        String counter = (reason == null ? 0 : reason.getValue().length()) + "/" + EconomyConfig.invoiceReasonMax();
        int counterW = font.width(counter);
        fitted(g, Component.translatable(GUI + "reason"), left + PAD, top + REASON_LABEL_Y, inner - counterW - 6,
                BenchTheme.TEXT_DIM);
        g.drawString(font, counter, left + PANEL_W - PAD - counterW, top + REASON_LABEL_Y, BenchTheme.TEXT_DIM, false);
        BenchTheme.well(g, left + PAD, top + REASON_Y, inner, FIELD_H);

        int y = top + BUTTONS_Y;
        boolean ok = valid();
        StallShopScreen.fittedButton(g, font, issueX(), y, BUTTON_W, BUTTON_H,
                Component.translatable(GUI + "issue").getString(),
                ok && BenchTheme.inside(mx, my, issueX(), y, BUTTON_W, BUTTON_H), ok, true);
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
