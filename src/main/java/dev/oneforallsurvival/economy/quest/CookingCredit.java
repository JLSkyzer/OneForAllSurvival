package dev.oneforallsurvival.economy.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Le crédit de cuisine d'un joueur sur un bloc de cuisson ({@link CookingBlock} : four, fumoir, haut fourneau, séchoir,
 * marmite, Cooking Pot). Une sortie ne compte pour une quête que si elle vient d'ingrédients que le joueur a mis
 * lui-même dans l'entrée, par l'écran (clic, clic majuscule, glisser, livre de recettes). Deux nombres par joueur :
 *
 * <ul>
 *   <li><b>entrée</b> : +N quand il dépose N ingrédients ({@link CookingInputMenu}), −N quand il en reprend ; récipients,
 *       combustible et sel jamais crédités ;</li>
 *   <li><b>sorties créditées</b> : à chaque cuisson ({@link #beginCook}/{@link #endCook}), si l'entrée créditée d'un
 *       joueur couvre <b>tous</b> les ingrédients consommés, elle en est décrémentée et ses sorties créditées augmentent
 *       de ce qui est sorti ; sinon les ingrédients consommés sont pris d'abord sur les crédits, sans sortie
 *       créditée ;</li>
 *   <li>au retrait par le joueur, la quête avance de {@code min(objets retirés, sorties créditées)}, décrémentées
 *       d'autant.</li>
 * </ul>
 *
 * <p><b>Toujours borné à ce qui est là</b> ({@link #bound}) : à chaque changement du contenu du bloc, par n'importe quel
 * chemin (trémie, entonnoir, transfert de JEI, tuyau), l'entrée de chaque joueur est ramenée à au plus les ingrédients
 * présents et ses sorties créditées à au plus les sorties présentes. Ce qui sort sans le joueur emporte donc d'abord ce
 * qui n'est pas crédité, puis le crédit : aucun surplus ne se met en réserve.
 *
 * <p>Le crédit vit dans les données persistantes du bloc ({@code ForgeData}, écrites avec le chunk) et part avec lui.
 */
public final class CookingCredit {

    /** Clé dans {@link BlockEntity#getPersistentData()} : par joueur (UUID en texte), {@code in} et {@code out}. */
    public static final String KEY = "oneforallsurvival_quest_cooking";
    private static final String IN = "in";
    private static final String OUT = "out";

    /** Blocs pendant un geste du joueur ou une cuisson : le bornage attend la fin (serveur seulement, un seul fil). */
    private static final Set<BlockEntity> BUSY = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Entrées et sorties présentes au début d'une cuisson. */
    private static final Map<BlockEntity, int[]> COOKING = new IdentityHashMap<>();

    private CookingCredit() {}

    // ---------------------------------------------------------------------------------------------
    // Lecture, écriture
    // ---------------------------------------------------------------------------------------------

    /** L'entrée créditée de ce joueur sur ce bloc. */
    public static int credit(BlockEntity block, UUID player) {
        return read(block, player, IN);
    }

    /** Les sorties créditées de ce joueur sur ce bloc. */
    public static int creditedOutputs(BlockEntity block, UUID player) {
        return read(block, player, OUT);
    }

    private static int read(BlockEntity block, UUID player, String field) {
        CompoundTag data = block.getPersistentData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) {
            return 0;
        }
        return data.getCompound(KEY).getCompound(player.toString()).getInt(field);
    }

    private static void write(BlockEntity block, UUID player, int in, int out) {
        CompoundTag data = block.getPersistentData();
        CompoundTag credits = data.getCompound(KEY);
        String id = player.toString();
        CompoundTag before = credits.getCompound(id);
        if (before.getInt(IN) == in && before.getInt(OUT) == out) {
            return;
        }
        if (in <= 0 && out <= 0) {
            credits.remove(id);
        } else {
            CompoundTag entry = new CompoundTag();
            entry.putInt(IN, Math.max(0, in));
            entry.putInt(OUT, Math.max(0, out));
            credits.put(id, entry);
        }
        if (credits.isEmpty()) {
            data.remove(KEY);
        } else {
            data.put(KEY, credits);
        }
        block.setChanged();
    }

    /** Les joueurs qui ont un crédit sur ce bloc, dans un ordre fixe. */
    private static List<UUID> players(BlockEntity block) {
        CompoundTag data = block.getPersistentData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(data.getCompound(KEY).getAllKeys());
        Collections.sort(keys);
        List<UUID> out = new ArrayList<>();
        for (String key : keys) {
            try {
                out.add(UUID.fromString(key));
            } catch (IllegalArgumentException e) {
                data.getCompound(KEY).remove(key);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // Bornage
    // ---------------------------------------------------------------------------------------------

    /**
     * Le contenu du bloc vient de changer : chaque crédit est ramené à ce qui est là. Sans effet pendant un geste du
     * joueur ou une cuisson (fait à leur fin).
     */
    public static void bound(BlockEntity block) {
        if (BUSY.contains(block) || !(block instanceof CookingBlock cooking)
                || !block.getPersistentData().contains(KEY)) {
            return;
        }
        int inputs = cooking.oneforall$cookingInputs();
        int outputs = cooking.oneforall$cookingOutputs();
        for (UUID player : players(block)) {
            int in = credit(block, player);
            int out = creditedOutputs(block, player);
            write(block, player, Math.min(in, inputs), Math.min(out, outputs));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Cuisson
    // ---------------------------------------------------------------------------------------------

    /** Avant qu'un bloc consomme son entrée : bornage, puis entrées et sorties relevées. */
    public static void beginCook(BlockEntity block) {
        if (!(block instanceof CookingBlock cooking) || block.getLevel() == null || block.getLevel().isClientSide()) {
            return;
        }
        bound(block);
        BUSY.add(block);
        COOKING.put(block, new int[] {cooking.oneforall$cookingInputs(), cooking.oneforall$cookingOutputs()});
    }

    /** Après : ingrédients consommés et sorties produites, attribués selon la règle de la classe. */
    public static void endCook(BlockEntity block) {
        int[] before = COOKING.remove(block);
        BUSY.remove(block);
        if (before == null || !(block instanceof CookingBlock cooking)) {
            return;
        }
        int consumed = before[0] - cooking.oneforall$cookingInputs();
        int produced = cooking.oneforall$cookingOutputs() - before[1];
        if (consumed > 0 && block.getPersistentData().contains(KEY)) {
            List<UUID> players = players(block);
            UUID owner = null;
            for (UUID player : players) {
                if (credit(block, player) >= consumed) {
                    owner = player;
                    break;
                }
            }
            if (owner != null) {
                write(block, owner, credit(block, owner) - consumed,
                        creditedOutputs(block, owner) + Math.max(0, produced));
            } else {
                int left = consumed;
                for (UUID player : players) {
                    int in = credit(block, player);
                    int used = Math.min(in, left);
                    write(block, player, in - used, creditedOutputs(block, player));
                    left -= used;
                }
            }
        }
        bound(block);
    }

    // ---------------------------------------------------------------------------------------------
    // Le geste à l'écran : état des entrées et des sorties avant, puis après
    // ---------------------------------------------------------------------------------------------

    /** Les cases d'entrée et de sortie d'un écran de cuisson, lues avant un geste du joueur. */
    public record Snapshot(BlockEntity block, ServerPlayer player, List<Integer> slots, List<ItemStack> before,
            List<Boolean> output) {}

    /** Avant un geste : {@code null} si ce n'est pas un écran de cuisson côté serveur. */
    @Nullable
    public static Snapshot before(AbstractContainerMenu menu, Player player) {
        if (!(menu instanceof CookingInputMenu cooking) || !(player instanceof ServerPlayer server)) {
            return null;
        }
        BlockEntity block = cooking.oneforall$cookingBlock();
        if (!(block instanceof CookingBlock) || block.getLevel() == null || block.getLevel().isClientSide()) {
            return null;
        }
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> before = new ArrayList<>();
        List<Boolean> output = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            boolean in = cooking.oneforall$isCookingInput(i);
            boolean out = cooking.oneforall$isCookingOutput(i);
            if (in || out) {
                slots.add(i);
                before.add(menu.slots.get(i).getItem().copy());
                output.add(out);
            }
        }
        if (slots.isEmpty()) {
            return null;
        }
        bound(block);
        BUSY.add(block);
        return new Snapshot(block, server, slots, before, output);
    }

    /**
     * Après le geste : ce qui a quitté l'entrée est retiré du crédit d'abord, puis ce qui y est entré ajouté (joueur qui
     * compte seulement, {@link QuestProgress#eligible}) ; ce qui a quitté la sortie compte dans la limite des sorties
     * créditées. Puis bornage.
     */
    public static void after(@Nullable Snapshot snapshot, AbstractContainerMenu menu) {
        if (snapshot == null) {
            return;
        }
        BlockEntity block = snapshot.block();
        BUSY.remove(block);
        int added = 0;
        int removed = 0;
        int taken = 0;
        ItemStack takenItem = ItemStack.EMPTY;
        for (int k = 0; k < snapshot.slots().size(); k++) {
            int index = snapshot.slots().get(k);
            if (index >= menu.slots.size()) {
                continue;
            }
            ItemStack was = snapshot.before().get(k);
            ItemStack now = menu.slots.get(index).getItem();
            int gone;
            int come;
            // Même objet, étiquettes comprises ou non (une date de fraîcheur fusionnée) : seul le nombre a bougé.
            if (was.isEmpty() || now.isEmpty() || ItemStack.isSameItem(was, now)) {
                gone = Math.max(0, was.getCount() - now.getCount());
                come = Math.max(0, now.getCount() - was.getCount());
            } else {
                gone = was.getCount();
                come = now.getCount();
            }
            if (snapshot.output().get(k)) {
                if (gone > 0) {
                    taken += gone;
                    takenItem = was;
                }
            } else {
                removed += gone;
                added += come;
            }
        }
        UUID id = snapshot.player().getUUID();
        int in = Math.max(0, credit(block, id) - removed);
        if (QuestProgress.eligible(snapshot.player())) {
            in += added;
        }
        int out = creditedOutputs(block, id);
        int counted = Math.min(taken, out);
        write(block, id, in, out - counted);
        if (counted > 0 && block instanceof CookingBlock cooking && cooking.oneforall$cookingCategory() != null) {
            QuestProgress.cookingTaken(snapshot.player(), cooking.oneforall$cookingCategory(),
                    takenItem.copyWithCount(counted), counted);
        }
        bound(block);
    }
}
