package dev.oneforallsurvival.economy.quest;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Cible d'une quête : un identifiant du registre, ou un tag ({@code #espace:chemin}). */
public record QuestTarget(ResourceLocation id, boolean tag) {

    public static Optional<QuestTarget> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        boolean tag = text.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? text.substring(1) : text);
        return id == null || id.getPath().isEmpty() ? Optional.empty() : Optional.of(new QuestTarget(id, tag));
    }

    /** Écriture du fichier de données ; {@link #parse} la relit. */
    public String text() {
        return tag ? "#" + id : id.toString();
    }
}
