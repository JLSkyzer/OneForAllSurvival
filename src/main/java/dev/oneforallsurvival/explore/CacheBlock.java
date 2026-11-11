package dev.oneforallsurvival.explore;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * La cache enfouie : une caisse rouillée que l'équipe place. Son butin vient de la table de sa {@link CacheBlockEntity},
 * tirée avec la position et le joueur. Elle ne lâche jamais son propre bloc. Cassée en créatif, rien ne tombe (le jeu
 * n'appelle pas les butins). Elle s'inscrit à l'{@link CacheIndex} en se posant et en sort en partant.
 */
public final class CacheBlock extends Block implements EntityBlock {

    public CacheBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CacheBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (level instanceof ServerLevel server) {
            CacheIndex.get(server).add(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            CacheIndex.get(server).remove(pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ServerLevel level = builder.getLevel();
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        ResourceLocation id = be instanceof CacheBlockEntity cache ? cache.lootTable() : CacheBlockEntity.DEFAULT_TABLE;
        LootTable table = level.getServer().getLootData().getLootTable(id);
        return table.getRandomItems(builder.withParameter(LootContextParams.BLOCK_STATE, state)
                .create(LootContextParamSets.BLOCK));
    }
}
