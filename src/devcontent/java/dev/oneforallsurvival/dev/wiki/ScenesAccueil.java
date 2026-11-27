package dev.oneforallsurvival.dev.wiki;

import static dev.oneforallsurvival.dev.wiki.ScenesElectricite.face;
import static dev.oneforallsurvival.dev.wiki.ScenesTelephone.constat;

import dev.oneforallsurvival.dev.JoueurDeTest;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.food.FoodRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Les vues de l'accueil du wiki (« Vu en jeu ») : la place de la ville, de haut, au matin puis au soir. Décor de la
 * mairie ({@link ScenesMaire#mairieDecor}), un étal tenu par Maelle, un coffre-fort, un séchoir et une armoire de cuisine,
 * Lucas qui passe. Aucun geste : une vue d'ensemble.
 */
public final class ScenesAccueil implements Chapitre {

    @Override
    public String id() {
        return "accueil";
    }

    @Override
    public void scenes(Atelier atelier) {
        atelier.scene("place")
                .serveur("la place : mairie, étal, coffre-fort, cuisine", lieu -> {
                    ScenesMaire.mairieDecor(lieu);
                    ScenesEconomie.quitterFigurants();
                    JoueurDeTest maelle = ScenesEconomie.figurant(lieu, "Maelle", new Vec3(5.5, 0, 0.0), 0F, true);
                    ScenesEconomie.etalGarni(lieu, new BlockPos(5, 0, 1), maelle.joueur());
                    ScenesEconomie.figurant(lieu, "Lucas", new Vec3(2.2, 0, 3.6), 160F, true);
                    lieu.poser(-7, 0, -2, face(EconomyRegistry.SAFE.get()));
                    lieu.poser(8, 0, -2, face(FoodRegistry.DRYER.get()));
                    lieu.poser(9, 0, -2, face(FoodRegistry.FRIDGE.get()));
                    for (int x = 7; x <= 10; x++) {
                        for (int y = 0; y <= 2; y++) {
                            lieu.poser(x, y, -3, Blocks.BIRCH_PLANKS.defaultBlockState());
                        }
                    }
                    lieu.poser(-2, 0, 2, Blocks.LANTERN.defaultBlockState());
                    constat("place posée");
                })
                .client("noms au-dessus des têtes, rien d'autre", mc -> {
                    WikiStudio.ath(mc, true);
                    WikiStudio.elements();
                    WikiStudio.reticule(false);
                    mc.gameRenderer.setRenderHand(false);
                })
                .camera(new Vec3(8.5, 2.3, 7.0), new Vec3(1.0, 1.0, -1.5))
                .pause(1500)
                .prise("place-jour")
                .serveur("coucher du soleil", lieu -> lieu.monde().setDayTime(12_600))
                .pause(3000)
                .prise("place-soir")
                .camera(new Vec3(-7.5, 2.4, 7.5), new Vec3(1.5, 1.0, -1.5))
                .pause(1500)
                .prise("place-soir-ouest")
                .client("interface masquée", mc -> WikiStudio.ath(mc, false))
                .serveur("rangement", lieu -> ScenesEconomie.quitterFigurants());
    }
}
