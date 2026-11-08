package dev.oneforallsurvival.debug;

import dev.oneforallsurvival.coast.CoastDetector;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.machine.MachineWear;
import dev.oneforallsurvival.role.RoleGate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Ce que le client ne sait pas de lui-même pour le panneau Lost Island du F3. Le filtrage par rôle se fait ici, à la
 * collecte : ce qu'un joueur n'a pas à savoir n'entre pas dans la réponse.
 *
 * @param coastZone le joueur se tient en zone d'eau salée
 * @param machine   la machine visée, ou {@code null}
 * @param meter     le compteur visé, ou {@code null}
 * @param breaker   le disjoncteur visé, ou {@code null}
 */
public record IslandDebugInfo(boolean coastZone, @Nullable Machine machine, @Nullable Meter meter,
        @Nullable Breaker breaker) {

    /** Rien du tout : la réponse sous la règle {@code reducedDebugInfo}. */
    public static final IslandDebugInfo EMPTY = new IslandDebugInfo(false, null, null, null);

    /** Marge de la portée : la main atteint la face du bloc, la distance se mesure à son centre. */
    private static final double REACH_PADDING = 1.0;
    /** Borne du nom d'abonné sur le réseau, comme à l'inscription. */
    private static final int MAX_SUBSCRIBER_LENGTH = 64;

    /**
     * @param state       l'état visible sur le bloc
     * @param coastal     la machine est en zone d'eau salée
     * @param wearPercent l'usure, de 0 (neuve) à 100 (hors d'usage) ; {@code null} si le joueur n'y a pas droit
     * @param failure     la pièce qui a lâché ; {@code null} sans panne ou si le joueur n'y a pas droit
     */
    public record Machine(MachineState state, boolean coastal, @Nullable Float wearPercent,
            @Nullable FaultType failure) {

        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(state);
            buf.writeBoolean(coastal);
            buf.writeNullable(wearPercent, FriendlyByteBuf::writeFloat);
            buf.writeNullable(failure, FriendlyByteBuf::writeEnum);
        }

        static Machine decode(FriendlyByteBuf buf) {
            return new Machine(buf.readEnum(MachineState.class), buf.readBoolean(),
                    buf.readNullable(FriendlyByteBuf::readFloat), buf.readNullable(b -> b.readEnum(FaultType.class)));
        }
    }

    /**
     * Le réseau reste visible par tous, l'abonné est déjà donné à quiconque clique sur le compteur. L'index, lui, sert à
     * la facturation : il reste à l'ingénieur, comme le relevé au testeur.
     *
     * @param subscriber l'abonné inscrit, vide si personne
     * @param fePerTick  les FE passés pendant le tick courant
     * @param index      le total des FE passés ; {@code null} si le joueur n'y a pas droit
     */
    public record Meter(String subscriber, int fePerTick, @Nullable Long index) {

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(subscriber, MAX_SUBSCRIBER_LENGTH);
            buf.writeVarInt(fePerTick);
            buf.writeNullable(index, FriendlyByteBuf::writeVarLong);
        }

        static Meter decode(FriendlyByteBuf buf) {
            return new Meter(buf.readUtf(MAX_SUBSCRIBER_LENGTH), buf.readVarInt(),
                    buf.readNullable(FriendlyByteBuf::readVarLong));
        }
    }

    /** Ce que le bloc montre à tous. */
    public record Breaker(boolean closed, boolean sealed) {

        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(closed);
            buf.writeBoolean(sealed);
        }

        static Breaker decode(FriendlyByteBuf buf) {
            return new Breaker(buf.readBoolean(), buf.readBoolean());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(coastZone);
        buf.writeNullable(machine, (b, m) -> m.encode(b));
        buf.writeNullable(meter, (b, m) -> m.encode(b));
        buf.writeNullable(breaker, (b, m) -> m.encode(b));
    }

    public static IslandDebugInfo decode(FriendlyByteBuf buf) {
        return new IslandDebugInfo(buf.readBoolean(), buf.readNullable(Machine::decode), buf.readNullable(Meter::decode),
                buf.readNullable(Breaker::decode));
    }

    /**
     * La cible retenue : {@code requested}, si elle est à portée du joueur plus un bloc et dans un chunk déjà chargé
     * (rien n'est chargé pour le F3) ; {@code null} sinon.
     */
    @Nullable
    public static BlockPos acceptedTarget(ServerPlayer player, @Nullable BlockPos requested) {
        if (requested == null || !player.canReach(requested, REACH_PADDING)
                || !player.serverLevel().isLoaded(requested)) {
            return null;
        }
        return requested;
    }

    /** Sur le thread du serveur. {@code target} vient de {@link #acceptedTarget}. */
    public static IslandDebugInfo collect(ServerPlayer player, @Nullable BlockPos target) {
        ServerLevel level = player.serverLevel();
        boolean coastZone = CoastDetector.isCoastal(level, player.blockPosition());
        if (target == null) {
            return new IslandDebugInfo(coastZone, null, null, null);
        }
        boolean engineer = RoleGate.canDoEngineerWork(player);
        BlockEntity blockEntity = level.getBlockEntity(target);
        BlockState state = level.getBlockState(target);
        Machine machine = blockEntity instanceof ElectricMachineBlockEntity be
                ? new Machine(be.state(), CoastDetector.isCoastal(level, target),
                        engineer ? (float) (MachineWear.FULL - be.condition()) : null, engineer ? be.fault() : null)
                : null;
        Meter meter = blockEntity instanceof MeterBlockEntity be
                ? new Meter(limited(be.subscriber()), be.transferredThisTick(), engineer ? be.totalTransferred() : null)
                : null;
        Breaker breaker = state.is(GridRegistry.BREAKER.get())
                ? new Breaker(state.getValue(BreakerBlock.CLOSED), state.getValue(BreakerBlock.SEALED)) : null;
        return new IslandDebugInfo(coastZone, machine, meter, breaker);
    }

    /** Un nom hors norme (monde retouché) ne doit pas faire échouer l'envoi. */
    private static String limited(String name) {
        return name.length() <= MAX_SUBSCRIBER_LENGTH ? name : name.substring(0, MAX_SUBSCRIBER_LENGTH);
    }
}
