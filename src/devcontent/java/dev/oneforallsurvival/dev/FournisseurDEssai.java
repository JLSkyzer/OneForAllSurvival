package dev.oneforallsurvival.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.grid.supplier.PowerClient;
import dev.oneforallsurvival.grid.supplier.PowerPairing;
import dev.oneforallsurvival.grid.supplier.PowerSupplier;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Un fournisseur d'essai (développement seulement, jamais empaqueté) : depuis le lot E6, un compteur ne laisse passer le
 * courant que jumelé à un fournisseur et accepté. Les essais du lot 4 qui font passer du courant par un compteur le
 * raccordent d'abord ici, en un appel : fournisseur neuf (fondateur fictif), jumelage, accord et acceptation. Leur
 * preuve reste celle du lot 4 ; le raccordement lui-même est prouvé par {@code RaccordementGameTests}.
 *
 * <p>Pour l'essai de bout en bout (client sur serveur dédié), la console a {@code fournisseurdessai raccorder x y z
 * [joueur]} et {@code fournisseurdessai detacher x y z} (niveau 4, donc RCON).
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FournisseurDEssai {

    /** Nom du fondateur fictif de tout fournisseur d'essai : c'est par lui qu'on les retrouve pour les ranger. */
    public static final String FONDATEUR = "Essai";

    private FournisseurDEssai() {}

    /**
     * Retire les fournisseurs d'essai (caisse vide) et leurs clients : au démarrage du serveur de GameTest, pour ne rien
     * garder d'un passage précédent, et à son arrêt. Les clients détachés restent (rien à facturer en essai).
     *
     * @return combien ont été retirés
     */
    public static int ranger(MinecraftServer server) {
        if (!(server instanceof net.minecraft.gametest.framework.GameTestServer)) {
            return 0; // jamais hors du serveur de GameTest (l'E2E garde son fournisseur d'essai d'une phase à l'autre)
        }
        PowerSuppliers registre = PowerSuppliers.get(server);
        int n = 0;
        for (PowerSupplier s : registre.all()) {
            if (FONDATEUR.equals(s.founderName()) && registre.removeIfEmpty(s.id())) {
                n++;
            }
        }
        return n;
    }

    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        ranger(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        ranger(event.getServer());
    }

    /** Raccorde ce compteur au nom de ce joueur d'essai ; le fournisseur créé. */
    public static PowerSupplier raccorder(GameTestHelper helper, MeterBlockEntity compteur, JoueurDeTest joueur) {
        return raccorder(helper.getLevel().getServer(), compteur, joueur.uuid(),
                joueur.joueur().getGameProfile().getName());
    }

    /** Raccorde ce compteur au nom d'un joueur fictif (les essais du lot 4 n'ont pas de débiteur). */
    public static PowerSupplier raccorder(GameTestHelper helper, MeterBlockEntity compteur) {
        return raccorder(helper.getLevel().getServer(), compteur, UUID.randomUUID(), "Essai");
    }

    /**
     * Fournisseur neuf, client accepté : le courant passe aussitôt. Contourne exprès le jumelage par le testeur, le
     * joueur connu et l'accord (essais seulement).
     */
    public static PowerSupplier raccorder(MinecraftServer server, MeterBlockEntity compteur, UUID joueur, String nom) {
        PowerSuppliers registre = PowerSuppliers.get(server);
        PowerSuppliers.Result fonde;
        do {
            fonde = registre.found(UUID.randomUUID(), FONDATEUR, "Essai " + UUID.randomUUID().toString().substring(0, 8));
        } while (!fonde.founded());
        PowerSupplier fournisseur = fonde.supplier();
        String abonne = compteur.subscriber().isEmpty() ? nom : compteur.subscriber();
        registre.putClient(new PowerClient(compteur.serial(), fournisseur.id(), joueur, nom, abonne,
                PowerClient.State.ACTIVE, 0L, 0L, Set.of(), false));
        if (compteur.getLevel() instanceof ServerLevel level) {
            GlobalPos ici = GlobalPos.of(level.dimension(), compteur.getBlockPos());
            registre.setPairedAt(compteur.serial(), ici);
            registre.noteMeterLoaded(compteur.serial(), ici);
        }
        PowerPairing.refreshMeter(server, compteur.serial());
        return fournisseur;
    }

    /**
     * Défait le raccordement de ce compteur (sans le garder pour la clôture : essais seulement). Un compteur jumelé ne se
     * réinscrit pas ; les essais du lot 4 qui changent d'abonné le détachent d'abord, puis le raccordent de nouveau.
     */
    public static void detacher(GameTestHelper helper, MeterBlockEntity compteur) {
        PowerSuppliers.get(helper.getLevel().getServer()).removeClient(compteur.serial());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("fournisseurdessai").requires(s -> s.hasPermission(4))
                .then(Commands.literal("raccorder").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> raccorderConsole(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                "Essai"))
                        .then(Commands.argument("joueur", StringArgumentType.word())
                                .executes(ctx -> raccorderConsole(ctx.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                        StringArgumentType.getString(ctx, "joueur"))))))
                .then(Commands.literal("detacher").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> detacherConsole(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))));
    }

    /** Défait le raccordement d'essai du compteur en {@code pos} : il retombe sous les règles du lot 4 (rien ne passe). */
    private static int detacherConsole(CommandSourceStack source, BlockPos pos) {
        if (!(source.getLevel().getBlockEntity(pos) instanceof MeterBlockEntity compteur)) {
            source.sendFailure(Component.literal("pas de compteur en " + pos.toShortString()));
            return 0;
        }
        boolean defait = PowerSuppliers.get(source.getServer()).removeClient(compteur.serial()).isPresent();
        PowerPairing.refreshMeter(source.getServer(), compteur.serial());
        source.sendSuccess(() -> Component.literal((defait ? "detache" : "deja detache") + " : compteur " + pos.toShortString()), true);
        return 1;
    }

    private static int raccorderConsole(CommandSourceStack source, BlockPos pos, String nom) {
        if (!(source.getLevel().getBlockEntity(pos) instanceof MeterBlockEntity compteur)) {
            source.sendFailure(Component.literal("pas de compteur en " + pos.toShortString()));
            return 0;
        }
        UUID joueur = PowerPairing.knownPlayer(source.getServer(), nom).map(p -> p.getId()).orElse(UUID.randomUUID());
        PowerSupplier f = raccorder(source.getServer(), compteur, joueur, nom);
        source.sendSuccess(() -> Component.literal("raccorde : compteur " + pos.toShortString() + " au fournisseur "
                + f.name()), true);
        return 1;
    }
}
