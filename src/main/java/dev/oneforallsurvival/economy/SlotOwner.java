package dev.oneforallsurvival.economy;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/**
 * Une case qui sait de quel écran elle fait partie. Une {@code Slot} ne le sait pas d'elle-même ; un mixin sur
 * {@code AbstractContainerMenu.addSlot} le lui apprend, et {@link MoneyStorageRules#mayHold} le lit. Cette interface
 * est greffée sur {@code Slot} par {@code SlotMixin} : elle vit hors du paquet des mixins, qu'aucun code ne peut nommer.
 */
public interface SlotOwner {

    /** L'écran qui a ajouté la case, ou null si aucun ne l'a encore fait. */
    @Nullable
    AbstractContainerMenu oneforall$menu();

    void oneforall$setMenu(@Nullable AbstractContainerMenu menu);
}
