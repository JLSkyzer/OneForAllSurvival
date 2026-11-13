package dev.oneforallsurvival.economy.invoice;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * {@code /invoice list} : ses factures émises et reçues, dues ou réglées, avec l'identifiant court et, pour une
 * facture émise encore due, un lien {@code [Réglée]} ; {@code /invoice settle <id>} : l'émetteur la déclare réglée de
 * main à main (identifiant complet, ou les 8 premiers caractères). Ouvert à tous (niveau 0).
 */
public final class InvoiceCommands {

    private static final String LIST = InvoiceService.MESSAGE + "list.";
    private static final String SETTLE = InvoiceService.MESSAGE + "settle.";
    /** Lignes montrées au plus par liste (les plus récentes). */
    private static final int MAX_LINES = 20;

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("invoice")
                .then(Commands.literal("list").executes(InvoiceCommands::list))
                .then(Commands.literal("settle").then(Commands.argument("id", StringArgumentType.word())
                        .executes(InvoiceCommands::settle))));
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer self = context.getSource().getPlayerOrException();
        InvoiceRegistry registry = InvoiceRegistry.get(self.server);
        CommandSourceStack source = context.getSource();
        section(source, "issued", registry.issuedBy(self.getUUID()), true);
        section(source, "owed", registry.owedBy(self.getUUID()), false);
        return 1;
    }

    private static void section(CommandSourceStack source, String title, List<Invoice> invoices, boolean issued) {
        source.sendSuccess(() -> Component.translatable(LIST + title, invoices.size()).withStyle(ChatFormatting.GOLD), false);
        if (invoices.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(LIST + "none").withStyle(ChatFormatting.DARK_GRAY), false);
            return;
        }
        int from = Math.max(0, invoices.size() - MAX_LINES);
        for (Invoice invoice : invoices.subList(from, invoices.size())) {
            source.sendSuccess(() -> line(invoice, issued), false);
        }
        if (from > 0) {
            source.sendSuccess(() -> Component.translatable(LIST + "more", from).withStyle(ChatFormatting.DARK_GRAY), false);
        }
    }

    private static Component line(Invoice invoice, boolean issued) {
        MutableComponent out = Component.literal("[" + invoice.shortId() + "] ").withStyle(ChatFormatting.DARK_GRAY);
        out.append(Component.translatable(LIST + (issued ? "to" : "from"),
                issued ? invoice.debtorName() : invoice.issuerName(), CoinMath.format(invoice.amount()),
                invoice.reason()).withStyle(ChatFormatting.GRAY));
        out.append(" ");
        String status = invoice.status().name().toLowerCase(Locale.ROOT);
        out.append(Component.translatable(LIST + "status." + status)
                .withStyle(invoice.status() == Status.DUE ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        if (issued && invoice.status() == Status.DUE) {
            out.append(" ");
            out.append(Component.translatable(LIST + "settle_link").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/invoice settle " + invoice.id()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable(LIST + "settle_hover")))));
        }
        return out;
    }

    private static int settle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer self = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "id");
        InvoiceService.SettleResult result = InvoiceService.settle(self, id);
        Component message = Component.translatable(SETTLE + result.name().toLowerCase(Locale.ROOT), id);
        if (result == InvoiceService.SettleResult.DONE) {
            context.getSource().sendSuccess(() -> message, false);
            return 1;
        }
        context.getSource().sendFailure(message);
        return 0;
    }
}
