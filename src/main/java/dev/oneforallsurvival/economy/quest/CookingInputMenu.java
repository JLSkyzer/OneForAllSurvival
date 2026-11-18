package dev.oneforallsurvival.economy.quest;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Un écran de cuisson dont certaines cases sont l'entrée (ingrédients, récipient ; ni combustible ni sel) : ce que le
 * joueur y met lui-même par l'écran ouvre un crédit sur le bloc ({@link CookingCredit}). Implémentée par le séchoir, la
 * marmite, et par mixin par les fours de Minecraft et le Cooking Pot de Farmer's Delight.
 */
public interface CookingInputMenu {

    /** Le bloc de cuisson, côté serveur ; {@code null} côté client. */
    @Nullable
    BlockEntity oneforall$cookingBlock();

    /** Cette case du menu (indice dans {@code slots}) est-elle une entrée ? */
    boolean oneforall$isCookingInput(int slotIndex);
}
