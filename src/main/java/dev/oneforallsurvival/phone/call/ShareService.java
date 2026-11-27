package dev.oneforallsurvival.phone.call;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneData;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.call.ShareBook.Request;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimNumbers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * « Partager mon numéro » (« AirDrop », retours d'essai du 27/11), côté serveur : seul juge de la liste, de l'envoi et
 * de la réponse. La page ne fait que montrer la liste ({@link PhoneSharePacket}) et proposer un envoi ; le destinataire
 * répond par les liens du chat, qui passent par {@link ShareCommands} et ne servent qu'à lui.
 *
 * <ul>
 *   <li>Liste et envoi ({@link CallActions}, écran déverrouillé, frein des gestes) : {@link ShareRules#listed} pour
 *       chaque autre joueur du même monde ; rejouée à l'envoi. L'envoyeur doit avoir une carte {@code ACTIVE} dans le
 *       téléphone de la session : c'est son numéro qui part, sous le nom du propriétaire de ce téléphone.
 *   <li>Le destinataire est prévenu même téléphone rangé : message du chat avec « Accepter » et « Refuser », et un son.
 *   <li>Accepté : la fiche s'ajoute au carnet de la carte de son téléphone (main principale, seconde main, puis le reste
 *       de l'inventaire) ; un numéro déjà au carnet garde sa fiche. Sans carte ou carnet plein, la demande reste.
 *   <li>Une demande tombe au bout de {@link ShareBook#TTL_MILLIS} ms (l'envoyeur l'apprend : « sans réponse »), au
 *       départ de l'un des deux (celui du destinataire vaut « sans réponse »), ou si l'envoyeur n'a plus sa carte (sans
 *       mot). L'envoyeur apprend « accepté » ou « refusé » à la réponse.
 * </ul>
 *
 * <p>Base de temps : {@link EconomyClock#now()}. Fil du serveur seulement ; ni numéro ni carte dans un journal.
 */
public final class ShareService {

    public static final String MESSAGE = CallService.MESSAGE + "share.";
    /** La commande des liens du chat (sans barre oblique). */
    public static final String COMMAND = "phoneshare";
    public static final Holder<SoundEvent> OFFER_SOUND = SoundEvents.NOTE_BLOCK_CHIME;
    /** Tics entre deux relevés des demandes (expiration, carte de l'envoyeur). */
    static final int SWEEP_TICKS = 20;

    private static final Pattern ENTITY_ID = Pattern.compile("0|[1-9][0-9]{0,9}");
    private static final ShareBook BOOK = new ShareBook();

    private ShareService() {}

    /** Les demandes en attente (lu par les essais). */
    public static List<Request> pending() {
        return BOOK.all();
    }

    // ---------------------------------------------------------------------------------------------
    // La liste
    // ---------------------------------------------------------------------------------------------

    /** Une personne de la liste : le joueur, et le nom de son téléphone. */
    private record Candidate(ServerPlayer player, String name, double distanceSqr) {}

    /**
     * La carte qui recevrait une fiche chez ce joueur : celle du téléphone de sa main principale, puis de sa seconde
     * main, puis du premier téléphone de son inventaire (curseur compris) dont la carte a une ligne (active ou
     * suspendue).
     */
    static Optional<Lodged> receiverCard(ServerPlayer player) {
        MinecraftServer server = player.server;
        for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            Optional<Lodged> lodged = lodged(server, stack);
            if (lodged.isPresent()) {
                return lodged;
            }
        }
        return PhoneCarriers.first(player, stack -> lodged(server, stack).isPresent())
                .flatMap(stack -> lodged(server, stack));
    }

    /** Un téléphone et la carte qui y porte une ligne. */
    record Lodged(ItemStack phone, SimCard card) {}

    private static Optional<Lodged> lodged(MinecraftServer server, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof PhoneItem)) {
            return Optional.empty();
        }
        return PhoneSims.line(server, stack).map(card -> new Lodged(stack, card));
    }

    /** {@code target} est-il dans la liste de {@code sender} ? Avec le nom de son téléphone. */
    private static Optional<Candidate> candidate(ServerPlayer sender, Entity entity) {
        if (!(entity instanceof ServerPlayer target) || target == sender || target instanceof FakePlayer
                || target.connection == null || !target.isAlive() || target.level() != sender.level()) {
            return Optional.empty();
        }
        double d = sender.distanceToSqr(target);
        Optional<Lodged> card = receiverCard(target);
        boolean listed = ShareRules.listed(d, sender.hasLineOfSight(target), target.isSpectator(),
                target.isInvisible(), card.isPresent());
        if (!listed) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(target, phoneName(target, card.get().phone()), d));
    }

    /** Le nom d'un téléphone : son propriétaire, ou le joueur qui le porte s'il n'en a pas encore. */
    private static String phoneName(ServerPlayer holder, ItemStack phone) {
        String owner = PhoneItem.phoneId(phone).map(id -> PhoneData.get(holder.server).record(id).ownerName())
                .orElse("");
        return owner.isEmpty() ? holder.getGameProfile().getName() : owner;
    }

    private static List<Candidate> candidates(ServerPlayer sender) {
        List<Candidate> out = new ArrayList<>();
        for (ServerPlayer other : sender.serverLevel().players()) {
            candidate(sender, other).ifPresent(out::add);
        }
        out.sort(Comparator.comparingDouble(Candidate::distanceSqr).thenComparing(Candidate::name));
        return out.size() > ShareRules.MAX_ROWS ? out.subList(0, ShareRules.MAX_ROWS) : out;
    }

    private static List<PhoneSharePacket.Row> rows(ServerPlayer sender) {
        List<PhoneSharePacket.Row> rows = new ArrayList<>();
        for (Candidate c : candidates(sender)) {
            rows.add(new PhoneSharePacket.Row(c.player().getId(), c.name()));
        }
        return rows;
    }

    /** La liste, pour l'écran ouvert (geste {@code SHARE_SCAN}). */
    static void scan(ServerPlayer sender) {
        reply(sender, null);
    }

    private static void reply(ServerPlayer sender, @Nullable Component message) {
        if (sender instanceof FakePlayer || sender.connection == null) {
            return;
        }
        BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                new PhoneSharePacket(rows(sender), message));
    }

    // ---------------------------------------------------------------------------------------------
    // L'envoi
    // ---------------------------------------------------------------------------------------------

    /**
     * Envoie le numéro de la carte du téléphone de la session au joueur d'entité {@code targetText} (geste
     * {@code SHARE_SEND}), après les contrôles de la liste rejoués ici. Répond toujours à l'envoyeur par la liste et un
     * mot (envoyé, en attente, patientez, plus à portée, sans carte active).
     */
    static void send(ServerPlayer sender, PhoneService.Held held, String targetText) {
        if (!ENTITY_ID.matcher(targetText).matches() || Long.parseLong(targetText) > Integer.MAX_VALUE) {
            return;
        }
        Optional<SimCard> line = PhoneSims.line(sender.server, held.phone());
        if (line.isEmpty() || line.get().state() != SimCard.State.ACTIVE
                || PhoneSims.check(sender.server, held.phone()) != PhoneSims.Validity.ACTIVE) {
            reply(sender, Component.translatable(MESSAGE + "no_line"));
            return;
        }
        Optional<Candidate> target = candidate(sender, sender.serverLevel().getEntity(Integer.parseInt(targetText)));
        if (target.isEmpty()) {
            reply(sender, Component.translatable(MESSAGE + "gone"));
            return;
        }
        ServerPlayer receiver = target.get().player();
        String name = ContactRules.cleanName(held.record().ownerName())
                .orElse(sender.getGameProfile().getName());
        ShareBook.SendResult r = BOOK.send(sender.getUUID(), sender.getGameProfile().getName(),
                line.get().simId(), line.get().number(), name, receiver.getUUID(),
                receiver.getGameProfile().getName(), EconomyClock.now());
        switch (r.outcome()) {
            case SENT -> {
                offer(receiver, r.request().get());
                reply(sender, Component.translatable(MESSAGE + "sent", target.get().name()));
            }
            case PENDING -> reply(sender, Component.translatable(MESSAGE + "pending", target.get().name()));
            case THROTTLED -> reply(sender, Component.translatable(MESSAGE + "wait"));
            case FULL, SELF -> reply(sender, Component.translatable(MESSAGE + "busy"));
        }
    }

    /** Le message du chat au destinataire : la fiche proposée, « Accepter », « Refuser », et un son pour lui seul. */
    private static void offer(ServerPlayer receiver, Request request) {
        receiver.sendSystemMessage(offerMessage(request));
        if (receiver.connection != null) {
            receiver.connection.send(new ClientboundSoundPacket(OFFER_SOUND, SoundSource.PLAYERS, receiver.getX(),
                    receiver.getY(), receiver.getZ(), 0.8f, 1.2f, receiver.getRandom().nextLong()));
        }
    }

    static Component offerMessage(Request request) {
        MutableComponent accept = Component.translatable(MESSAGE + "accept").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/" + COMMAND + " accept " + request.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(MESSAGE + "accept.hover"))));
        MutableComponent decline = Component.translatable(MESSAGE + "decline").withStyle(Style.EMPTY
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/" + COMMAND + " decline " + request.id())));
        return Component.translatable(MESSAGE + "offer", request.name(), SimNumbers.format(request.number()))
                .append(" ").append(accept).append(" ").append(decline);
    }

    // ---------------------------------------------------------------------------------------------
    // La réponse (commande des liens du chat)
    // ---------------------------------------------------------------------------------------------

    public enum Answer { SAVED, KNOWN, DECLINED, NOT_FOR_YOU, NO_CARD, FULL }

    /**
     * La réponse de {@code player} à la demande {@code id}. Une demande qui n'est pas la sienne, qui n'attend plus, ou
     * dont l'envoyeur n'a plus sa carte : {@link Answer#NOT_FOR_YOU}, rien ne change (le même mot dans tous ces cas).
     */
    public static Answer respond(ServerPlayer player, int id, boolean accept) {
        Optional<Request> found = BOOK.forReceiver(id, player.getUUID());
        if (found.isEmpty() || player instanceof FakePlayer) {
            return Answer.NOT_FOR_YOU;
        }
        Request request = found.get();
        if (!senderHasCard(player.server, request)) {
            BOOK.remove(id);
            return Answer.NOT_FOR_YOU;
        }
        ServerPlayer sender = player.server.getPlayerList().getPlayer(request.sender());
        if (!accept) {
            BOOK.remove(id);
            tell(sender, Component.translatable(MESSAGE + "refused", request.receiverName()));
            return Answer.DECLINED;
        }
        Optional<Lodged> card = receiverCard(player);
        if (card.isEmpty()) {
            return Answer.NO_CARD;
        }
        PhoneDirectory directory = PhoneDirectory.get(player.server);
        UUID simId = card.get().card().simId();
        Answer answer;
        if (directory.nameOf(simId, request.number()).isPresent()) {
            answer = Answer.KNOWN;
        } else if (directory.saveContact(simId, request.number(), request.name()) == PhoneDirectory.SaveResult.SAVED) {
            answer = Answer.SAVED;
        } else {
            return Answer.FULL;
        }
        BOOK.remove(id);
        tell(sender, Component.translatable(MESSAGE + "accepted", request.receiverName()));
        // L'application Téléphone ouverte sur cette carte montre la fiche tout de suite.
        PhoneItem.phoneId(card.get().phone()).filter(phoneId -> PhoneService.unlockedOn(player, phoneId))
                .ifPresent(phoneId -> CallUpdates.send(player, CallUpdates.current(player, null,
                        CallUpdates.book(player.server, simId))));
        return answer;
    }

    /** L'envoyeur est-il en ligne, avec la carte de la demande active dans un téléphone qu'il porte ? */
    private static boolean senderHasCard(MinecraftServer server, Request request) {
        ServerPlayer sender = server.getPlayerList().getPlayer(request.sender());
        if (sender == null) {
            return false;
        }
        return PhoneCarriers.first(sender, stack -> PhoneSims.line(server, stack)
                .filter(card -> card.simId().equals(request.senderSim()) && card.state() == SimCard.State.ACTIVE)
                .isPresent()).isPresent();
    }

    private static void tell(@Nullable ServerPlayer player, Component message) {
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bus de Forge
    // ---------------------------------------------------------------------------------------------

    /** Toutes les {@value #SWEEP_TICKS} tics : les demandes trop vieilles, et celles dont l'envoyeur n'a plus sa carte. */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % SWEEP_TICKS != 0) {
            return;
        }
        sweep(event.getServer());
    }

    /** Le relevé des demandes (essais : sans attendre le tic). */
    public static void sweep(MinecraftServer server) {
        if (BOOK.all().isEmpty()) {
            return;
        }
        for (Request r : BOOK.expire(EconomyClock.now())) {
            tell(server.getPlayerList().getPlayer(r.sender()),
                    Component.translatable(MESSAGE + "no_answer", r.receiverName()));
        }
        BOOK.removeIf(r -> !senderHasCard(server, r));
    }

    /** Le départ d'un joueur : ses demandes tombent ; celles qu'il n'a pas reçues valent « sans réponse ». */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID gone = event.getEntity().getUUID();
        MinecraftServer server = event.getEntity().getServer();
        for (Request r : BOOK.dropPlayer(gone)) {
            if (r.receiver().equals(gone) && server != null) {
                tell(server.getPlayerList().getPlayer(r.sender()),
                        Component.translatable(MESSAGE + "no_answer", r.receiverName()));
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        BOOK.clear();
    }
}
