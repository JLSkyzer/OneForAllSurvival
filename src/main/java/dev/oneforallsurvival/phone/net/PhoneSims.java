package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

/**
 * La carte SIM côté serveur (lot T2) : émission par le fondateur, insertion dans un téléphone, retrait, et lecture de la
 * ligne d'un téléphone. Fil du serveur seulement.
 *
 * <p>Ce qui fait foi est le registre ({@link PhoneOperators}) : une carte vaut quelque chose si son {@code sim_id} y est
 * connu <b>et</b> si la pile porte le jeton courant du registre. Chaque insertion fait tourner ce jeton
 * ({@link PhoneOperators#rotateToken}) et note le téléphone. Une copie faite avant une insertion porte l'ancien jeton :
 * elle est refusée. Deux piles au jeton courant (copie d'une carte logée) se disputent la ligne : la dernière glissée
 * l'emporte, l'autre devient morte ({@link Validity#UNKNOWN} si elle est logée, refusée à l'insertion). Une seule ligne
 * à la fois.
 *
 * <p>Le jeton est une preuve de présence, pas un secret fort (voir {@link SimCardItem}, avec la limite du mode
 * créatif que {@link #replaceCard} compense).
 *
 * <p>Un faux joueur (déployeur, automate) n'émet, n'insère ni ne retire rien. Le jeton n'est écrit ni dans un journal ni
 * dans une exception.
 */
public final class PhoneSims {

    /** Ce que vaut la carte logée dans un téléphone. */
    public enum Validity { NONE, UNKNOWN, ACTIVE, SUSPENDED, DEACTIVATED }

    /** Issue d'une insertion. {@code REFUSED} : un faux joueur (aucun droit). */
    public enum Insert { DONE, NOT_A_SIM, UNKNOWN_CARD, NO_PHONE, REFUSED }

    /** Issue d'un retrait. {@code REFUSED} : un faux joueur, ou un téléphone qui n'est pas dans son inventaire. */
    public enum Remove { DONE, NO_SIM, INVENTORY_FULL, REFUSED }

    /** Une carte de l'inventaire, proposée au choix (Réglages du téléphone, tâche 7). */
    public record SimChoice(int slot, String number, String operator, Validity validity) {}

    private PhoneSims() {}

    // ---------------------------------------------------------------------------------------------
    // Lecture
    // ---------------------------------------------------------------------------------------------

    /**
     * La carte de cette pile si elle est authentique : connue du registre et porteuse de son jeton courant. Le jeton est
     * comparé ici et nulle part ailleurs.
     */
    private static Optional<SimCard> genuine(PhoneOperators registry, ItemStack sim) {
        Optional<UUID> id = SimCardItem.simId(sim);
        Optional<UUID> token = SimCardItem.token(sim);
        if (id.isEmpty() || token.isEmpty()) {
            return Optional.empty();
        }
        return registry.card(id.get()).filter(card -> card.token().equals(token.get()));
    }

    private static Validity of(SimCard card) {
        return switch (card.state()) {
            case ACTIVE -> Validity.ACTIVE;
            case SUSPENDED -> Validity.SUSPENDED;
            case DEACTIVATED -> Validity.DEACTIVATED;
        };
    }

    /** La carte logée dans ce téléphone, si elle est authentique et liée à ce téléphone ({@code phone_id}). */
    private static Optional<SimCard> lodged(PhoneOperators registry, ItemStack phone) {
        Optional<UUID> phoneId = PhoneItem.phoneId(phone);
        if (phoneId.isEmpty()) {
            return Optional.empty();
        }
        return genuine(registry, PhoneItem.sim(phone)).filter(card -> phoneId.get().equals(card.phoneId()));
    }

    /**
     * Ce que vaut la carte logée : {@code NONE} sans carte ; {@code UNKNOWN} si son {@code sim_id} est inconnu, si son
     * jeton n'est pas celui du registre, ou si le registre la dit dans un autre téléphone ; sinon son état.
     */
    public static Validity check(MinecraftServer server, ItemStack phone) {
        if (!phone.is(PhoneRegistry.PHONE.get()) || PhoneItem.sim(phone).isEmpty()) {
            return Validity.NONE;
        }
        return lodged(PhoneOperators.get(server), phone).map(PhoneSims::of).orElse(Validity.UNKNOWN);
    }

    /** La ligne de ce téléphone : sa carte, si elle est {@code ACTIVE} ou {@code SUSPENDED} (le numéro reste à elle). */
    public static Optional<SimCard> line(MinecraftServer server, ItemStack phone) {
        if (!phone.is(PhoneRegistry.PHONE.get())) {
            return Optional.empty();
        }
        return lodged(PhoneOperators.get(server), phone).filter(card -> card.state() != SimCard.State.DEACTIVATED);
    }

    // ---------------------------------------------------------------------------------------------
    // Gestes
    // ---------------------------------------------------------------------------------------------

    /** Ce téléphone est-il une pile de l'inventaire de ce joueur (la pile elle-même, pas une copie) ? */
    private static boolean holds(ServerPlayer player, ItemStack phone) {
        if (phone.isEmpty() || !phone.is(PhoneRegistry.PHONE.get())) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i) == phone) {
                return true;
            }
        }
        return false;
    }

    private static String operatorName(PhoneOperators registry, SimCard card, ItemStack fallback) {
        return registry.byId(card.operatorId()).map(PhoneOperator::name).orElseGet(() ->
                fallback.hasTag() ? fallback.getTag().getString(SimCardItem.TAG_OPERATOR) : "");
    }

    /**
     * Le joueur glisse la carte de la case {@code slot} dans {@code phone}. La case est relue ici ; la carte doit être
     * authentique (connue du registre, jeton courant). Son jeton tourne, le téléphone est noté, et la carte qui était
     * dans le téléphone revient dans la case (déliée du registre si c'était bien elle qui portait la ligne).
     */
    public static Insert insert(ServerPlayer player, ItemStack phone, int slot) {
        if (player instanceof FakePlayer) {
            return Insert.REFUSED;
        }
        if (!holds(player, phone)) {
            return Insert.NO_PHONE;
        }
        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return Insert.NOT_A_SIM;
        }
        ItemStack inSlot = inventory.getItem(slot);
        if (!(inSlot.getItem() instanceof SimCardItem)) {
            return Insert.NOT_A_SIM;
        }
        PhoneOperators registry = PhoneOperators.get(player.server);
        Optional<SimCard> card = genuine(registry, inSlot);
        if (card.isEmpty()) {
            return Insert.UNKNOWN_CARD;
        }
        UUID phoneId = PhoneItem.ensurePhoneId(phone);
        ItemStack previous = PhoneItem.sim(phone);
        lodged(registry, phone).ifPresent(old -> registry.unbind(old.simId()));

        ItemStack sim = inSlot.copyWithCount(1);
        registry.rotateToken(card.get().simId(), phoneId, player.getRandom());
        SimCard rotated = registry.card(card.get().simId()).orElseThrow();
        SimCardItem.write(sim, rotated, operatorName(registry, rotated, inSlot));
        PhoneItem.setSim(phone, sim);
        inSlot.shrink(1);
        inventory.setItem(slot, inSlot.isEmpty() ? previous : inSlot);
        if (!inSlot.isEmpty() && !previous.isEmpty() && !inventory.add(previous)) {
            player.drop(previous, false);
        }
        inventory.setChanged();
        return Insert.DONE;
    }

    /**
     * Le joueur retire la carte du téléphone : elle revient dans son inventaire, déliée du registre si c'était elle qui
     * portait la ligne. Refusé si l'inventaire n'a pas de case libre (la carte reste dans le téléphone).
     */
    public static Remove remove(ServerPlayer player, ItemStack phone) {
        if (player instanceof FakePlayer || !holds(player, phone)) {
            return Remove.REFUSED;
        }
        ItemStack sim = PhoneItem.sim(phone);
        if (sim.isEmpty()) {
            return Remove.NO_SIM;
        }
        Inventory inventory = player.getInventory();
        if (inventory.getFreeSlot() < 0) {
            return Remove.INVENTORY_FULL;
        }
        PhoneOperators registry = PhoneOperators.get(player.server);
        lodged(registry, phone).ifPresent(card -> registry.unbind(card.simId()));
        PhoneItem.setSim(phone, ItemStack.EMPTY);
        inventory.add(sim);
        inventory.setChanged();
        return Remove.DONE;
    }

    /** Les cartes de l'inventaire du joueur, case par case (41 au plus), avec ce qu'elles valent au registre. */
    public static List<SimChoice> choices(ServerPlayer player) {
        List<SimChoice> out = new ArrayList<>();
        PhoneOperators registry = PhoneOperators.get(player.server);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof SimCardItem)) {
                continue;
            }
            Optional<SimCard> card = genuine(registry, stack);
            String number = card.map(SimCard::number).orElseGet(() ->
                    stack.hasTag() ? stack.getTag().getString(SimCardItem.TAG_NUMBER) : "");
            String operator = card.map(c -> operatorName(registry, c, stack)).orElseGet(() ->
                    stack.hasTag() ? stack.getTag().getString(SimCardItem.TAG_OPERATOR) : "");
            out.add(new SimChoice(i, SimNumbers.format(number), operator,
                    card.map(PhoneSims::of).orElse(Validity.UNKNOWN)));
        }
        return out;
    }

    /**
     * Le fondateur émet une carte de son opérateur (geste du panneau, tâche 9), au nom de {@code holder} (nul : sans
     * nom). Elle arrive dans l'inventaire du fondateur, jamais au sol.
     *
     * @return la pile émise (une copie), ou vide sans rien émettre ni réserver : faux joueur, opérateur inconnu du
     *         registre, joueur qui n'en est pas le fondateur, opérateur à son plafond de cartes
     *         ({@link PhoneOperators#mayIssue}), ou inventaire plein
     */
    public static Optional<ItemStack> issueTo(ServerPlayer founder, PhoneOperator op, @Nullable UUID holder,
            String holderName, long now) {
        if (founder instanceof FakePlayer || op == null) {
            return Optional.empty();
        }
        PhoneOperators registry = PhoneOperators.get(founder.server);
        Optional<PhoneOperator> current = registry.byId(op.id());
        if (current.isEmpty() || !AntennaRules.mayManageCards(founder, current.get())
                || !registry.mayIssue(current.get().id())) {
            return Optional.empty();
        }
        Inventory inventory = founder.getInventory();
        if (inventory.getFreeSlot() < 0) {
            return Optional.empty();
        }
        SimCard card = registry.issue(current.get().id(), holder, holder == null ? "" : holderName,
                founder.getRandom(), now);
        ItemStack stack = SimCardItem.create(card, current.get().name());
        ItemStack copy = stack.copy();
        inventory.add(stack);
        inventory.setChanged();
        return Optional.of(copy);
    }

    /**
     * « Remplacer la carte » (geste du fondateur, branché sur le panneau à la tâche 9) : une nouvelle pile de la même
     * carte, même {@code sim_id} et même numéro, au jeton neuf. Toute pile ancienne devient morte, et le téléphone qui
     * portait la ligne est délié (le registre ne note plus de téléphone). La nouvelle pile arrive dans l'inventaire du
     * fondateur, jamais au sol. Rend une carte perdue (pile tuée par le mode créatif, téléphone détruit) sans toucher au
     * numéro.
     *
     * @return la nouvelle pile (une copie), ou vide sans rien changer : faux joueur, carte inconnue ou désactivée,
     *         opérateur inconnu du registre, joueur qui n'en est pas le fondateur, ou inventaire plein
     */
    public static Optional<ItemStack> replaceCard(ServerPlayer founder, UUID simId) {
        if (founder instanceof FakePlayer || simId == null) {
            return Optional.empty();
        }
        PhoneOperators registry = PhoneOperators.get(founder.server);
        Optional<SimCard> card = registry.card(simId);
        if (card.isEmpty() || card.get().state() == SimCard.State.DEACTIVATED) {
            return Optional.empty();
        }
        Optional<PhoneOperator> operator = registry.byId(card.get().operatorId());
        if (operator.isEmpty() || !AntennaRules.mayManageCards(founder, operator.get())) {
            return Optional.empty();
        }
        Inventory inventory = founder.getInventory();
        if (inventory.getFreeSlot() < 0) {
            return Optional.empty();
        }
        // Jeton neuf, aucun téléphone noté : toute pile ancienne meurt, le porteur de la ligne est délié.
        registry.rotateToken(simId, null, founder.getRandom());
        SimCard renewed = registry.card(simId).orElseThrow();
        ItemStack stack = SimCardItem.create(renewed, operator.get().name());
        ItemStack copy = stack.copy();
        inventory.add(stack);
        inventory.setChanged();
        return Optional.of(copy);
    }
}
