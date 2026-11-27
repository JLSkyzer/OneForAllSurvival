package dev.oneforallsurvival.dev.wiki;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Le terrain d'une scène, côté serveur : le monde, le joueur de l'atelier et l'origine de la scène (premier bloc d'air
 * au-dessus de l'herbe). Une scène écrit ses positions par rapport à l'origine ({@link #p}) ; chaque scène a son
 * terrain, loin des autres (au-delà de la distance d'affichage), dans un monde plat neuf.
 */
public final class Lieu {

    private final MinecraftServer serveur;
    private final BlockPos origine;

    Lieu(MinecraftServer serveur, BlockPos origine) {
        this.serveur = serveur;
        this.origine = origine;
    }

    public MinecraftServer serveur() {
        return serveur;
    }

    public ServerLevel monde() {
        return serveur.overworld();
    }

    /** Le joueur de l'atelier (le seul du monde solo). */
    public ServerPlayer joueur() {
        return serveur.getPlayerList().getPlayers().get(0);
    }

    public BlockPos origine() {
        return origine;
    }

    /** La position {@code origine + (dx, dy, dz)}. */
    public BlockPos p(int dx, int dy, int dz) {
        return origine.offset(dx, dy, dz);
    }

    /** Le point {@code origine + (dx, dy, dz)} (coin nord-ouest du bloc d'origine). */
    public Vec3 v(double dx, double dy, double dz) {
        return new Vec3(origine.getX() + dx, origine.getY() + dy, origine.getZ() + dz);
    }

    /** Pose un bloc par le code (charge le chunk au besoin ; une commande ne poserait rien hors des chunks chargés). */
    public void poser(int dx, int dy, int dz, BlockState etat) {
        monde().setBlock(p(dx, dy, dz), etat, 3);
    }

    /** Une action jouée à chaque tic du serveur jusqu'à la fin de la scène (courant d'une machine, état tenu). */
    public void tenir(Consumer<ServerLevel> chaqueTic) {
        WikiStudio.tenir(chaqueTic);
    }
}
