package dev.oneforallsurvival.machine;

import net.minecraftforge.energy.EnergyStorage;

/**
 * Réserve d'énergie d'une machine : l'extérieur peut y verser du courant, jamais en retirer.
 * Seule la machine elle-même puise dedans, par {@link #consume(int)}.
 */
public class MachineEnergyStorage extends EnergyStorage {

    public MachineEnergyStorage(int capacity, int maxReceive) {
        super(capacity, maxReceive, 0);
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    /** Retire jusqu'à {@code amount} FE pour la machine et renvoie ce qui a réellement été retiré. */
    public int consume(int amount) {
        int taken = Math.min(Math.max(amount, 0), energy);
        energy -= taken;
        return taken;
    }

    /** Fixe la réserve au chargement, bornée à la capacité. */
    public void setStored(int stored) {
        energy = Math.max(0, Math.min(stored, capacity));
    }
}
