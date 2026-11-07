package dev.oneforallsurvival.food;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * L'armoire frigorifique : 27 cases, que le courant garde au froid. Tant qu'elle travaille (courant payé, au
 * rendement de la machine), elle accumule un crédit de froid de {@link #coldFraction()} par tick ; chaque
 * quart d'heure de crédit ({@link FoodDecay#BUCKET_TICKS}) rajeunit d'autant la date de chaque aliment daté, sans
 * jamais la dépasser « maintenant ». Les aliments y vieillissent ainsi {@code fridgeFactor} fois plus lentement.
 *
 * <p>Sans courant ou en panne, aucun crédit ne s'acquiert mais rien n'est perdu : ni les dates ni les nombres ne
 * bougent. Une zone déchargée n'est pas une coupure : si l'armoire était froide en la quittant, le temps écoulé
 * est rattrapé au premier tick suivant (tout écart de ticks, chargement ou non), sans payer d'énergie. Toutes les 100 ticks, les 27 cases sont observées,
 * menu fermé : les objets reçus par une trémie sont datés et les aliments avariés deviennent des déchets.
 */
public final class FridgeBlockEntity extends ElectricMachineBlockEntity {

    public static final int SLOTS = 27;
    public static final int CAPACITY = 2000;
    public static final int MAX_RECEIVE = 100;
    public static final int ENERGY_PER_TICK = 4;
    /** Période de l'observation des cases, en ticks. */
    private static final int OBSERVE_PERIOD = 100;

    private static final String TAG_ITEMS = "items";
    private static final String TAG_CREDIT = "cold_credit";
    private static final String TAG_LAST_TICK = "last_tick";
    private static final String TAG_COLD_WHEN_LEFT = "cold_when_left";

    /** Cases lues par le menu : énergie, capacité, état. */
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_COUNT = 3;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy().getEnergyStored();
                case DATA_CAPACITY -> energy().getMaxEnergyStored();
                case DATA_STATE -> state().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> items);

    /** Froid acquis et pas encore converti en quart d'heure de décalage. */
    private double coldCredit;
    /** Dernier tick serveur joué : sert à rattraper le temps passé hors de tout chunk chargé. */
    private long lastTick;
    /** L'armoire était-elle au froid au dernier tick joué ? Sauvegardé : c'est ce qui permet de rattraper. */
    private boolean coldNow;
    private long nextObserve;

    public FridgeBlockEntity(BlockPos pos, BlockState state) {
        super(FoodRegistry.FRIDGE_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    /** Les 27 cases, pour le menu, les tests et le bloc. */
    public IItemHandlerModifiable inventory() {
        return items;
    }

    public ContainerData data() {
        return data;
    }

    /** Part du temps qui ne compte pas pour les aliments : 0,75 avec un facteur de 4. */
    public double coldFraction() {
        return 1 - 1 / FoodConfig.fridgeFactor();
    }

    /** Le froid acquis qui n'a pas encore décalé les dates, arrondi vers le bas. */
    public long coldCredit() {
        return (long) coldCredit;
    }

    /** Pour les tests : recule le dernier tick joué, comme si la zone était restée déchargée. */
    public void debugSetLastTick(long tick) {
        lastTick = tick;
    }

    // ---------------------------------------------------------------------------------------------
    // Travail
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean wantsToWork() {
        for (int i = 0; i < SLOTS; i++) {
            if (FoodStamp.isPerishable(items.getStackInSlot(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected int energyPerTick() {
        return ENERGY_PER_TICK;
    }

    @Override
    protected void workTick() {
        coldCredit += coldFraction();
        if (coldCredit >= FoodDecay.BUCKET_TICKS && level != null) {
            applyCredit(FoodStamp.now(level));
        }
    }

    @Override
    protected void afterTick(Level world, MachineState tickState) {
        long now = FoodStamp.now(world);
        // Un écart de ticks : la zone a été déchargée, ou chargée sans tiquer (bordure de la distance de simulation).
        // Une armoire neuve ou posée n'a jamais été froide (coldNow faux) : elle ne rattrape rien.
        if (coldNow && now - lastTick > 1) {
            // Même rendement que du temps travaillé, mais sans payer d'énergie.
            coldCredit += (now - lastTick) * coldFraction() * efficiency();
            setChanged();
            applyCredit(now);
        }
        if (now >= nextObserve) {
            nextObserve = now + OBSERVE_PERIOD;
            observeAll(now);
        }
        boolean cold = tickState == MachineState.RUNNING;
        if (cold != coldNow) {
            coldNow = cold;
            setChanged();
        }
        lastTick = now;
    }

    /** Convertit en quarts d'heure ce que le crédit permet, et rajeunit les aliments d'autant. */
    private void applyCredit(long now) {
        long buckets = (long) (coldCredit / FoodDecay.BUCKET_TICKS);
        if (buckets <= 0) {
            return;
        }
        long shift = buckets * FoodDecay.BUCKET_TICKS;
        coldCredit -= shift;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (FoodStamp.isPerishable(stack) && FoodStamp.isStamped(stack)) {
                long stamp = FoodStamp.stamp(stack);
                // Sur la grille du quart d'heure, jamais au-delà du quart d'heure courant, jamais reculée.
                long shifted = Math.min(stamp + shift, Math.max(stamp, FoodDecay.roundDown(now)));
                if (shifted != stamp) {
                    FoodStamp.setStamp(stack, shifted);
                }
            }
        }
        setChanged();
    }

    /** Date les objets reçus sans date et rend les déchets, sur les 27 cases, sans menu ouvert. */
    private void observeAll(long now) {
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!FoodStamp.isPerishable(stack)) {
                continue;
            }
            boolean wasStamped = FoodStamp.isStamped(stack);
            ItemStack seen = FoodEvents.observe(stack, now);
            if (seen != stack) {
                items.setStackInSlot(i, seen);
            } else if (!wasStamped) {
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    /** Jette tout le contenu au sol et vide les cases : appelé quand le bloc disparaît. */
    void dropContents(Level world, BlockPos pos) {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                items.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Faces
    // ---------------------------------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCap = LazyOptional.of(() -> items);
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ITEMS, items.serializeNBT());
        tag.putDouble(TAG_CREDIT, coldCredit);
        tag.putLong(TAG_LAST_TICK, lastTick);
        tag.putBoolean(TAG_COLD_WHEN_LEFT, coldNow);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        coldCredit = Math.max(0, tag.getDouble(TAG_CREDIT));
        lastTick = tag.getLong(TAG_LAST_TICK);
        coldNow = tag.getBoolean(TAG_COLD_WHEN_LEFT);
        // Une machine posée depuis un objet n'a pas ces clés : coldNow reste faux, il n'y a rien à rattraper.
    }
}
