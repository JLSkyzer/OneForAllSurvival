package dev.oneforallsurvival.economy.quest;

import javax.annotation.Nullable;

/**
 * Un bloc de cuisson vu par le crédit de cuisine ({@link CookingCredit}) : ce qu'il a en entrée (ingrédients seulement :
 * ni récipient, ni combustible, ni sel), ce qu'il a de prêt en sortie, et la catégorie de quête de ses sorties.
 * Implémentée par le séchoir et la marmite, et par mixin par les fours de Minecraft et le Cooking Pot de Farmer's
 * Delight.
 */
public interface CookingBlock {

    /** Le nombre d'ingrédients présents dans les cases d'entrée. */
    int oneforall$cookingInputs();

    /** Le nombre d'objets cuits présents (en sortie, ou en attente de récipient). */
    int oneforall$cookingOutputs();

    /** La catégorie de quête de ce qui en sort, {@code null} si aucune (haut fourneau). */
    @Nullable
    QuestCategory oneforall$cookingCategory();
}
