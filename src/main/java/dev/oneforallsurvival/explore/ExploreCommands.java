package dev.oneforallsurvival.explore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Les commandes de l'équipe pour l'exploration, toutes au niveau de permission 2 : {@code /cache} ici, {@code /signal}
 * et {@code /mechanism} par les tâches suivantes (chacune fournit son nœud racine et l'ajoute dans {@link #register}).
 */
public final class ExploreCommands {

    /** Rayon horizontal maximal de {@code /signal send}, en blocs. */
    public static final int MAX_SIGNAL_RADIUS = 30000;
    /** Bornes de {@code /mechanism set}. */
    public static final int MAX_MECHANISM_FE = 1_000_000;
    public static final int MAX_MECHANISM_SECONDS = 86400;

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(cache());
        dispatcher.register(signal());
        dispatcher.register(mechanism());
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

    private static LiteralArgumentBuilder<CommandSourceStack> signal() {
        return Commands.literal("signal").requires(s -> s.hasPermission(2))
                .then(Commands.literal("send")
                        .then(Commands.argument("frequency", IntegerArgumentType.integer(
                                ReceiverBlockEntity.MIN_FREQUENCY, ReceiverBlockEntity.MAX_FREQUENCY))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_SIGNAL_RADIUS))
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(ctx -> send(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "frequency"),
                                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                                        StringArgumentType.getString(ctx, "message")))))));
    }

    /**
     * Livre {@code text} aux récepteurs de la dimension de la source, à {@code radius} blocs (horizontaux) au plus de
     * celle-ci. Seuls les récepteurs dont le chunk est chargé, qui écoutent et sont réglés sur {@code frequency} le
     * reçoivent ; un chunk n'est jamais chargé pour l'occasion. Renvoie leur nombre, 0 sans erreur.
     */
    public static int send(CommandSourceStack source, int frequency, int radius, String text) {
        ServerLevel level = source.getLevel();
        ReceiverIndex index = ReceiverIndex.get(level);
        SignalMessage message = new SignalMessage(level.getGameTime(), frequency, text);
        int received = 0;
        for (BlockPos pos : index.within(BlockPos.containing(source.getPosition()), radius)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof ReceiverBlockEntity receiver)) {
                index.remove(pos); // le bloc a disparu sans prévenir l'index
                continue;
            }
            if (receiver.deliver(message)) {
                received++;
            }
        }
        int count = received;
        source.sendSuccess(() -> Component.translatable("command.oneforallsurvival.signal.sent", count), true);
        return count;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mechanism() {
        return Commands.literal("mechanism").requires(s -> s.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("fe_per_tick", IntegerArgumentType.integer(1, MAX_MECHANISM_FE))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_MECHANISM_SECONDS))
                                                .executes(ExploreCommands::mechanismSet)))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ExploreCommands::mechanismReset)))
                .then(Commands.literal("info")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ExploreCommands::mechanismInfo)));
    }

    private static MechanismBlockEntity mechanismAt(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        if (ctx.getSource().getLevel().getBlockEntity(pos) instanceof MechanismBlockEntity mechanism) {
            return mechanism;
        }
        ctx.getSource().sendFailure(
                Component.translatable("command.oneforallsurvival.mechanism.not_a_mechanism", pos.toShortString()));
        return null;
    }

    private static int mechanismSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        MechanismBlockEntity mechanism = mechanismAt(ctx, pos);
        if (mechanism == null) {
            return 0;
        }
        int fe = IntegerArgumentType.getInteger(ctx, "fe_per_tick");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        mechanism.setSettings(fe, seconds * 20);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.mechanism.set",
                pos.toShortString(), fe, seconds), true);
        return 1;
    }

    private static int mechanismReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        MechanismBlockEntity mechanism = mechanismAt(ctx, pos);
        if (mechanism == null) {
            return 0;
        }
        mechanism.reset();
        ctx.getSource().sendSuccess(
                () -> Component.translatable("command.oneforallsurvival.mechanism.reset", pos.toShortString()), true);
        return 1;
    }

    private static int mechanismInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        MechanismBlockEntity mechanism = mechanismAt(ctx, pos);
        if (mechanism == null) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.oneforallsurvival.mechanism.info",
                pos.toShortString(), mechanism.threshold(), mechanism.durationTicks() / 20.0,
                mechanism.progress() / 20.0, Component.translatable(mechanism.active()
                        ? "command.oneforallsurvival.mechanism.state.active"
                        : "command.oneforallsurvival.mechanism.state.idle")), false);
        return 1;
    }
}
