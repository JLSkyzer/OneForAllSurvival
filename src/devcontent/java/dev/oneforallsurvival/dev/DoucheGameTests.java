package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.town.ShowerHeadBlock;
import dev.oneforallsurvival.town.ShowerHeadBlockEntity;
import dev.oneforallsurvival.town.TownConfig;
import dev.oneforallsurvival.town.TownRegistry;
import dev.oneforallsurvival.town.WellBeing;
import dev.oneforallsurvival.town.WellBeingEffect;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La douche et le Bien-être. La pomme est en hauteur, un plafond de pierre au-dessus, les joueurs ({@link JoueurDeTest},
 * seuls trouvables dans le monde) debout sous elle. La pomme relève les joueurs toutes les 20 ticks à partir de sa
 * pose : les échéances ci-dessous tombent sur ces relevés (la seconde n est comptée au tick 20 n).
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class DoucheGameTests {

    /**
     * Le plancher du gabarit est sous le niveau 0 : un joueur que le tic complet soumet à la gravité se pose à y = 0
     * (observé : y absolu -60 pour un origine à -60), donc la pomme est à 2,5 de ses pieds.
     */
    private static final BlockPos TETE = new BlockPos(3, 2, 1);
    /** Hauteur du plafond posé par {@link #degager}. */
    private static final int PLAFOND = 4;
    private static final String LOT_A = "douche_a";
    private static final String LOT_B = "douche_b";

    /** Les joueurs que le pilote de chaque test fait tourner. */
    private static final Map<GameTestHelper, List<JoueurDeTest>> POOLS = new WeakHashMap<>();

    private DoucheGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Dégage l'espace d'essai et le couvre (voir {@code ClotureGameTests.degager} pour la cause). */
    private static void degager(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                for (int y = 1; y < PLAFOND; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
                helper.setBlock(new BlockPos(x, PLAFOND, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(3, 12, 1), Blocks.GRAVEL);
    }

    /** Une pomme pleine d'eau chaude en {@link #TETE}. */
    private static ShowerHeadBlockEntity poser(GameTestHelper helper, int mb) {
        degager(helper);
        helper.setBlock(TETE, TownRegistry.SHOWER_HEAD.get());
        ShowerHeadBlockEntity be = (ShowerHeadBlockEntity) helper.getBlockEntity(TETE);
        helper.assertTrue(be != null, "pas de block entity de la pomme de douche");
        be.tank().setFluid(mb == 0 ? FluidStack.EMPTY : new FluidStack(TownRegistry.HOT_WATER.get(), mb));
        return be;
    }

    /** Un joueur en survie, debout à {@code x} (relatif), sous la pomme si {@code x} est proche de 3,5. */
    private static JoueurDeTest debout(GameTestHelper helper, String nom, double x) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(new Vec3(x, 0, 1.5)));
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        // Sans client, personne ne fait tourner le tic complet du joueur (effets, faim) : le test le fait à sa place.
        // Un seul pilote par test, créé au premier joueur : GameTestInfo itère ses échéances en les exécutant, et y
        // ajouter une échéance depuis une échéance (onEachTick dans un rappel) corrompt l'itération (NPE du serveur).
        POOLS.computeIfAbsent(helper, h -> {
            List<JoueurDeTest> pool = new CopyOnWriteArrayList<>();
            h.onEachTick(() -> pool.forEach(p -> {
                if (p.connecte()) {
                    p.joueur().doTick();
                }
            }));
            return pool;
        }).add(j);
        return j;
    }

    private static void deplacer(GameTestHelper helper, JoueurDeTest j, double x) {
        Vec3 v = helper.absoluteVec(new Vec3(x, 0, 1.5));
        j.joueur().moveTo(v.x, v.y, v.z);
    }

    private static MobEffectInstance bienEtre(JoueurDeTest j) {
        return j.joueur().getEffect(TownRegistry.WELL_BEING.get());
    }

    private static int dureeMax() {
        return TownConfig.showerWellBeingMinutes() * 60 * 20;
    }

    // ---------------------------------------------------------------------------------------------
    // La douche
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20, timeoutTicks = 300)
    public static void cinqSecondesDonnentLeBienEtre(GameTestHelper helper) {
        ShowerHeadBlockEntity be = poser(helper, 1000);
        JoueurDeTest j = debout(helper, "douche_cinq", 3.5);
        helper.runAfterDelay(110, () -> {
            try {
                MobEffectInstance effet = bienEtre(j);
                helper.assertTrue(effet != null, "le Bien-être était attendu après "
                        + TownConfig.showerSeconds() + " s d'eau chaude");
                helper.assertTrue(effet.getAmplifier() == 0, "niveau 0 attendu, vu " + effet.getAmplifier());
                helper.assertTrue(effet.getDuration() > dureeMax() - 40 && effet.getDuration() <= dureeMax(),
                        "durée proche de " + dureeMax() + " attendue, vue " + effet.getDuration());
                helper.assertTrue(be.secondsUnder(j.uuid()) == 0, "le compteur devait retomber à zéro, vu "
                        + be.secondsUnder(j.uuid()));
                helper.assertTrue(be.tank().getFluidAmount() == 1000 - 5 * TownConfig.showerMbPerTick() * 20,
                        "5 secondes d'eau consommées attendues, reste " + be.tank().getFluidAmount());
                helper.assertTrue(helper.getBlockState(TETE).getValue(ShowerHeadBlock.ACTIVE),
                        "la pomme devait couler (active)");
            } finally {
                j.quitter();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20, timeoutTicks = 300)
    public static void quatreSecondesNeSuffisentPas(GameTestHelper helper) {
        ShowerHeadBlockEntity be = poser(helper, 1000);
        JoueurDeTest j = debout(helper, "douche_quatre", 3.5);
        helper.runAfterDelay(90, () -> {
            try {
                helper.assertTrue(be.secondsUnder(j.uuid()) == TownConfig.showerSeconds() - 1,
                        (TownConfig.showerSeconds() - 1) + " secondes attendues, vues " + be.secondsUnder(j.uuid())
                               );
                helper.assertTrue(bienEtre(j) == null, "pas de Bien-être avant " + TownConfig.showerSeconds() + " s");
            } finally {
                j.quitter();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20, timeoutTicks = 300)
    public static void sansEauChaudeRien(GameTestHelper helper) {
        ShowerHeadBlockEntity be = poser(helper, 0);
        JoueurDeTest j = debout(helper, "douche_sec", 3.5);
        helper.runAfterDelay(130, () -> {
            try {
                helper.assertTrue(be.secondsUnder(j.uuid()) == 0, "aucune seconde sans eau, vues " + be.secondsUnder(j.uuid()));
                helper.assertTrue(bienEtre(j) == null, "pas de Bien-être sans eau chaude");
                helper.assertTrue(!helper.getBlockState(TETE).getValue(ShowerHeadBlock.ACTIVE),
                        "la pomme ne devait pas couler");
            } finally {
                j.quitter();
            }
            helper.succeed();
        });
    }

    /** Trois secondes d'eau, puis rien : le compteur retombe à zéro, et ne reprend pas où il en était. */
    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20, timeoutTicks = 400)
    public static void lEauManqueEnPleineDouche(GameTestHelper helper) {
        ShowerHeadBlockEntity be = poser(helper, 3 * TownConfig.showerMbPerTick() * 20);
        JoueurDeTest j = debout(helper, "douche_panne", 3.5);
        // Toutes les échéances sont posées d'emblée : on n'en ajoute pas depuis une échéance (voir debout).
        helper.runAfterDelay(70, () -> helper.assertTrue(be.secondsUnder(j.uuid()) == 3,
                "3 secondes attendues avant la panne, vues " + be.secondsUnder(j.uuid())));
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(be.secondsUnder(j.uuid()) == 0, "le compteur devait repartir à zéro, vu "
                    + be.secondsUnder(j.uuid()));
            helper.assertTrue(bienEtre(j) == null, "pas de Bien-être après une douche interrompue");
            be.tank().setFluid(new FluidStack(TownRegistry.HOT_WATER.get(), 1000));
        });
        helper.runAfterDelay(150, () -> {
            try {
                // Relevés aux ticks 100, 120, 140 : trois secondes neuves, pas 3 + 3.
                helper.assertTrue(be.secondsUnder(j.uuid()) == 3, "3 secondes neuves attendues, vues "
                        + be.secondsUnder(j.uuid()));
                helper.assertTrue(bienEtre(j) == null, "pas de Bien-être : le compteur ne reprend pas");
            } finally {
                j.quitter();
            }
            helper.succeed();
        });
    }

    /** Sortir de sous la pomme efface le compteur : revenir ne reprend pas où l'on en était. */
    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20, timeoutTicks = 400)
    public static void sortirRemetAZero(GameTestHelper helper) {
        ShowerHeadBlockEntity be = poser(helper, 1000);
        JoueurDeTest j = debout(helper, "douche_sortie", 3.5);
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(be.secondsUnder(j.uuid()) == 2, "2 secondes attendues, vues " + be.secondsUnder(j.uuid()));
            deplacer(helper, j, 6.5);
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(be.secondsUnder(j.uuid()) == 0, "sorti, le compteur devait être à zéro, vu "
                    + be.secondsUnder(j.uuid()));
            deplacer(helper, j, 3.5);
        });
        helper.runAfterDelay(130, () -> {
            try {
                // Relevés aux ticks 80, 100, 120 : trois secondes neuves ; sans remise à zéro, l'effet serait là.
                helper.assertTrue(be.secondsUnder(j.uuid()) == 3, "3 secondes neuves attendues, vues "
                        + be.secondsUnder(j.uuid()));
                helper.assertTrue(bienEtre(j) == null, "pas de Bien-être : il fallait recommencer");
            } finally {
                j.quitter();
            }
            helper.succeed();
        });
    }

    /** Chacun son compteur : le second arrive plus tard et reçoit l'effet plus tard. À 0,4 bloc de l'axe de part et d'autre : assez loin l'un de l'autre pour ne pas se pousser. */
    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20, timeoutTicks = 500)
    public static void deuxJoueursChacunLeSien(GameTestHelper helper) {
        ShowerHeadBlockEntity be = poser(helper, 1000);
        JoueurDeTest premier = debout(helper, "douche_premier", 3.1);
        JoueurDeTest[] second = new JoueurDeTest[1];
        helper.runAfterDelay(50, () -> second[0] = debout(helper, "douche_second", 3.9));
        helper.runAfterDelay(110, () -> {
            // Le premier a eu 5 secondes (effet), le second les relevés 60, 80, 100 : 3.
            helper.assertTrue(bienEtre(premier) != null, "le premier devait avoir le Bien-être");
            helper.assertTrue(bienEtre(second[0]) == null, "le second n'avait pas encore ses 5 secondes");
            helper.assertTrue(be.secondsUnder(second[0].uuid()) == 3, "3 secondes attendues pour le second, vues "
                    + be.secondsUnder(second[0].uuid()));
        });
        helper.runAfterDelay(170, () -> {
            try {
                // Relevés 120, 140, 160 de plus pour le second.
                helper.assertTrue(bienEtre(second[0]) != null, "le second devait avoir le Bien-être à son tour");
            } finally {
                second[0].quitter();
                premier.quitter();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20, timeoutTicks = 300)
    public static void lesSpectateursNeSontPasComptes(GameTestHelper helper) {
        ShowerHeadBlockEntity be = poser(helper, 1000);
        JoueurDeTest j = debout(helper, "douche_spectre", 3.5);
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
        helper.runAfterDelay(130, () -> {
            try {
                helper.assertTrue(be.secondsUnder(j.uuid()) == 0, "un spectateur ne prend pas de douche");
                helper.assertTrue(bienEtre(j) == null, "pas de Bien-être pour un spectateur");
                helper.assertTrue(be.tank().getFluidAmount() == 1000, "aucune eau ne devait couler");
            } finally {
                j.quitter();
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Le Bien-être
    // ---------------------------------------------------------------------------------------------

    /** Un joueur blessé, 30 s d'effet : ni vie, ni absorption, ni effet de soin. */
    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20, timeoutTicks = 900)
    public static void leBienEtreNeSoignePas(GameTestHelper helper) {
        degager(helper);
        helper.assertTrue(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL,
                "en paisible le jeu soigne tout seul : l'essai ne prouverait rien");
        JoueurDeTest j = debout(helper, "douche_blesse", 3.5);
        // Sous 18 de faim, la régénération naturelle du jeu ne joue pas : seule la vie du Bien-être est en cause.
        j.joueur().getFoodData().setFoodLevel(6);
        j.joueur().setHealth(10.0F);
        float vie = j.joueur().getHealth();
        float absorption = j.joueur().getAbsorptionAmount();
        Set<MobEffect> avant = j.joueur().getActiveEffects().stream().map(MobEffectInstance::getEffect)
                .collect(Collectors.toSet());
        WellBeing.grant(j.joueur(), 1500);
        helper.onEachTick(() -> j.joueur().getFoodData().setFoodLevel(6));
        helper.runAfterDelay(620, () -> {
            try {
                MobEffectInstance effet = bienEtre(j);
                helper.assertTrue(effet != null && effet.getDuration() < 1500 - 600,
                        "l'effet devait avoir tourné 30 s, vu " + effet);
                helper.assertTrue(j.joueur().getHealth() == vie, "la vie devait rester à " + vie + ", vue "
                        + j.joueur().getHealth());
                helper.assertTrue(j.joueur().getAbsorptionAmount() == absorption, "l'absorption devait rester à "
                        + absorption + ", vue " + j.joueur().getAbsorptionAmount());
                Set<MobEffect> apres = j.joueur().getActiveEffects().stream().map(MobEffectInstance::getEffect)
                        .collect(Collectors.toSet());
                apres.remove(TownRegistry.WELL_BEING.get());
                helper.assertTrue(apres.equals(avant), "aucun autre effet ne devait apparaître, vus " + apres);
                // L'effet n'a aucun tic : rien de ce qui soigne n'est surchargé.
                for (String methode : new String[] {"applyEffectTick", "isDurationEffectTick", "applyInstantenousEffect"}) {
                    for (var m : WellBeingEffect.class.getDeclaredMethods()) {
                        helper.assertTrue(!m.getName().equals(methode), "WellBeingEffect ne doit pas surcharger " + methode);
                    }
                }
            } finally {
                j.quitter();
            }
            helper.succeed();
        });
    }

    /** Deux douches à la suite : la durée est renouvelée, jamais additionnée. */
    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20, timeoutTicks = 400)
    public static void pasDeCumulAuDela(GameTestHelper helper) {
        poser(helper, 1000);
        JoueurDeTest j = debout(helper, "douche_deux", 3.5);
        helper.runAfterDelay(230, () -> {
            try {
                // Deux octrois (ticks 100 et 200, contrôle à 230) : la durée repart de showerWellBeingMinutes, elle ne s'additionne pas.
                MobEffectInstance effet = bienEtre(j);
                helper.assertTrue(effet != null, "le Bien-être était attendu");
                helper.assertTrue(effet.getDuration() <= dureeMax(), "durée ≤ " + dureeMax() + " attendue, vue "
                        + effet.getDuration());
                helper.assertTrue(effet.getDuration() > dureeMax() - 40, "durée renouvelée attendue, vue "
                        + effet.getDuration());
                helper.assertTrue(effet.getAmplifier() == 0, "toujours le niveau 0, vu " + effet.getAmplifier());
            } finally {
                j.quitter();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20)
    public static void lEffetDonneSesBonus(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = debout(helper, "douche_bonus", 3.5);
        try {
            AttributeInstance vitesse = j.joueur().getAttribute(Attributes.MOVEMENT_SPEED);
            double base = vitesse.getValue();
            float minage = ForgeEventFactory.getBreakSpeed(j.joueur(), Blocks.STONE.defaultBlockState(), 1.0F,
                    helper.absolutePos(new BlockPos(1, 1, 1)));
            WellBeing.grant(j.joueur(), 200);
            AttributeModifier modificateur = vitesse.getModifier(WellBeingEffect.speedModifierId());
            helper.assertTrue(modificateur != null, "le modificateur de vitesse du Bien-être est absent");
            helper.assertTrue(modificateur.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL,
                    "MULTIPLY_TOTAL attendu, vu " + modificateur.getOperation());
            helper.assertTrue(Math.abs(modificateur.getAmount() - TownConfig.wellBeingSpeedBonus()) < 1.0E-9,
                    "+" + TownConfig.wellBeingSpeedBonus() + " attendu, vu " + modificateur.getAmount());
            helper.assertTrue(Math.abs(vitesse.getValue() / base - (1.0 + TownConfig.wellBeingSpeedBonus())) < 1.0E-6,
                    "vitesse ×" + (1.0 + TownConfig.wellBeingSpeedBonus()) + " attendue, vue ×" + vitesse.getValue() / base);
            float minageBienEtre = ForgeEventFactory.getBreakSpeed(j.joueur(), Blocks.STONE.defaultBlockState(), 1.0F,
                    helper.absolutePos(new BlockPos(1, 1, 1)));
            helper.assertTrue(Math.abs(minageBienEtre / minage - (1.0F + (float) TownConfig.wellBeingMiningBonus())) < 1.0E-4F,
                    "minage ×" + (1.0F + TownConfig.wellBeingMiningBonus()) + " attendu, vu " + minage + " puis " + minageBienEtre);
        } finally {
            j.quitter();
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // La cuve
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void laPommeNAccepteQueLEauChaude(GameTestHelper helper) {
        ShowerHeadBlockEntity be = poser(helper, 0);
        Direction[] faces = new Direction[7];
        System.arraycopy(Direction.values(), 0, faces, 0, 6);
        for (Direction face : faces) {
            IFluidHandler tuyau = be.getCapability(ForgeCapabilities.FLUID_HANDLER, face)
                    .orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException(
                            "pas de capability de fluides sur la face " + face));
            for (Fluid refuse : new Fluid[] {Fluids.WATER, Fluids.FLOWING_WATER, Fluids.LAVA,
                    FoodRegistry.POTABLE_WATER.get()}) {
                helper.assertTrue(tuyau.fill(new FluidStack(refuse, 1000), FluidAction.SIMULATE) == 0,
                        refuse + " ne doit pas se simuler rempli (face " + face + ")");
                helper.assertTrue(tuyau.fill(new FluidStack(refuse, 1000), FluidAction.EXECUTE) == 0,
                        refuse + " ne doit pas entrer (face " + face + ")");
            }
        }
        helper.assertTrue(be.tank().isEmpty(), "la cuve devait rester vide, vue " + be.tank().getFluid());
        int pris = be.fluids().fill(new FluidStack(TownRegistry.HOT_WATER.get(), 1500), FluidAction.EXECUTE);
        helper.assertTrue(pris == ShowerHeadBlockEntity.TANK_CAPACITY && be.tank().getFluidAmount() == pris,
                "l'eau chaude devait entrer jusqu'à " + ShowerHeadBlockEntity.TANK_CAPACITY + ", prise " + pris);
        // Aucune face ne vide.
        for (Direction face : faces) {
            IFluidHandler tuyau = be.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElseThrow(
                    () -> new net.minecraft.gametest.framework.GameTestAssertException("pas de capability"));
            helper.assertTrue(tuyau.drain(1000, FluidAction.EXECUTE).isEmpty(), "rien ne doit sortir (face " + face + ")");
            helper.assertTrue(tuyau.drain(new FluidStack(TownRegistry.HOT_WATER.get(), 1000), FluidAction.EXECUTE).isEmpty(),
                    "rien ne doit sortir, même demandé par type (face " + face + ")");
        }
        helper.assertTrue(be.tank().getFluidAmount() == ShowerHeadBlockEntity.TANK_CAPACITY, "la cuve devait rester pleine");
        helper.succeed();
    }
}
