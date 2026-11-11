package dev.oneforallsurvival.explore;

import dev.oneforallsurvival.machine.MachineEnergyStorage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

/**
 * Le récepteur de signaux : une réserve de FE qui ne reçoit que du courant, une fréquence et l'archive des 50 derniers
 * messages. Il ne s'use pas et ne tombe pas en panne (ce n'est donc pas une machine électrique du mod). Il consomme
 * {@link ExploreConfig#receiverEnergy()} par tick tant qu'il en a ; sans cette réserve ou dans un chunk déchargé il
 * manque le message, sans file d'attente.
 */
public final class ReceiverBlockEntity extends BlockEntity {

    public static final int CAPACITY = 2000;
    public static final int MAX_ARCHIVE = 50;
    public static final int MIN_FREQUENCY = 1;
    public static final int MAX_FREQUENCY = 9999;

    /** Indices des nombres que le menu suit : énergie, fréquence, écoute (0 ou 1). */
    public static final int DATA_ENERGY = 0;
    public static final int DATA_FREQUENCY = 1;
    public static final int DATA_LISTENING = 2;
    public static final int DATA_COUNT = 3;

    private static final int MAX_RECEIVE = 200;
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_FREQUENCY = "Frequency";
    private static final String TAG_ARCHIVE = "Archive";
    private static final String TAG_TIME = "Time";
    private static final String TAG_TEXT = "Text";

    private final MachineEnergyStorage storage = new MachineEnergyStorage(CAPACITY, MAX_RECEIVE) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) {
                setChanged();
            }
            return received;
        }
    };
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> storage);
    private final List<SignalMessage> archive = new ArrayList<>();
    private int frequency = MIN_FREQUENCY;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> storage.getEnergyStored();
                case DATA_FREQUENCY -> frequency;
                case DATA_LISTENING -> listening() ? 1 : 0;
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

    public ReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ExploreRegistry.RECEIVER_BE.get(), pos, state);
    }

    public static boolean validFrequency(int frequency) {
        return frequency >= MIN_FREQUENCY && frequency <= MAX_FREQUENCY;
    }

    public MachineEnergyStorage energy() {
        return storage;
    }

    public ContainerData data() {
        return data;
    }

    public int frequency() {
        return frequency;
    }

    /** Règle la fréquence ; une valeur hors de 1 à 9999 est refusée (renvoie faux, rien ne change). */
    public boolean setFrequency(int value) {
        if (!validFrequency(value)) {
            return false;
        }
        frequency = value;
        setChanged();
        return true;
    }

    /** Vrai si la réserve couvre au moins un tick d'écoute. */
    public boolean listening() {
        return storage.getEnergyStored() >= ExploreConfig.receiverEnergy();
    }

    /** L'archive, le message le plus récent en premier ; une copie. */
    public List<SignalMessage> archive() {
        return List.copyOf(archive);
    }

    /**
     * Livre un message. Refusé (faux, rien d'enregistré) si le récepteur n'écoute pas ou n'est pas sur la fréquence du
     * message. Sinon le message prend la tête de l'archive (le 51e plus ancien tombe) et le voyant « non lu » s'allume.
     */
    public boolean deliver(SignalMessage message) {
        if (!listening() || message.frequency() != frequency) {
            return false;
        }
        archive.add(0, message);
        while (archive.size() > MAX_ARCHIVE) {
            archive.remove(archive.size() - 1);
        }
        setUnread(true);
        setChanged();
        return true;
    }

    /** Le joueur a ouvert l'écran : le voyant « non lu » s'éteint. */
    public void markRead() {
        setUnread(false);
    }

    private void setUnread(boolean unread) {
        if (level instanceof ServerLevel server) {
            BlockState state = getBlockState();
            if (state.hasProperty(ReceiverBlock.UNREAD) && state.getValue(ReceiverBlock.UNREAD) != unread) {
                server.setBlock(worldPosition, state.setValue(ReceiverBlock.UNREAD, unread), 3);
            }
        }
    }

    public void serverTick(ServerLevel server, BlockPos pos, BlockState state) {
        int cost = ExploreConfig.receiverEnergy();
        if (storage.getEnergyStored() >= cost) {
            storage.consume(cost);
        }
        boolean on = listening();
        if (state.getValue(ReceiverBlock.LISTENING) != on) {
            server.setBlock(pos, state.setValue(ReceiverBlock.LISTENING, on), 3);
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> storage);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_ENERGY, storage.getEnergyStored());
        tag.putInt(TAG_FREQUENCY, frequency);
        ListTag list = new ListTag();
        for (SignalMessage message : archive) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(TAG_TIME, message.gameTime());
            entry.putInt(TAG_FREQUENCY, message.frequency());
            entry.putString(TAG_TEXT, message.text());
            list.add(entry);
        }
        tag.put(TAG_ARCHIVE, list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        storage.setStored(tag.getInt(TAG_ENERGY));
        int saved = tag.contains(TAG_FREQUENCY) ? tag.getInt(TAG_FREQUENCY) : MIN_FREQUENCY;
        frequency = validFrequency(saved) ? saved : MIN_FREQUENCY;
        archive.clear();
        ListTag list = tag.getList(TAG_ARCHIVE, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && archive.size() < MAX_ARCHIVE; i++) {
            CompoundTag entry = list.getCompound(i);
            archive.add(new SignalMessage(entry.getLong(TAG_TIME), entry.getInt(TAG_FREQUENCY), entry.getString(TAG_TEXT)));
        }
    }
}
