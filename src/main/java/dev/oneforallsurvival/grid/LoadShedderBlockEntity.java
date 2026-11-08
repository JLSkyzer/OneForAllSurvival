package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Le tableau de délestage. Le courant entre par la face arrière, la seule qui expose l'énergie, et repart aussitôt vers
 * quatre sorties (devant, gauche et droite vues de devant, dessus) servies dans l'ordre de leurs rangs : quand il en
 * manque, ce sont les dernières qui sont coupées. Aucune réserve : seul ce que les sorties acceptent est rendu.
 *
 * <p>Le débit est borné par tick de jeu à {@link GridConfig#shedderMaxPerTick()} fois le rendement de son état
 * d'usure, toutes poussées confondues ; une simulation respecte ce plafond sans rien compter. En panne, rien ne passe.
 * Il s'use comme les autres machines, mais seulement quand du courant est passé au tick précédent.
 *
 * <p>L'affichage (état et FE de chaque sortie) est celui du dernier tick achevé : il se fige au premier passage d'un
 * nouveau tick, que ce soit le tick du bloc ou une poussée. Les voisins ne sont pas mis en cache et une zone déchargée
 * n'est jamais chargée par une recherche. Une garde empêche le courant qui reviendrait sur l'entrée, par une boucle,
 * de relancer la répartition : il est refusé, rien n'est créé.
 */
public final class LoadShedderBlockEntity extends ElectricMachineBlockEntity {

    public static final int OUTPUT_FRONT = 0;
    public static final int OUTPUT_LEFT = 1;
    public static final int OUTPUT_RIGHT = 2;
    public static final int OUTPUT_TOP = 3;
    public static final int OUTPUTS = 4;

    /** Nombres lus par le menu : rang de chaque sortie, son état, ses FE (en deux moitiés de 16 bits), l'état du tableau. */
    public static final int DATA_RANK = 0;
    public static final int DATA_STATUS = DATA_RANK + OUTPUTS;
    public static final int DATA_FE_LOW = DATA_STATUS + OUTPUTS;
    public static final int DATA_FE_HIGH = DATA_FE_LOW + OUTPUTS;
    public static final int DATA_STATE = DATA_FE_HIGH + OUTPUTS;
    public static final int DATA_COUNT = DATA_STATE + 1;

    private static final String TAG_PRIORITIES = "priorities";

    /** {@code order[rang] = sortie} : le rang 0 est servi le premier. */
    private final int[] order = defaultOrder();

    // Le tick en cours d'accumulation : ni sauvegardé, ni montré.
    private long accTick = Long.MIN_VALUE;
    private int accUsed;
    private final int[] accDelivered = new int[OUTPUTS];
    /** Les sorties affamées par la dernière poussée réelle du tick. */
    private final boolean[] accStarved = new boolean[OUTPUTS];
    private boolean accPushed;

    // Le dernier tick achevé, ce que l'écran montre.
    private int lastTotal;
    private final int[] lastDelivered = new int[OUTPUTS];
    private final OutputStatus[] lastStatus = {OutputStatus.NONE, OutputStatus.NONE, OutputStatus.NONE, OutputStatus.NONE};

    /** Vrai pendant que le tableau interroge ses sorties : ce qui revient sur l'entrée est alors refusé. */
    private boolean relaying;

    private final IEnergyStorage input = new InputStorage();
    private LazyOptional<IEnergyStorage> inputCap = LazyOptional.of(() -> input);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index >= DATA_RANK && index < DATA_STATUS) {
                return rank(index - DATA_RANK);
            }
            if (index >= DATA_STATUS && index < DATA_FE_LOW) {
                return lastStatus[index - DATA_STATUS].ordinal();
            }
            // Le menu envoie des nombres de 16 bits : les FE voyagent en deux moitiés.
            if (index >= DATA_FE_LOW && index < DATA_FE_HIGH) {
                return lastDelivered[index - DATA_FE_LOW] & 0xFFFF;
            }
            if (index >= DATA_FE_HIGH && index < DATA_STATE) {
                return lastDelivered[index - DATA_FE_HIGH] >>> 16;
            }
            return index == DATA_STATE ? state().ordinal() : 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public LoadShedderBlockEntity(BlockPos pos, BlockState state) {
        // Aucune réserve : le courant ne fait que traverser.
        super(GridRegistry.LOAD_SHEDDER_BE.get(), pos, state, 0, 0);
    }

    private static int[] defaultOrder() {
        return new int[] {OUTPUT_FRONT, OUTPUT_LEFT, OUTPUT_RIGHT, OUTPUT_TOP};
    }

    // ---------------------------------------------------------------------------------------------
    // Priorités
    // ---------------------------------------------------------------------------------------------

    /** Les sorties dans l'ordre où elles sont servies : une permutation de 0 à 3 (copie). */
    public int[] priorities() {
        return order.clone();
    }

    /** Le rang d'une sortie, de 0 (servie la première) à 3. */
    public int rank(int output) {
        for (int r = 0; r < OUTPUTS; r++) {
            if (order[r] == output) {
                return r;
            }
        }
        return -1;
    }

    /** Donne ce rang (de 0 à 3) à la sortie ; celle qui l'avait prend l'ancien rang de la sortie. Hors limites, rien. */
    public void setPriority(int output, int rank) {
        if (output < 0 || output >= OUTPUTS || rank < 0 || rank >= OUTPUTS) {
            return;
        }
        int current = rank(output);
        if (current == rank) {
            return;
        }
        order[current] = order[rank];
        order[rank] = output;
        setChanged();
    }

    // ---------------------------------------------------------------------------------------------
    // Affichage
    // ---------------------------------------------------------------------------------------------

    /** L'état de la sortie au dernier tick achevé. */
    public OutputStatus status(int output) {
        return output >= 0 && output < OUTPUTS ? lastStatus[output] : OutputStatus.NONE;
    }

    /** Les FE livrés à la sortie au dernier tick achevé. */
    public int lastDelivered(int output) {
        return output >= 0 && output < OUTPUTS ? lastDelivered[output] : 0;
    }

    public ContainerData data() {
        return data;
    }

    /** Ce que le tableau laisse passer au plus pendant un tick, à son état d'usure. */
    public int maxPerTick() {
        return (int) Math.floor(GridConfig.shedderMaxPerTick() * efficiency());
    }

    // ---------------------------------------------------------------------------------------------
    // Faces et voisins
    // ---------------------------------------------------------------------------------------------

    private Direction facing() {
        return getBlockState().getValue(LoadShedderBlock.FACING);
    }

    /** La face d'une sortie, dans le monde. Gauche et droite s'entendent pour qui regarde le tableau de face. */
    public Direction outputSide(int output) {
        Direction facing = facing();
        return switch (output) {
            case OUTPUT_FRONT -> facing;
            case OUTPUT_LEFT -> facing.getClockWise();
            case OUTPUT_RIGHT -> facing.getCounterClockWise();
            default -> Direction.UP;
        };
    }

    /** Ce qui reçoit le courant sur cette face, ou {@code null}. Jamais de chunk chargé, jamais de cache. */
    @Nullable
    private IEnergyStorage neighbor(Direction side) {
        Level lvl = level;
        if (lvl == null) {
            return null;
        }
        BlockPos neighborPos = worldPosition.relative(side);
        // Un voisin en chunk non chargé ne doit pas être chargé (ni généré) par un simple appel de capability.
        if (!lvl.isLoaded(neighborPos)) {
            return null;
        }
        BlockEntity target = lvl.getBlockEntity(neighborPos);
        if (target == null) {
            return null;
        }
        IEnergyStorage storage = target.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).resolve().orElse(null);
        return storage != null && storage.canReceive() ? storage : null;
    }

    /** Les voisins de chaque sortie, indexés par sortie ; {@code null} là où rien n'est branché. */
    private IEnergyStorage[] targets() {
        IEnergyStorage[] targets = new IEnergyStorage[OUTPUTS];
        for (int output = 0; output < OUTPUTS; output++) {
            targets[output] = neighbor(outputSide(output));
        }
        return targets;
    }

    /**
     * Sert les sorties branchées, dans l'ordre des rangs. Ajoute à {@code delivered} et marque {@code starved}, indexés
     * par sortie. L'appelant tient la garde.
     */
    private int relay(IEnergyStorage[] targets, int offer, boolean simulate, int[] delivered, boolean[] starved) {
        List<PriorityRelay.Sink> sinks = new ArrayList<>(OUTPUTS);
        int[] outputs = new int[OUTPUTS];
        for (int output : order) {
            IEnergyStorage target = targets[output];
            if (target != null) {
                outputs[sinks.size()] = output;
                sinks.add(target::receiveEnergy);
            }
        }
        int[] byRank = new int[sinks.size()];
        boolean[] starvedByRank = new boolean[sinks.size()];
        int total = PriorityRelay.distribute(offer, sinks, simulate, byRank, starvedByRank);
        for (int i = 0; i < sinks.size(); i++) {
            delivered[outputs[i]] += byRank[i];
            starved[outputs[i]] |= starvedByRank[i];
        }
        return total;
    }

    // ---------------------------------------------------------------------------------------------
    // Passage du courant
    // ---------------------------------------------------------------------------------------------

    private int receive(int amount, boolean simulate) {
        Level lvl = level;
        if (lvl == null || lvl.isClientSide || amount <= 0 || relaying || fault() != null) {
            return 0;
        }
        long now = lvl.getGameTime();
        if (!simulate) {
            roll(now);
        }
        int used = accTick == now ? accUsed : 0;
        int offer = Math.max(0, Math.min(amount, maxPerTick() - used));
        if (simulate && offer == 0) {
            return 0;
        }
        int[] delivered = new int[OUTPUTS];
        boolean[] starved = new boolean[OUTPUTS];
        int total;
        relaying = true;
        try {
            // Plafond atteint, hors simulation : on sert quand même une offre nulle, pour savoir qui en manque.
            total = relay(targets(), offer, simulate, delivered, starved);
        } finally {
            relaying = false;
        }
        if (!simulate) {
            accUsed += total;
            for (int output = 0; output < OUTPUTS; output++) {
                accDelivered[output] += delivered[output];
            }
            System.arraycopy(starved, 0, accStarved, 0, OUTPUTS);
            accPushed = true;
        }
        return total;
    }

    /** Du courant est-il passé au tick qui précède {@code now} ? Sans rien changer. */
    private boolean passedTickBefore(long now) {
        if (accTick == now - 1) {
            return accUsed > 0;
        }
        // Figé à ce tick-ci : le total figé est celui du tick précédent, ou 0 s'il n'y a rien eu.
        return accTick == now && lastTotal > 0;
    }

    /** Fige le tick accumulé dès qu'un nouveau tick commence, puis repart de zéro pour {@code now}. */
    private void roll(long now) {
        if (accTick == now) {
            return;
        }
        boolean previous = accTick == now - 1;
        freeze(previous);
        accTick = now;
        accUsed = 0;
        Arrays.fill(accDelivered, 0);
        Arrays.fill(accStarved, false);
        accPushed = false;
    }

    /** {@code previous} : l'accumulation porte sur le tick qui vient de finir (sinon, rien n'est passé depuis). */
    private void freeze(boolean previous) {
        IEnergyStorage[] targets;
        relaying = true;
        try {
            targets = targets();
            if (fault() != null) {
                // En panne, toute sortie branchée est coupée.
                lastTotal = 0;
                for (int output = 0; output < OUTPUTS; output++) {
                    lastDelivered[output] = 0;
                    lastStatus[output] = targets[output] != null ? OutputStatus.CUT : OutputStatus.NONE;
                }
                return;
            }
            int[] delivered = previous ? accDelivered.clone() : new int[OUTPUTS];
            boolean[] starved;
            if (previous && accPushed) {
                starved = accStarved.clone();
            } else {
                // Aucune poussée : chaque sortie qui accepte encore est coupée.
                starved = new boolean[OUTPUTS];
                relay(targets, 0, false, new int[OUTPUTS], starved);
            }
            lastTotal = previous ? accUsed : 0;
            for (int output = 0; output < OUTPUTS; output++) {
                lastDelivered[output] = delivered[output];
                lastStatus[output] = PriorityRelay.status(targets[output] != null, delivered[output], starved[output]);
            }
        } finally {
            relaying = false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Machine
    // ---------------------------------------------------------------------------------------------

    /** Au travail (donc à l'usure) seulement si du courant est passé au tick précédent. */
    @Override
    protected boolean wantsToWork() {
        return level != null && passedTickBefore(level.getGameTime());
    }

    @Override
    protected int energyPerTick() {
        return 0;
    }

    @Override
    protected void workTick() {}

    @Override
    protected void afterTick(Level world, MachineState tickState) {
        roll(world.getGameTime());
    }

    /** L'entrée : relaie vers les sorties, ne garde rien, ne rend rien. */
    private final class InputStorage implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return receive(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return sum(false);
        }

        @Override
        public int getMaxEnergyStored() {
            return sum(true);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            // Volontairement sans regarder les sorties : un autre tableau qui s'interroge ne relance rien.
            return fault() == null;
        }

        /** Ce que les sorties branchées stockent, ou peuvent stocker, en somme bornée ; 0 pendant une répartition. */
        private int sum(boolean max) {
            if (relaying) {
                return 0;
            }
            relaying = true;
            try {
                long total = 0;
                for (IEnergyStorage target : targets()) {
                    if (target != null) {
                        total += max ? target.getMaxEnergyStored() : target.getEnergyStored();
                    }
                }
                return (int) Math.min(Integer.MAX_VALUE, total);
            } finally {
                relaying = false;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Capability et sauvegarde
    // ---------------------------------------------------------------------------------------------

    /** L'énergie n'est exposée que sur la face arrière : l'entrée. Aucune autre face, ni l'appel sans face. */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return side != null && side == facing().getOpposite() ? inputCap.cast() : LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inputCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inputCap = LazyOptional.of(() -> input);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray(TAG_PRIORITIES, order);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] saved = tag.getIntArray(TAG_PRIORITIES);
        System.arraycopy(isPermutation(saved) ? saved : defaultOrder(), 0, order, 0, OUTPUTS);
    }

    /** Une sauvegarde abîmée (longueur, doublon, valeur hors limites) retombe sur l'ordre d'usine. */
    private static boolean isPermutation(int[] values) {
        if (values.length != OUTPUTS) {
            return false;
        }
        boolean[] seen = new boolean[OUTPUTS];
        for (int value : values) {
            if (value < 0 || value >= OUTPUTS || seen[value]) {
                return false;
            }
            seen[value] = true;
        }
        return true;
    }
}
