package dev.oneforallsurvival.compat.mixin.farmersdelight;

import dev.oneforallsurvival.economy.quest.CookingCredit;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.entity.SyncedBlockEntity;

/**
 * Tout changement des cases du Cooking Pot (trémie, entonnoir, transfert de JEI, clic) appelle {@code inventoryChanged}
 * (lu au bytecode de 1.20.1-1.3.4 : {@code onContentsChanged} de son gestionnaire) : le crédit de cuisine y est borné
 * à ce qui est là ({@link CookingCredit#bound}). Sans effet sur les autres blocs de Farmer's Delight (pas de crédit).
 */
@Mixin(SyncedBlockEntity.class)
public abstract class SyncedBlockEntityMixin {

    @Inject(method = "inventoryChanged", at = @At("RETURN"), remap = false)
    private void oneforall$boundCredit(CallbackInfo ci) {
        CookingCredit.bound((BlockEntity) (Object) this);
    }
}
