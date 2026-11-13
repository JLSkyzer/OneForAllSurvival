package dev.oneforallsurvival.economy.stall;

import dev.oneforallsurvival.economy.EconomyConfig;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;

/**
 * Une offre de vente : {@code lot} objets identiques au modèle (identifiant et NBT, {@link ItemStack#isSameItemSameTags})
 * pour {@code price} cuivres. Le modèle est une <b>copie</b> gardée dans les données de l'offre, quantité 1 : il n'est
 * dans aucune case et ne se prend nulle part. {@link #template()} en rend une copie : le modèle gardé ne se modifie pas.
 *
 * <p>Le modèle part à tout client qui voit l'étal (vitrine, comptoir) : sa taille est bornée ({@link #MAX_TEMPLATE_BYTES},
 * et {@code MarketStallBlockEntity#MAX_TEMPLATES_BYTES} pour tout l'étal), sans quoi un objet à NBT énorme (boîte de
 * Shulker pleine de livres signés), recopié sur toutes les offres, rendrait le chunk impossible à charger.
 */
public record StallOffer(ItemStack template, int lot, long price) {

    public static final int MAX_LOT = 64;

    /**
     * Taille maximale d'un modèle, NBT sérialisé (objet, nombre et étiquettes) : 8 Kio. Les objets courants en font
     * bien moins (mesuré par le GameTest {@code unModeleTropLourdRefuse} : épée nommée et enchantée, livre enchanté,
     * potion, tous sous 400 octets) ; 8 Kio laisse la place aux objets de mods, et 18 + 18 modèles de cette taille
     * resteraient sous la limite de 2 Mio d'une étiquette NBT reçue par le client. Un livre écrit très long est refusé.
     */
    public static final int MAX_TEMPLATE_BYTES = 8 * 1024;

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

    /** Une copie du modèle : celui de l'offre ne se modifie pas. */
    @Override
    public ItemStack template() {
        return template.copy();
    }

    /** La taille du modèle sérialisé en NBT, en octets ({@link Integer#MAX_VALUE} si l'écriture échoue). */
    public static int nbtSize(ItemStack template) {
        CompoundTag tag = template.copyWithCount(1).save(new CompoundTag());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.write(tag, out);
        } catch (IOException e) {
            return Integer.MAX_VALUE;
        }
        return bytes.size();
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
