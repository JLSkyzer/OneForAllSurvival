package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.EconomyConfig;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Une offre de vente : {@code lot} objets identiques au modèle (identifiant et NBT, {@link ItemStack#isSameItemSameTags})
 * pour {@code price} cuivres. Le modèle est une <b>copie</b> gardée dans les données de l'offre, quantité 1 : il n'est
 * dans aucune case et ne se prend nulle part.
 */
public record StallOffer(ItemStack template, int lot, long price) {

    public static final int MAX_LOT = 64;

    public StallOffer {
        template = template == null || template.isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1);
    }

    /** Lot de 1 à 64, prix de 1 au maximum d'une facture, modèle non vide. */
    public static boolean valid(ItemStack template, int lot, long price) {
        return template != null && !template.isEmpty() && lot >= 1 && lot <= MAX_LOT && price >= 1L
                && price <= EconomyConfig.invoiceMaxAmount();
    }

    public boolean isValid() {
        return valid(template, lot, price);
    }

    /**
     * À la relecture : modèle, lot et prix positif. Le plafond du prix n'est pas relu, pour qu'un réglage abaissé
     * n'efface pas les offres déjà écrites.
     */
    static boolean readable(ItemStack template, int lot, long price) {
        return template != null && !template.isEmpty() && lot >= 1 && lot <= MAX_LOT && price >= 1L;
    }

    /** Cette pile est-elle faite des objets de l'offre (même objet, même NBT) ? */
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameTags(template, stack);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("item", template.save(new CompoundTag()));
        tag.putInt("lot", lot);
        tag.putLong("price", price);
        return tag;
    }

    /** Une offre relue, ou nulle si elle est abîmée ou hors des bornes. */
    @Nullable
    public static StallOffer load(CompoundTag tag) {
        StallOffer offer = new StallOffer(ItemStack.of(tag.getCompound("item")), tag.getInt("lot"), tag.getLong("price"));
        return readable(offer.template, offer.lot, offer.price) ? offer : null;
    }

    @Override
    public String toString() {
        return "StallOffer[" + lot + " x " + template.getItem() + (template.getTag() == null ? "" : template.getTag()) + " pour "
                + price + "]";
    }
}
