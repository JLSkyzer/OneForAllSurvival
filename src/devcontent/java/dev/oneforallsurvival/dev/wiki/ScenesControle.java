package dev.oneforallsurvival.dev.wiki;

import dev.oneforallsurvival.dev.OperateurDEssai;
import dev.oneforallsurvival.explore.ExploreRegistry;
import dev.oneforallsurvival.explore.client.ReceiverScreen;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FridgeBlockEntity;
import dev.oneforallsurvival.food.client.FridgeScreen;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Le chapitre de contrôle de l'atelier (preuve du socle, pas une page du wiki) : l'antenne en service contre un mur avec
 * le repère de son voyant, deux écrans de bloc ouverts par un vrai clic (repères d'une case et d'un champ), et une
 * courte séquence (une seconde antenne qui s'allume quand le courant arrive).
 */
public final class ScenesControle implements Chapitre {

    private static final BlockPos ANTENNE = new BlockPos(0, 0, 0);
    private static final BlockPos ANTENNE_B = new BlockPos(4, 0, 0);
    private static final BlockPos ARMOIRE = new BlockPos(0, 0, 0);
    private static final BlockPos RECEPTEUR = new BlockPos(3, 0, 0);
    private static final String OPERATEUR = "Radio du Lagon";

    @Override
    public String id() {
        return "controle";
    }

    @Override
    public void scenes(Atelier atelier) {
        atelier.scene("antenne")
                .serveur("mur et antennes", lieu -> {
                    murDePierre(lieu, -2, 6);
                    ServerPlayer joueur = lieu.joueur();
                    OperateurDEssai.oublier(lieu.serveur(), joueur.getUUID());
                    BlockPos a = poserAntenne(lieu, ANTENNE);
                    var fonde = AntennaBlock.found(joueur, a, OPERATEUR);
                    if (fonde.isEmpty() || !fonde.get().founded()) {
                        throw new IllegalStateException("fondation de l'opérateur : " + fonde);
                    }
                    poserAntenne(lieu, ANTENNE_B);
                    alimenter(lieu, a, 0);
                })
                .attendre("antenne en service", () -> etat(ANTENNE) == MachineState.RUNNING)
                .attendre("seconde antenne sans courant", () -> etat(ANTENNE_B) == MachineState.NO_POWER)
                .regarder(ANTENNE, new Vec3(1.0, 0.45, 1.8))
                .repereBloc("voyant", ANTENNE, 0.5, 0.875, 0.5)
                .repereBloc("semelle", ANTENNE, 0.5, 0.06, 0.5)
                .prise("antenne-service")
                // La seconde antenne s'allume : le courant arrive une seconde après la première image.
                .regarder(ANTENNE_B, new Vec3(0.0, 0.45, 2.2))
                .sequence("antenne-allumage", 3000, 100, lieu -> alimenter(lieu, lieu.origine().offset(ANTENNE_B), 20))
                .attendre("seconde antenne en service", () -> etat(ANTENNE_B) == MachineState.RUNNING);

        atelier.scene("ecrans")
                .serveur("armoire et récepteur", lieu -> {
                    murDePierre(lieu, -2, 5);
                    ServerLevel monde = lieu.monde();
                    monde.setBlock(lieu.p(0, 0, 0), FoodRegistry.FRIDGE.get().defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
                    if (!(monde.getBlockEntity(lieu.p(0, 0, 0)) instanceof FridgeBlockEntity armoire)) {
                        throw new IllegalStateException("armoire non posée");
                    }
                    armoire.inventory().setStackInSlot(0, new ItemStack(Items.COOKED_BEEF, 8));
                    armoire.inventory().setStackInSlot(1, new ItemStack(Items.COOKED_COD, 5));
                    armoire.inventory().setStackInSlot(9, new ItemStack(Items.SWEET_BERRIES, 12));
                    monde.setBlock(lieu.p(3, 0, 0), ExploreRegistry.RECEIVER.get().defaultBlockState(), 3);
                })
                .regarder(ARMOIRE, new Vec3(0.0, 0.6, 2.6))
                .ouvrirBloc(ARMOIRE, Direction.SOUTH, FridgeScreen.class)
                .repereCase("viande", 0)
                .repereCase("baies", 9)
                .prise("armoire-ecran")
                .fermer()
                .regarder(RECEPTEUR, new Vec3(0.0, 0.6, 2.6))
                .ouvrirBloc(RECEPTEUR, Direction.SOUTH, ReceiverScreen.class)
                .repereWidget("frequence", w -> w instanceof EditBox)
                .prise("recepteur-ecran")
                .fermer();
    }

    /** Un mur de pierre de 3 de haut au nord de la rangée (z = -1), de {@code x0} à {@code x1}. */
    private static void murDePierre(Lieu lieu, int x0, int x1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = 0; y <= 2; y++) {
                lieu.poser(x, y, -1, Blocks.STONE.defaultBlockState());
            }
        }
    }

    /** Pose une antenne neuve par un clic du joueur sur le bloc du dessous (le vrai chemin de l'objet). */
    private static BlockPos poserAntenne(Lieu lieu, BlockPos rel) {
        BlockPos p = lieu.p(rel.getX(), rel.getY(), rel.getZ());
        ServerPlayer joueur = lieu.joueur();
        ItemStack avant = joueur.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack pile = new ItemStack(PhoneRegistry.ANTENNA_ITEM.get());
        joueur.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = p.below();
        var r = pile.useOn(new UseOnContext(lieu.monde(), joueur, InteractionHand.MAIN_HAND, pile,
                new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false)));
        joueur.setItemInHand(InteractionHand.MAIN_HAND, avant);
        if (!r.consumesAction() || !(lieu.monde().getBlockEntity(p) instanceof AntennaBlockEntity)) {
            throw new IllegalStateException("antenne non posée en " + p.toShortString() + " : " + r);
        }
        return p;
    }

    /** 100 FE à chaque tic dans la machine, à partir du tic {@code apres} (la scène les tient jusqu'à sa fin). */
    private static void alimenter(Lieu lieu, BlockPos p, int apres) {
        int[] tics = {0};
        lieu.tenir(monde -> {
            if (tics[0]++ >= apres && monde.getBlockEntity(p) instanceof ElectricMachineBlockEntity machine) {
                machine.energy().receiveEnergy(100, false);
            }
        });
    }

    /** L'état de la machine en {@code rel} vu par le client, ou {@code null}. */
    private static MachineState etat(BlockPos rel) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos o = WikiStudio.origineEnCours();
        if (o == null || mc.level == null) {
            return null;
        }
        var s = mc.level.getBlockState(o.offset(rel));
        return s.hasProperty(ElectricMachineBlock.STATE) ? s.getValue(ElectricMachineBlock.STATE) : null;
    }
}
