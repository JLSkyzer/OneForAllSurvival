package dev.oneforallsurvival.fence;

import net.minecraft.world.level.block.FenceBlock;

/**
 * La clôture électrique. Un bloc de clôture ordinaire : elle ne fait rien seule. C'est l'électrificateur qui la
 * parcourt et qui blesse ceux qui la touchent ; sans courant, c'est un bloc comme un autre. Elle est dans le tag
 * {@code minecraft:fences} et n'est pas en bois, donc elle se raccorde aux clôtures qui ne sont pas en bois et
 * pas à celles en bois (règle de {@link FenceBlock}).
 */
public final class ElectricFenceBlock extends FenceBlock {

    public ElectricFenceBlock(Properties properties) {
        super(properties);
    }
}
