package dev.oneforallsurvival.salvage;

import dev.oneforallsurvival.OneForAllSurvival;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Un morceau de l'avion écrasé. Il ne cède qu'à un vrai outil (tag {@code salvage_tools}) : à mains nues
 * ou à la pelle, la progression de casse reste nulle. Il résiste aux explosions et aux pistons.
 */
public class SalvageBlock extends Block {

    /** Les outils capables de démonter un débris : pioches et clé de Create. */
    public static final TagKey<Item> SALVAGE_TOOLS =
            TagKey.create(ForgeRegistries.Keys.ITEMS, new ResourceLocation(OneForAllSurvival.MODID, "salvage_tools"));

    public SalvageBlock(MapColor color) {
        super(BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 1200.0F).sound(SoundType.METAL));
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Pas de requiresCorrectToolForDrops : bloquer le cassage suffit, et une perceuse de Create rend le butin.
        if (!player.isCreative() && !player.getMainHandItem().is(SALVAGE_TOOLS)) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }
}
