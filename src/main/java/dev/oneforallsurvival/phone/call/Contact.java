package dev.oneforallsurvival.phone.call;

import dev.oneforallsurvival.phone.net.SimNumbers;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Un contact du carnet d'une carte SIM ({@link PhoneDirectory}).
 *
 * @param number numéro de carte, dix chiffres sans espaces ({@link SimNumbers#parse})
 * @param name nom donné par le joueur, nettoyé ({@link ContactRules#cleanName})
 */
public record Contact(String number, String name) {

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("number", number);
        tag.putString("name", name);
        return tag;
    }

    /**
     * Relit un contact ; nul si la ligne est illisible : numéro qui n'est pas de la forme d'un numéro de carte, ou nom
     * dont il ne reste rien (ou trop) après nettoyage. Un nom piégé écrit dans le fichier est nettoyé à la lecture.
     */
    @Nullable
    static Contact load(CompoundTag tag) {
        String number = tag.getString("number");
        if (!SimNumbers.parse(number).filter(number::equals).isPresent()) {
            return null;
        }
        return ContactRules.cleanName(tag.getString("name")).map(name -> new Contact(number, name)).orElse(null);
    }
}
