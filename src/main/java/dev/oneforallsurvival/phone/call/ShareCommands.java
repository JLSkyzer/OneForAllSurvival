package dev.oneforallsurvival.phone.call;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * {@code /phoneshare accept|decline <demande>} (« AirDrop », retours d'essai du 27/11) : la commande des liens
 * « Accepter » et « Refuser » du chat. Ouverte à tous, elle ne sert qu'au destinataire de la demande
 * ({@link ShareService#respond}) : pour tout autre joueur, et pour une demande qui n'attend plus, le même refus.
 */
public final class ShareCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ShareService.COMMAND)
                .then(Commands.literal("accept").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> answer(ctx, true))))
                .then(Commands.literal("decline").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> answer(ctx, false)))));
    }

    private static int answer(CommandContext<CommandSourceStack> ctx, boolean accept) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ShareService.Answer a = ShareService.respond(player, IntegerArgumentType.getInteger(ctx, "id"), accept);
        String key = ShareService.MESSAGE + switch (a) {
            case SAVED -> "saved";
            case KNOWN -> "known";
            case DECLINED -> "declined";
            case NOT_FOR_YOU -> "expired";
            case NO_CARD -> "no_card";
            case FULL -> "full";
        };
        // Un message du système, pas un retour de commande : il passe même quand sendCommandFeedback est coupé.
        player.sendSystemMessage(Component.translatable(key));
        return a == ShareService.Answer.NOT_FOR_YOU ? 0 : 1;
    }
}
