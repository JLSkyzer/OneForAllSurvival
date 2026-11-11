package dev.oneforallsurvival.explore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Les commandes de l'équipe pour l'exploration, toutes au niveau de permission 2 : {@code /cache} ici, {@code /signal}
 * et {@code /mechanism} par les tâches suivantes (chacune fournit son nœud racine et l'ajoute dans {@link #register}).
 */
public final class ExploreCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(cache());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cache() {
        return Commands.literal("cache").requires(s -> s.hasPermission(2))
                .then(Commands.literal("loot")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("table", ResourceLocationArgument.id())
                                        .executes(ExploreCommands::loot))))
                .then(Commands.literal("list").executes(ExploreCommands::list));
    }

    private static int loot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "table");
        if (!(source.getLevel().getBlockEntity(pos) instanceof CacheBlockEntity cache)) {
            source.sendFailure(Component.translatable("command.oneforallsurvival.cache.not_a_cache", pos.toShortString()));
            return 0;
        }
        if (source.getServer().getLootData().getLootTable(id) == LootTable.EMPTY) {
            source.sendFailure(Component.translatable("command.oneforallsurvival.cache.unknown_table", id.toString()));
            return 0;
        }
        cache.setLootTable(id);
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.cache.loot.set",
                pos.toShortString(), id.toString()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        List<BlockPos> all = CacheIndex.get(level).sortedFrom(BlockPos.containing(source.getPosition()));
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.cache.list.empty"), false);
            return 0;
        }
        int shown = Math.min(all.size(), ExploreConfig.cacheListMax());
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.cache.list.header",
                all.size(), level.dimension().location().toString()), false);
        for (BlockPos pos : all.subList(0, shown)) {
            source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.cache.list.entry",
                    pos.toShortString()), false);
        }
        return all.size();
    }
}
