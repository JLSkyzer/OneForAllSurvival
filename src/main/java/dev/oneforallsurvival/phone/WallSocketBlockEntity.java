package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La prise murale : une machine de la ville (réserve de {@value #CAPACITY} FE, reçoit au plus
 * {@link #TOWN_MAX_RECEIVE} FE par tic, par n'importe quelle face, ou à travers son mur : {@link WallFeed}) qui verse
 * {@link PhoneConfig#socketChargePerTick()} FE par tic de travail dans le téléphone branché, jusqu'à sa capacité. Elle
 * s'use et tombe en panne comme les autres machines.
 *
 * <p>Le téléphone est gardé tel quel (pile entière, {@code phone_id} et charge compris) dans le NBT {@value #TAG_PHONE}.
 * Aucune capability d'objets : seuls la main et {@link WallSocketBlock#onRemove} l'en sortent.
 */
public final class WallSocketBlockEntity extends ElectricMachineBlockEntity {

    public static final int CAPACITY = 4000;
    public static final int MAX_RECEIVE = TOWN_MAX_RECEIVE;
    private static final String TAG_PHONE = "phone";
    private static final String TAG_WALL = "wall_connectors";

    private ItemStack phone = ItemStack.EMPTY;
    /** Les connecteurs du mur déjà vus ({@link WallFeed#wake}). */
    private final Set<Long> wallConnectors = new HashSet<>();

    public WallSocketBlockEntity(BlockPos pos, BlockState state) {
        super(PhoneRegistry.WALL_SOCKET_BE.get(), pos, state, CAPACITY, MAX_RECEIVE);
    }

    /** Le téléphone branché (la pile elle-même, pas une copie), ou une pile vide. */
    public ItemStack phone() {
        return phone;
    }

    /** Branche cette pile (vide : débranche). La prise la garde telle quelle. */
    public void setPhone(ItemStack stack) {
        phone = stack;
        setChanged();
    }

    @Override
    protected boolean wantsToWork() {
        return !phone.isEmpty() && PhoneItem.stored(phone) < PhoneConfig.capacity();
    }

    @Override
    protected int energyPerTick() {
        return PhoneConfig.socketChargePerTick();
    }

    /** Verse la charge d'un tic dans le téléphone ; {@code PhoneItem.store} la borne à la capacité. */
    @Override
    protected void workTick() {
        PhoneItem.store(phone, PhoneItem.stored(phone) + PhoneConfig.socketChargePerTick());
        setChanged();
    }

    /** {@code PLUGGED} suit la présence du téléphone, dans le même {@code setBlock} que l'état de la machine. */
    @Override
    protected BlockState adjustState(Level level, BlockState target, MachineState tickState) {
        if (!target.is(PhoneRegistry.WALL_SOCKET.get())) {
            return target;
        }
        return target.setValue(WallSocketBlock.PLUGGED, !phone.isEmpty());
    }

    /** Les connecteurs posés sur le mur de la prise l'alimentent ({@link WallFeed}). */
    @Override
    protected void afterTick(Level level, MachineState tickState) {
        if (level.getGameTime() % WallFeed.PERIOD == 0
                && WallFeed.wake(level, worldPosition, getBlockState(), wallConnectors)) {
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!phone.isEmpty()) {
            tag.put(TAG_PHONE, phone.save(new CompoundTag()));
        }
        if (!wallConnectors.isEmpty()) {
            tag.putLongArray(TAG_WALL, wallConnectors.stream().mapToLong(Long::longValue).toArray());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        phone = tag.contains(TAG_PHONE) ? ItemStack.of(tag.getCompound(TAG_PHONE)) : ItemStack.EMPTY;
        wallConnectors.clear();
        for (long connector : tag.getLongArray(TAG_WALL)) {
            wallConnectors.add(connector);
        }
    }
}
