package dev.oneforallsurvival.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.CoinActions;
import dev.oneforallsurvival.economy.SafeActionPacket;
import dev.oneforallsurvival.economy.SafeBlock;
import dev.oneforallsurvival.economy.SafeMenu;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le côté serveur de l'essai de bout en bout de la monnaie (développement seulement, jamais empaqueté). Quatre commandes
 * réservées à la console (niveau 4, donc au RCON : le joueur de l'essai n'est pas opérateur) pour ce qu'un vrai client ne
 * peut pas faire seul :
 *
 * <ul>
 *   <li>{@code e2emonnaie belt x y z} : trois tapis de Create bout à bout, vers l'est ;
 *   <li>{@code e2emonnaie sol_creee <coupure> <nombre> x y z} : des pièces posées au sol, comptées « créées par
 *       l'équipe » au registre (l'essai en tient le compte) ;
 *   <li>{@code e2emonnaie sol_prise <joueur> <coupure> <nombre> x y z} : des pièces retirées à un joueur et posées au
 *       sol, comme s'il les avait jetées (aucune création, aucune destruction) ;
 *   <li>{@code e2emonnaie autre x y z} : un second joueur, sans droits, sur une connexion simulée
 *       ({@link JoueurDeTest}) : il entre (somme de départ), tente d'ouvrir le coffre-fort en {@code x y z} et d'y
 *       retirer une pièce, sort, revient (la somme de départ ne doit pas être redonnée) et sort.
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class E2eMonnaieServeur {

    private static final String AUTRE = "Autre";

    private E2eMonnaieServeur() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("e2emonnaie").requires(s -> s.hasPermission(4))
                .then(Commands.literal("belt").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(E2eMonnaieServeur::belt)))
                .then(Commands.literal("sol_creee")
                        .then(Commands.argument("coupure", StringArgumentType.word())
                                .then(Commands.argument("nombre", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> sol(ctx, null))))))
                .then(Commands.literal("sol_prise")
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .then(Commands.argument("coupure", StringArgumentType.word())
                                        .then(Commands.argument("nombre", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(ctx -> sol(ctx, EntityArgument.getPlayer(ctx, "joueur"))))))))
                .then(Commands.literal("autre").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(E2eMonnaieServeur::autre))));
    }

    private static int dire(CommandContext<CommandSourceStack> ctx, String texte) {
        ctx.getSource().sendSuccess(() -> Component.literal(texte), false);
        return 1;
    }

    // ---------------------------------------------------------------------------------------------------- tapis

    private static int belt(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos debut = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        Block tapis = BuiltInRegistries.BLOCK.get(new ResourceLocation("create", "belt"));
        if (tapis == Blocks.AIR) {
            return dire(ctx, "ABSENT : Create n'est pas charge");
        }
        for (int i = 0; i < 3; i++) {
            String part = i == 0 ? "start" : (i == 2 ? "end" : "middle");
            monde.setBlock(debut.east(i), etat(tapis, "part", part, "slope", "horizontal", "facing", "east"), 3);
        }
        try {
            Class<?> classe = Class.forName("com.simibubi.create.content.kinetics.belt.BeltBlock");
            classe.getMethod("initBelt", net.minecraft.world.level.Level.class, BlockPos.class).invoke(null, monde, debut);
        } catch (ReflectiveOperationException e) {
            return dire(ctx, "ECHEC initBelt : " + e);
        }
        return dire(ctx, "ok : trois tapis de " + debut.toShortString() + " vers l'est");
    }

    private static BlockState etat(Block bloc, String... nomsEtValeurs) {
        BlockState etat = bloc.defaultBlockState();
        for (int i = 0; i < nomsEtValeurs.length; i += 2) {
            Property<?> propriete = bloc.getStateDefinition().getProperty(nomsEtValeurs[i]);
            if (propriete == null) {
                throw new IllegalStateException("propriete absente : " + nomsEtValeurs[i]);
            }
            etat = avec(etat, propriete, nomsEtValeurs[i + 1]);
        }
        return etat;
    }

    private static <T extends Comparable<T>> BlockState avec(BlockState etat, Property<T> propriete, String valeur) {
        return etat.setValue(propriete, propriete.getValue(valeur).orElseThrow(() -> new IllegalStateException("valeur : " + valeur)));
    }

    // ------------------------------------------------------------------------------------------------------ sol

    private static Coin coupure(String nom) {
        return Coin.valueOf(nom.toUpperCase(Locale.ROOT));
    }

    private static int sol(CommandContext<CommandSourceStack> ctx, ServerPlayer joueur)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel monde = ctx.getSource().getLevel();
        Coin coin = coupure(StringArgumentType.getString(ctx, "coupure"));
        int nombre = IntegerArgumentType.getInteger(ctx, "nombre");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        if (joueur != null) {
            EnumMap<Coin, Long> voulu = new EnumMap<>(Coin.class);
            voulu.put(coin, (long) nombre);
            if (PlayerCoins.held(joueur).getOrDefault(coin, 0L) < nombre) {
                return dire(ctx, "REFUS : le joueur n'a pas " + nombre + " " + coin);
            }
            PlayerCoins.remove(joueur, voulu);
        } else {
            EconomyLedger.get(ctx.getSource().getServer()).created(MoneySource.STAFF, CoinMath.valueOf(coin, nombre), null,
                    "essai de bout en bout : pieces posees au sol");
        }
        CoinDropEntity piece = CoinDropEntity.at(monde, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, coin, nombre);
        piece.setDeltaMovement(Vec3.ZERO);
        monde.addFreshEntity(piece);
        return dire(ctx, "ok : " + nombre + " " + coin + " au sol en " + pos.toShortString());
    }

    // ---------------------------------------------------------------------------------------------- second joueur

    private static int autre(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel monde = ctx.getSource().getLevel();
        var serveur = ctx.getSource().getServer();
        BlockPos coffre = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        EconomyLedger registre = EconomyLedger.get(serveur);
        Vec3 debout = Vec3.atBottomCenterOf(coffre.south(2));

        long departAvant = registre.totalCreated(MoneySource.STARTING);
        JoueurDeTest premier = JoueurDeTest.rejoindre(monde, AUTRE, debout, false, p -> {});
        ServerPlayer joueur = premier.joueur();
        long departPremiere = registre.totalCreated(MoneySource.STARTING) - departAvant;
        long porte = CoinMath.valueOf(PlayerCoins.held(joueur));
        boolean operateur = joueur.hasPermissions(2);
        long compteAvant = SafeAccounts.get(serveur).totalMass();
        boolean ouvert = SafeBlock.open(joueur, coffre);
        List<String> messages = premier.messagesRecus();
        boolean menuDuCoffre = joueur.containerMenu instanceof SafeMenu;
        CoinActions.Result retrait = SafeActionPacket.apply(joueur, SafeActionPacket.withdraw(coffre, Coin.COPPER, 1));
        CoinActions.Result depot = SafeActionPacket.apply(joueur, SafeActionPacket.depositAll(coffre));
        long compteApres = SafeAccounts.get(serveur).totalMass();
        premier.quitter();

        // Il revient : sa somme de départ est déjà notée, rien de plus ne lui est donné.
        long departAvantRetour = registre.totalCreated(MoneySource.STARTING);
        JoueurDeTest retour = JoueurDeTest.rejoindre(monde, AUTRE, debout, false, p -> {});
        long departRetour = registre.totalCreated(MoneySource.STARTING) - departAvantRetour;
        long porteAuRetour = CoinMath.valueOf(PlayerCoins.held(retour.joueur()));
        retour.quitter();

        return dire(ctx, "AUTRE operateur=" + operateur + " depart1=" + departPremiere + " porte1=" + porte + " ouvert=" + ouvert
                + " menu=" + menuDuCoffre + " messages=" + messages + " retrait=" + retrait + " depot=" + depot
                + " compte=" + (compteApres - compteAvant) + " depart2=" + departRetour + " porte2=" + porteAuRetour
                + " ; AUTRE_FIN");
    }
}
