package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinStore;
import dev.oneforallsurvival.phone.PhoneConfig;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/**
 * Le registre des opérateurs, des cartes SIM, des demandes et des antennes. NBT seulement, comme {@code PhoneDataTest} :
 * pas de monde, pas de Minecraft. Les clés de dimension viennent de {@link TestDimensions} (le registre de dimensions
 * du jeu ne s'amorce pas sous JUnit), et le rechargement passe par {@link PhoneOperators#load(CompoundTag, Function)}.
 */
class PhoneOperatorsTest {

    private static final Function<ResourceLocation, ResourceKey<Level>> DIMENSIONS = loc -> TestDimensions.key(loc.getPath());
    private static final AntennaLoad.Breakdown ZERO = new AntennaLoad.Breakdown(0, 0, 0);

    private static final UUID U1 = uuid(1);
    private static final UUID U2 = uuid(2);
    private static final UUID U3 = uuid(3);
    private static final UUID U4 = uuid(4);
    private static final UUID PHONE = uuid(900);

    private static UUID uuid(int n) {
        return UUID.fromString(String.format(java.util.Locale.ROOT, "00000000-0000-0000-0000-%012d", n));
    }

    private static CompoundTag cardRow(CompoundTag model, int sim, String number, String state) {
        CompoundTag row = model.copy();
        row.putUUID("sim", uuid(sim));
        row.putString("number", number);
        row.putString("state", state);
        return row;
    }

    private static CompoundTag antennaRow(CompoundTag model, int id, String dimension, boolean withX) {
        CompoundTag row = model.copy();
        row.putUUID("id", uuid(id));
        row.putString("dimension", dimension);
        if (!withX) {
            row.remove("x");
        }
        return row;
    }

    private static PhoneOperators reload(PhoneOperators registry) {
        return PhoneOperators.load(registry.save(new CompoundTag()), DIMENSIONS);
    }

    private static PhoneOperator found(PhoneOperators registry, UUID founder, String name) {
        PhoneOperators.Result result = registry.found(founder, "Fondateur", name, 1_000L);
        assertTrue(result.founded(), "fondation refusée : " + result.status());
        return result.operator();
    }

    private static AntennaRecord antenna(UUID id, String operatorId, ResourceKey<Level> dimension, int x, long placedAt) {
        return new AntennaRecord(id, operatorId, dimension, new BlockPos(x, 64, -x), placedAt, true, 0L, 0L, 0L, ZERO);
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Test
    void allerRetourComplet() {
        PhoneOperators registry = new PhoneOperators();
        RandomSource random = RandomSource.create(7L);
        PhoneOperator mairie = found(registry, U1, "Mairie");
        PhoneOperator sud = found(registry, U2, "Sud Télécom");
        // Tarifs du jour (10 pour la mairie) et du lendemain (12 et 7), plafond du jour (30) et du lendemain (25).
        assertTrue(registry.setNextTariff(mairie.id(), 10L));
        registry.setNextTariffCap(30L);
        registry.applyNextTariffs();
        assertTrue(registry.setNextTariff(mairie.id(), 12L));
        assertTrue(registry.setNextTariff(sud.id(), 7L));
        registry.setNextTariffCap(25L);
        registry.deposit(mairie.id(), Coin.COPPER, 5L);
        registry.deposit(mairie.id(), Coin.GOLD, 2L);

        SimCard named = registry.issue(mairie.id(), U3, "Alice", random, 2_000L);
        SimCard anonymous = registry.issue(mairie.id(), null, "", random, 2_001L);
        SimCard suspended = registry.issue(sud.id(), U4, "Bob", random, 2_002L);
        SimCard deactivated = registry.issue(sud.id(), U3, "Alice", random, 2_003L);
        SimCard lodged = registry.issue(mairie.id(), U4, "Bob", random, 2_004L);
        assertTrue(registry.setState(suspended.simId(), SimCard.State.SUSPENDED));
        assertTrue(registry.setState(deactivated.simId(), SimCard.State.DEACTIVATED));
        UUID lodgedToken = registry.rotateToken(lodged.simId(), PHONE, random);

        assertTrue(registry.request(mairie.id(), U3, "Alice", 3_000L).isPresent());
        assertTrue(registry.request(sud.id(), U4, "Bob", 3_001L).isPresent());

        UUID a1 = uuid(101);
        UUID a2 = uuid(102);
        UUID a3 = uuid(103);
        registry.putAntenna(antenna(a1, mairie.id(), TestDimensions.OVERWORLD, 10, 500L));
        registry.putAntenna(antenna(a2, mairie.id(), TestDimensions.NETHER, 20, 400L));
        registry.putAntenna(antenna(a3, sud.id(), TestDimensions.OVERWORLD, 30, 600L));
        registry.noteTick(a1, true, 100L, 50, new AntennaLoad.Breakdown(40, 12, 9));
        registry.noteTick(a1, true, 101L, 60, new AntennaLoad.Breakdown(40, 18, 0));
        registry.noteTick(a3, false, 100L, 0, new AntennaLoad.Breakdown(1, 2, 3));
        registry.closeAntennaDay();
        registry.noteTick(a1, true, 200L, 7, new AntennaLoad.Breakdown(40, 6, 0));

        registry.addNotice(U3, Component.literal("Votre carte est prête"));
        registry.addNotice(U3, Component.literal("Tarif modifié"));

        CompoundTag saved = registry.save(new CompoundTag());
        PhoneOperators back = PhoneOperators.load(saved, DIMENSIONS);

        assertEquals(List.copyOf(registry.all()), List.copyOf(back.all()));
        assertEquals(registry.tariffCap(), back.tariffCap());
        assertEquals(registry.nextTariffCap(), back.nextTariffCap());
        assertEquals(30L, back.tariffCap());
        assertEquals(Optional.of(25L), back.nextTariffCap());
        assertEquals(12L, back.byId(mairie.id()).orElseThrow().nextTariff());
        assertEquals(7L, back.byId(sud.id()).orElseThrow().nextTariff());
        assertEquals(10L, back.byId(mairie.id()).orElseThrow().tariff());
        assertEquals(registry.till(mairie.id()), back.till(mairie.id()));
        assertEquals(5L, back.till(mairie.id()).get(Coin.COPPER));
        for (SimCard card : List.of(named, anonymous, suspended, deactivated, lodged)) {
            assertEquals(registry.card(card.simId()).orElseThrow(), back.card(card.simId()).orElseThrow());
            assertEquals(card.number(), back.cardByNumber(card.number()).orElseThrow().number());
        }
        assertNull(back.card(anonymous.simId()).orElseThrow().holder());
        assertEquals(SimCard.State.SUSPENDED, back.card(suspended.simId()).orElseThrow().state());
        assertEquals(SimCard.State.DEACTIVATED, back.card(deactivated.simId()).orElseThrow().state());
        assertEquals(PHONE, back.card(lodged.simId()).orElseThrow().phoneId());
        assertEquals(lodgedToken, back.card(lodged.simId()).orElseThrow().token());
        assertEquals(registry.requestsOf(mairie.id()), back.requestsOf(mairie.id()));
        assertEquals(registry.requestsOf(sud.id()), back.requestsOf(sud.id()));
        assertEquals(1, back.requestsOf(mairie.id()).size());
        assertEquals(3, back.antennas().size());
        for (UUID id : List.of(a1, a2, a3)) {
            assertEquals(registry.antenna(id).orElseThrow(), back.antenna(id).orElseThrow());
        }
        // La première posée est la principale, même si la seconde est plus ancienne.
        assertEquals(a1, back.byId(mairie.id()).orElseThrow().mainAntenna());
        assertEquals(a3, back.byId(sud.id()).orElseThrow().mainAntenna());
        assertEquals(registry.byId(mairie.id()).orElseThrow().mainAntenna(),
                back.byId(mairie.id()).orElseThrow().mainAntenna());
        assertEquals(TestDimensions.NETHER, back.antenna(a2).orElseThrow().dimension());
        assertEquals(new AntennaLoad.Breakdown(40, 6, 0), back.antenna(a1).orElseThrow().last());
        assertEquals(7L, back.antenna(a1).orElseThrow().feToday());
        assertEquals(110L, back.antenna(a1).orElseThrow().feYesterday());
        List<Component> notices = back.takeNotices(U3);
        assertEquals(2, notices.size());
        assertEquals("Votre carte est prête", notices.get(0).getString());

        // Octet pour octet : ce qui se relit se réécrit à l'identique (avant que takeNotices ne vide les avis).
        assertEquals(saved, PhoneOperators.load(saved, DIMENSIONS).save(new CompoundTag()));
    }

    @Test
    void uneAncienneSauvegardeSansOperateurSeCharge() {
        PhoneOperators registry = PhoneOperators.load(new CompoundTag());
        assertTrue(registry.all().isEmpty());
        assertTrue(registry.antennas().isEmpty());
        assertEquals(PhoneConfig.phoneDefaultTariffCap(), registry.tariffCap());
        assertEquals(20L, registry.tariffCap());
        assertTrue(registry.nextTariffCap().isEmpty());
        assertFalse(registry.isDirty());
        // Le registre est utilisable : une carte s'émet, un opérateur se fonde.
        found(registry, U1, "Mairie");
        assertEquals(1, registry.all().size());
    }

    @Test
    void uneLigneAbimeeEstIgnoreeSansFairePlanter() {
        PhoneOperators registry = new PhoneOperators();
        RandomSource random = RandomSource.create(11L);
        PhoneOperator op = found(registry, U1, "Mairie");
        SimCard card = registry.issue(op.id(), U3, "Alice", random, 5L);
        registry.request(op.id(), U3, "Alice", 6L);
        registry.putAntenna(antenna(uuid(101), op.id(), TestDimensions.OVERWORLD, 10, 500L));
        CompoundTag good = registry.save(new CompoundTag());

        CompoundTag bad = good.copy();
        // Lignes sans identifiant, vides, ou dont un champ n'a pas le bon type.
        for (String list : List.of("operators", "cards", "requests", "antennas")) {
            ListTag rows = bad.getList(list, 10);
            rows.add(new CompoundTag());
            CompoundTag wrongTypes = rows.getCompound(0).copy();
            for (String key : List.copyOf(wrongTypes.getAllKeys())) {
                wrongTypes.put(key, IntTag.valueOf(7));
            }
            rows.add(wrongTypes);
            CompoundTag stringsEverywhere = rows.getCompound(0).copy();
            for (String key : List.copyOf(stringsEverywhere.getAllKeys())) {
                stringsEverywhere.put(key, StringTag.valueOf("n'importe quoi"));
            }
            rows.add(stringsEverywhere);
        }
        // Énumération inconnue, numéro qui n'en est pas un, dimension illisible, coordonnée absente ; chacune a un
        // témoin sans défaut, pour que seul le défaut explique le rejet.
        ListTag badCards = bad.getList("cards", 10);
        badCards.add(cardRow(badCards.getCompound(0), 776, "0622222222", "ACTIVE"));
        badCards.add(cardRow(badCards.getCompound(0), 777, "0611111111", "EXPLOSEE"));
        badCards.add(cardRow(badCards.getCompound(0), 778, "pas un numero", "ACTIVE"));
        ListTag badAntennas = bad.getList("antennas", 10);
        badAntennas.add(antennaRow(badAntennas.getCompound(0), 781, "minecraft:the_nether", true));
        badAntennas.add(antennaRow(badAntennas.getCompound(0), 779, "Pas Une Dimension", true));
        badAntennas.add(antennaRow(badAntennas.getCompound(0), 780, "minecraft:overworld", false));
        CompoundTag noDimension = badAntennas.getCompound(0).copy();
        noDimension.putUUID("id", uuid(782));
        noDimension.remove("dimension");
        badAntennas.add(noDimension);
        badAntennas.add(antennaRow(badAntennas.getCompound(0), 783, "", true));
        bad.put("tariffCap", StringTag.valueOf("beaucoup"));
        bad.put("usedNumbers", IntTag.valueOf(3));

        PhoneOperators back = PhoneOperators.load(bad, DIMENSIONS);

        assertEquals(1, back.all().size());
        assertEquals(registry.byId(op.id()).orElseThrow(), back.byId(op.id()).orElseThrow());
        assertEquals(card, back.card(card.simId()).orElseThrow());
        assertEquals(2, back.cardsOf(op.id()).size());
        assertTrue(back.card(uuid(776)).isPresent());
        assertTrue(back.card(uuid(777)).isEmpty());
        assertTrue(back.card(uuid(778)).isEmpty());
        assertEquals(1, back.requestsOf(op.id()).size());
        assertEquals(2, back.antennas().size());
        assertTrue(back.antenna(uuid(781)).isPresent());
        assertTrue(back.antenna(uuid(779)).isEmpty());
        assertTrue(back.antenna(uuid(780)).isEmpty());
        assertTrue(back.antenna(uuid(782)).isEmpty());
        assertTrue(back.antenna(uuid(783)).isEmpty());
        // Un plafond illisible retombe sur celui de la configuration.
        assertEquals(PhoneConfig.phoneDefaultTariffCap(), back.tariffCap());
        // Même si la liste des numéros est abîmée, le numéro d'une carte relue reste réservé.
        assertThrows(IllegalStateException.class, () -> back.issue(op.id(), null, "", new FixedNumbers(card.number()), 9L));
    }

    @Test
    void deuxLignesDeMemeIdentifiantOuDeMemeNumeroNeFontQuUneCarte() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        SimCard card = registry.issue(op.id(), U3, "Alice", RandomSource.create(3L), 5L);
        CompoundTag tag = registry.save(new CompoundTag());
        ListTag cards = tag.getList("cards", 10);
        cards.add(cards.getCompound(0).copy());
        CompoundTag sameNumber = cards.getCompound(0).copy();
        sameNumber.putUUID("sim", uuid(555));
        cards.add(sameNumber);
        ListTag operators = tag.getList("operators", 10);
        operators.add(operators.getCompound(0).copy());

        PhoneOperators back = PhoneOperators.load(tag, DIMENSIONS);

        assertEquals(1, back.cardsOf(op.id()).size());
        assertEquals(card, back.cardByNumber(card.number()).orElseThrow());
        assertEquals(1, back.all().size());
    }

    // ---------------------------------------------------------------------------------------------
    // Opérateurs
    // ---------------------------------------------------------------------------------------------

    @Test
    void unNomPrisEstRefuseSansCasse() {
        PhoneOperators registry = new PhoneOperators();
        found(registry, U1, "Mairie");
        PhoneOperators.Result again = registry.found(U2, "Autre", "mairie", 1L);
        assertEquals(PhoneOperators.Status.NAME_TAKEN, again.status());
        assertNull(again.operator());
        assertEquals(PhoneOperators.Status.NAME_TAKEN, registry.found(U2, "Autre", "  MAIRIE ", 1L).status());
        assertEquals(1, registry.all().size());
        assertEquals("Mairie", registry.byName("mAiRiE").orElseThrow().name());
        // Un nom qui n'est pas valable est refusé comme tel.
        assertEquals(PhoneOperators.Status.BAD_NAME, registry.found(U2, "Autre", "   ", 1L).status());
        assertEquals(PhoneOperators.Status.BAD_NAME, registry.found(U2, "Autre", "a".repeat(25), 1L).status());
        assertEquals(PhoneOperators.Status.BAD_NAME, registry.found(U2, "Autre", "Trop" + (char) 7 + "fou", 1L).status());
    }

    @Test
    void unSeulOperateurParJoueur() {
        PhoneOperators registry = new PhoneOperators();
        found(registry, U1, "Mairie");
        assertEquals(PhoneOperators.Status.ALREADY_HAS_ONE, registry.found(U1, "Fondateur", "Autre nom", 1L).status());
        assertEquals(1, registry.all().size());
        // Un autre joueur peut fonder, et son fondateur se retrouve.
        PhoneOperator second = found(registry, U2, "Autre nom");
        assertEquals(second, registry.byFounder(U2).orElseThrow());
        // Passer l'opérateur à quelqu'un qui en a déjà un est refusé ; à un libre, accepté.
        assertFalse(registry.setFounder(second.id(), U1, "Fondateur"));
        assertTrue(registry.setFounder(second.id(), U3, "Troisième"));
        assertEquals(U3, registry.byId(second.id()).orElseThrow().founder());
        assertFalse(registry.setFounder("inconnu", U4, "Quatrième"));
    }

    @Test
    void retirerUnOperateurDesactiveSesCartesEtLaisseLeReste() {
        PhoneOperators registry = new PhoneOperators();
        RandomSource random = RandomSource.create(1L);
        PhoneOperator op = found(registry, U1, "Mairie");
        PhoneOperator other = found(registry, U2, "Sud");
        SimCard active = registry.issue(op.id(), U3, "Alice", random, 1L);
        SimCard suspended = registry.issue(op.id(), null, "", random, 2L);
        registry.setState(suspended.simId(), SimCard.State.SUSPENDED);
        SimCard elsewhere = registry.issue(other.id(), U4, "Bob", random, 3L);
        registry.putAntenna(antenna(uuid(101), op.id(), TestDimensions.OVERWORLD, 1, 1L));
        registry.request(op.id(), U4, "Bob", 4L);
        registry.setDirty(false);

        assertFalse(registry.remove("inconnu"));
        assertFalse(registry.isDirty());
        assertTrue(registry.remove(op.id()));

        assertTrue(registry.isDirty());
        assertTrue(registry.byId(op.id()).isEmpty());
        assertTrue(registry.byFounder(U1).isEmpty());
        // Aucune carte active ne survit à son opérateur ; celles des autres ne bougent pas.
        assertEquals(SimCard.State.DEACTIVATED, registry.card(active.simId()).orElseThrow().state());
        assertEquals(SimCard.State.DEACTIVATED, registry.card(suspended.simId()).orElseThrow().state());
        assertEquals(0, registry.activeCards(op.id()));
        assertEquals(SimCard.State.ACTIVE, registry.card(elsewhere.simId()).orElseThrow().state());
        // Leurs numéros restent réservés et à elles.
        assertEquals(active.simId(), registry.cardByNumber(active.number()).orElseThrow().simId());
        assertTrue(registry.save(new CompoundTag()).getList("usedNumbers", 8).contains(StringTag.valueOf(active.number())));
        // Antennes et demandes restent à la charge de l'appelant ; le joueur peut de nouveau fonder.
        assertEquals(1, registry.antennas().size());
        assertEquals(1, registry.requestsOf(op.id()).size());
        assertTrue(registry.found(U1, "Fondateur", "Mairie", 5L).founded());
    }

    @Test
    void uneCaisseNonVideEmpecheLeRetrait() {
        // Tâche 11 : la caisse se verse d'abord au fondateur ; tant qu'elle n'est pas vide, rien ne bouge.
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        SimCard card = registry.issue(op.id(), U3, "Alice", RandomSource.create(7L), 1L);
        registry.deposit(op.id(), Coin.GOLD, 3L);
        registry.setDirty(false);
        assertFalse(registry.remove(op.id()));
        assertFalse(registry.isDirty());
        assertTrue(registry.byId(op.id()).isPresent());
        assertEquals(3L, registry.till(op.id()).get(Coin.GOLD));
        assertEquals(SimCard.State.ACTIVE, registry.card(card.simId()).orElseThrow().state());
        assertTrue(registry.formerFounder(op.id()).isEmpty());
        // Vidée, la caisse ne retient plus rien.
        assertTrue(registry.withdraw(op.id(), Coin.GOLD, 3L));
        assertTrue(registry.remove(op.id()));
        assertTrue(registry.byId(op.id()).isEmpty());
    }

    @Test
    void lAncienFondateurEstGardeEtRelu() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        assertTrue(registry.formerFounder(op.id()).isEmpty(), "un opérateur vivant n'a pas d'ancien fondateur");
        assertTrue(registry.formerFounder(null).isEmpty());
        assertTrue(registry.remove(op.id()));
        PhoneOperators.FormerFounder former = registry.formerFounder(op.id()).orElseThrow();
        assertEquals(U1, former.uuid());
        assertEquals("Fondateur", former.name());
        PhoneOperators back = reload(registry);
        assertEquals(Optional.of(former), back.formerFounder(op.id()));
        assertTrue(back.formerFounder("inconnu").isEmpty());
    }

    @Test
    void desactiverLesCartesRendCellesQuiChangent() {
        PhoneOperators registry = new PhoneOperators();
        RandomSource random = RandomSource.create(3L);
        PhoneOperator op = found(registry, U1, "Mairie");
        PhoneOperator other = found(registry, U2, "Sud");
        SimCard active = registry.issue(op.id(), U3, "Alice", random, 1L);
        SimCard suspended = registry.issue(op.id(), U4, "Bob", random, 2L);
        registry.setState(suspended.simId(), SimCard.State.SUSPENDED);
        SimCard elsewhere = registry.issue(other.id(), U3, "Alice", random, 3L);
        int revision = registry.cardRevision();
        registry.setDirty(false);
        List<SimCard> changed = registry.deactivateCards(op.id());
        assertEquals(2, changed.size());
        assertTrue(registry.isDirty());
        assertNotEquals(revision, registry.cardRevision());
        assertEquals(0, registry.activeCards(op.id()));
        assertEquals(SimCard.State.DEACTIVATED, registry.card(active.simId()).orElseThrow().state());
        assertEquals(SimCard.State.DEACTIVATED, registry.card(suspended.simId()).orElseThrow().state());
        assertEquals(SimCard.State.ACTIVE, registry.card(elsewhere.simId()).orElseThrow().state());
        registry.setDirty(false);
        assertTrue(registry.deactivateCards(op.id()).isEmpty(), "une seconde fois : rien ne change");
        assertFalse(registry.isDirty());
    }

    @Test
    void lePlafondDuLendemainSeRemetTelQuIlEtait() {
        PhoneOperators registry = new PhoneOperators();
        registry.setNextTariffCap(9L);
        registry.setDirty(false);
        registry.resetNextTariffCap(null);
        assertTrue(registry.isDirty());
        assertTrue(registry.nextTariffCap().isEmpty());
        registry.resetNextTariffCap(4L);
        assertEquals(Optional.of(4L), registry.nextTariffCap());
    }

    // ---------------------------------------------------------------------------------------------
    // Cartes SIM
    // ---------------------------------------------------------------------------------------------

    @Test
    void chaqueCarteAUnNumeroUnique() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        RandomSource random = RandomSource.create(20261120L);
        Set<String> numbers = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        Set<UUID> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            SimCard card = registry.issue(op.id(), null, "", random, i);
            assertTrue(card.number().matches("06[0-9]{8}"), card.number());
            assertTrue(numbers.add(card.number()), "numéro en double : " + card.number());
            assertTrue(ids.add(card.simId()));
            assertTrue(tokens.add(card.token()));
            assertEquals(SimCard.State.ACTIVE, card.state());
            assertNull(card.phoneId());
        }
        assertEquals(200, registry.cardsOf(op.id()).size());
        assertEquals(200, registry.activeCards(op.id()));
        assertEquals(200, numbers.size());
    }

    @Test
    void unNumeroNeRevientJamais() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        // Même graine : le second tirage retombe d'abord sur le numéro du premier.
        SimCard first = registry.issue(op.id(), U3, "Alice", RandomSource.create(42L), 1L);
        assertTrue(registry.setState(first.simId(), SimCard.State.DEACTIVATED));
        SimCard second = registry.issue(op.id(), U4, "Bob", RandomSource.create(42L), 2L);
        assertNotEquals(first.number(), second.number());
        assertEquals(first.number(), registry.card(first.simId()).orElseThrow().number());

        // Le numéro reste réservé après un rechargement, même si la ligne de la carte disparaît.
        CompoundTag tag = registry.save(new CompoundTag());
        assertTrue(tag.getList("usedNumbers", 8).contains(StringTag.valueOf(first.number())));
        ListTag cards = tag.getList("cards", 10);
        ListTag kept = new ListTag();
        for (int i = 0; i < cards.size(); i++) {
            if (!cards.getCompound(i).getString("number").equals(first.number())) {
                kept.add(cards.getCompound(i));
            }
        }
        tag.put("cards", kept);
        PhoneOperators back = PhoneOperators.load(tag, DIMENSIONS);
        assertTrue(back.card(first.simId()).isEmpty());
        SimCard third = back.issue(op.id(), null, "", RandomSource.create(42L), 3L);
        assertNotEquals(first.number(), third.number());
        assertNotEquals(second.number(), third.number());
    }

    @Test
    void uneCarteDesactiveeNeCompteNiCommeActiveNiCommeLibre() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        RandomSource random = RandomSource.create(5L);
        SimCard a = registry.issue(op.id(), U3, "Alice", random, 1L);
        SimCard b = registry.issue(op.id(), U4, "Bob", random, 2L);
        SimCard c = registry.issue(op.id(), null, "", random, 3L);
        assertEquals(3, registry.activeCards(op.id()));
        registry.setState(a.simId(), SimCard.State.SUSPENDED);
        registry.setState(b.simId(), SimCard.State.DEACTIVATED);
        assertEquals(1, registry.activeCards(op.id()));
        assertEquals(3, registry.cardsOf(op.id()).size());
        assertEquals(c.simId(), registry.cardByNumber(c.number()).orElseThrow().simId());
        assertEquals(b.simId(), registry.cardByNumber(b.number()).orElseThrow().simId());
        assertTrue(registry.cardByNumber("0600000000").isEmpty());
        assertTrue(registry.cardByNumber("n'importe quoi").isEmpty());
        assertFalse(registry.setState(uuid(12345), SimCard.State.ACTIVE));
        assertEquals(0, registry.activeCards("inconnu"));
    }

    @Test
    void leJetonTourneALInsertion() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        RandomSource random = RandomSource.create(9L);
        SimCard card = registry.issue(op.id(), U3, "Alice", random, 1L);
        UUID old = card.token();

        UUID fresh = registry.rotateToken(card.simId(), PHONE, random);

        assertNotEquals(old, fresh);
        SimCard stored = registry.card(card.simId()).orElseThrow();
        assertEquals(fresh, stored.token());
        assertEquals(PHONE, stored.phoneId());
        // Une seconde insertion donne encore un autre jeton, et l'ancien ne revient pas.
        UUID again = registry.rotateToken(card.simId(), uuid(901), random);
        assertNotEquals(fresh, again);
        assertNotEquals(old, again);
        assertEquals(uuid(901), registry.card(card.simId()).orElseThrow().phoneId());
        // Retirer la carte du téléphone la déloge sans toucher au reste.
        registry.unbind(card.simId());
        SimCard unbound = registry.card(card.simId()).orElseThrow();
        assertNull(unbound.phoneId());
        assertEquals(again, unbound.token());
        assertEquals(card.number(), unbound.number());
        // Carte inconnue : refus net, sans jeton dans le message.
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> registry.rotateToken(uuid(31337), PHONE, random));
        assertFalse(String.valueOf(refused.getMessage()).contains(again.toString()));
    }

    @Test
    void uneCarteNeMontreJamaisSonJeton() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        SimCard card = registry.issue(op.id(), U3, "Alice", RandomSource.create(9L), 1L);
        String text = card.toString();
        assertFalse(text.contains(card.token().toString()), text);
        assertTrue(text.contains(card.number()), text);
        // Même chose pour une carte rendue par le registre après rotation.
        UUID fresh = registry.rotateToken(card.simId(), PHONE, RandomSource.create(10L));
        assertFalse(registry.card(card.simId()).orElseThrow().toString().contains(fresh.toString()));
    }

    // ---------------------------------------------------------------------------------------------
    // Demandes
    // ---------------------------------------------------------------------------------------------

    @Test
    void uneDemandeParJoueurEtParOperateur() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator mairie = found(registry, U1, "Mairie");
        PhoneOperator sud = found(registry, U2, "Sud");
        SimRequest request = registry.request(mairie.id(), U3, "Alice", 10L).orElseThrow();
        assertEquals(mairie.id(), request.operatorId());
        assertEquals(U3, request.player());
        assertEquals("Alice", request.playerName());
        assertEquals(10L, request.at());
        // Redemander au même opérateur : refusé, et rien ne change.
        assertTrue(registry.request(mairie.id(), U3, "Alice", 11L).isEmpty());
        assertEquals(1, registry.requestsOf(mairie.id()).size());
        // Un autre opérateur, un autre joueur : accepté.
        assertTrue(registry.request(sud.id(), U3, "Alice", 12L).isPresent());
        assertTrue(registry.request(mairie.id(), U4, "Bob", 13L).isPresent());
        assertEquals(2, registry.requestsOf(mairie.id()).size());
        assertEquals(1, registry.requestsOf(sud.id()).size());
        // Un opérateur inconnu n'a pas de file.
        assertTrue(registry.request("inconnu", U1, "Fondateur", 14L).isEmpty());
        assertTrue(registry.requestsOf("inconnu").isEmpty());
        // La prendre la retire, une seule fois ; le joueur peut redemander.
        assertEquals(request, registry.takeRequest(request.id()).orElseThrow());
        assertTrue(registry.takeRequest(request.id()).isEmpty());
        assertEquals(1, registry.requestsOf(mairie.id()).size());
        assertTrue(registry.request(mairie.id(), U3, "Alice", 15L).isPresent());
    }

    @Test
    void pasPlusDeTrenteDeuxDemandes() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator mairie = found(registry, U1, "Mairie");
        PhoneOperator sud = found(registry, U2, "Sud");
        assertEquals(32, PhoneConfig.simRequestsMax());
        for (int i = 0; i < 32; i++) {
            assertTrue(registry.request(mairie.id(), uuid(1000 + i), "Joueur" + i, i).isPresent(), "demande " + i);
        }
        assertTrue(registry.request(mairie.id(), uuid(2000), "Trente-troisième", 99L).isEmpty());
        assertEquals(32, registry.requestsOf(mairie.id()).size());
        // La limite est par opérateur.
        assertTrue(registry.request(sud.id(), uuid(2000), "Trente-troisième", 99L).isPresent());
        // Une place libérée se reprend.
        SimRequest first = registry.requestsOf(mairie.id()).get(0);
        assertEquals(uuid(1000), first.player());
        registry.takeRequest(first.id());
        assertTrue(registry.request(mairie.id(), uuid(2000), "Trente-troisième", 100L).isPresent());
        assertEquals(32, registry.requestsOf(mairie.id()).size());
    }

    // ---------------------------------------------------------------------------------------------
    // Antennes
    // ---------------------------------------------------------------------------------------------

    @Test
    void laPrincipaleEstReelue() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        PhoneOperator other = found(registry, U2, "Sud");
        UUID a = uuid(101);
        UUID b = uuid(102);
        UUID c = uuid(103);
        UUID z = uuid(104);
        assertNull(registry.byId(op.id()).orElseThrow().mainAntenna());
        registry.putAntenna(antenna(a, op.id(), TestDimensions.OVERWORLD, 1, 100L));
        // La première posée devient la principale ; une plus ancienne arrivée ensuite ne la détrône pas.
        assertEquals(a, registry.byId(op.id()).orElseThrow().mainAntenna());
        registry.putAntenna(antenna(b, op.id(), TestDimensions.OVERWORLD, 2, 50L));
        registry.putAntenna(antenna(c, op.id(), TestDimensions.NETHER, 3, 75L));
        registry.putAntenna(antenna(z, other.id(), TestDimensions.OVERWORLD, 4, 10L));
        assertEquals(a, registry.byId(op.id()).orElseThrow().mainAntenna());
        assertEquals(z, registry.byId(other.id()).orElseThrow().mainAntenna());

        // Retirer une antenne qui n'est pas la principale ne change rien.
        registry.removeAntenna(c);
        assertEquals(a, registry.byId(op.id()).orElseThrow().mainAntenna());
        registry.putAntenna(antenna(c, op.id(), TestDimensions.NETHER, 3, 75L));
        // Retirer la principale élit la plus ancienne restante (b : posée à 50, c à 75), de son opérateur seulement.
        registry.removeAntenna(a);
        assertEquals(b, registry.byId(op.id()).orElseThrow().mainAntenna());
        assertEquals(z, registry.byId(other.id()).orElseThrow().mainAntenna());
        registry.removeAntenna(b);
        assertEquals(c, registry.byId(op.id()).orElseThrow().mainAntenna());
        registry.removeAntenna(c);
        assertNull(registry.byId(op.id()).orElseThrow().mainAntenna());
        assertEquals(1, registry.antennas().size());
        assertTrue(registry.antennasOf(op.id()).isEmpty());
        assertEquals(List.of(registry.antenna(z).orElseThrow()), registry.antennasOf(other.id()));
        // Une nouvelle antenne redevient la principale.
        registry.putAntenna(antenna(a, op.id(), TestDimensions.OVERWORLD, 1, 100L));
        assertEquals(a, registry.byId(op.id()).orElseThrow().mainAntenna());
    }

    @Test
    void laPrincipaleSeRetrouveAuChargement() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        registry.putAntenna(antenna(uuid(101), op.id(), TestDimensions.OVERWORLD, 1, 100L));
        registry.putAntenna(antenna(uuid(102), op.id(), TestDimensions.OVERWORLD, 2, 50L));
        CompoundTag tag = registry.save(new CompoundTag());
        // Une principale qui n'existe plus (ligne perdue) est réélue à la plus ancienne.
        tag.getList("operators", 10).getCompound(0).putUUID("main", uuid(999));
        PhoneOperators back = PhoneOperators.load(tag, DIMENSIONS);
        assertEquals(uuid(102), back.byId(op.id()).orElseThrow().mainAntenna());
    }

    @Test
    void noteTickNEcritQueToutesLesVingtTics() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        UUID a = uuid(101);
        registry.putAntenna(antenna(a, op.id(), TestDimensions.OVERWORLD, 1, 0L));
        registry.setDirty(false);

        int dirtyCount = 0;
        List<Long> dirtyAt = new ArrayList<>();
        for (long tick = 100; tick < 200; tick++) {
            registry.noteTick(a, true, tick, 10, new AntennaLoad.Breakdown(5, 3, 2));
            if (registry.isDirty()) {
                dirtyCount++;
                dirtyAt.add(tick);
                registry.setDirty(false);
            }
        }
        // Une fois au départ, puis au plus toutes les vingt tics.
        assertEquals(List.of(100L, 120L, 140L, 160L, 180L), dirtyAt, "tics marqués sales : " + dirtyCount);
        // Tout est compté en mémoire, marqué sale ou non.
        AntennaRecord record = registry.antenna(a).orElseThrow();
        assertEquals(1000L, record.feToday());
        assertEquals(199L, record.seenGameTime());
        assertEquals(new AntennaLoad.Breakdown(5, 3, 2), record.last());
        // Ce qui est écrit est l'état du moment, pas celui du dernier marquage.
        assertEquals(1000L, PhoneOperators.load(registry.save(new CompoundTag()), DIMENSIONS).antenna(a).orElseThrow()
                .feToday());
    }

    @Test
    void noteTickMarqueSaleDesQueLEtatDeMarcheChange() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        UUID a = uuid(101);
        registry.putAntenna(antenna(a, op.id(), TestDimensions.OVERWORLD, 1, 0L));
        registry.noteTick(a, true, 100L, 10, ZERO);
        registry.setDirty(false);
        // Deux tics plus tard (dans la fenêtre des vingt) : arrêt, donc sale aussitôt.
        registry.noteTick(a, false, 102L, 0, ZERO);
        assertTrue(registry.isDirty());
        assertFalse(registry.antenna(a).orElseThrow().running());
        registry.setDirty(false);
        // Même état : rien, jusqu'à la fin de la fenêtre.
        registry.noteTick(a, false, 103L, 0, ZERO);
        assertFalse(registry.isDirty());
        // Remise en marche à nouveau dans la fenêtre : sale aussitôt.
        registry.noteTick(a, true, 104L, 4, ZERO);
        assertTrue(registry.isDirty());
        assertTrue(registry.antenna(a).orElseThrow().running());
        // Une antenne inconnue est ignorée.
        registry.setDirty(false);
        registry.noteTick(uuid(404), true, 105L, 4, ZERO);
        assertFalse(registry.isDirty());
        // Un temps de jeu qui recule ne bloque pas l'écriture jusqu'à son rattrapage.
        registry.noteTick(a, true, 1L, 1, ZERO);
        assertTrue(registry.isDirty());
    }

    @Test
    void laConsommationDuJourPasseAHierALaCloture() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        UUID a = uuid(101);
        UUID b = uuid(102);
        registry.putAntenna(antenna(a, op.id(), TestDimensions.OVERWORLD, 1, 0L));
        registry.putAntenna(antenna(b, op.id(), TestDimensions.OVERWORLD, 2, 0L));
        registry.noteTick(a, true, 1L, 30, ZERO);
        registry.noteTick(a, true, 2L, 12, ZERO);
        registry.noteTick(b, true, 2L, -5, ZERO);
        registry.setDirty(false);
        registry.closeAntennaDay();
        assertTrue(registry.isDirty());
        assertEquals(0L, registry.antenna(a).orElseThrow().feToday());
        assertEquals(42L, registry.antenna(a).orElseThrow().feYesterday());
        // Une consommation négative ne retire rien.
        assertEquals(0L, registry.antenna(b).orElseThrow().feYesterday());
        registry.noteTick(a, true, 3L, 5, ZERO);
        registry.closeAntennaDay();
        assertEquals(5L, registry.antenna(a).orElseThrow().feYesterday());
        // Sans antenne, rien à écrire.
        PhoneOperators empty = new PhoneOperators();
        empty.closeAntennaDay();
        assertFalse(empty.isDirty());
    }

    // ---------------------------------------------------------------------------------------------
    // Tarifs
    // ---------------------------------------------------------------------------------------------

    @Test
    void unTarifAuDessusDuMaximumEstRefuse() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        long max = PhoneConfig.phoneTariffMax();
        assertEquals(1000L, max);
        assertFalse(registry.setNextTariff(op.id(), max + 1));
        assertNull(registry.byId(op.id()).orElseThrow().nextTariff());
        assertFalse(registry.setNextTariff(op.id(), -1L));
        assertNull(registry.byId(op.id()).orElseThrow().nextTariff());
        assertFalse(registry.setNextTariff("inconnu", 5L));
        assertTrue(registry.setNextTariff(op.id(), max));
        assertEquals(max, registry.byId(op.id()).orElseThrow().nextTariff());
        assertTrue(registry.setNextTariff(op.id(), 0L));
        assertEquals(0L, registry.byId(op.id()).orElseThrow().nextTariff());
    }

    @Test
    void leTarifDuLendemainEntreEnVigueurRameneAuPlafond() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator a = found(registry, U1, "Mairie");
        PhoneOperator b = found(registry, U2, "Sud");
        PhoneOperator c = found(registry, U3, "Nord");
        assertEquals(20L, registry.tariffCap());
        registry.setNextTariff(a.id(), 15L);
        registry.setNextTariff(b.id(), 500L);

        List<PhoneOperators.DayChange> changes = registry.applyNextTariffs();

        assertEquals(15L, registry.byId(a.id()).orElseThrow().tariff());
        assertEquals(20L, registry.byId(b.id()).orElseThrow().tariff());
        assertNull(registry.byId(a.id()).orElseThrow().nextTariff());
        assertNull(registry.byId(b.id()).orElseThrow().nextTariff());
        assertEquals(0L, registry.byId(c.id()).orElseThrow().tariff());
        assertEquals(List.of(new PhoneOperators.DayChange(a.id(), 0L, 15L, false),
                new PhoneOperators.DayChange(b.id(), 0L, 20L, true)), changes);

        // Un plafond abaissé le lendemain s'applique avant les tarifs, même à qui n'a rien demandé.
        registry.setNextTariffCap(10L);
        assertEquals(Optional.of(10L), registry.nextTariffCap());
        assertEquals(20L, registry.tariffCap());
        registry.setNextTariff(c.id(), 12L);
        List<PhoneOperators.DayChange> second = registry.applyNextTariffs();
        assertEquals(10L, registry.tariffCap());
        assertTrue(registry.nextTariffCap().isEmpty());
        assertEquals(10L, registry.byId(a.id()).orElseThrow().tariff());
        assertEquals(10L, registry.byId(b.id()).orElseThrow().tariff());
        assertEquals(10L, registry.byId(c.id()).orElseThrow().tariff());
        assertEquals(List.of(new PhoneOperators.DayChange(a.id(), 15L, 10L, true),
                new PhoneOperators.DayChange(b.id(), 20L, 10L, true),
                new PhoneOperators.DayChange(c.id(), 0L, 10L, true)), second);
        // Un jour sans rien de nouveau ne change rien et ne marque rien.
        registry.setDirty(false);
        assertTrue(registry.applyNextTariffs().isEmpty());
        assertFalse(registry.isDirty());
        // Un plafond négatif est ramené à zéro.
        registry.setNextTariffCap(-4L);
        assertEquals(Optional.of(0L), registry.nextTariffCap());
    }

    // ---------------------------------------------------------------------------------------------
    // Caisse
    // ---------------------------------------------------------------------------------------------

    @Test
    void laCaisseNeCreeNiNeDetruitDePiece() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        assertEquals(CoinStore.MAX_PER_COIN, registry.tillRoom(op.id(), Coin.GOLD));
        assertEquals(0L, registry.tillRoom("inconnu", Coin.GOLD));
        registry.deposit(op.id(), Coin.GOLD, 4L);
        registry.deposit(op.id(), Coin.GOLD, 3L);
        assertEquals(7L, registry.till(op.id()).get(Coin.GOLD));
        assertEquals(CoinStore.MAX_PER_COIN - 7L, registry.tillRoom(op.id(), Coin.GOLD));
        // Un retrait de trop ne retire rien.
        assertFalse(registry.withdraw(op.id(), Coin.GOLD, 8L));
        assertEquals(7L, registry.till(op.id()).get(Coin.GOLD));
        assertFalse(registry.withdraw(op.id(), Coin.GOLD, -1L));
        assertFalse(registry.withdraw("inconnu", Coin.GOLD, 1L));
        assertTrue(registry.withdraw(op.id(), Coin.GOLD, 7L));
        assertTrue(registry.till(op.id()).isEmpty());
        // Un dépôt trop grand, négatif ou chez un inconnu : refusé sans rien écrire.
        registry.setDirty(false);
        assertThrows(IllegalArgumentException.class,
                () -> registry.deposit(op.id(), Coin.GOLD, CoinStore.MAX_PER_COIN + 1L));
        assertThrows(IllegalArgumentException.class, () -> registry.deposit(op.id(), Coin.GOLD, -1L));
        assertThrows(IllegalArgumentException.class, () -> registry.deposit("inconnu", Coin.GOLD, 1L));
        assertTrue(registry.till(op.id()).isEmpty());
        assertFalse(registry.isDirty());
        // La caisse rendue est une copie : la modifier ne change pas le registre.
        registry.deposit(op.id(), Coin.COPPER, 2L);
        EnumMap<Coin, Long> copy = registry.till(op.id());
        copy.put(Coin.COPPER, 99L);
        assertEquals(2L, registry.till(op.id()).get(Coin.COPPER));
        assertEquals(2L, registry.byId(op.id()).orElseThrow().till().get(Coin.COPPER));
    }

    // ---------------------------------------------------------------------------------------------
    // Avis
    // ---------------------------------------------------------------------------------------------

    @Test
    void lesAvisSeLisentUneFoisEtLesPlusAnciensTombent() {
        PhoneOperators registry = new PhoneOperators();
        for (int i = 0; i < PhoneOperators.MAX_NOTICES + 4; i++) {
            registry.addNotice(U3, Component.literal("avis " + i));
        }
        PhoneOperators back = reload(registry);
        List<Component> notices = back.takeNotices(U3);
        assertEquals(PhoneOperators.MAX_NOTICES, notices.size());
        assertEquals("avis 4", notices.get(0).getString());
        assertEquals("avis " + (PhoneOperators.MAX_NOTICES + 3), notices.get(notices.size() - 1).getString());
        assertTrue(back.takeNotices(U3).isEmpty());
        assertTrue(back.takeNotices(U4).isEmpty());
    }

    // ---------------------------------------------------------------------------------------------
    // Écriture
    // ---------------------------------------------------------------------------------------------

    @Test
    void chaqueChangementMarqueLeRegistreSale() {
        PhoneOperators registry = new PhoneOperators();
        RandomSource random = RandomSource.create(1L);
        assertFalse(registry.isDirty());
        PhoneOperator op = found(registry, U1, "Mairie");
        assertTrue(registry.isDirty());
        UUID antennaId = uuid(101);
        SimCard[] card = new SimCard[1];
        SimRequest[] request = new SimRequest[1];
        List<Runnable> changes = new ArrayList<>();
        changes.add(() -> card[0] = registry.issue(op.id(), U3, "Alice", random, 1L));
        changes.add(() -> registry.setState(card[0].simId(), SimCard.State.SUSPENDED));
        changes.add(() -> registry.rotateToken(card[0].simId(), PHONE, random));
        changes.add(() -> registry.unbind(card[0].simId()));
        changes.add(() -> request[0] = registry.request(op.id(), U3, "Alice", 2L).orElseThrow());
        changes.add(() -> registry.takeRequest(request[0].id()));
        changes.add(() -> registry.putAntenna(antenna(antennaId, op.id(), TestDimensions.OVERWORLD, 1, 5L)));
        // La même antenne mise à jour : la principale ne change pas, seul le setDirty() de putAntenna salit.
        changes.add(() -> registry.putAntenna(antenna(antennaId, op.id(), TestDimensions.OVERWORLD, 1, 6L)));
        changes.add(() -> registry.noteTick(antennaId, false, 10L, 1, ZERO));
        changes.add(registry::closeAntennaDay);
        changes.add(() -> registry.removeAntenna(antennaId));
        changes.add(() -> registry.setNextTariff(op.id(), 5L));
        changes.add(() -> registry.setNextTariffCap(8L));
        changes.add(registry::applyNextTariffs);
        changes.add(() -> registry.deposit(op.id(), Coin.SILVER, 1L));
        changes.add(() -> registry.withdraw(op.id(), Coin.SILVER, 1L));
        changes.add(() -> registry.addNotice(U3, Component.literal("x")));
        changes.add(() -> registry.takeNotices(U3));
        changes.add(() -> registry.setFounder(op.id(), U2, "Autre"));
        changes.add(() -> registry.remove(op.id()));
        for (int i = 0; i < changes.size(); i++) {
            registry.setDirty(false);
            changes.get(i).run();
            assertTrue(registry.isDirty(), "le changement n° " + i + " n'a pas marqué le registre sale");
        }
    }

    @Test
    void unRefusOuUneLectureNeSalitPas() {
        PhoneOperators registry = new PhoneOperators();
        RandomSource random = RandomSource.create(2L);
        PhoneOperator op = found(registry, U1, "Mairie");
        SimCard card = registry.issue(op.id(), U3, "Alice", random, 1L);
        registry.request(op.id(), U3, "Alice", 2L);
        registry.setDirty(false);

        registry.found(U1, "Fondateur", "Autre", 3L);
        registry.found(U2, "Autre", "mairie", 3L);
        registry.request(op.id(), U3, "Alice", 4L);
        registry.setNextTariff(op.id(), 5_000L);
        registry.setState(uuid(1), SimCard.State.SUSPENDED);
        registry.unbind(uuid(1));
        registry.unbind(card.simId());
        registry.removeAntenna(uuid(1));
        registry.takeRequest(uuid(1));
        registry.takeNotices(U3);
        registry.remove("inconnu");
        registry.setFounder("inconnu", U4, "Quatrième");
        registry.byId(op.id());
        registry.byName("Mairie");
        registry.card(card.simId());
        registry.cardsOf(op.id());
        registry.requestsOf(op.id());
        registry.antennas();
        registry.till(op.id());
        registry.tariffCap();

        assertFalse(registry.isDirty());
    }

    // ---------------------------------------------------------------------------------------------
    // Réglages
    // ---------------------------------------------------------------------------------------------

    @Test
    void lesReglagesDuReseauOntLeursValeursParDefaut() {
        assertEquals(270, PhoneConfig.antennaRange());
        assertEquals(40, PhoneConfig.antennaStandbyFe());
        assertEquals(6, PhoneConfig.antennaPerSimFe());
        assertEquals(9, PhoneConfig.antennaCallFe());
        assertEquals(24, PhoneConfig.antennaFacetimeFe());
        assertEquals(100, PhoneConfig.antennaMessageFe());
        assertEquals(40_000, PhoneConfig.antennaCapacity());
        assertEquals(4_000, PhoneConfig.antennaMaxReceive());
        assertTrue(PhoneConfig.antennaKeepsChunkLoaded());
        assertEquals(40, PhoneConfig.antennaStaleTicks());
        assertEquals(32, PhoneConfig.simRequestsMax());
        assertEquals(1_000, PhoneConfig.phoneTariffMax());
        assertEquals(20, PhoneConfig.phoneDefaultTariffCap());
        assertEquals(20, PhoneConfig.signalRefreshTicks());
    }

    @Test
    void emettreUneCarteChezUnOperateurInconnuLeveSansRienTirerNiSalir() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        registry.setDirty(false);
        RandomSource random = RandomSource.create(42L);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> registry.issue("inconnu", U3, "Alice", random, 1L));
        assertTrue(refused.getMessage().contains("inconnu"));
        assertFalse(registry.isDirty());
        assertTrue(registry.cardsOf("inconnu").isEmpty());
        // Rien n'a été tiré (le générateur est resté à son départ) ni réservé : le premier numéro émis est celui d'une
        // graine neuve.
        SimCard first = registry.issue(op.id(), U3, "Alice", random, 2L);
        assertEquals(SimNumbers.draw(RandomSource.create(42L), n -> false), first.number());
    }

    @Test
    void uneCarteDesactiveeLEstPourToujours() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        SimCard card = registry.issue(op.id(), U3, "Alice", RandomSource.create(3L), 1L);
        assertTrue(registry.setState(card.simId(), SimCard.State.DEACTIVATED));
        registry.setDirty(false);
        assertFalse(registry.setState(card.simId(), SimCard.State.ACTIVE));
        assertFalse(registry.setState(card.simId(), SimCard.State.SUSPENDED));
        assertEquals(SimCard.State.DEACTIVATED, registry.card(card.simId()).orElseThrow().state());
        assertEquals(0, registry.activeCards(op.id()));
        assertFalse(registry.isDirty());
        // Redire « désactivée » à une désactivée n'est pas un refus.
        assertTrue(registry.setState(card.simId(), SimCard.State.DEACTIVATED));
        assertFalse(registry.isDirty());
        // Suspendue, au contraire, elle se réactive.
        SimCard other = registry.issue(op.id(), U4, "Bob", RandomSource.create(4L), 2L);
        assertTrue(registry.setState(other.simId(), SimCard.State.SUSPENDED));
        assertTrue(registry.setState(other.simId(), SimCard.State.ACTIVE));
        assertEquals(1, registry.activeCards(op.id()));
    }

    @Test
    void uneCarteDUnOperateurAbsentNeSeReactivePas() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        SimCard card = registry.issue(op.id(), U3, "Alice", RandomSource.create(3L), 1L);
        registry.setState(card.simId(), SimCard.State.SUSPENDED);
        // Une ancienne sauvegarde dont la ligne de l'opérateur a disparu : la carte suspendue reste, sans opérateur.
        CompoundTag tag = registry.save(new CompoundTag());
        tag.put("operators", new ListTag());
        PhoneOperators back = PhoneOperators.load(tag, DIMENSIONS);
        assertTrue(back.byId(op.id()).isEmpty());
        assertFalse(back.setState(card.simId(), SimCard.State.ACTIVE));
        assertEquals(SimCard.State.SUSPENDED, back.card(card.simId()).orElseThrow().state());
        assertEquals(0, back.activeCards(op.id()));
        // Elle peut en revanche être désactivée.
        assertTrue(back.setState(card.simId(), SimCard.State.DEACTIVATED));
        assertEquals(SimCard.State.DEACTIVATED, back.card(card.simId()).orElseThrow().state());
    }

    @Test
    void lIdentifiantEtLeJetonNeSeDeduisentPasDuGenerateur() {
        PhoneOperators one = new PhoneOperators();
        PhoneOperators two = new PhoneOperators();
        PhoneOperator opOne = found(one, U1, "Mairie");
        PhoneOperator opTwo = found(two, U1, "Mairie");
        // Même graine : le numéro, seul tiré du générateur, est le même ; ni l'identifiant ni le jeton ne le sont.
        SimCard a = one.issue(opOne.id(), U3, "Alice", RandomSource.create(77L), 1L);
        SimCard b = two.issue(opTwo.id(), U3, "Alice", RandomSource.create(77L), 1L);
        assertEquals(a.number(), b.number());
        assertNotEquals(a.simId(), b.simId());
        assertNotEquals(a.token(), b.token());
        UUID rotatedA = one.rotateToken(a.simId(), PHONE, RandomSource.create(78L));
        UUID rotatedB = two.rotateToken(b.simId(), PHONE, RandomSource.create(78L));
        assertNotEquals(rotatedA, rotatedB);
    }

    @Test
    void laPlusAncienneRestanteEstEluMemeSiElleEstPoseeEnDernier() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        UUID a = uuid(101);
        UUID c = uuid(103);
        UUID b = uuid(102);
        // Ordre d'arrivée : a (posée à 100), c (75), b (50) : la plus ancienne n'est pas la première reçue.
        registry.putAntenna(antenna(a, op.id(), TestDimensions.OVERWORLD, 1, 100L));
        registry.putAntenna(antenna(c, op.id(), TestDimensions.OVERWORLD, 3, 75L));
        registry.putAntenna(antenna(b, op.id(), TestDimensions.OVERWORLD, 2, 50L));
        assertEquals(a, registry.byId(op.id()).orElseThrow().mainAntenna());
        registry.removeAntenna(a);
        assertEquals(b, registry.byId(op.id()).orElseThrow().mainAntenna());
        registry.removeAntenna(b);
        assertEquals(c, registry.byId(op.id()).orElseThrow().mainAntenna());
    }

    @Test
    void aEgaliteDePoseLaPremiereRecueEstElue() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        UUID a = uuid(101);
        UUID b = uuid(102);
        UUID c = uuid(103);
        registry.putAntenna(antenna(a, op.id(), TestDimensions.OVERWORLD, 1, 10L));
        registry.putAntenna(antenna(b, op.id(), TestDimensions.OVERWORLD, 2, 20L));
        registry.putAntenna(antenna(c, op.id(), TestDimensions.OVERWORLD, 3, 20L));
        registry.removeAntenna(a);
        assertEquals(b, registry.byId(op.id()).orElseThrow().mainAntenna());
    }

    @Test
    void laPrincipaleSeDesigneSurLesAntennesDeLOperateurSeulement() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        PhoneOperator other = found(registry, U2, "Sud");
        UUID a = uuid(101);
        UUID b = uuid(102);
        UUID z = uuid(103);
        registry.putAntenna(antenna(a, op.id(), TestDimensions.OVERWORLD, 1, 100L));
        registry.putAntenna(antenna(b, op.id(), TestDimensions.OVERWORLD, 2, 200L));
        registry.putAntenna(antenna(z, other.id(), TestDimensions.OVERWORLD, 3, 50L));
        registry.setDirty(false);

        // Inconnue, d'un autre opérateur, opérateur inconnu : refusé, rien ne bouge.
        assertFalse(registry.setMainAntenna(op.id(), uuid(404)));
        assertFalse(registry.setMainAntenna(op.id(), z));
        assertFalse(registry.setMainAntenna("inconnu", a));
        assertEquals(a, registry.byId(op.id()).orElseThrow().mainAntenna());
        assertEquals(z, registry.byId(other.id()).orElseThrow().mainAntenna());
        assertFalse(registry.isDirty());

        // Valide : la principale change, le registre est sale, et cela se relit.
        assertTrue(registry.setMainAntenna(op.id(), b));
        assertTrue(registry.isDirty());
        assertEquals(b, registry.byId(op.id()).orElseThrow().mainAntenna());
        assertEquals(b, reload(registry).byId(op.id()).orElseThrow().mainAntenna());
        // La désigner de nouveau ne change rien (et ne salit pas).
        registry.setDirty(false);
        assertTrue(registry.setMainAntenna(op.id(), b));
        assertFalse(registry.isDirty());
        // Retirée, la désignée laisse la place à la plus ancienne restante.
        registry.removeAntenna(b);
        assertEquals(a, registry.byId(op.id()).orElseThrow().mainAntenna());
    }

    @Test
    void unNomOuUnFondateurDejaVuEstIgnoreAuChargement() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator first = found(registry, U1, "Mairie");
        PhoneOperator second = found(registry, U2, "Sud");
        CompoundTag tag = registry.save(new CompoundTag());
        ListTag rows = tag.getList("operators", 10);
        CompoundTag sameName = rows.getCompound(0).copy();
        sameName.putString("id", "doublon-nom");
        sameName.putString("name", "  mairie");
        sameName.putUUID("founder", U4);
        rows.add(sameName);
        CompoundTag sameFounder = rows.getCompound(1).copy();
        sameFounder.putString("id", "doublon-fondateur");
        sameFounder.putString("name", "Autre nom");
        rows.add(sameFounder);
        CompoundTag fine = rows.getCompound(0).copy();
        fine.putString("id", "libre");
        fine.putString("name", "Libre");
        fine.putUUID("founder", U3);
        rows.add(fine);

        PhoneOperators back = PhoneOperators.load(tag, DIMENSIONS);

        assertEquals(3, back.all().size());
        assertTrue(back.byId("doublon-nom").isEmpty());
        assertTrue(back.byId("doublon-fondateur").isEmpty());
        assertTrue(back.byId("libre").isPresent());
        assertEquals(first.id(), back.byName("MAIRIE").orElseThrow().id());
        assertEquals(second.id(), back.byFounder(U2).orElseThrow().id());
    }

    @Test
    void unNbtSansListeDeNumerosGardeCeuxDesCartes() {
        PhoneOperators registry = new PhoneOperators();
        PhoneOperator op = found(registry, U1, "Mairie");
        SimCard card = registry.issue(op.id(), U3, "Alice", RandomSource.create(42L), 1L);
        CompoundTag tag = registry.save(new CompoundTag());
        tag.remove("usedNumbers");
        PhoneOperators back = PhoneOperators.load(tag, DIMENSIONS);
        // Même graine : le tirage retombe d'abord sur le numéro de la carte relue, qui reste pris.
        SimCard next = back.issue(op.id(), U4, "Bob", RandomSource.create(42L), 2L);
        assertNotEquals(card.number(), next.number());
    }

    /** Un tirage qui ne rend jamais que le numéro donné : sert à voir qu'un numéro retenu n'est pas redonné. */
    private static final class FixedNumbers implements RandomSource {

        private final int value;

        FixedNumbers(String number) {
            this.value = Integer.parseInt(number.substring(2));
        }

        @Override
        public RandomSource fork() {
            return this;
        }

        @Override
        public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSeed(long seed) {}

        @Override
        public int nextInt() {
            return value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }

        @Override
        public long nextLong() {
            return 1L;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }

        @Override
        public float nextFloat() {
            return 0f;
        }

        @Override
        public double nextDouble() {
            return 0d;
        }

        @Override
        public double nextGaussian() {
            return 0d;
        }
    }
}
