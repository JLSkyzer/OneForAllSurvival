package dev.oneforallsurvival.economy.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.util.RandomSource;

/** Tirage des quêtes d'une série : pondéré par rôle, sans remise, reproductible avec une graine. */
public final class QuestGenerator {

    public enum Series { DAILY, WEEKLY }

    private QuestGenerator() {}

    /**
     * Tire au plus {@code count} modèles distincts. Les modèles de poids nul pour ce rôle et ceux de {@code excluded}
     * sont écartés ; une réserve plus petite que {@code count} donne ce qu'elle a. En hebdomadaire, quantité et
     * récompense sont multipliées par le facteur du modèle, ou {@code weeklyFactor} à défaut.
     */
    public static List<Quest> draw(List<QuestTemplate> pool, String roleId, Series series, int count,
            Set<String> excluded, int weeklyFactor, RandomSource random) {
        List<QuestTemplate> candidates = new ArrayList<>();
        for (QuestTemplate template : pool) {
            if (!excluded.contains(template.id()) && template.weightFor(roleId) > 0.0D) {
                candidates.add(template);
            }
        }
        List<Quest> out = new ArrayList<>();
        while (out.size() < count && !candidates.isEmpty()) {
            double total = 0.0D;
            for (QuestTemplate template : candidates) {
                total += template.weightFor(roleId);
            }
            double pick = random.nextDouble() * total;
            int index = candidates.size() - 1;
            for (int i = 0; i < candidates.size(); i++) {
                pick -= candidates.get(i).weightFor(roleId);
                if (pick < 0.0D) {
                    index = i;
                    break;
                }
            }
            out.add(make(candidates.remove(index), series, weeklyFactor, random));
        }
        return out;
    }

    private static Quest make(QuestTemplate template, Series series, int weeklyFactor, RandomSource random) {
        int quantity = template.min() + random.nextInt(template.max() - template.min() + 1);
        long reward = template.rewardFor(quantity);
        if (series == Series.WEEKLY) {
            int factor = template.weeklyFactor().orElse(weeklyFactor);
            quantity *= factor;
            reward *= factor;
        }
        return new Quest(template.id(), template.category(), template.target(), quantity, reward, 0, false,
                template.titleKey());
    }
}
