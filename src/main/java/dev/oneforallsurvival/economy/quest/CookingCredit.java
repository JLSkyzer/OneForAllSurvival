package dev.oneforallsurvival.economy.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Le crédit de cuisine d'un joueur sur un bloc de cuisson (four, fumoir, haut fourneau, séchoir, marmite, Cooking Pot) :
 * une sortie ne compte pour une quête que pour la part que le joueur a mise lui-même dans l'entrée, par l'écran (clic,
 * clic majuscule, glisser, livre de recettes). Ce qui entre par trémie, tapis ou bras de Create ne donne rien.
 *
 * <ul>
 *   <li>+N quand le joueur dépose N objets dans une case d'entrée ({@link CookingInputMenu}), −N quand il en retire
 *       (ce qu'il reprend ne peut plus rien cuire pour lui) ;</li>
 *   <li>à chaque sortie prise par le joueur, {@code min(pris, crédit)} est compté et retiré du crédit, que l'objet
 *       fasse avancer une quête ou non (un lingot cuit consomme le crédit de son minerai).</li>
 * </ul>
 *
 * <p>Le crédit vit dans les données persistantes du bloc ({@code ForgeData}), écrites avec le chunk ; il part avec le
 * bloc. Le calcul se fait sur l'état lu juste avant et juste après le geste, dans le même appel du serveur : aucune
 * trémie ne peut agir entre les deux.
 */
public final class CookingCredit {

    /** Clé dans {@link BlockEntity#getPersistentData()} : un nombre par joueur (UUID en texte). */
    public static final String KEY = "oneforallsurvival_quest_cooking";

    private CookingCredit() {}

    /** Le crédit de ce joueur sur ce bloc. */
    public static int credit(BlockEntity block, UUID player) {
        CompoundTag data = block.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? data.getCompound(KEY).getInt(player.toString()) : 0;
    }

    /** Ajoute {@code delta} (négatif : retire) au crédit, jamais sous zéro ; le bloc est marqué à sauver. */
    static void add(BlockEntity block, UUID player, int delta) {
        if (delta == 0) {
            return;
        }
        int before = credit(block, player);
        int after = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) before + delta));
        if (after == before) {
            return;
        }
        CompoundTag data = block.getPersistentData();
        CompoundTag credits = data.getCompound(KEY);
        if (after == 0) {
            credits.remove(player.toString());
        } else {
            credits.putInt(player.toString(), after);
        }
        if (credits.isEmpty()) {
            data.remove(KEY);
        } else {
            data.put(KEY, credits);
        }
        block.setChanged();
    }

    /** Le joueur vient de prendre {@code taken} objets à la sortie : rend la part comptée et la retire du crédit. */
    public static int take(BlockEntity block, UUID player, int taken) {
        int counted = Math.min(Math.max(0, taken), credit(block, player));
        add(block, player, -counted);
        return counted;
    }

    // ---------------------------------------------------------------------------------------------
    // Le geste à l'écran : état des entrées avant, puis après
    // ---------------------------------------------------------------------------------------------

    /** Les cases d'entrée d'un écran de cuisson, lues avant un geste du joueur. */
    public record Snapshot(BlockEntity block, ServerPlayer player, List<Integer> slots, List<ItemStack> before) {}

    /** Avant un geste : {@code null} si ce n'est pas un écran de cuisson côté serveur. */
    @Nullable
    public static Snapshot before(AbstractContainerMenu menu, Player player) {
        if (!(menu instanceof CookingInputMenu cooking) || !(player instanceof ServerPlayer server)) {
            return null;
        }
        BlockEntity block = cooking.oneforall$cookingBlock();
        if (block == null) {
            return null;
        }
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> before = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            if (cooking.oneforall$isCookingInput(i)) {
                slots.add(i);
                before.add(menu.slots.get(i).getItem().copy());
            }
        }
        return slots.isEmpty() ? null : new Snapshot(block, server, slots, before);
    }

    /**
     * Après le geste : ce qui a quitté les entrées est retiré du crédit d'abord (jamais sous zéro), puis ce qui y est
     * entré est ajouté, seulement pour un joueur qui compte ({@link QuestProgress#eligible}).
     */
    public static void after(@Nullable Snapshot snapshot, AbstractContainerMenu menu) {
        if (snapshot == null) {
            return;
        }
        int added = 0;
        int removed = 0;
        for (int k = 0; k < snapshot.slots().size(); k++) {
            int index = snapshot.slots().get(k);
            if (index >= menu.slots.size()) {
                continue;
            }
            ItemStack was = snapshot.before().get(k);
            ItemStack now = menu.slots.get(index).getItem();
            // Même objet, étiquettes comprises ou non (une date de fraîcheur fusionnée) : seul le nombre a bougé.
            if (was.isEmpty() || now.isEmpty() || ItemStack.isSameItem(was, now)) {
                int delta = now.getCount() - was.getCount();
                if (delta > 0) {
                    added += delta;
                } else {
                    removed -= delta;
                }
            } else {
                removed += was.getCount();
                added += now.getCount();
            }
        }
        UUID id = snapshot.player().getUUID();
        add(snapshot.block(), id, -removed);
        if (QuestProgress.eligible(snapshot.player())) {
            add(snapshot.block(), id, added);
        }
    }
}
