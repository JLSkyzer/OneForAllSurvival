package dev.oneforallsurvival.food;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * La couveuse électrique : quatre places, un œuf par place, du courant, et un petit animal naît au bout du temps réglé
 * (vingt minutes de marche au départ). Chaque œuf a sa propre progression, qui avance à chaque tick de travail et qui
 * repart de zéro dès que sa place est vidée ou garnie d'un autre œuf, ce que la machine voit à chaque tick.
 *
 * <p>À maturité, l'animal naît petit au-dessus de la machine, ou à défaut sur l'un de ses quatre côtés. Sans place,
 * l'œuf reste mûr dans sa case et la machine réessaie toutes les {@value #RETRY_PERIOD} ticks : il n'est jamais perdu.
 *
 * <p>C'est la seule machine du mod qu'une coupure prive de son contenu. Le compteur de coupure monte à chaque tick passé
 * sans courant (réserve vide), retombe à zéro dès que la machine tourne, et reste figé en panne : une machine en panne ne
 * perd rien. Au-delà du seuil, la couvée est détruite, avec fumée et son d'extinction. Un monde éteint ou une zone
 * déchargée ne comptent pas : rien ne tourne alors. Progressions et compteur sont sauvegardés.
 *
 * <p>Les faces : le dessus insère, aucune n'extrait, les autres n'exposent rien. Le contenu est jeté quand le bloc
 * disparaît.
 */
public final class IncubatorBlockEntity extends ElectricMachineBlockEntity {

    public static final int SLOTS = 4;
    public static final int CAPACITY = 2000;
    public static final int MAX_RECEIVE = 40;

    /** Ticks entre deux essais d'un œuf mûr qui n'a pas trouvé de place. */
    private static final int RETRY_PERIOD = 20;
    private static final String TAG_ITEMS = "items";
    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_OUTAGE = "outage";

    /** Cases lues par le menu : quatre progressions, durée, énergie, capacité, état, coupure, durée de coupure. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = DATA_PROGRESS + SLOTS;
    public static final int DATA_ENERGY = DATA_TOTAL + 1;
    public static final int DATA_CAPACITY = DATA_ENERGY + 1;
    public static final int DATA_STATE = DATA_CAPACITY + 1;
    public static final int DATA_OUTAGE = DATA_STATE + 1;
    public static final int DATA_OUTAGE_TOTAL = DATA_OUTAGE + 1;
    public static final int DATA_COUNT = DATA_OUTAGE_TOTAL + 1;

    /** Les quatre places : un seul œuf chacune, et seulement un œuf de la liste de la configuration. */
    public static ItemStackHandler newEggHandler(Runnable onChanged) {
        return new ItemStackHandler(SLOTS) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return IncubatorEggs.entityFor(stack) != null;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    private final ItemStackHandler items = newEggHandler(this::setChanged);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index >= DATA_PROGRESS && index < DATA_PROGRESS + SLOTS) {
                return Math.min(progress[index], FoodConfig.incubatorHatchTicks());
            }
            return switch (index) {
                case DATA_TOTAL -> FoodConfig.incubatorHatchTicks();
                case DATA_ENERGY -> energy().getEnergyStored();
                case DATA_CAPACITY -> energy().getMaxEnergyStored();
                case DATA_STATE -> state().ordinal();
                case DATA_OUTAGE -> Math.min(outage, Short.MAX_VALUE);
                case DATA_OUTAGE_TOTAL -> FoodConfig.incubatorOutageTicks();
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

    private LazyOptional<IItemHandler> allCap = LazyOptional.of(() -> items);
    private LazyOptional<IItemHandler> topCap = LazyOptional.of(() -> new TopFace(items));

    /** Ticks de travail de chaque œuf. */
    private final int[] progress = new int[SLOTS];
    /** L'objet de chaque case à la dernière lecture : sert à voir qu'une case a été vidée ou changée. */
    private final Item[] tracked = new Item[SLOTS];
    /** Ticks consécutifs passés sans courant, œufs en place. */
    private int outage;

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(FoodRegistry.INCUBATOR_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    /** Les quatre places, pour le menu, les tests et le bloc. */
    public IItemHandlerModifiable inventory() {
        return items;
    }

    public ContainerData data() {
        return data;
    }

    /** Ticks de travail de l'œuf de cette place. */
    public int progress(int slot) {
        return progress[slot];
    }

    /** Ticks consécutifs passés sans courant. */
    public int outageTicks() {
        return outage;
    }

    // ---------------------------------------------------------------------------------------------
    // Œufs
    // ---------------------------------------------------------------------------------------------

    private boolean hasEgg(int slot) {
        return IncubatorEggs.entityFor(items.getStackInSlot(slot)) != null;
    }

    private boolean anyEgg() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (hasEgg(slot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Une place vidée ou changée repart de zéro. Lu une fois par tick (via {@link #energyPerTick()}) : un clic de souris
     * qui vide une case un instant, dans le même tick, n'y change rien.
     */
    private void syncSlots() {
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            Item now = stack.isEmpty() ? null : stack.getItem();
            if (now != tracked[slot]) {
                tracked[slot] = now;
                if (progress[slot] != 0) {
                    progress[slot] = 0;
                    setChanged();
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Travail
    // ---------------------------------------------------------------------------------------------

    @Override
    protected boolean wantsToWork() {
        return anyEgg();
    }

    @Override
    protected int energyPerTick() {
        syncSlots();
        return anyEgg() ? FoodConfig.incubatorEnergy() : 0;
    }

    @Override
    protected void workTick() {
        int total = FoodConfig.incubatorHatchTicks();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!hasEgg(slot)) {
                continue;
            }
            if (progress[slot] < total) {
                progress[slot]++;
                setChanged();
                if (progress[slot] < total) {
                    continue;
                }
            } else if (level.getGameTime() % RETRY_PERIOD != 0) {
                continue; // mûr, sans place : on réessaie de temps en temps seulement
            }
            hatch(slot);
        }
    }

    /** Fait naître l'animal de cette place s'il y a de la place ; sinon l'œuf reste mûr, intact. */
    private void hatch(int slot) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        EntityType<?> type = IncubatorEggs.entityFor(items.getStackInSlot(slot));
        Entity animal = type == null ? null : type.create(server);
        if (animal == null || !placeAnimal(server, animal)) {
            return;
        }
        if (animal instanceof Mob mob) {
            mob.setPersistenceRequired(); // une couvée ne s'évapore pas en s'éloignant du joueur
            mob.finalizeSpawn(server, server.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.BREEDING, null, null);
        }
        if (animal instanceof AgeableMob ageable) {
            ageable.setBaby(true);
        }
        if (!server.addFreshEntity(animal)) {
            return;
        }
        items.setStackInSlot(slot, ItemStack.EMPTY);
        tracked[slot] = null;
        progress[slot] = 0;
        setChanged();
    }

    /** Pose l'animal au-dessus de la machine, à défaut sur l'un de ses côtés, là où rien ne le gêne. */
    private boolean placeAnimal(ServerLevel server, Entity animal) {
        float yaw = server.random.nextFloat() * 360F;
        if (tryPlace(server, animal, worldPosition.above(), yaw)) {
            return true;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (tryPlace(server, animal, worldPosition.relative(side), yaw)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryPlace(ServerLevel server, Entity animal, BlockPos at, float yaw) {
        animal.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0F);
        return server.noCollision(animal);
    }

    // ---------------------------------------------------------------------------------------------
    // Coupure
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void afterTick(Level world, MachineState tickState) {
        int before = outage;
        if (!anyEgg()) {
            outage = 0;
        } else if (tickState == MachineState.NO_POWER) {
            outage++;
        } else if (tickState == MachineState.RUNNING) {
            outage = 0;
        } // en panne : le compteur reste tel quel
        if (outage > FoodConfig.incubatorOutageTicks()) {
            loseBrood(world);
        }
        if (outage != before) {
            setChanged();
        }
    }

    /** La coupure a duré trop longtemps : les œufs sont détruits, avec fumée et son d'extinction. */
    private void loseBrood(Level world) {
        for (int slot = 0; slot < SLOTS; slot++) {
            items.setStackInSlot(slot, ItemStack.EMPTY);
            tracked[slot] = null;
            progress[slot] = 0;
        }
        outage = 0;
        setChanged();
        if (world instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5, 20, 0.25, 0.15, 0.25, 0.02);
        }
        world.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
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

    /** Le dessus : il voit les quatre places, y insère, et n'en sort rien. */
    private static final class TopFace extends RangedWrapper {
        TopFace(IItemHandlerModifiable handler) {
            super(handler, 0, SLOTS);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) {
                return allCap.cast();
            }
            return side == Direction.UP ? topCap.cast() : LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        allCap.invalidate();
        topCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        allCap = LazyOptional.of(() -> items);
        topCap = LazyOptional.of(() -> new TopFace(items));
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ITEMS, items.serializeNBT());
        tag.putIntArray(TAG_PROGRESS, progress);
        tag.putInt(TAG_OUTAGE, outage);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        int[] saved = tag.getIntArray(TAG_PROGRESS);
        for (int slot = 0; slot < SLOTS; slot++) {
            progress[slot] = slot < saved.length ? Math.max(0, saved[slot]) : 0;
            ItemStack stack = items.getStackInSlot(slot);
            tracked[slot] = stack.isEmpty() ? null : stack.getItem();
        }
        outage = Math.max(0, tag.getInt(TAG_OUTAGE));
    }
}
