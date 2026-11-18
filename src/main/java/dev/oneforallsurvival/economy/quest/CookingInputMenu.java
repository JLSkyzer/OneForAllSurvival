package dev.oneforallsurvival.economy.quest;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Un écran de bloc de cuisson ({@link CookingBlock}) : quelles cases sont l'entrée créditable (ingrédients ; ni
 * récipient, ni combustible, ni sel) et lesquelles sont la sortie. Ce que le joueur met lui-même dans l'entrée par
 * l'écran ouvre un crédit, ce qu'il prend de la sortie compte dans la limite de ses sorties créditées
 * ({@link CookingCredit}). Implémentée par le séchoir, la marmite, et par mixin par les fours de Minecraft et le
 * Cooking Pot de Farmer's Delight.
 */
public interface CookingInputMenu {

    /** Le bloc de cuisson, côté serveur ; {@code null} côté client ou si ce n'est pas un {@link CookingBlock}. */
    @Nullable
    BlockEntity oneforall$cookingBlock();

    /** Cette case du menu (indice dans {@code slots}) est-elle une entrée créditable ? */
    boolean oneforall$isCookingInput(int slotIndex);

    /** Cette case du menu est-elle la sortie ? */
    boolean oneforall$isCookingOutput(int slotIndex);
}
