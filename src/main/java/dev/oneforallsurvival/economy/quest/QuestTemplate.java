package dev.oneforallsurvival.economy.quest;

import java.util.Map;
import java.util.OptionalInt;
import javax.annotation.Nullable;

/**
 * Modèle de quête : ce que l'on demande, en quelle quantité (de {@code min} à {@code max}) et pour combien de cuivres
 * à la quantité minimale ({@code reward}). Immuable.
 */
public record QuestTemplate(String id, QuestCategory category, @Nullable QuestTarget target, int min, int max,
        long reward, double weight, Map<String, Double> roleWeights, OptionalInt weeklyFactor,
        @Nullable String titleKey) {

    public QuestTemplate {
        roleWeights = Map.copyOf(roleWeights);
    }

    /** {@code max(1, arrondi(reward × quantité / min))} : la récompense suit la quantité. */
    public long rewardFor(int quantity) {
        return Math.max(1L, Math.round((double) reward * quantity / min));
    }

    /** Poids de tirage pour un rôle ; nul pour un modèle de métier qui n'est pas celui de ce rôle. */
    public double weightFor(String roleId) {
        if (category == QuestCategory.JOB) {
            boolean mine = target != null && QuestJobs.roleOf(target.id()).filter(roleId::equals).isPresent();
            if (!mine) {
                return 0.0D;
            }
        }
        return weight * roleWeights.getOrDefault(roleId, 1.0D);
    }
}
