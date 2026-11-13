package dev.oneforallsurvival.economy.stall;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Une offre de rachat : l'étal achète {@code lot} objets identiques au modèle pour {@code price} cuivres, payés sur
 * sa caisse, au plus {@code lotsLeft} fois. Chaque rachat décrémente {@code lotsLeft} ; à zéro, l'offre reste
 * affichée, épuisée. Le modèle est une copie gardée dans les données de l'offre, comme pour {@link StallOffer}.
 */
public record StallBuyback(ItemStack template, int lot, long price, int lotsLeft) {

    public static final int MAX_LOTS = 9_999;

    public StallBuyback {
        template = template == null || template.isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1);
    }

    /** À l'écriture par le propriétaire : comme une offre de vente, et de 1 à 9 999 lots. */
    public static boolean valid(ItemStack template, int lot, long price, int lotsLeft) {
        return StallOffer.valid(template, lot, price) && lotsLeft >= 1 && lotsLeft <= MAX_LOTS;
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameTags(template, stack);
    }

    /** La même offre, un lot de moins (jamais sous zéro). */
    public StallBuyback oneLotLess() {
        return new StallBuyback(template, lot, price, Math.max(0, lotsLeft - 1));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("item", template.save(new CompoundTag()));
        tag.putInt("lot", lot);
        tag.putLong("price", price);
        tag.putInt("left", lotsLeft);
        return tag;
    }

    /** Un rachat relu (épuisé compris), ou nul s'il est abîmé ou hors des bornes. */
    @Nullable
    public static StallBuyback load(CompoundTag tag) {
        StallBuyback buyback = new StallBuyback(ItemStack.of(tag.getCompound("item")), tag.getInt("lot"), tag.getLong("price"),
                tag.getInt("left"));
        return StallOffer.readable(buyback.template, buyback.lot, buyback.price) && buyback.lotsLeft >= 0
                && buyback.lotsLeft <= MAX_LOTS ? buyback : null;
    }

    @Override
    public String toString() {
        return "StallBuyback[" + lot + " x " + template.getItem() + (template.getTag() == null ? "" : template.getTag()) + " pour "
                + price + ", reste " + lotsLeft + "]";
    }
}
