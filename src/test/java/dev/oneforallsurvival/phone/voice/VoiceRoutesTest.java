package dev.oneforallsurvival.phone.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.call.CallBook;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Bystander;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Presence;
import java.util.Optional;
import java.util.function.Function;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.CallSides;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Flow;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Kind;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** La matrice des routes de la voix : qui entend qui, par quel canal, pour un appel décroché. */
class VoiceRoutesTest {

    private static final String MONDE = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final UUID D = new UUID(0, 4);
    private static final int HEAR = 8;
    private static final int CAPTURE = 16;

    private static Side side(UUID p, double x, int bars, boolean muted, boolean speaker) {
        return new Side(p, new Spot(MONDE, x, 64, 0), bars, muted, speaker);
    }

    private static Side a() {
        return side(A, 0, 4, false, false);
    }

    private static Side b() {
        return side(B, 1000, 4, false, false);
    }

    private static List<Bystander> autour(Bystander... autres) {
        return List.of(autres);
    }

    private static Bystander pres(UUID p, double x) {
        return new Bystander(p, new Spot(MONDE, x, 64, 0), false);
    }

    private static VoiceRoutes routes(Side caller, Side callee, List<Bystander> bystanders) {
        return VoiceRoutes.compute(List.of(new CallSides(7, caller, callee)), bystanders, HEAR, CAPTURE);
    }

    private static Set<UUID> auditeursPrives(VoiceRoutes r, UUID parleur) {
        return r.from(parleur).stream().filter(f -> f.key().kind() == Kind.PRIVATE).map(f -> f.key().listener())
                .collect(Collectors.toSet());
    }

    @Test
    void enPriveChacunEntendLAutreSeul() {
        VoiceRoutes r = routes(a(), b(), autour(pres(C, 3)));
        assertEquals(Set.of(B), auditeursPrives(r, A));
        assertEquals(Set.of(A), auditeursPrives(r, B));
        assertTrue(r.from(C).isEmpty(), "sans haut-parleur, personne autour n'est capté");
        for (Flow f : r.from(A)) {
            assertFalse(f.crackle(), "quatre barres : aucun grésillement");
            assertEquals(7, f.key().callId());
        }
    }

    @Test
    void leSilenceCoupeSaVoixSeulement() {
        VoiceRoutes r = routes(side(A, 0, 4, true, false), b(), autour());
        assertTrue(r.from(A).isEmpty(), "A en silence : sa voix ne part pas");
        assertEquals(Set.of(A), auditeursPrives(r, B), "A entend toujours B");
    }

    @Test
    void leHautParleurFaitSortirLaVoixDeBDuTelephoneDeA() {
        VoiceRoutes r = routes(side(A, 0, 4, false, true), b(), autour());
        List<Flow> deB = r.from(B);
        assertEquals(1, deB.size(), "B n'arrive plus à A qu'une fois : par le haut-parleur, pas en privé : " + deB);
        Flow f = deB.get(0);
        assertEquals(Kind.SPEAKER, f.key().kind());
        assertEquals(A, f.key().listener(), "le canal est le téléphone de A");
        assertEquals(new Spot(MONDE, 0, 64, 0), f.at(), "le canal est posé sur A");
        assertEquals(HEAR, f.hearBlocks());
        assertTrue(f.excluded().contains(B), "B ne s'entend jamais dans le haut-parleur de A");
        assertEquals(Set.of(B), auditeursPrives(r, A), "la voix de A va toujours à B en privé");
    }

    @Test
    void bNEntendJamaisLeHautParleurDeAMemeACote() {
        Side bACote = side(B, 2, 4, false, false);
        VoiceRoutes r = routes(side(A, 0, 4, false, true), bACote, autour(pres(C, 3)));
        Flow f = r.from(B).get(0);
        assertFalse(r.hears(f, B), "B, à deux blocs, n'entend pas sa propre voix sortir du téléphone de A");
        assertTrue(r.hears(f, A), "A entend B par son haut-parleur");
        assertTrue(r.hears(f, C), "C, à trois blocs de A, entend B");
        assertTrue(r.from(B).stream().noneMatch(x -> x.key().kind() == Kind.PRIVATE && x.key().listener().equals(B)));
    }

    @Test
    void leHautParleurSEntendJusquAHuitBlocsBorneComprise() {
        VoiceRoutes r = routes(side(A, 0, 4, false, true), b(), autour(pres(C, 8), pres(D, 8.01)));
        Flow f = r.from(B).get(0);
        assertTrue(r.hears(f, C), "à 8 blocs : entendu");
        assertFalse(r.hears(f, D), "au-delà de 8 blocs : rien");
        assertFalse(r.hears(f, UUID.randomUUID()), "un joueur inconnu de l'instantané n'entend rien");
    }

    @Test
    void leHautParleurNeSEntendPasDansUneAutreDimension() {
        List<Bystander> autres = List.of(new Bystander(C, new Spot(NETHER, 1, 64, 0), false));
        VoiceRoutes r = routes(side(A, 0, 4, false, true), b(), autres);
        assertFalse(r.hears(r.from(B).get(0), C));
    }

    @Test
    void leHautParleurCapteLesVoisinsJusquASeizeBlocsBorneComprise() {
        VoiceRoutes r = routes(side(A, 0, 4, false, true), b(), autour(pres(C, 16), pres(D, 16.01)));
        assertEquals(Set.of(B), auditeursPrives(r, C), "C, à 16 blocs de A, part à B");
        assertTrue(r.from(D).isEmpty(), "D, au-delà de 16 blocs, n'est pas capté");
        Flow f = r.from(C).get(0);
        assertEquals(Kind.PRIVATE, f.key().kind());
        assertEquals(C, f.key().speaker());
    }

    @Test
    void leHautParleurNeCaptePasUnGroupeFermeNiUneAutreDimension() {
        List<Bystander> autres = List.of(new Bystander(C, new Spot(MONDE, 2, 64, 0), true),
                new Bystander(D, new Spot(NETHER, 2, 64, 0), false));
        VoiceRoutes r = routes(side(A, 0, 4, false, true), b(), autres);
        assertTrue(r.from(C).isEmpty(), "membre d'un groupe fermé : pas capté");
        assertTrue(r.from(D).isEmpty(), "autre dimension : pas capté");
    }

    @Test
    void leSilenceDeACoupeAussiLesVoixCapteesAutourDeLui() {
        VoiceRoutes r = routes(side(A, 0, 4, true, true), b(), autour(pres(C, 3)));
        assertTrue(r.from(A).isEmpty(), "A en silence");
        assertTrue(r.from(C).isEmpty(), "A en silence : rien de ce qui l'entoure ne part à B");
        assertEquals(1, r.from(B).size(), "A entend toujours B par le haut-parleur");
    }

    @Test
    void leHautParleurDeBRestitueAussiLesVoixCapteesChezA() {
        Side haut = side(A, 0, 4, false, true);
        Side hautB = side(B, 1000, 4, false, true);
        VoiceRoutes r = routes(haut, hautB, autour(pres(C, 3)));
        List<Flow> deC = r.from(C);
        assertEquals(1, deC.size());
        Flow f = deC.get(0);
        assertEquals(Kind.SPEAKER, f.key().kind(), "B en haut-parleur : la voix de C sort de son téléphone");
        assertEquals(B, f.key().listener());
        assertTrue(f.excluded().contains(A) && f.excluded().contains(C), "ni A ni C ne s'y entendent : " + f);
        assertTrue(r.from(A).stream().allMatch(x -> x.key().kind() == Kind.SPEAKER && x.key().listener().equals(B)));
    }

    @Test
    void lesPartiesNeSontJamaisCapteesCommeVoisins() {
        Side haut = side(A, 0, 4, false, true);
        Side bACote = side(B, 2, 4, false, false);
        List<Bystander> tous = List.of(pres(A, 0), pres(B, 2), pres(C, 3));
        VoiceRoutes r = routes(haut, bACote, tous);
        assertEquals(Set.of(B), auditeursPrives(r, A), "A n'est pas capté en plus de sa voix privée");
        assertTrue(r.from(B).stream().noneMatch(f -> f.key().listener().equals(B)), "B ne s'entend pas");
        assertEquals(Set.of(B), auditeursPrives(r, C));
    }

    @Test
    void uneBarreDUnCoteFaitGresillerLesDeuxSens() {
        VoiceRoutes r = routes(side(A, 0, 1, false, true), b(), autour(pres(C, 3)));
        List<Flow> tous = new ArrayList<>();
        tous.addAll(r.from(A));
        tous.addAll(r.from(B));
        tous.addAll(r.from(C));
        assertEquals(3, tous.size());
        assertTrue(tous.stream().allMatch(Flow::crackle), "A à une barre : tout l'appel grésille : " + tous);
        VoiceRoutes deux = routes(side(A, 0, 2, false, false), b(), autour());
        assertTrue(deux.from(A).stream().noneMatch(Flow::crackle), "deux barres : net");
    }

    @Test
    void lesClesDistinguentAppelParleurAuditeurEtGenre() {
        VoiceRoutes r = routes(side(A, 0, 4, false, true), side(B, 1000, 4, false, true), autour(pres(C, 3)));
        Set<VoiceRoutes.FlowKey> cles = new java.util.HashSet<>();
        int n = 0;
        for (UUID p : List.of(A, B, C)) {
            for (Flow f : r.from(p)) {
                cles.add(f.key());
                n++;
            }
        }
        assertEquals(n, cles.size(), "une clé par flux");
        assertTrue(r.keys().containsAll(cles) && cles.containsAll(r.keys()));
    }

    @Test
    void deuxAppelsNeSeMelentPas() {
        UUID e = new UUID(0, 5);
        UUID g = new UUID(0, 6);
        List<CallSides> appels = List.of(new CallSides(1, a(), b()),
                new CallSides(2, new Side(e, new Spot(MONDE, 5000, 64, 0), 4, false, false),
                        new Side(g, new Spot(MONDE, 9000, 64, 0), 4, false, false)));
        VoiceRoutes r = VoiceRoutes.compute(appels, List.of(), HEAR, CAPTURE);
        assertEquals(Set.of(B), auditeursPrives(r, A));
        assertEquals(Set.of(g), auditeursPrives(r, e));
    }

    @Test
    void sansAppelAucunFlux() {
        VoiceRoutes r = VoiceRoutes.compute(List.of(), List.of(pres(A, 0), pres(B, 1)), HEAR, CAPTURE);
        assertTrue(r.from(A).isEmpty() && r.from(B).isEmpty() && r.keys().isEmpty());
        assertTrue(VoiceRoutes.EMPTY.keys().isEmpty());
    }

    @Test
    void unAppelEnSonnerieNaAucunFlux() {
        CallBook.Party pa = new CallBook.Party(A, UUID.randomUUID(), UUID.randomUUID(), "0600000001");
        CallBook.Party pb = new CallBook.Party(B, UUID.randomUUID(), UUID.randomUUID(), "0600000002");
        CallBook book = new CallBook();
        CallBook.Call sonne = book.start(pa, pb, 0).orElseThrow();
        Function<CallBook.Party, Optional<Presence>> ici =
                p -> Optional.of(new Presence(new Spot(MONDE, 0, 64, 0), 4));
        assertTrue(CallSides.of(sonne, ici).isEmpty(), "sonnerie : aucune route");
        CallBook.Call decroche = book.answer(sonne.id(), B, 5).orElseThrow();
        Optional<CallSides> actif = CallSides.of(decroche, ici);
        assertTrue(actif.isPresent());
        assertEquals(A, actif.get().caller().player());
        assertEquals(B, actif.get().callee().player());
        CallBook.Call muet = book.setMuted(A, true).orElseThrow();
        CallBook.Call haut = book.setSpeaker(B, true).orElseThrow();
        CallSides s = CallSides.of(haut, ici).orElseThrow();
        assertTrue(s.caller().muted() && !s.caller().speaker() && s.callee().speaker() && !s.callee().muted(),
                "silence et haut-parleur repris du carnet : " + s + " / " + muet);
        assertTrue(CallSides.of(decroche, p -> p.player().equals(B) ? Optional.empty()
                : ici.apply(p)).isEmpty(), "un bout absent : aucune route");
    }

    @Test
    void lInstantaneEstImmuable() {
        VoiceRoutes r = routes(a(), b(), autour());
        List<Flow> flux = r.from(A);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> flux.clear());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> flux.get(0).excluded().clear());
    }
}
