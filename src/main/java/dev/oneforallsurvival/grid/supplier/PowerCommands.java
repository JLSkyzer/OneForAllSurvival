package dev.oneforallsurvival.grid.supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyConfig;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * {@code /power} : les gestes des joueurs sur leurs raccordements (lot E6, tâche 3). Ouverts à tous ; chaque geste ne
 * touche qu'aux raccordements de celui qui l'envoie.
 * <ul>
 *   <li>{@code /power consent <code>} : accord donné, la demande part au fournisseur ;</li>
 *   <li>{@code /power refuse <code>} : refus, le compteur est détaché, personne n'est facturé ;</li>
 *   <li>{@code /power resign [fournisseur]} : résiliation (dernière facture à la minuit suivante, tâche 4) ;</li>
 *   <li>{@code /power list} : ses raccordements, prix et abonnement.</li>
 * </ul>
 * Les gestes du maire et de l'équipe s'ajouteront ici (tâche 6).
 */
public final class PowerCommands {

    private static final String KEY = "command.oneforallsurvival.power.";

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("power")
                .then(Commands.literal("consent").then(Commands.argument("code", StringArgumentType.word())
                        .executes(ctx -> consent(ctx, true))))
                .then(Commands.literal("refuse").then(Commands.argument("code", StringArgumentType.word())
                        .executes(ctx -> consent(ctx, false))))
                .then(Commands.literal("resign")
                        .executes(ctx -> resign(ctx, null))
                        .then(Commands.argument("supplier", StringArgumentType.greedyString())
                                .executes(ctx -> resign(ctx, StringArgumentType.getString(ctx, "supplier")))))
                .then(Commands.literal("list").executes(PowerCommands::list)));
    }

    private static int consent(CommandContext<CommandSourceStack> ctx, boolean yes) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String code = StringArgumentType.getString(ctx, "code");
        PowerPairing.Outcome outcome = yes ? PowerPairing.consent(player, code) : PowerPairing.refuse(player, code);
        if (outcome != PowerPairing.Outcome.DONE) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "code_" + outcome.name().toLowerCase(java.util.Locale.ROOT)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + (yes ? "consented" : "refused")), false);
        return 1;
    }

    private static int resign(CommandContext<CommandSourceStack> ctx, String supplier) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int changed = PowerPairing.resign(player, supplier);
        if (changed == 0) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "resign_none"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "resigned", changed), false);
        return changed;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PowerSuppliers registry = PowerSuppliers.get(player.server);
        int count = 0;
        for (PowerSupplier supplier : registry.all()) {
            for (PowerClient client : registry.clients(supplier.id())) {
                if (!client.player().equals(player.getUUID())) {
                    continue;
                }
                count++;
                MutableComponent line = Component.translatable(KEY + "list_line", supplier.name(), client.subscriber(),
                        Component.translatable(KEY + "state." + client.state().name().toLowerCase(java.util.Locale.ROOT)),
                        CoinMath.format(supplier.price()), EconomyConfig.powerPriceUnitFe(),
                        CoinMath.format(supplier.subscription()));
                if (client.cut()) {
                    line.append(" ").append(Component.translatable(KEY + "list_cut").withStyle(ChatFormatting.RED));
                }
                Optional<String> code = registry.codeOf(client.meterSerial());
                if (client.state() == PowerClient.State.AWAITING_CONSENT && code.isPresent()) {
                    line.append(" ").append(Component.translatable(PowerPairing.MESSAGE + "button_consent")
                            .withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/power consent " + code.get()))));
                    line.append(" ").append(Component.translatable(PowerPairing.MESSAGE + "button_refuse")
                            .withStyle(s -> s.withColor(ChatFormatting.RED).withClickEvent(
                                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/power refuse " + code.get()))));
                }
                ctx.getSource().sendSuccess(() -> line, false);
            }
        }
        if (count == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "list_none"), false);
        }
        return count;
    }
}
