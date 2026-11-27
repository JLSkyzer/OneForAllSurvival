package dev.oneforallsurvival.dev.wiki;

import static dev.oneforallsurvival.dev.wiki.ScenesElectricite.face;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.constat;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.ile;

import dev.oneforallsurvival.dev.JoueurDeTest;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.town.TownBoardBlock;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.supplier.PowerStationBlock;
import dev.oneforallsurvival.grid.supplier.PowerSupplier;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Le chapitre « Maire » du wiki : la mairie avec sa maire (un joueur simulé, Maelle, nommé par la console) et son nom
 * préfixé au-dessus de la tête ; la nomination tapée au chat ; les gestes de la mairie sur les réseaux ({@code /power},
 * {@code /operator}). Le joueur de l'atelier (Robin) est opérateur (monde solo avec commandes) : ses commandes passent
 * aussi pour lui comme pour l'équipe ; les réponses sont celles qu'un maire reçoit (même chemin, même message).
 */
public final class ScenesMaire implements Chapitre {

    private static final BlockPos TRESOR = new BlockPos(-3, 0, -2);
    private static final BlockPos TABLEAU = new BlockPos(0, 0, -2);
    private static final String MAELLE = "Maelle";
    private static final String LUCAS = "Lucas";
    private static final String FOURNISSEUR = "Courant de la Baie";
    private static final String OPERATEUR = "Radio du Lagon";

    @Override
    public String id() {
        return "maire";
    }

    @Override
    public void scenes(Atelier atelier) {
        mairie(atelier);
        nommer(atelier);
        reseaux(atelier);
    }

    /** La mairie de briques, le trésor et le tableau ; Maelle, maire, devant. */
    static void mairieDecor(Lieu lieu) {
        ile(lieu);
        for (int x = -6; x <= 6; x++) {
            for (int y = 0; y <= 3; y++) {
                lieu.poser(x, y, -3, Blocks.STONE_BRICKS.defaultBlockState());
            }
            lieu.poser(x, 4, -3, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        }
        lieu.poser(-5, 0, -1, Blocks.LANTERN.defaultBlockState());
        lieu.poser(5, 0, -1, Blocks.LANTERN.defaultBlockState());
        lieu.poser(TRESOR.getX(), 0, TRESOR.getZ(), face(EconomyRegistry.TREASURY.get()));
        ((TownBoardBlock) EconomyRegistry.TOWN_BOARD.get()).placeWhole(lieu.monde(),
                lieu.p(TABLEAU.getX(), 0, TABLEAU.getZ()), Direction.SOUTH);
        if (TreasuryAccount.get(lieu.serveur()).total() < 1_500L) {
            ScenesEconomie.console(lieu.serveur(), "eco treasury give 1500 Fonds de la ville");
        }
    }

    // --- la mairie et sa maire -------------------------------------------------------------------------------------

    private void mairie(Atelier atelier) {
        atelier.scene("mairie")
                .serveur("la mairie ; Maelle, citoyenne, nommée maire", lieu -> {
                    mairieDecor(lieu);
                    ScenesEconomie.quitterFigurants();
                    ScenesEconomie.figurant(lieu, MAELLE, new Vec3(2.6, 0, -0.8), 0F, true);
                    ScenesEconomie.console(lieu.serveur(), "role set " + MAELLE + " citoyen");
                    ScenesEconomie.console(lieu.serveur(), "role mayor set " + MAELLE);
                    constat("maire : " + dev.oneforallsurvival.role.RoleSavedData.get(lieu.serveur()).book().mayor());
                })
                .client("noms au-dessus des têtes, rien d'autre", mc -> {
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements();
                    WikiStudio.reticule(false);
                    mc.gameRenderer.setRenderHand(false);
                })
                .camera(new Vec3(0.0, 2.1, 6.4), new Vec3(-0.2, 1.1, -1.5))
                .pause(1500)
                .repereMonde("maire", new Vec3(2.6, 2.25, -0.8))
                .repereBloc("tresor", TRESOR, 0.5, 1.0, 0.5)
                .repereBloc("tableau", TABLEAU, 0.5, 1.6, 0.9)
                .prise("mairie")
                .camera(new Vec3(1.9, 2.0, 1.7), new Vec3(2.6, 1.95, -0.8))
                .pause(1000)
                .prise("maire-nom")
                .client("interface masquée", mc -> WikiStudio.ath(mc, false))
                .serveur("rangement", lieu -> {
                    ScenesEconomie.console(lieu.serveur(), "role mayor remove");
                    ScenesEconomie.quitterFigurants();
                });
    }

    // --- nommer le maire -------------------------------------------------------------------------------------------

    private void nommer(Atelier atelier) {
        atelier.scene("nommer")
                .serveur("Maelle connectée, aucun maire ; retours de commande visibles", lieu -> {
                    ile(lieu);
                    ScenesEconomie.quitterFigurants();
                    ScenesEconomie.figurant(lieu, MAELLE, new Vec3(0.5, 0, -2.0), 0F, true);
                    ScenesEconomie.console(lieu.serveur(), "role mayor remove");
                    ScenesEconomie.retours(lieu.serveur(), true);
                })
                .camera(new Vec3(0.5, 1.9, 4.0), new Vec3(0.5, 3.5, 14.0))
                .client("chat visible", ScenesEconomie::chatVisible)
                .client("/role mayor (personne)", mc -> mc.player.connection.sendCommand("role mayor"))
                .attendre("aucun maire", () -> ScenesEconomie.cle("command.oneforallsurvival.role.mayor.none"))
                .client("/role mayor set Maelle", mc -> mc.player.connection.sendCommand("role mayor set " + MAELLE))
                .attendre("Maelle maire", () -> ScenesEconomie.cle("command.oneforallsurvival.role.mayor.set"))
                .client("/role mayor", mc -> mc.player.connection.sendCommand("role mayor"))
                .attendre("qui est maire", () -> ScenesEconomie.cle("command.oneforallsurvival.role.mayor.show"))
                .client("/role mayor set Robin (remplace Maelle)", mc -> mc.player.connection.sendCommand("role mayor set "
                        + mc.player.getGameProfile().getName()))
                .attendre("remplacée", () -> ScenesEconomie.cle("command.oneforallsurvival.role.mayor.replaced"))
                .pause(1000)
                .echelle(3)
                .pause(500)
                .prise("nommer-chat")
                .echelle(2)
                .client("chat masqué", ScenesEconomie::chatMasque)
                .serveur("rangement", lieu -> {
                    ScenesEconomie.console(lieu.serveur(), "role mayor remove");
                    ScenesEconomie.retours(lieu.serveur(), false);
                    ScenesEconomie.quitterFigurants();
                });
    }

    // --- les réseaux : électricité et téléphone --------------------------------------------------------------------

    private void reseaux(Atelier atelier) {
        atelier.scene("reseaux")
                .serveur("Robin maire ; Lucas fournisseur ; Maelle opérateur", lieu -> {
                    ile(lieu);
                    ScenesEconomie.quitterFigurants();
                    ServerPlayer robin = lieu.joueur();
                    ScenesEconomie.console(lieu.serveur(), "role mayor set " + robin.getGameProfile().getName());
                    ScenesEconomie.retours(lieu.serveur(), true);
                    JoueurDeTest lucas = ScenesEconomie.figurant(lieu, LUCAS, new Vec3(4.5, 0, 2.5), 90F, true);
                    ScenesEconomie.oublierFournisseur(lieu.serveur(), lucas.uuid());
                    ScenesEconomie.poserPar(lucas.joueur(), new ItemStack(GridRegistry.POWER_STATION_ITEM.get()),
                            lieu.p(3, -1, 0), Direction.UP);
                    var fonde = PowerStationBlock.found(lucas.joueur(), lieu.p(3, 0, 0), FOURNISSEUR);
                    PowerSuppliers r = PowerSuppliers.get(lieu.serveur());
                    PowerSupplier f = r.byFounder(lucas.uuid()).orElseThrow();
                    r.setNextPrice(f.id(), 18L);
                    r.setNextSubscription(f.id(), 10L);
                    constat("poste de Lucas : fondation " + fonde);
                    JoueurDeTest maelle = ScenesEconomie.figurant(lieu, MAELLE, new Vec3(-3.5, 0, 2.5), 270F, true);
                    dev.oneforallsurvival.dev.OperateurDEssai.oublier(lieu.serveur(), maelle.uuid());
                    ScenesEconomie.poserPar(maelle.joueur(), new ItemStack(PhoneRegistry.ANTENNA_ITEM.get()),
                            lieu.p(-3, -1, 0), Direction.UP);
                    var op = AntennaBlock.found(maelle.joueur(), lieu.p(-3, 0, 0), OPERATEUR);
                    constat("antenne de Maelle : fondation " + op + " ; opérateurs "
                            + PhoneOperators.get(lieu.serveur()).all().size());
                })
                .camera(new Vec3(0.5, 1.9, 4.0), new Vec3(0.5, 3.5, 14.0))
                .client("chat visible, pleine largeur", mc -> {
                    ScenesEconomie.chatVisible(mc);
                    mc.options.chatWidth().set(1.0);
                })
                .client("/power suppliers", mc -> mc.player.connection.sendCommand("power suppliers"))
                .attendre("liste des fournisseurs", () -> ScenesEconomie.cle("command.oneforallsurvival.power.suppliers_title"))
                .client("/power cap 15", mc -> mc.player.connection.sendCommand("power cap 15"))
                .attendre("plafond réglé", () -> ScenesEconomie.cle("command.oneforallsurvival.power.cap_set"))
                .client("/power tax 5", mc -> mc.player.connection.sendCommand("power tax 5"))
                .attendre("taxe réglée", () -> ScenesEconomie.cle("command.oneforallsurvival.power.tax_set"))
                .pause(1000)
                .prise("reseaux-power")
                .client("chat vidé", mc -> mc.gui.getChat().clearMessages(false))
                .client("/operator list", mc -> mc.player.connection.sendCommand("operator list"))
                .attendre("liste des opérateurs", () -> ScenesEconomie.cle("command.oneforallsurvival.operator.list_title"))
                .client("/operator cap 40", mc -> mc.player.connection.sendCommand("operator cap 40"))
                .attendre("plafond du téléphone", () -> ScenesEconomie.cle("command.oneforallsurvival.operator.cap_set"))
                .pause(1000)
                .prise("reseaux-operator")
                .client("chat vidé", mc -> mc.gui.getChat().clearMessages(false))
                .client("/power remove (demande)", mc -> mc.player.connection.sendCommand("power remove \"" + FOURNISSEUR
                        + "\" Prix abusifs"))
                .attendre("demande de confirmation", () -> ScenesEconomie.cle("command.oneforallsurvival.power.remove_requested"))
                .pause(1000)
                .prise("reseaux-remove")
                .client("chat masqué", ScenesEconomie::chatMasque)
                .serveur("rangement : réglages rendus, plus de maire", lieu -> {
                    ScenesEconomie.console(lieu.serveur(), "power cap 20");
                    ScenesEconomie.console(lieu.serveur(), "power tax 0");
                    ScenesEconomie.console(lieu.serveur(), "role mayor remove");
                    ScenesEconomie.retours(lieu.serveur(), false);
                    ScenesEconomie.quitterFigurants();
                });
    }
}
