package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaChunks;
import dev.oneforallsurvival.phone.net.AntennaRecord;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Un opérateur d'essai (lot T2) : une antenne posée par un joueur dans le gabarit, nommée (ce qui fonde l'opérateur),
 * alimentée par sa réserve ; et l'émission d'une carte par son fondateur, telle que le panneau la fera (tâche 9).
 *
 * <p>Tout se fait sur place, sans séquence : les aides n'utilisent que des assertions, jamais une autre exception.
 */
public final class OperateurDEssai {

    /** Où l'antenne se pose dans le gabarit (case mise à l'air, sol de pierre dessous). */
    public static final BlockPos ANTENNE = new BlockPos(1, 0, 1);

    /** Ce que l'antenne reçoit dans sa réserve à la fondation. */
    private static final int RESERVE = 40_000;

    private OperateurDEssai() {}

    /**
     * Pose l'antenne en {@link #ANTENNE} par {@code joueur} (l'objet neuf en main, un clic sur le sol), la nomme
     * {@code nom} (ce qui fonde l'opérateur) et remplit sa réserve. L'opérateur qu'un passage précédent aurait laissé à
     * ce joueur est oublié d'abord.
     */
    public static PhoneOperator fonder(GameTestHelper helper, JoueurDeTest joueur, String nom) {
        oublier(helper.getLevel().getServer(), joueur.uuid());
        helper.setBlock(ANTENNE.below(), Blocks.STONE);
        helper.setBlock(ANTENNE, Blocks.AIR);
        ServerPlayer p = joueur.joueur();
        ItemStack avant = p.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack pile = new ItemStack(PhoneRegistry.ANTENNA_ITEM.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = helper.absolutePos(ANTENNE.below());
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
        InteractionResult r = pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
        p.setItemInHand(InteractionHand.MAIN_HAND, avant);
        helper.assertTrue(r.consumesAction(), "l'antenne devait se poser : " + r);
        helper.assertTrue(helper.getBlockEntity(ANTENNE) instanceof AntennaBlockEntity, "pas d'antenne posée");
        AntennaBlockEntity be = (AntennaBlockEntity) helper.getBlockEntity(ANTENNE);
        Optional<PhoneOperators.Result> fonde = AntennaBlock.found(p, helper.absolutePos(ANTENNE), nom);
        helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
        int reste = RESERVE;
        while (reste > 0) {
            int recu = be.energy().receiveEnergy(reste, false);
            if (recu <= 0) {
                break;
            }
            reste -= recu;
        }
        return fonde.get().operator();
    }

    /**
     * Le fondateur (en ligne) émet une carte au nom de {@code titulaire} ; elle arrive dans son inventaire à lui. Rend
     * une copie de la pile émise.
     */
    public static ItemStack emettre(GameTestHelper helper, PhoneOperator operateur, JoueurDeTest titulaire) {
        MinecraftServer serveur = helper.getLevel().getServer();
        ServerPlayer fondateur = serveur.getPlayerList().getPlayer(operateur.founder());
        helper.assertTrue(fondateur != null, "le fondateur de " + operateur.name() + " doit être en ligne");
        Optional<ItemStack> emise = PhoneSims.issueTo(fondateur, operateur, titulaire.uuid(),
                titulaire.joueur().getGameProfile().getName(), EconomyClock.now());
        helper.assertTrue(emise.isPresent() && !emise.get().isEmpty(),
                "la carte devait être émise par " + operateur.name());
        return emise.get();
    }

    /**
     * Vide la caisse de cet opérateur (pièces des essais, jamais versées à personne) : le registre refuse de retirer un
     * opérateur dont la caisse n'est pas vide (tâche 11).
     */
    public static void viderCaisse(PhoneOperators registre, PhoneOperator op) {
        registre.till(op.id()).forEach((coin, n) -> registre.withdraw(op.id(), coin, n));
    }

    /**
     * Retire l'opérateur de ce joueur (caisse vidée d'abord ; ses cartes sont désactivées par le registre) et les
     * inscriptions de ses antennes.
     */
    public static void oublier(MinecraftServer serveur, UUID fondateur) {
        PhoneOperators registre = PhoneOperators.get(serveur);
        registre.byFounder(fondateur).ifPresent(op -> {
            for (AntennaRecord antenne : registre.antennasOf(op.id())) {
                registre.removeAntenna(antenne.id());
                ServerLevel level = serveur.getLevel(antenne.dimension());
                if (level != null) {
                    AntennaChunks.release(level, antenne.pos());
                }
            }
            viderCaisse(registre, op);
            registre.remove(op.id());
        });
    }

    /** Retire l'antenne du gabarit (son inscription et son ticket partent avec elle), puis l'opérateur du fondateur. */
    public static void ranger(GameTestHelper helper, UUID fondateur) {
        helper.setBlock(ANTENNE, Blocks.AIR);
        oublier(helper.getLevel().getServer(), fondateur);
    }
}
