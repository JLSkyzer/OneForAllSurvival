package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.ItemBattery;
import dev.oneforallsurvival.machine.MachineConfig;
import dev.oneforallsurvival.machine.RepairOutcome;
import dev.oneforallsurvival.role.RoleGate;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Les gestes du dépanneur : réparer avec le poste à souder et une pièce, recharger le poste. */
public final class RepairActions {

    /** Issue d'une tentative de réparation, dans l'ordre où les contrôles sont faits. */
    public enum Result {
        REPAIRED,
        NOT_ENGINEER,
        NO_PART,
        WRONG_PART,
        NOTHING_TO_DO,
        NO_ENERGY;

        /** Clé de langue du message que le joueur reçoit. */
        public String messageKey() {
            return "message.oneforallsurvival.repair." + name().toLowerCase(Locale.ROOT);
        }
    }

    private RepairActions() {}

    /**
     * Répare {@code machine} avec la pièce {@code part}, sur la batterie de {@code welder}. La pièce et
     * l'énergie ne sont consommées que sur {@link Result#REPAIRED} ; la pièce est conservée en créatif.
     * Le joueur reçoit le message de l'issue.
     */
    public static Result repair(ServerPlayer player, ElectricMachineBlockEntity machine, ItemStack welder,
            ItemStack part) {
        Result result = attempt(player, machine, welder, part);
        if (result == Result.WRONG_PART) {
            player.displayClientMessage(Component.translatable(result.messageKey(),
                    RepairRegistry.partItem(machine.expectedPart(), false).getDescription()), true);
        } else {
            player.displayClientMessage(Component.translatable(result.messageKey()), true);
        }
        return result;
    }

    private static Result attempt(ServerPlayer player, ElectricMachineBlockEntity machine, ItemStack welder,
            ItemStack part) {
        if (!RoleGate.canDoEngineerWork(player)) {
            return Result.NOT_ENGINEER;
        }
        RepairTags.PartMatch match = RepairTags.partFor(part).orElse(null);
        if (match == null) {
            return Result.NO_PART;
        }
        RepairOutcome preview = machine.previewRepair(match.type(), match.treated());
        if (preview == RepairOutcome.WRONG_PART) {
            return Result.WRONG_PART;
        }
        if (preview == RepairOutcome.NOTHING_TO_DO) {
            return Result.NOTHING_TO_DO;
        }
        if (WelderEnergy.stored(welder) < MachineConfig.repairEnergy()) {
            return Result.NO_ENERGY;
        }
        if (machine.repair(match.type(), match.treated()) != RepairOutcome.REPAIRED) {
            return Result.NOTHING_TO_DO; // ne devrait pas arriver : l'aperçu venait de dire le contraire
        }
        WelderEnergy.spend(welder, MachineConfig.repairEnergy());
        if (!player.getAbilities().instabuild) {
            part.shrink(1);
        }
        celebrate(machine);
        return Result.REPAIRED;
    }

    /** Le coup de marteau et les étincelles, envoyés par le serveur. */
    private static void celebrate(ElectricMachineBlockEntity machine) {
        Level level = machine.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = machine.getBlockPos();
        serverLevel.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.3F, 1.2F);
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                12, 0.3, 0.2, 0.3, 0.1);
    }

    /**
     * Recharge le poste depuis {@code source} et renvoie les FE reçus. Un bloc qui laisse sortir son
     * courant est vidé par sa capability ; une machine du mod, dont la capability ne laisse rien sortir,
     * est vidée directement dans sa réserve.
     */
    public static int charge(ItemStack welder, BlockEntity source, @Nullable Direction side) {
        return ItemBattery.chargeFrom(welder, source, side, MachineConfig.welderCapacity());
    }
}
