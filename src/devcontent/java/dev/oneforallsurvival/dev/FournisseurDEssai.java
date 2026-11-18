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
 * [joueur]} (niveau 4, donc RCON).
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FournisseurDEssai {

    private FournisseurDEssai() {}

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
            fonde = registre.found(UUID.randomUUID(), "Essai", "Essai " + UUID.randomUUID().toString().substring(0, 8));
        } while (!fonde.founded());
        PowerSupplier fournisseur = fonde.supplier();
        String abonne = compteur.subscriber().isEmpty() ? nom : compteur.subscriber();
        registre.putClient(new PowerClient(compteur.serial(), fournisseur.id(), joueur, nom, abonne,
                PowerClient.State.ACTIVE, 0L, 0L, Set.of(), false));
        if (compteur.getLevel() instanceof ServerLevel level) {
            registre.noteMeterLoaded(compteur.serial(), GlobalPos.of(level.dimension(), compteur.getBlockPos()));
        }
        PowerPairing.refreshMeter(server, compteur.serial());
        return fournisseur;
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
                                        StringArgumentType.getString(ctx, "joueur")))))));
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
