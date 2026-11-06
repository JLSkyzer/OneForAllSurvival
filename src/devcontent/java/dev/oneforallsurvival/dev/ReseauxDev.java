package dev.oneforallsurvival.dev;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;

/**
 * Construit, dans un monde, une scène « source, câblage d'un réseau tiers, machine d'essai » en
 * passant par le code réel des mods : les connecteurs sont posés avec leur objet (BlockItem.useOn
 * par un joueur factice) et les fils tirés avec leur bobine (useOn de la bobine, deux clics).
 * Aucune dépendance de compilation : tout passe par les registres et les propriétés d'état.
 *
 * <p>La scène s'étend le long de +X à partir de l'origine : source en 0, machine en 5 (ou en 6 avec
 * un bloc en ligne en 5), pour tenir dans le plancher des GameTests et servir telle quelle à la
 * démonstration. Dev seulement, jamais livré.
 */
public final class ReseauxDev {

    /** Les réseaux éprouvés. */
    public enum Reseau {
        TEMOIN("Témoin : source collée à la machine", "createaddition"),
        CREATE_ADDITION("Create Crafts & Additions", "createaddition"),
        NEW_AGE("Create New Age", "create_new_age"),
        ADVANCED_ELECTRICITY("Advanced Electricity", "light_bulb_addon_create_crafts_and__addions");

        public final String nom;
        public final String modId;

        Reseau(String nom, String modId) {
            this.nom = nom;
            this.modId = modId;
        }

        /** Les mods nécessaires sont-ils chargés (le pack complet, ou pas) ? */
        public boolean disponible() {
            ModList mods = ModList.get();
            // La source de toutes les scènes est la créative de Create Crafts & Additions.
            return mods.isLoaded("createaddition") && mods.isLoaded(modId);
        }
    }

    /** Le résultat d'une construction : où sont les blocs, et ce qui s'est passé. */
    public record Scene(Reseau reseau, boolean enLigne, BlockPos source, BlockPos machine, BlockPos inline,
            BlockPos pointA, BlockPos pointB, List<String> journal) {

        /** Le bloc posé contre la machine ou le bloc en ligne, côté réseau (pour l'étiquette). */
        public String resume() {
            return reseau.nom + (enLigne ? " + bloc en ligne" : " (direct)");
        }
    }

    private ReseauxDev() {}

    private static ResourceLocation id(String s) {
        return new ResourceLocation(s);
    }

    /** Écrit une propriété d'état par son nom et la valeur par son nom, sans connaître l'enum. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState avec(BlockState etat, String propriete, String valeur) {
        for (Property<?> p : etat.getProperties()) {
            if (p.getName().equals(propriete)) {
                java.util.Optional<?> v = p.getValue(valeur);
                if (v.isPresent()) {
                    return etat.setValue((Property) p, (Comparable) v.get());
                }
            }
        }
        return etat;
    }

    public static String propriete(BlockState etat, String propriete) {
        for (Property<?> p : etat.getProperties()) {
            if (p.getName().equals(propriete)) {
                return etat.getValue(p).toString();
            }
        }
        return "?";
    }

    private static float lacet(Direction d) {
        return switch (d) {
            case SOUTH -> 0F;
            case WEST -> 90F;
            case NORTH -> 180F;
            case EAST -> -90F;
            default -> 0F;
        };
    }

    private static float tangage(Direction d) {
        return d == Direction.UP ? -90F : d == Direction.DOWN ? 90F : 0F;
    }

    /**
     * Pose un objet comme un joueur : un clic droit sur la face {@code face} du bloc {@code clique},
     * le joueur regardant dans la direction {@code regard}. Renvoie le résultat du clic.
     */
    public static InteractionResult cliquer(ServerLevel level, String objet, BlockPos clique, Direction face,
            Direction regard, int quantite, List<String> journal) {
        Item item = BuiltInRegistries.ITEM.get(id(objet));
        Player joueur = FakePlayerFactory.getMinecraft(level);
        BlockPos oeil = clique.relative(face);
        joueur.setPos(oeil.getX() + 0.5, oeil.getY(), oeil.getZ() + 0.5);
        joueur.setYRot(lacet(regard));
        joueur.setXRot(tangage(regard));
        ItemStack pile = new ItemStack(item, quantite);
        joueur.setItemInHand(InteractionHand.MAIN_HAND, pile);
        Vec3 point = Vec3.atCenterOf(clique).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        UseOnContext ctx = new UseOnContext(level, joueur, InteractionHand.MAIN_HAND, pile,
                new BlockHitResult(point, face, clique, false));
        InteractionResult r = pile.useOn(ctx);
        if (journal != null) {
            journal.add(objet + " sur " + clique.toShortString() + " face " + face + " -> " + r);
        }
        return r;
    }

    /** Clique l'objet au centre d'un bloc (pour les bobines et les fils). */
    private static InteractionResult cliquerAuCentre(ServerLevel level, String objet, BlockPos cible,
            ItemStack pile, List<String> journal) {
        Player joueur = FakePlayerFactory.getMinecraft(level);
        joueur.setItemInHand(InteractionHand.MAIN_HAND, pile);
        UseOnContext ctx = new UseOnContext(level, joueur, InteractionHand.MAIN_HAND, pile,
                new BlockHitResult(Vec3.atCenterOf(cible), Direction.UP, cible, false));
        InteractionResult r = pile.useOn(ctx);
        journal.add(objet + " clic sur " + cible.toShortString() + " -> " + r);
        return r;
    }

    /** Tire un fil entre deux blocs avec la bobine ou le fil de l'objet donné : deux clics. */
    public static void tirerFil(ServerLevel level, String objet, BlockPos a, BlockPos b, List<String> journal) {
        ItemStack pile = new ItemStack(BuiltInRegistries.ITEM.get(id(objet)), 4);
        cliquerAuCentre(level, objet, a, pile, journal);
        cliquerAuCentre(level, objet, b, pile, journal);
    }

    private static void poserBloc(ServerLevel level, BlockPos pos, String bloc, List<String> journal) {
        Block b = BuiltInRegistries.BLOCK.get(id(bloc));
        level.setBlock(pos, b.defaultBlockState(), Block.UPDATE_ALL);
        journal.add(bloc + " posé en " + pos.toShortString());
    }

    /** Pose la machine d'essai en {@code pos} (via le registre de développement). */
    private static void poserMachine(ServerLevel level, BlockPos pos, List<String> journal) {
        level.setBlock(pos, DevBlocks.testMachine().defaultBlockState(), Block.UPDATE_ALL);
        journal.add("test_machine posée en " + pos.toShortString());
    }

    private static void poserEnLigne(ServerLevel level, BlockPos pos, List<String> journal) {
        level.setBlock(pos, DevBlocks.testInline().defaultBlockState()
                .setValue(dev.oneforallsurvival.machine.InlineEnergyBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        journal.add("test_inline posé en " + pos.toShortString() + " (sortie à l'est, entrée à l'ouest)");
    }

    /**
     * Phase 1 : pose la machine, le bloc en ligne, la source et les connecteurs. {@code origine} est
     * la position absolue de la source ; la machine est 5 blocs à l'est (6 avec un bloc en ligne,
     * posé en 5). Les fils se tirent plus tard, par {@link #cabler} : un connecteur New Age n'a pas
     * de réseau avant son premier tick, comme en jeu où le joueur met un instant à sortir sa bobine.
     */
    public static Scene construire(ServerLevel level, BlockPos origine, Reseau reseau, boolean enLigne) {
        List<String> j = new ArrayList<>();
        BlockPos source = origine;
        if (reseau == Reseau.TEMOIN) {
            // La source collée à la machine, ou à la machine derrière le bloc en ligne.
            BlockPos machine = origine.east(enLigne ? 2 : 1);
            BlockPos inline = enLigne ? origine.east(1) : null;
            poserMachine(level, machine, j);
            if (enLigne) {
                poserEnLigne(level, inline, j);
            }
            poserBloc(level, source, "createaddition:creative_energy", j);
            return new Scene(reseau, enLigne, source, machine, inline, source, source, j);
        }

        BlockPos machine = origine.east(enLigne ? 6 : 5);
        BlockPos inline = enLigne ? origine.east(5) : null;
        BlockPos cibleFils = enLigne ? inline : machine;
        BlockPos pointA = origine.east(1);
        BlockPos pointB = origine.east(4);

        poserMachine(level, machine, j);
        if (enLigne) {
            poserEnLigne(level, inline, j);
        }
        poserBloc(level, source, "createaddition:creative_energy", j);

        switch (reseau) {
            case CREATE_ADDITION -> {
                cliquer(level, "createaddition:connector", source, Direction.EAST, Direction.WEST, 1, j);
                cliquer(level, "createaddition:connector", cibleFils, Direction.WEST, Direction.EAST, 1, j);
            }
            case NEW_AGE -> {
                cliquer(level, "create_new_age:electrical_connector", source, Direction.EAST, Direction.WEST, 1, j);
                cliquer(level, "create_new_age:electrical_connector", cibleFils, Direction.WEST, Direction.EAST, 1, j);
            }
            case ADVANCED_ELECTRICITY -> {
                cliquer(level, "createaddition:connector", source, Direction.EAST, Direction.WEST, 1, j);
                cliquer(level, "light_bulb_addon_create_crafts_and__addions:power_box", cibleFils, Direction.WEST,
                        Direction.EAST, 1, j);
                j.add("power_box : face " + propriete(level.getBlockState(pointB), "facing"));
            }
            default -> {
            }
        }
        return new Scene(reseau, enLigne, source, machine, inline, pointA, pointB, j);
    }

    /** Phase 2 : règle les modes et tire le fil entre les deux connecteurs, avec l'objet réel du mod. */
    public static void cabler(ServerLevel level, Scene scene) {
        List<String> j = scene.journal();
        switch (scene.reseau()) {
            case CREATE_ADDITION, ADVANCED_ELECTRICITY -> {
                j.add("mode du connecteur de la source : " + propriete(level.getBlockState(scene.pointA()), "mode")
                        + ", de l'autre bout : " + propriete(level.getBlockState(scene.pointB()), "mode"));
                tirerFil(level, "createaddition:copper_spool", scene.pointA(), scene.pointB(), j);
            }
            case NEW_AGE -> {
                // Comme un coup de clé sur le connecteur de la source : il tire du courant de son voisin.
                level.setBlock(scene.pointA(), avec(level.getBlockState(scene.pointA()), "mode", "pull"), Block.UPDATE_ALL);
                j.add("connecteur New Age de la source : mode " + propriete(level.getBlockState(scene.pointA()), "mode")
                        + ", face " + propriete(level.getBlockState(scene.pointA()), "facing")
                        + " ; de l'autre bout : mode " + propriete(level.getBlockState(scene.pointB()), "mode")
                        + ", face " + propriete(level.getBlockState(scene.pointB()), "facing"));
                tirerFil(level, "create_new_age:copper_wire", scene.pointA(), scene.pointB(), j);
            }
            default -> {
            }
        }
    }
}
