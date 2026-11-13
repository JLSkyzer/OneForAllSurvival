package dev.oneforallsurvival.economy.client;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.bench.client.BenchTheme;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.invoice.InvoiceItem;
import dev.oneforallsurvival.economy.invoice.InvoicePayPacket;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * L'écran d'une facture : émetteur, débiteur, date, motif complet, montant, identifiant court, et le bouton
 * « Payer », actif pour le seul débiteur (grisé pour tout autre). Rien n'est payé avant ce bouton ; le serveur relit
 * la case, le registre et le débiteur ({@link InvoicePayPacket}). Ce que montre l'écran vient de l'objet, qui ne fait
 * pas foi. Les textes dont la longueur dépend du contenu sont mesurés : rétrécis s'ils tiennent sur une ligne, le
 * motif coupé en lignes.
 */
public final class InvoiceScreen extends Screen {

    private static final String GUI = "gui.oneforallsurvival.invoice.view.";
    private static final int PANEL_W = 220;
    private static final int PAD = 10;
    private static final int LINE = 10;
    private static final int BUTTON_W = 94;
    private static final int BUTTON_H = 16;

    private final ItemStack invoice;
    private final int slot;
    private List<FormattedCharSequence> reasonLines = List.of();
    private int left;
    private int top;
    private int panelH;
    private int buttonsY;

    private InvoiceScreen(ItemStack invoice, int slot) {
        super(Component.translatable(GUI + "title"));
        this.invoice = invoice;
        this.slot = slot;
    }

    /** Ouvert par l'objet {@link InvoiceItem}, côté client seulement ; {@code slot} : la case qui la porte. */
    public static void open(ItemStack invoice, int slot) {
        Minecraft.getInstance().setScreen(new InvoiceScreen(invoice, slot));
    }

    private boolean isDebtor() {
        UUID debtor = InvoiceItem.debtorOf(invoice);
        return debtor != null && Minecraft.getInstance().player != null
                && debtor.equals(Minecraft.getInstance().player.getUUID()) && InvoiceItem.idOf(invoice).isPresent();
    }

    /** Lignes : titre (8), filet, de, à, date, motif (n lignes), montant, aide, boutons. */
    @Override
    protected void init() {
        reasonLines = font.split(Component.literal(InvoiceItem.reason(invoice)), PANEL_W - 2 * PAD - 8);
        int y = 24 + 3 * LINE + 4 + LINE + reasonLines.size() * LINE + 6;
        y += 14; // montant
        y += LINE + 4; // aide (débiteur ou non)
        buttonsY = y;
        panelH = buttonsY + BUTTON_H + PAD;
        left = (width - PANEL_W) / 2;
        top = Math.max(4, (height - panelH) / 2);
    }

    private int payX() {
        return left + PAD;
    }

    private int closeX() {
        return left + PANEL_W - PAD - BUTTON_W;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int y = top + buttonsY;
            if (BenchTheme.inside(mx, my, payX(), y, BUTTON_W, BUTTON_H) && isDebtor()) {
                click();
                BenchNetwork.CHANNEL.sendToServer(new InvoicePayPacket(slot));
                onClose();
                return true;
            }
            if (BenchTheme.inside(mx, my, closeX(), y, BUTTON_W, BUTTON_H)) {
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

    /** Un texte d'une ligne, rétréci (jamais coupé) pour tenir dans {@code room} pixels ; {@code scaleMax} : taille. */
    private void fitted(GuiGraphics g, Component text, int x, int y, int room, int color, float scaleMax) {
        float scale = Math.min(scaleMax, room / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - Math.min(1.0F, scale)) * 4.0F, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        int inner = PANEL_W - 2 * PAD;
        BenchTheme.plate(g, left, top, PANEL_W, panelH);
        BenchTheme.rivets(g, left, top, PANEL_W, panelH);
        String id = "#" + InvoiceItem.shortId(invoice);
        int idW = font.width(id);
        fitted(g, title, left + PAD, top + 8, inner - idW - 6, BenchTheme.TEXT, 1.0F);
        g.drawString(font, id, left + PANEL_W - PAD - idW, top + 8, BenchTheme.TEXT_DIM, false);
        BenchTheme.rule(g, left + PAD, top + 19, inner);

        int y = top + 24;
        fitted(g, Component.translatable(GUI + "issuer", InvoiceItem.issuerName(invoice)), left + PAD, y, inner,
                BenchTheme.TEXT, 1.0F);
        y += LINE;
        fitted(g, Component.translatable(GUI + "debtor", InvoiceItem.debtorName(invoice)), left + PAD, y, inner,
                BenchTheme.TEXT, 1.0F);
        y += LINE;
        fitted(g, Component.translatable(GUI + "date", InvoiceItem.date(invoice)), left + PAD, y, inner,
                BenchTheme.TEXT_DIM, 1.0F);
        y += LINE + 4;
        fitted(g, Component.translatable(GUI + "reason"), left + PAD, y, inner, BenchTheme.TEXT_DIM, 1.0F);
        y += LINE;
        BenchTheme.well(g, left + PAD, y - 2, inner, reasonLines.size() * LINE + 3);
        for (FormattedCharSequence line : reasonLines) {
            g.drawString(font, line, left + PAD + 4, y, BenchTheme.TEXT, false);
            y += LINE;
        }
        y += 6;
        Component total = Component.translatable(GUI + "amount", CoinMath.format(InvoiceItem.amount(invoice)));
        fitted(g, total, left + PAD, y, inner, BenchTheme.BRASS_LIGHT, 1.0F);
        y += 14;
        boolean debtor = isDebtor();
        Component hint = debtor ? Component.translatable(GUI + "hint.debtor")
                : Component.translatable(GUI + "hint.other", InvoiceItem.debtorName(invoice));
        fitted(g, hint, left + PAD, y, inner, BenchTheme.TEXT_DIM, 1.0F);

        int by = top + buttonsY;
        StallShopScreen.fittedButton(g, font, payX(), by, BUTTON_W, BUTTON_H, Component.translatable(GUI + "pay").getString(),
                debtor && BenchTheme.inside(mx, my, payX(), by, BUTTON_W, BUTTON_H), debtor, true);
        StallShopScreen.fittedButton(g, font, closeX(), by, BUTTON_W, BUTTON_H,
                Component.translatable(GUI + "close").getString(),
                BenchTheme.inside(mx, my, closeX(), by, BUTTON_W, BUTTON_H), true, false);
        super.render(g, mx, my, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
