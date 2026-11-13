package dev.oneforallsurvival.economy.trade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * {@code /trade <joueur>} propose un échange ; {@code /trade accept <joueur>} et {@code /trade deny <joueur>}
 * répondent à une proposition (les liens cliquables du message y mènent). Ouvert à tous (niveau 0).
 */
public final class TradeCommands {

    private static final String REFUSED = TradeService.MESSAGE + "refused.";

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("trade")
                .then(Commands.literal("accept").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> answer(c, true))))
                .then(Commands.literal("deny").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> answer(c, false))))
                .then(Commands.argument("player", EntityArgument.player()).executes(TradeCommands::propose)));
    }

    private static int propose(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer self = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        return tell(context, TradeService.request(self, target), target);
    }

    private static int answer(CommandContext<CommandSourceStack> context, boolean accept) throws CommandSyntaxException {
        ServerPlayer self = context.getSource().getPlayerOrException();
        ServerPlayer from = EntityArgument.getPlayer(context, "player");
        TradeService.RequestResult result = accept ? TradeService.accept(self, from) : TradeService.deny(self, from);
        return tell(context, result, from);
    }

    private static int tell(CommandContext<CommandSourceStack> context, TradeService.RequestResult result,
            ServerPlayer other) {
        switch (result) {
            case SENT, ACCEPTED -> {
                return 1;
            }
            case DENIED -> {
                context.getSource().sendSuccess(() -> Component.translatable(TradeService.MESSAGE + "you_denied",
                        other.getGameProfile().getName()), false);
                return 1;
            }
            default -> {
                context.getSource().sendFailure(Component.translatable(REFUSED + result.name().toLowerCase(Locale.ROOT),
                        other.getGameProfile().getName()));
                return 0;
            }
        }
    }
}
