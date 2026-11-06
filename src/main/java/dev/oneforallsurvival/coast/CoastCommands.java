package dev.oneforallsurvival.coast;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Commande {@code /coast} : déclarer les zones côtières et vérifier un lieu. Réservée aux opérateurs. */
public final class CoastCommands {

    private static final SuggestionProvider<CommandSourceStack> ZONE_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CoastSavedData.get(ctx.getSource().getServer()).zones().list()
                    .stream().map(CoastZones.Zone::id), builder);

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("coast")
                .requires(s -> s.hasPermission(2));
        root.then(Commands.literal("zone")
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(CoastCommands::add)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                                .executes(CoastCommands::remove)))
                .then(Commands.literal("list").executes(CoastCommands::list)));
        root.then(Commands.literal("check")
                .executes(ctx -> check(ctx, BlockPos.containing(ctx.getSource().getPosition())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> check(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos")))));
        dispatcher.register(root);
    }

    private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "id");
        BlockPos from = BlockPosArgument.getBlockPos(ctx, "from");
        BlockPos to = BlockPosArgument.getBlockPos(ctx, "to");
        var dimension = ctx.getSource().getLevel().dimension().location();
        if (!CoastSavedData.get(ctx.getSource().getServer()).add(id, dimension, from, to)) {
            ctx.getSource().sendFailure(Component.translatable("command.oneforallsurvival.coast.zone.duplicate", id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.coast.zone.added", id), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        if (!CoastSavedData.get(ctx.getSource().getServer()).remove(id)) {
            ctx.getSource().sendFailure(Component.translatable("command.oneforallsurvival.coast.zone.unknown", id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.coast.zone.removed", id), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var zones = CoastSavedData.get(ctx.getSource().getServer()).zones().list();
        if (zones.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.coast.zone.list.empty"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.coast.zone.list.header"), false);
        for (CoastZones.Zone zone : zones) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.coast.zone.list.entry",
                    zone.id(), zone.dimension().toString(), zone.min().toShortString(), zone.max().toShortString()), false);
        }
        return zones.size();
    }

    private static int check(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        CoastDetector.clearCache(); // un diagnostic doit refléter le monde tel qu'il est maintenant
        boolean coastal = CoastDetector.isCoastal(ctx.getSource().getLevel(), pos);
        String key = coastal ? "command.oneforallsurvival.coast.check.yes" : "command.oneforallsurvival.coast.check.no";
        ctx.getSource().sendSuccess(() -> Component.translatable(key, pos.toShortString()), false);
        return coastal ? 1 : 0;
    }
}
