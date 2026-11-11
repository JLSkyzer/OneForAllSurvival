package dev.oneforallsurvival.explore;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** La table de butin d'une cache : celle par défaut, ou celle que l'équipe a réglée par {@code /cache loot}. */
public final class CacheBlockEntity extends BlockEntity {

    public static final ResourceLocation DEFAULT_TABLE = new ResourceLocation("oneforallsurvival", "caches/default");
    private static final String TAG_TABLE = "LootTable";

    private ResourceLocation lootTable = DEFAULT_TABLE;

    public CacheBlockEntity(BlockPos pos, BlockState state) {
        super(ExploreRegistry.CACHE_BE.get(), pos, state);
    }

    public ResourceLocation lootTable() {
        return lootTable;
    }

    public void setLootTable(ResourceLocation table) {
        this.lootTable = table;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(TAG_TABLE, lootTable.toString());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ResourceLocation table = tag.contains(TAG_TABLE) ? ResourceLocation.tryParse(tag.getString(TAG_TABLE)) : null;
        lootTable = table != null ? table : DEFAULT_TABLE;
    }
}
