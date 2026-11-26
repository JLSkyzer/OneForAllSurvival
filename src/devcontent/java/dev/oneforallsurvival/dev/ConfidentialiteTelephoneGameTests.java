package dev.oneforallsurvival.dev;

import com.mojang.datafixers.util.Pair;
import dev.oneforallsurvival.dev.PanneauOperateurGameTests.Essai;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimCardItem;
import dev.oneforallsurvival.phone.net.SimNumbers;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Tâche 15 du lot T2 (décision de Killian du 2026-11-26) : un téléphone ou une carte SIM tenus ne disent rien de la
 * carte aux autres joueurs. Dans tout paquet d'équipement ({@link ClientboundSetEquipmentPacket}, envoi au suivi comme
 * changement en cours), la pile n'a plus ni numéro, ni titulaire, ni nom d'opérateur, ni {@code sim_id}, ni jeton, ni
 * sous-NBT {@code sim}, ni {@code phone_id} ; ce qui sert au rendu (l'objet, sa batterie) reste ; la pile du serveur
 * n'est jamais modifiée ; le porteur garde tout dans ses paquets d'inventaire.
 *
 * <p>Limite écrite, hors de ces essais : une pile posée au sol, en cadre ou dans un conteneur ouvert garde ses données.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class ConfidentialiteTelephoneGameTests {

    private static final String LOT = "telephone_confidentialite";
    private static final BlockPos A = PanneauOperateurGameTests.A;
    private static final int RESERVE = PanneauOperateurGameTests.RESERVE;
    private static final List<ArmorStand> SUPPORTS = new ArrayList<>();

    private ConfidentialiteTelephoneGameTests() {}

    @AfterBatch(batch = LOT)
    public static void apres(ServerLevel level) {
        for (ArmorStand s : SUPPORTS) {
            s.discard();
        }
        SUPPORTS.clear();
        PanneauOperateurGameTests.apres();
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Ce qu'une ligne ne doit jamais montrer à un autre joueur : textes et identifiants. */
    private record Secrets(List<String> textes, List<UUID> ids) {
        static Secrets de(Essai e, PhoneOperator op, SimCard... cartes) {
            List<String> textes = new ArrayList<>(List.of(op.name(), "sim_number", "sim_holder", "sim_operator",
                    "sim_id", "sim_token", "phone_id"));
            List<UUID> ids = new ArrayList<>();
            for (SimCard c : cartes) {
                SimCard now = e.carte(c);
                textes.add(now.number());
                textes.add(SimNumbers.format(now.number()));
                if (!now.holderName().isEmpty()) {
                    textes.add(now.holderName());
                }
                ids.add(now.simId());
                ids.add(now.token());
                if (now.phoneId() != null) {
                    ids.add(now.phoneId());
                }
            }
            return new Secrets(List.copyOf(textes), List.copyOf(ids));
        }

        /** Le premier secret trouvé dans ces octets, ou vide. */
        Optional<String> dans(byte[] octets) {
            String texte = new String(octets, StandardCharsets.ISO_8859_1);
            for (String t : textes) {
                if (texte.contains(t)) {
                    return Optional.of("texte « " + t + " »");
                }
            }
            for (UUID id : ids) {
                if (porte(octets, id)) {
                    return Optional.of("identifiant " + id.toString().substring(0, 8) + "…");
                }
            }
            return Optional.empty();
        }
    }

    private static boolean contient(byte[] tas, byte[] aiguille) {
        outer:
        for (int i = 0; i + aiguille.length <= tas.length; i++) {
            for (int k = 0; k < aiguille.length; k++) {
                if (tas[i + k] != aiguille[k]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** L'identifiant en binaire (deux moitiés, comme un tableau de quatre entiers NBT) ou en texte (début ou fin). */
    private static boolean porte(byte[] octets, UUID id) {
        byte[] binaire = ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits()).array();
        String texte = new String(octets, StandardCharsets.ISO_8859_1);
        return contient(octets, Arrays.copyOf(binaire, 8)) || contient(octets, Arrays.copyOfRange(binaire, 8, 16))
                || texte.contains(id.toString().substring(0, 8)) || texte.contains(id.toString().substring(28));
    }

    /** Les octets d'un paquet, tels qu'ils partiraient sur le fil. */
    private static byte[] octets(Packet<?> paquet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            paquet.write(buf);
            byte[] out = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), out);
            return out;
        } finally {
            buf.release();
        }
    }

    /** Le paquet relu par un client. */
    private static ClientboundSetEquipmentPacket relu(byte[] octets) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(octets));
        try {
            return new ClientboundSetEquipmentPacket(buf);
        } finally {
            buf.release();
        }
    }

    /** Une pile qui ne dit rien de la carte : ni clé de carte, ni sous-NBT {@code sim}, ni {@code phone_id}. */
    private static Optional<String> cleInterdite(ItemStack pile) {
        CompoundTag tag = pile.getTag();
        if (tag == null) {
            return Optional.empty();
        }
        for (String cle : List.of(PhoneItem.TAG_ID, PhoneItem.TAG_SIM, SimCardItem.TAG_ID, SimCardItem.TAG_TOKEN,
                SimCardItem.TAG_NUMBER, SimCardItem.TAG_OPERATOR, SimCardItem.TAG_HOLDER)) {
            if (tag.contains(cle)) {
                return Optional.of(cle);
            }
        }
        return Optional.empty();
    }

    /** La pile du serveur a toujours tout : identifiant, carte logée avec son jeton (téléphone), ou ses clés (carte). */
    private static void intacte(GameTestHelper helper, ItemStack pile, String quoi) {
        CompoundTag tag = pile.getTag();
        helper.assertTrue(tag != null, quoi + " : la pile du serveur a perdu son NBT");
        if (pile.getItem() instanceof PhoneItem) {
            helper.assertTrue(tag.hasUUID(PhoneItem.TAG_ID) && tag.contains(PhoneItem.TAG_SIM), quoi
                    + " : la pile du serveur a perdu phone_id ou sa carte, clés " + tag.getAllKeys());
            CompoundTag carte = tag.getCompound(PhoneItem.TAG_SIM).getCompound("tag");
            helper.assertTrue(carte.hasUUID(SimCardItem.TAG_TOKEN) && carte.contains(SimCardItem.TAG_NUMBER), quoi
                    + " : la carte logée du serveur a perdu son jeton ou son numéro, clés " + carte.getAllKeys());
        } else {
            for (String cle : List.of(SimCardItem.TAG_ID, SimCardItem.TAG_TOKEN, SimCardItem.TAG_NUMBER,
                    SimCardItem.TAG_OPERATOR, SimCardItem.TAG_HOLDER)) {
                helper.assertTrue(tag.contains(cle), quoi + " : la pile du serveur a perdu " + cle);
            }
        }
    }

    /** Vérifie un paquet d'équipement : ni secret dans ses octets, ni clé de carte une fois relu, l'objet et la batterie gardés. */
    private static void sansSecret(GameTestHelper helper, ClientboundSetEquipmentPacket paquet, Secrets secrets,
            String quoi) {
        byte[] b = octets(paquet);
        Optional<String> fuite = secrets.dans(b);
        helper.assertTrue(fuite.isEmpty(), quoi + " : le paquet d'équipement porte " + fuite.orElse(""));
        // Ce que reçoit un client en mémoire (partie solo, réseau local) : les piles du paquet lui-même.
        for (Pair<EquipmentSlot, ItemStack> p : paquet.getSlots()) {
            Optional<String> cle = cleInterdite(p.getSecond());
            helper.assertTrue(cle.isEmpty(), quoi + " : pile " + p.getFirst() + " du paquet avec " + cle.orElse(""));
        }
        for (Pair<EquipmentSlot, ItemStack> p : relu(b).getSlots()) {
            Optional<String> cle = cleInterdite(p.getSecond());
            helper.assertTrue(cle.isEmpty(), quoi + " : pile " + p.getFirst() + " relue avec " + cle.orElse(""));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Essais
    // ---------------------------------------------------------------------------------------------

    /**
     * Le paquet construit (comme par le serveur, sur des copies) : rien de la carte en binaire ni en texte, ni dans ses
     * piles ; l'objet et la batterie restent (le rendu) ; les piles passées et celles du serveur sont intactes.
     */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void lePaquetDEquipementNePorteRienDeLaCarte(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pv_paquet_f");
        JoueurDeTest t = e.joueur("pv_paquet_t");
        PhoneOperator op = e.fonder(f, A, "Ecoute Discrete", RESERVE);
        ItemStack telephone = e.ligne(op, t);
        SimCard logee = e.registre().cardsOf(op.id()).get(0);
        SimCard seule = e.carte(op, t);
        ItemStack carte = SimCardItem.create(seule, op.name());
        t.joueur().setItemInHand(InteractionHand.OFF_HAND, carte);
        Secrets secrets = Secrets.de(e, op, logee, seule);
        int batterie = PhoneItem.stored(telephone);
        helper.assertTrue(batterie > 0, "témoin : téléphone chargé");

        ItemStack copieTel = telephone.copy();
        ItemStack copieCarte = carte.copy();
        ClientboundSetEquipmentPacket paquet = new ClientboundSetEquipmentPacket(t.joueur().getId(),
                List.of(Pair.of(EquipmentSlot.MAINHAND, copieTel), Pair.of(EquipmentSlot.OFFHAND, copieCarte)));
        sansSecret(helper, paquet, secrets, "paquet construit");
        ClientboundSetEquipmentPacket client = relu(octets(paquet));
        helper.assertTrue(client.getSlots().size() == 2, "deux piles relues : " + client.getSlots().size());
        ItemStack telVu = client.getSlots().get(0).getSecond();
        ItemStack carteVue = client.getSlots().get(1).getSecond();
        helper.assertTrue(telVu.is(PhoneRegistry.PHONE.get()) && carteVue.is(PhoneRegistry.SIM_CARD.get()),
                "les objets restent (rendu) : " + telVu + ", " + carteVue);
        helper.assertTrue(PhoneItem.stored(telVu) == batterie, "la batterie reste : " + PhoneItem.stored(telVu)
                + " au lieu de " + batterie);
        intacte(helper, copieTel, "copie passée au paquet (téléphone)");
        intacte(helper, copieCarte, "copie passée au paquet (carte)");
        intacte(helper, t.joueur().getItemInHand(InteractionHand.MAIN_HAND), "téléphone du serveur");
        intacte(helper, t.joueur().getItemInHand(InteractionHand.OFF_HAND), "carte du serveur");
        // Témoin : sans carte ni téléphone, une pile ordinaire passe telle quelle.
        ItemStack nommee = new ItemStack(net.minecraft.world.item.Items.PAPER);
        nommee.getOrCreateTag().putString("sim_number", "temoin");
        ClientboundSetEquipmentPacket ordinaire = new ClientboundSetEquipmentPacket(1,
                List.of(Pair.of(EquipmentSlot.MAINHAND, nommee)));
        helper.assertTrue(ordinaire.getSlots().get(0).getSecond().getTag() != null
                && ordinaire.getSlots().get(0).getSecond().getTag().contains("sim_number"),
                "une pile d'un autre objet n'est pas touchée");
        e.fermer();
        helper.succeed();
    }

    /**
     * Le vrai chemin : un observateur près d'un joueur qui tient téléphone et carte, et d'un porte-armure qui tient un
     * téléphone ; chaque paquet d'équipement reçu (début du suivi, puis changement de main) ne dit rien de la carte ; le
     * porteur, lui, garde numéro et titulaire dans ses paquets d'inventaire ; les piles du serveur sont intactes.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void unObservateurNeVoitNiNumeroNiTitulaire(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pv_vrai_f");
        JoueurDeTest t = e.joueur("pv_vrai_t");
        PhoneOperator op = e.fonder(f, A, "Ligne Privee", RESERVE);
        ItemStack telF = e.ligne(op, f);
        ItemStack telStand = telF.copy();
        f.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        e.ligne(op, t);
        SimCard seule = e.carte(op, t);
        t.joueur().setItemInHand(InteractionHand.OFF_HAND, SimCardItem.create(seule, op.name()));
        Secrets secrets = Secrets.de(e, op, e.registre().cardsOf(op.id()).toArray(new SimCard[0]));
        ArmorStand support = EntityType.ARMOR_STAND.create(helper.getLevel());
        helper.assertTrue(support != null, "porte-armure");
        Vec3 ou = helper.absoluteVec(new Vec3(5.5, 0, 2.5));
        support.moveTo(ou.x, ou.y, ou.z, 0f, 0f);
        support.setItemSlot(EquipmentSlot.MAINHAND, telStand);
        helper.getLevel().addFreshEntity(support);
        SUPPORTS.add(support);
        JoueurDeTest o = e.joueur("pv_vrai_o");
        Map<Integer, List<ClientboundSetEquipmentPacket>> recus = new HashMap<>();
        boolean[] change = {false};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (ClientboundSetEquipmentPacket p : o.equipementsRecus()) {
                        recus.computeIfAbsent(p.getEntity(), k -> new ArrayList<>()).add(p);
                    }
                    helper.assertTrue(aUnTelephone(recus.get(t.joueur().getId()))
                            && aUnTelephone(recus.get(support.getId())), "l'observateur attend les deux téléphones");
                })
                .thenExecute(() -> {
                    // Un changement en cours : le joueur passe la carte dans sa main principale, le téléphone à côté ;
                    // le porte-armure reçoit une carte dans l'autre main. Le joueur simulé n'a pas de connexion tiquée
                    // par le serveur : son tic de vivant (qui relève les changements d'équipement) est joué ici.
                    ItemStack tel = t.joueur().getItemInHand(InteractionHand.MAIN_HAND);
                    ItemStack carte = t.joueur().getItemInHand(InteractionHand.OFF_HAND);
                    t.joueur().setItemInHand(InteractionHand.MAIN_HAND, carte);
                    t.joueur().setItemInHand(InteractionHand.OFF_HAND, tel);
                    t.joueur().doTick();
                    support.setItemSlot(EquipmentSlot.OFFHAND, carte.copy());
                    change[0] = true;
                })
                .thenWaitUntil(() -> {
                    for (ClientboundSetEquipmentPacket p : o.equipementsRecus()) {
                        recus.computeIfAbsent(p.getEntity(), k -> new ArrayList<>()).add(p);
                    }
                    List<ClientboundSetEquipmentPacket> deT = recus.getOrDefault(t.joueur().getId(), List.of());
                    helper.assertTrue(deT.stream().anyMatch(p -> p.getSlots().stream().anyMatch(s ->
                            s.getFirst() == EquipmentSlot.MAINHAND && s.getSecond().is(PhoneRegistry.SIM_CARD.get()))),
                            "l'observateur attend le changement de main du joueur");
                    List<ClientboundSetEquipmentPacket> duSupport = recus.getOrDefault(support.getId(), List.of());
                    helper.assertTrue(duSupport.stream().anyMatch(p -> p.getSlots().stream().anyMatch(s ->
                            s.getFirst() == EquipmentSlot.OFFHAND && s.getSecond().is(PhoneRegistry.SIM_CARD.get()))),
                            "l'observateur attend la carte du porte-armure");
                })
                .thenExecute(() -> {
                    int vus = 0;
                    for (Map.Entry<Integer, List<ClientboundSetEquipmentPacket>> en : recus.entrySet()) {
                        for (ClientboundSetEquipmentPacket p : en.getValue()) {
                            sansSecret(helper, p, secrets, "paquet reçu par l'observateur (entité " + en.getKey() + ")");
                            vus++;
                        }
                    }
                    helper.assertTrue(vus >= 4, "paquets d'équipement lus : " + vus);
                    intacte(helper, t.joueur().getItemInHand(InteractionHand.OFF_HAND), "téléphone du porteur");
                    intacte(helper, t.joueur().getItemInHand(InteractionHand.MAIN_HAND), "carte du porteur");
                    intacte(helper, support.getItemBySlot(EquipmentSlot.MAINHAND), "téléphone du porte-armure");
                    intacte(helper, support.getItemBySlot(EquipmentSlot.OFFHAND), "carte du porte-armure");
                    // Le porteur, lui, voit sa carte : son inventaire lui part avec numéro et titulaire.
                    t.menusRecus();
                    t.joueur().inventoryMenu.sendAllDataToRemote();
                    SimCard deT = e.carte(seule);
                    String numero = deT.number();
                    String titulaire = deT.holderName();
                    boolean numeroVu = false;
                    boolean titulaireVu = false;
                    for (Packet<?> p : t.menusRecus()) {
                        if (p instanceof ClientboundContainerSetContentPacket) {
                            String texte = new String(octets(p), StandardCharsets.ISO_8859_1);
                            numeroVu |= texte.contains(numero);
                            titulaireVu |= texte.contains(titulaire);
                        }
                    }
                    helper.assertTrue(numeroVu && titulaireVu, "le porteur garde numéro et titulaire dans son inventaire"
                            + " (numéro " + numeroVu + ", titulaire " + titulaireVu + ")");
                    // Le porteur ne reçoit pas son propre paquet d'équipement (le suivi l'exclut) : sinon son client
                    // remplacerait ses piles par les piles réduites.
                    helper.assertTrue(change[0], "témoin : changement fait");
                    int siens = 0;
                    for (ClientboundSetEquipmentPacket p : t.equipementsRecus()) {
                        if (p.getEntity() == t.joueur().getId()) {
                            siens++;
                        }
                    }
                    helper.assertTrue(siens == 0, "le porteur a reçu " + siens + " paquet(s) d'équipement de lui-même");
                    support.discard();
                    SUPPORTS.remove(support);
                    e.fermer();
                })
                .thenSucceed();
    }

    /**
     * Revue de la tâche 15, point 1 : le message de mort d'un tueur qui tient un téléphone (puis une carte) renommé part
     * à tout le serveur avec le survol de l'objet ; sérialisé, il ne dit rien de la carte (ni texte, ni identifiant en
     * NBT texte) ; un autre objet renommé garde son survol entier ; les piles du serveur sont intactes.
     */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void leMessageDeMortNeDitRienDeLaCarte(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pv_mort_f");
        JoueurDeTest k = e.joueur("pv_mort_k");
        JoueurDeTest v = e.joueur("pv_mort_v");
        PhoneOperator op = e.fonder(f, A, "Avis Funebre", RESERVE);
        // Le titulaire est f, pas le tueur : le nom du tueur est dans le message de mort, à bon droit.
        ItemStack telephone = e.ligne(op, f).copy();
        f.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        telephone.setHoverName(net.minecraft.network.chat.Component.literal("Mon tel"));
        SimCard seule = e.carte(op, f);
        ItemStack carte = SimCardItem.create(seule, op.name());
        carte.setHoverName(net.minecraft.network.chat.Component.literal("Ma carte"));
        Secrets secrets = Secrets.de(e, op, e.registre().cardsOf(op.id()).toArray(new SimCard[0]));
        for (ItemStack tenue : List.of(telephone, carte)) {
            k.joueur().setItemInHand(InteractionHand.MAIN_HAND, tenue);
            ItemStack dansLaMain = k.joueur().getMainHandItem();
            net.minecraft.network.chat.Component mort = k.joueur().damageSources().playerAttack(k.joueur())
                    .getLocalizedDeathMessage(v.joueur());
            String json = net.minecraft.network.chat.Component.Serializer.toJson(mort);
            String quoi = dansLaMain.is(PhoneRegistry.PHONE.get()) ? "téléphone" : "carte";
            helper.assertTrue(json.contains("show_item") && json.contains(quoi.equals("téléphone") ? "Mon tel" : "Ma carte"),
                    quoi + " : témoin, le message porte le survol de l'objet renommé");
            Optional<String> fuite = secrets.dans(json.getBytes(StandardCharsets.UTF_8));
            for (UUID id : secrets.ids()) {
                int[] q = net.minecraft.core.UUIDUtil.uuidToIntArray(id);
                if (json.contains(Integer.toString(q[0])) && json.contains(Integer.toString(q[3]))) {
                    fuite = Optional.of("identifiant en NBT texte " + id.toString().substring(0, 8) + "…");
                }
            }
            helper.assertTrue(fuite.isEmpty(), quoi + " : le message de mort porte " + fuite.orElse(""));
            intacte(helper, dansLaMain, quoi + " du tueur");
        }
        // Témoin : un autre objet renommé garde son survol entier.
        ItemStack epee = new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        epee.setHoverName(net.minecraft.network.chat.Component.literal("Lame"));
        epee.getOrCreateTag().putString("sim_number", "temoin-garde");
        k.joueur().setItemInHand(InteractionHand.MAIN_HAND, epee);
        String json = net.minecraft.network.chat.Component.Serializer.toJson(k.joueur().damageSources()
                .playerAttack(k.joueur()).getLocalizedDeathMessage(v.joueur()));
        helper.assertTrue(json.contains("temoin-garde"), "un autre objet renommé garde son survol : " + json);
        e.fermer();
        helper.succeed();
    }

    private static boolean aUnTelephone(List<ClientboundSetEquipmentPacket> paquets) {
        if (paquets == null) {
            return false;
        }
        for (ClientboundSetEquipmentPacket p : paquets) {
            for (Pair<EquipmentSlot, ItemStack> s : p.getSlots()) {
                if (s.getSecond().is(PhoneRegistry.PHONE.get())) {
                    return true;
                }
            }
        }
        return false;
    }
}
