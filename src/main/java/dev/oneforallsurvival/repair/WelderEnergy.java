package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.machine.ItemBattery;
import dev.oneforallsurvival.machine.MachineConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

/**
 * La batterie du poste à souder : celle de {@link ItemBattery}, à la capacité {@code welderCapacity}. On peut la
 * charger, jamais la vider de l'extérieur : seul le poste lui-même dépense, par {@link #spend}.
 */
public final class WelderEnergy {

    private WelderEnergy() {}

    /** FE contenus dans la batterie de cette pile. */
    public static int stored(ItemStack stack) {
        return ItemBattery.stored(stack, MachineConfig.welderCapacity());
    }

    /** Fixe la charge, bornée à la capacité. */
    public static void store(ItemStack stack, int fe) {
        ItemBattery.store(stack, fe, MachineConfig.welderCapacity());
    }

    /** Dépense {@code fe} si la batterie les a ; sinon ne touche à rien et renvoie faux. */
    public static boolean spend(ItemStack stack, int fe) {
        return ItemBattery.spend(stack, fe, MachineConfig.welderCapacity());
    }

    /** La capability d'énergie du poste. */
    public static ICapabilityProvider provider(ItemStack stack) {
        return new ItemBattery.Provider(stack, MachineConfig::welderCapacity);
    }
}
