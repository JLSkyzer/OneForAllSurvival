package dev.oneforallsurvival.phone;

import com.mojang.datafixers.util.Pair;
import dev.oneforallsurvival.phone.net.SimCardItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Ce qu'un autre joueur voit d'un téléphone ou d'une carte SIM tenus ou portés (lot T2, tâche 15 : décision de Killian
 * du 2026-11-26, « les cacher maintenant »). Le paquet d'équipement ({@code ClientboundSetEquipmentPacket}) part vers
 * les clients qui voient le porteur ; {@code getShareTag} ne connaît pas le destinataire, d'où ce tri, appliqué par
 * {@code ClientboundSetEquipmentPacketMixin} à la construction du paquet (envoi au début du suivi comme à chaque
 * changement, joueur ou porte-armure).
 *
 * <p>Une pile de téléphone y perd {@value PhoneItem#TAG_ID} et sa carte logée ({@value PhoneItem#TAG_SIM}) ; une pile
 * de carte y perd son identifiant, son jeton et l'empreinte de celui-ci, son numéro, son opérateur et son titulaire.
 * Le reste (l'objet, sa batterie, tout NBT étranger) reste : c'est ce qui sert au rendu. Rien n'est modifié en place :
 * une copie part.
 *
 * <p>Le porteur garde tout : ses paquets d'inventaire ne passent pas par ici. Hors de cette règle (limite écrite) :
 * une pile posée au sol, dans un cadre ou dans un conteneur ouvert garde ses données, l'objet étant physiquement
 * visible.
 */
public final class PhonePrivacy {

    private static final List<String> SIM_KEYS = List.of(SimCardItem.TAG_ID, SimCardItem.TAG_TOKEN,
            SimCardItem.TAG_MARK, SimCardItem.TAG_NUMBER, SimCardItem.TAG_OPERATOR, SimCardItem.TAG_HOLDER);

    private PhonePrivacy() {}

    /** Cette pile telle qu'un autre joueur peut la voir : la même si elle n'a rien à cacher, sinon une copie réduite. */
    public static ItemStack forOthers(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return stack;
        }
        List<String> keys;
        if (stack.getItem() instanceof PhoneItem) {
            keys = List.of(PhoneItem.TAG_ID, PhoneItem.TAG_SIM);
        } else if (stack.getItem() instanceof SimCardItem) {
            keys = SIM_KEYS;
        } else {
            return stack;
        }
        CompoundTag tag = stack.getTag();
        if (keys.stream().noneMatch(tag::contains)) {
            return stack;
        }
        ItemStack copy = stack.copy();
        CompoundTag reduced = copy.getTag();
        keys.forEach(reduced::remove);
        if (reduced.isEmpty()) {
            copy.setTag(null);
        }
        return copy;
    }

    /** La liste d'un paquet d'équipement : la même si rien n'est à cacher, sinon une nouvelle, aux piles réduites. */
    public static List<Pair<EquipmentSlot, ItemStack>> forOthers(List<Pair<EquipmentSlot, ItemStack>> slots) {
        List<Pair<EquipmentSlot, ItemStack>> out = null;
        for (int i = 0; i < slots.size(); i++) {
            Pair<EquipmentSlot, ItemStack> p = slots.get(i);
            ItemStack shown = forOthers(p.getSecond());
            if (shown != p.getSecond() && out == null) {
                out = new ArrayList<>(slots.subList(0, i));
            }
            if (out != null) {
                out.add(shown == p.getSecond() ? p : Pair.of(p.getFirst(), shown));
            }
        }
        return out == null ? slots : out;
    }
}
