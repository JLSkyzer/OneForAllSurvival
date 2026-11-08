package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.DesalinatorMenu;
import dev.oneforallsurvival.food.DryerMenu;
import dev.oneforallsurvival.food.FoodConfig;
import dev.oneforallsurvival.food.FoodDecay;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FoodStamp;
import dev.oneforallsurvival.food.FridgeMenu;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;

/**
 * Étape « vivres » de {@link E2eClient} (développement seulement, jamais empaquetée) : les aliments qui périment, l'armoire,
 * le dessalinisateur, le séchoir et la marmite de Farmer's Delight, vus par un vrai client contre un serveur dédié.
 * Tout passe par le chemin d'un joueur : {@code MultiPlayerGameMode.useItemOn} pour poser et ouvrir, clics de menu
 * pour déplacer les piles, la touche « utiliser » tenue pour manger. Le client ne connaît le serveur que par le réseau :
 * inventaire, menus, état des blocs, messages. Les dates sont posées par de vraies commandes ({@code /give}, {@code /item}).
 *
 * <p>Deux séquences. {@link #premierePhase} joue le scénario et laisse l'armoire, ses piles et un plat daté ; les positions
 * et les dates vont dans {@value #FICHIER}. {@link #relance}, après un vrai arrêt et une vraie relance du serveur, relit le tout.
 * Un pas qui échoue est consigné (ligne {@code FAIL}) puis la séquence continue, sauf pour les pas dont la suite dépend.
 */
final class E2eVivres {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-e2e");
    private static final String FICHIER = "e2e-vivres.txt";
    private static final String ZONE = "e2e_vivres";
    private static final double DELAI = 25;
    private static final int ECHECS_MAX = 8;

    private static final int H_ARMOIRE = 3;
    private static final int H_DESAL = 4;
    private static final int H_SECHOIR = 5;
    private static final int H_FRAIS = 6;
    private static final int H_RASSIS = 7;

    private static final String P_COTE = "command.oneforallsurvival.coast.";
    private static final String CLE_DATE = FoodStamp.KEY;

    private record Recu(String cle, String texte) {}

    private record Pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes,
            double delai, boolean critique) {}

    private static final List<Recu> RECUS = new ArrayList<>();
    private static final List<Pas> PAS = new ArrayList<>();
    private static int index;
    private static boolean actionFaite;
    private static long debut;
    private static long tic;
    private static int echecsDuPas;

    private static Minecraft mc;
    private static BlockPos o;
    private static BlockPos armoire;
    private static BlockPos desal;
    private static BlockPos sechoir;
    private static BlockPos marmite;

    // Ce que les pas se transmettent.
    private static long dateBoeuf;
    private static int faimAvant;
    private static long refBalayage;
    private static boolean utilisation;
    private static int phaseUtilisation;
    private static final List<String> CONTENU_ARMOIRE = new ArrayList<>();
    private static long dateRagout;
    private static long tempsAvantArret;

    private E2eVivres() {}

    static String legende() {
        return index < PAS.size() ? PAS.get(index).legende() : "Fin de l'étape";
    }

    static void recevoir(Component message) {
        Component m = message;
        if (!(m.getContents() instanceof TranslatableContents) && !m.getSiblings().isEmpty()) {
            m = m.getSiblings().get(0);
        }
        String cle = m.getContents() instanceof TranslatableContents t ? t.getKey() : "";
        synchronized (RECUS) {
            RECUS.add(new Recu(cle, message.getString()));
        }
    }

    // ================================================================================================
    // Déroulement
    // ================================================================================================

    /** Avance d'un pas ; vrai quand la séquence est finie. */
    static boolean avancer(Minecraft minecraft) {
        mc = minecraft;
        tic++;
        if (index >= PAS.size()) {
            return true;
        }
        Pas p = PAS.get(index);
        if (!actionFaite) {
            LOG.info("[e2e] pas : {}", p.legende());
            p.action().run();
            actionFaite = true;
            debut = System.nanoTime();
            return false;
        }
        if (p.pret().getAsBoolean()) {
            if (p.succes() != null) {
                E2eClient.pass(p.succes().get());
            }
            index++;
            actionFaite = false;
        } else if (ecoule() > p.delai()) {
            String detail = p.legende() + " : " + p.echec().get() + " ; recus : " + recus();
            if (p.critique()) {
                E2eClient.fail(detail);
            } else {
                E2eClient.failSoft(detail);
                echecsDuPas++;
                index++;
                actionFaite = false;
                if (echecsDuPas >= ECHECS_MAX) {
                    E2eClient.fail("trop d'echecs dans l'etape des vivres : arret");
                }
            }
        }
        return false;
    }

    private static double ecoule() {
        return (System.nanoTime() - debut) / 1e9;
    }

    private static String recus() {
        synchronized (RECUS) {
            return RECUS.size() > 6 ? RECUS.subList(RECUS.size() - 6, RECUS.size()).toString() : RECUS.toString();
        }
    }

    private static void pas(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, DELAI, false));
    }

    private static void pasD(double delai, String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec,
            Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, delai, false));
    }

    private static void pasC(String legende, Runnable action, BooleanSupplier pret, Supplier<String> echec, Supplier<String> succes) {
        PAS.add(new Pas(legende, action, pret, echec, succes, DELAI, true));
    }

    /** Un contrôle immédiat : passe ou attend jusqu'au délai, puis échoue avec le détail. */
    private static void controle(String legende, BooleanSupplier ok, Supplier<String> detail) {
        pas(legende, () -> {}, ok, detail, detail);
    }

    private static void commande(String texte) {
        LOG.info("[e2e] commande : /{}", texte);
        mc.player.connection.sendCommand(texte);
    }

    private static String nom() {
        return mc.player.getGameProfile().getName();
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static void vider() {
        synchronized (RECUS) {
            RECUS.clear();
        }
    }

    private static boolean recu(String cle) {
        synchronized (RECUS) {
            return RECUS.stream().anyMatch(r -> r.cle().equals(cle));
        }
    }

    private static void clic(BlockPos pos) {
        var res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
        LOG.info("[e2e] clic droit en {}, main {} : {}", pos, mc.player.getMainHandItem(), res);
    }

    private static void tenir(int fente) {
        mc.player.getInventory().selected = fente;
    }

    /** Une case de la barre d'action vide, pour ouvrir un bloc à main vide ; à défaut, la dernière. */
    private static void tenirVide() {
        for (int i = 8; i >= 0; i--) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                tenir(i);
                return;
            }
        }
        tenir(8);
    }

    private static MachineState etat(BlockPos pos) {
        var s = mc.level.getBlockState(pos);
        return s.hasProperty(ElectricMachineBlock.STATE) ? s.getValue(ElectricMachineBlock.STATE) : null;
    }

    private static Item objet(String id) {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(id));
    }

    private static Block bloc(String id) {
        return BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
    }

    private static String idDe(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    private static int compter(Predicate<ItemStack> p) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (p.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int piles(Predicate<ItemStack> p) {
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) {
            if (p.test(s)) {
                n++;
            }
        }
        return n;
    }

    private static ItemStack premiere(Predicate<ItemStack> p) {
        for (ItemStack s : mc.player.getInventory().items) {
            if (p.test(s)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Les cases non vides de l'inventaire, avec leur tag : pour le détail d'un échec. */
    private static String inventaire() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mc.player.getInventory().items.size(); i++) {
            ItemStack s = mc.player.getInventory().items.get(i);
            if (!s.isEmpty()) {
                sb.append(i).append('=').append(s).append(s.getTag() == null ? "" : s.getTag()).append(' ');
            }
        }
        return sb.toString();
    }

    private static Predicate<ItemStack> est(Item item) {
        return s -> s.is(item);
    }

    private static boolean date(ItemStack s) {
        return FoodStamp.isStamped(s);
    }

    // ---------------------------------------------------------------------------------------------- menus

    private static AbstractContainerMenu menu() {
        return mc.player.containerMenu;
    }

    private static boolean ouvert(Class<? extends AbstractContainerMenu> type) {
        return type.isInstance(menu()) && mc.screen instanceof AbstractContainerScreen<?>;
    }

    /** Le rang, dans le menu ouvert, de la première case de la machine ({@code joueur} faux) ou du joueur qui répond au test. */
    private static int fenteMenu(Predicate<ItemStack> p, boolean joueur) {
        for (Slot s : menu().slots) {
            if ((s.container == mc.player.getInventory()) == joueur && p.test(s.getItem())) {
                return s.index;
            }
        }
        return -1;
    }

    private static void deplacer(int fente) {
        if (fente < 0) {
            throw new IllegalStateException("case absente du menu " + menu().getClass().getSimpleName());
        }
        LOG.info("[e2e] clic majuscule sur la case {} : {}", fente, menu().slots.get(fente).getItem());
        mc.gameMode.handleInventoryMouseClick(menu().containerId, fente, 0, ClickType.QUICK_MOVE, mc.player);
    }

    private static void ouvrir(String legende, Supplier<BlockPos> ou, Class<? extends AbstractContainerMenu> type) {
        pasC(legende, () -> {
            tenirVide();
            clic(ou.get());
        }, () -> ouvert(type), () -> "menu " + type.getSimpleName() + " non ouvert, menu " + menu().getClass().getSimpleName()
                + ", ecran " + mc.screen, () -> "menu " + type.getSimpleName() + " ouvert par un vrai clic droit");
    }

    private static void fermer(String legende) {
        pas(legende, () -> mc.player.closeContainer(), () -> mc.screen == null && menu() == mc.player.inventoryMenu,
                () -> "menu encore ouvert : " + menu().getClass().getSimpleName(), null);
    }

    private static void poser(String legende, int fente, Supplier<BlockPos> ou, Supplier<Block> bloc) {
        pasC(legende, () -> {
            tenir(fente);
            BlockPos sol = ou.get().below();
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false));
        }, () -> mc.level.getBlockState(ou.get()).is(bloc.get()), () -> "bloc absent en " + ou.get() + " : " + mc.level.getBlockState(ou.get()),
                () -> "pose par un clic droit reel en " + ou.get().toShortString() + " : " + mc.level.getBlockState(ou.get()).getBlock().getName().getString());
    }

    private static void commandeAttendue(String legende, Supplier<String> texte, String cle) {
        pas(legende, () -> {
            vider();
            commande(texte.get());
        }, () -> recu(cle), () -> "pas de message " + cle, null);
    }

    // ---------------------------------------------------------------------------------------------- infobulles

    /** L'état affiché dans l'infobulle (suffixe de la clé {@code food.oneforallsurvival.*}), ou {@code null} si aucune ligne. */
    private static String infobulle(ItemStack s) {
        for (Component ligne : s.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL)) {
            if (ligne.getContents() instanceof TranslatableContents t && t.getKey().startsWith("food.oneforallsurvival.")) {
                return t.getKey().substring("food.oneforallsurvival.".length());
            }
        }
        return null;
    }

    /** Les traits pleins de la barre de péremption de l'infobulle (la ligne de 20 traits), ou -1 s'il n'y en a pas. */
    private static int traitsPleins(ItemStack s) {
        for (Component ligne : s.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL)) {
            String t = ligne.getString();
            if (t.length() == 20 && t.chars().allMatch(c -> c == '|')) {
                return ligne.plainCopy().getString().length();
            }
        }
        return -1;
    }

    private static String couleurBarre(ItemStack s) {
        for (Component ligne : s.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL)) {
            String t = ligne.getString();
            if (t.length() == 20 && t.chars().allMatch(c -> c == '|')) {
                return ligne.getStyle().getColor() == null ? "?" : ligne.getStyle().getColor().serialize();
            }
        }
        return "-";
    }

    private static ItemStack datee(Item item, int nombre, long date) {
        ItemStack s = new ItemStack(item, nombre);
        FoodStamp.setStamp(s, date);
        return s;
    }

    private static String nbtItem(String id, long date) {
        return id + "{" + CLE_DATE + ":" + date + "L}";
    }

    // ================================================================================================
    // Première phase
    // ================================================================================================

    static void premierePhase() {
        preparation();
        machines();
        boeuf();
        infobulles();
        manger();
        avarie();
        dessalinisateur();
        sechoirDepart();
        marmite();
        armoire();
        sechoirFin();
        persistance();
    }

    private static void preparation() {
        pasC("Aire du lot 3, règles du monde et objets par de vraies commandes", () -> {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
            o = mc.player.blockPosition();
            armoire = o.offset(3, 0, -3);
            desal = o.offset(-3, 0, -3);
            sechoir = o.offset(3, 0, 3);
            marmite = o.offset(-3, 0, 3);
            // Un essai interrompu a pu laisser ses blocs : l'aire est rendue libre avant la pose.
            for (BlockPos q : new BlockPos[] {armoire, desal, sechoir, marmite, armoire.east(), desal.west(), sechoir.east()}) {
                commande("setblock " + xyz(q) + " minecraft:air");
            }
            // Une armoire retirée lâche ses aliments : ramassés, ils fusionneraient avec les steaks de l'essai (dates
            // moyennées), et le steak « frais » disparaîtrait dans une pile plus vieille. Ces objets au sol sont détruits.
            commande("kill @e[type=minecraft:item,distance=..48]");
            for (String aliment : new String[] {"cooked_beef", "cooked_chicken", "cooked_porkchop", "beef"}) {
                commande("clear " + nom() + " minecraft:" + aliment);
            }
            // Le regard au ciel : la touche « utiliser » tenue ne vise aucun bloc de l'aire.
            mc.player.setXRot(-90.0F);
            // En paisible la faim ne baisse pas : il faut le niveau facile, sans monstre ni régénération.
            commande("difficulty easy");
            commande("gamerule doMobSpawning false");
            commande("gamerule naturalRegeneration false");
            commande("kill @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,distance=..48]");
            commande("gamemode survival " + nom());
            commande("item replace entity " + nom() + " hotbar." + H_ARMOIRE + " with oneforallsurvival:fridge 1");
            commande("item replace entity " + nom() + " hotbar." + H_DESAL + " with oneforallsurvival:desalinator 1");
            commande("item replace entity " + nom() + " hotbar." + H_SECHOIR + " with oneforallsurvival:dryer 1");
        }, () -> mc.player.getInventory().getItem(H_ARMOIRE).is(FoodRegistry.FRIDGE_ITEM.get())
                && mc.player.getInventory().getItem(H_DESAL).is(FoodRegistry.DESALINATOR_ITEM.get())
                && mc.player.getInventory().getItem(H_SECHOIR).is(FoodRegistry.DRYER_ITEM.get()),
                () -> "machines absentes de la barre d'action", () -> "/difficulty easy, /gamerule, /item replace : armoire, dessalinisateur et sechoir recus ; durees du serveur : "
                        + "frais " + FoodConfig.freshTicks() + " ticks, rassis " + FoodConfig.staleTicks() + " ticks (lues cote client, config synchronisee)");
    }

    private static void machines() {
        poser("Pose de l'armoire par un clic droit", H_ARMOIRE, () -> armoire, FoodRegistry.FRIDGE::get);
        poser("Pose du dessalinisateur par un clic droit", H_DESAL, () -> desal, FoodRegistry.DESALINATOR::get);
        poser("Pose du séchoir par un clic droit", H_SECHOIR, () -> sechoir, FoodRegistry.DRYER::get);
        pasC("Sources Create Crafts & Additions contre les trois machines", () -> {
            Block creative = bloc("createaddition:creative_energy");
            if (creative == net.minecraft.world.level.block.Blocks.AIR) {
                throw new IllegalStateException("createaddition:creative_energy absent : lancer avec -PwithPack");
            }
            commande("setblock " + xyz(armoire.east()) + " createaddition:creative_energy");
            commande("setblock " + xyz(desal.west()) + " createaddition:creative_energy");
            commande("setblock " + xyz(sechoir.east()) + " createaddition:creative_energy");
            // La marmite de Farmer's Delight, sur un bloc de magma qui la chauffe.
            commande("setblock " + xyz(marmite.below()) + " minecraft:magma_block");
            commande("setblock " + xyz(marmite) + " farmersdelight:cooking_pot");
        }, () -> mc.level.getBlockState(armoire.east()).is(bloc("createaddition:creative_energy"))
                && mc.level.getBlockState(desal.west()).is(bloc("createaddition:creative_energy"))
                && mc.level.getBlockState(sechoir.east()).is(bloc("createaddition:creative_energy"))
                && mc.level.getBlockState(marmite).is(bloc("farmersdelight:cooking_pot")),
                () -> "sources ou marmite absentes", () -> "sources de courant posees contre les trois machines, marmite sur bloc de magma");

        pas("Zone /coast autour du dessalinisateur", () -> {
            vider();
            commande("coast zone remove " + ZONE);
            commande("coast zone add " + ZONE + " " + xyz(desal.offset(-2, -2, -2)) + " " + xyz(desal.offset(2, 2, 2)));
        }, () -> recu(P_COTE + "zone.added"), () -> "zone non ajoutee", () -> "/coast zone add " + ZONE + " autour du dessalinisateur "
                + desal.toShortString());
        pas("Trois bouteilles de verre en main", () -> commande("give " + nom() + " minecraft:glass_bottle 3"),
                () -> compter(est(Items.GLASS_BOTTLE)) >= 3, () -> "bouteilles : " + compter(est(Items.GLASS_BOTTLE)), null);
        ouvrir("Ouverture du dessalinisateur", () -> desal, DesalinatorMenu.class);
        pas("Les bouteilles passent dans la case d'entrée (clic majuscule)", () -> deplacer(fenteMenu(est(Items.GLASS_BOTTLE), true)),
                () -> menu().getSlot(1).getItem().is(Items.GLASS_BOTTLE) && menu().getSlot(1).getItem().getCount() == 3,
                () -> "case d'entree : " + menu().getSlot(1).getItem(),
                () -> "bouteilles dans le dessalinisateur : " + menu().getSlot(1).getItem().getCount() + " ; il se met au travail dans la zone /coast");
        fermer("Fermeture du dessalinisateur");
    }

    private static void boeuf() {
        pas("Deux boeufs crus tombent au sol près du joueur", () -> {
            Vec3 p = mc.player.position();
            String xyz = p.x + " " + p.y + " " + p.z;
            commande("summon minecraft:item " + xyz + " {Item:{id:\"minecraft:beef\",Count:1b}}");
            commande("summon minecraft:item " + xyz + " {Item:{id:\"minecraft:beef\",Count:1b}}");
        }, () -> compter(est(Items.BEEF)) == 2 && piles(est(Items.BEEF)) == 1 && date(premiere(est(Items.BEEF))),
                () -> "boeufs : " + compter(est(Items.BEEF)) + " en " + piles(est(Items.BEEF)) + " pile(s), tag " + premiere(est(Items.BEEF)).getTag(),
                () -> {
                    dateBoeuf = FoodStamp.stamp(premiere(est(Items.BEEF)));
                    return "deux boeufs ramasses : une pile de 2, datee (" + dateBoeuf + " ticks, soit un multiple de "
                            + FoodDecay.BUCKET_TICKS + " : " + (dateBoeuf % FoodDecay.BUCKET_TICKS == 0) + "), tag " + premiere(est(Items.BEEF)).getTag();
                });
    }

    private static void infobulles() {
        pas("Deux steaks cuits dont la date est posée par commande : frais et vieilli", () -> {
            long maintenant = mc.level.getGameTime();
            long frais = FoodDecay.roundDown(maintenant);
            long rassis = frais - FoodConfig.freshTicks() - FoodDecay.BUCKET_TICKS;
            commande("item replace entity " + nom() + " hotbar." + H_FRAIS + " with " + nbtItem("minecraft:cooked_beef", frais) + " 1");
            commande("item replace entity " + nom() + " hotbar." + H_RASSIS + " with " + nbtItem("minecraft:cooked_porkchop", rassis) + " 1");
        }, () -> date(mc.player.getInventory().getItem(H_FRAIS)) && date(mc.player.getInventory().getItem(H_RASSIS)),
                () -> "steaks absents ou sans date", null);
        controle("Infobulles : frais, rassis, et avarié sur une pile construite", () -> {
            long maintenant = mc.level.getGameTime();
            ItemStack avarie = datee(Items.COOKED_BEEF, 1, maintenant - FoodConfig.freshTicks() - FoodConfig.staleTicks() - FoodDecay.BUCKET_TICKS);
            ItemStack frais = mc.player.getInventory().getItem(H_FRAIS);
            ItemStack rassis = mc.player.getInventory().getItem(H_RASSIS);
            int bf = traitsPleins(frais);
            int br = traitsPleins(rassis);
            return "fresh".equals(infobulle(frais)) && "stale".equals(infobulle(rassis)) && "spoiled".equals(infobulle(avarie))
                    && bf >= 19 && br >= 8 && br <= 12 && "gold".equals(couleurBarre(rassis)) && traitsPleins(avarie) == 0
                    && "green".equals(couleurBarre(frais));
        }, () -> {
            long maintenant = mc.level.getGameTime();
            ItemStack avarie = datee(Items.COOKED_BEEF, 1, maintenant - FoodConfig.freshTicks() - FoodConfig.staleTicks() - FoodDecay.BUCKET_TICKS);
            ItemStack frais = mc.player.getInventory().getItem(H_FRAIS);
            ItemStack rassis = mc.player.getInventory().getItem(H_RASSIS);
            return "infobulles : steak frais -> " + infobulle(frais) + " barre " + traitsPleins(frais) + "/20 " + couleurBarre(frais)
                    + " ; cotelette vieillie -> " + infobulle(rassis) + " barre " + traitsPleins(rassis) + "/20 " + couleurBarre(rassis)
                    + " ; pile avariee construite -> " + infobulle(avarie) + " barre " + traitsPleins(avarie) + "/20 " + couleurBarre(avarie)
                    + " (cles et lignes lues dans l'infobulle, temps client " + maintenant + ")";
        });
    }

    /** Ouvre l'appétit, retire l'effet, mange la pile de cette case, tenue sur la touche « utiliser », et lit le gain de faim. */
    private static void repas(String nomRepas, int fente, int attendu) {
        pas("Faim avant le repas (" + nomRepas + ")", () -> commande("effect give " + nom() + " minecraft:hunger 4 255 true"),
                () -> mc.player.getFoodData().getFoodLevel() <= 10, () -> "faim : " + mc.player.getFoodData().getFoodLevel(), null);
        pasD(10, "Effet de faim retiré (" + nomRepas + ")", () -> commande("effect clear " + nom() + " minecraft:hunger"),
                () -> ecoule() >= 1.5, () -> "", () -> "faim avant le repas : " + mc.player.getFoodData().getFoodLevel());
        pas("Repas : " + nomRepas + ", touche utiliser tenue", () -> {
            faimAvant = mc.player.getFoodData().getFoodLevel();
            tenir(fente);
            mc.options.keyUse.setDown(true);
        }, () -> mc.player.getInventory().getItem(fente).isEmpty() && ecoule() >= 1.0,
                () -> "case " + fente + " : " + mc.player.getInventory().getItem(fente) + ", utilisation " + mc.player.isUsingItem(), null);
        pas("Repas : " + nomRepas + ", lecture de la faim", () -> mc.options.keyUse.setDown(false), () -> ecoule() >= 1.0
                && Math.abs((mc.player.getFoodData().getFoodLevel() - faimAvant) - attendu) <= 1,
                () -> "faim " + faimAvant + " -> " + mc.player.getFoodData().getFoodLevel() + " (gain attendu " + attendu + ") ; inventaire "
                        + inventaire(),
                () -> "repas " + nomRepas + " : faim " + faimAvant + " -> " + mc.player.getFoodData().getFoodLevel() + ", gain "
                        + (mc.player.getFoodData().getFoodLevel() - faimAvant) + " (attendu " + attendu + " : steak cuit = 8)");
    }

    private static void manger() {
        repas("frais", H_FRAIS, 8);
        repas("rassis, moitié moins", H_RASSIS, 4);
    }

    private static void avarie() {
        pas("Sonde : un cabillaud sans date", () -> commande("item replace entity " + nom() + " inventory.0 with minecraft:cod 1"),
                () -> date(premiere(est(Items.COD))), () -> "cabillaud sans date", () -> {
                    refBalayage = tic;
                    return "le serveur date le cabillaud de la sonde au balayage d'une seconde ; reference de phase prise";
                });
        pas("Sonde retirée", () -> commande("clear " + nom() + " minecraft:cod"), () -> compter(est(Items.COD)) == 0,
                () -> "cabillaud encore la", null);
        pas("Attente de la phase juste après un balayage", () -> tenir(H_FRAIS), () -> (tic - refBalayage) % 20 == 3,
                () -> "phase " + ((tic - refBalayage) % 20), null);
        pas("Un steak avarié arrive en main", () -> {
            long maintenant = mc.level.getGameTime();
            long date = maintenant - FoodConfig.freshTicks() - FoodConfig.staleTicks() - 2 * FoodDecay.BUCKET_TICKS;
            commande("item replace entity " + nom() + " hotbar." + H_FRAIS + " with " + nbtItem("minecraft:cooked_beef", date) + " 1");
        }, () -> mc.player.getInventory().getItem(H_FRAIS).is(Items.COOKED_BEEF) && date(mc.player.getInventory().getItem(H_FRAIS)),
                () -> "main : " + mc.player.getInventory().getItem(H_FRAIS), null);
        pas("Clic droit sur l'avarié, touche tenue trois secondes", () -> {
            faimAvant = mc.player.getFoodData().getFoodLevel();
            utilisation = false;
            long phase = (tic - refBalayage) % 20;
            ItemStack main = mc.player.getMainHandItem();
            LOG.info("[e2e] avarie : main {} a la phase {} / 20 du balayage, infobulle {}", main, phase, infobulle(main));
            var res = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            LOG.info("[e2e] avarie : useItem -> {}, utilisation en cours {}", res, mc.player.isUsingItem());
            mc.options.keyUse.setDown(true);
            // Le balayage n'a pas pu passer avant la requete : la phase est tout juste apres le balayage + au plus quelques ticks.
            phaseUtilisation = (int) phase;
        }, () -> {
            if (mc.player.isUsingItem()) {
                utilisation = true;
            }
            return ecoule() >= 3.0;
        }, () -> "", () -> "");
        pas("L'avarié est refusé : rien mangé, la pile n'est pas consommée", () -> mc.options.keyUse.setDown(false), () -> {
            ItemStack s = mc.player.getInventory().getItem(H_FRAIS);
            return !utilisation && mc.player.getFoodData().getFoodLevel() == faimAvant && s.getCount() == 1 && ecoule() >= 1.0
                    && (s.is(FoodRegistry.SPOILED_FOOD.get()) || s.is(Items.COOKED_BEEF));
        }, () -> "utilisation vue " + utilisation + ", faim " + faimAvant + " -> " + mc.player.getFoodData().getFoodLevel() + ", case : "
                + mc.player.getInventory().getItem(H_FRAIS),
                () -> "useItem d'un steak avarie tenu 3 s sur un vrai client (a la phase " + phaseUtilisation + " du balayage, donc avant lui) : jamais en train de manger, faim "
                        + faimAvant + " -> " + mc.player.getFoodData().getFoodLevel() + ", case apres : "
                        + mc.player.getInventory().getItem(H_FRAIS) + " (le dechet remplace l'avarie, jamais d'aliment gratuit)");
        pas("Le déchet a remplacé l'avarié dans l'inventaire", () -> {},
                () -> mc.player.getInventory().getItem(H_FRAIS).is(FoodRegistry.SPOILED_FOOD.get())
                        && mc.player.getInventory().getItem(H_FRAIS).getCount() == 1,
                () -> "case : " + mc.player.getInventory().getItem(H_FRAIS),
                () -> "l'avarie est devenu un dechet dans l'inventaire : " + mc.player.getInventory().getItem(H_FRAIS));
        pas("Rangement du déchet", () -> commande("clear " + nom() + " " + idDe(new ItemStack(FoodRegistry.SPOILED_FOOD.get()))),
                () -> compter(est(FoodRegistry.SPOILED_FOOD.get())) == 0, () -> "dechet encore la", null);
    }

    private static void dessalinisateur() {
        ouvrir("Retour au dessalinisateur", () -> desal, DesalinatorMenu.class);
        pasD(120, "Le dessalinisateur a fini : eau potable, sel et bouteilles pleines", () -> {},
                () -> ouvert(DesalinatorMenu.class) && ((DesalinatorMenu) menu()).potableAmount() > 0
                        && menu().getSlot(0).getItem().is(FoodRegistry.SALT.get())
                        && menu().getSlot(2).getItem().is(FoodRegistry.POTABLE_WATER_BOTTLE.get()),
                () -> {
                    DesalinatorMenu m = (DesalinatorMenu) menu();
                    return "eau " + m.waterAmount() + " mB, potable " + m.potableAmount() + " mB, progression " + m.progress() + "/" + m.cycleLength()
                            + ", sel " + m.getSlot(0).getItem() + ", pleines " + m.getSlot(2).getItem() + ", vides " + m.getSlot(1).getItem()
                            + ", etat " + etat(desal);
                }, () -> {
                    DesalinatorMenu m = (DesalinatorMenu) menu();
                    return "dessalinisateur sur zone /coast, courant d'une source Create : eau a traiter " + m.waterAmount() + " mB, eau potable "
                            + m.potableAmount() + " mB, sel " + m.getSlot(0).getItem().getCount() + ", bouteilles pleines "
                            + m.getSlot(2).getItem().getCount() + " (3 vides au depart, " + m.getSlot(1).getItem().getCount() + " restantes), etat du bloc " + etat(desal);
                });
        pas("Le sel et les bouteilles pleines sont repris (clics majuscule)", () -> {
            deplacer(0);
            deplacer(2);
        }, () -> compter(est(FoodRegistry.SALT.get())) >= 1 && compter(est(FoodRegistry.POTABLE_WATER_BOTTLE.get())) >= 1,
                () -> "sel " + compter(est(FoodRegistry.SALT.get())) + ", bouteilles " + compter(est(FoodRegistry.POTABLE_WATER_BOTTLE.get())),
                () -> "dans l'inventaire : " + compter(est(FoodRegistry.SALT.get())) + " sel, " + compter(est(FoodRegistry.POTABLE_WATER_BOTTLE.get()))
                        + " bouteille(s) d'eau potable");
        fermer("Fermeture du dessalinisateur");
    }

    private static void sechoirDepart() {
        pas("Un cabillaud pour le séchoir", () -> commande("give " + nom() + " minecraft:cod 1"), () -> compter(est(Items.COD)) >= 1,
                () -> "cabillaud absent", null);
        ouvrir("Ouverture du séchoir", () -> sechoir, DryerMenu.class);
        pas("Cabillaud et sel dans le séchoir (clics majuscule)", () -> {
            deplacer(fenteMenu(est(Items.COD), true));
            deplacer(fenteMenu(est(FoodRegistry.SALT.get()), true));
        }, () -> menu().getSlot(0).getItem().is(Items.COD) && menu().getSlot(1).getItem().is(FoodRegistry.SALT.get()),
                () -> "entree " + menu().getSlot(0).getItem() + ", sel " + menu().getSlot(1).getItem(),
                () -> "dans le sechoir : " + menu().getSlot(0).getItem() + " et " + menu().getSlot(1).getItem() + " (sel du dessalinisateur)");
        pas("Le séchoir se met en marche", () -> {}, () -> etat(sechoir) == MachineState.RUNNING && ((DryerMenu) menu()).progress() > 0,
                () -> "etat " + etat(sechoir) + ", progression " + ((DryerMenu) menu()).progress(),
                () -> "sechoir : etat RUNNING, progression " + ((DryerMenu) menu()).progress() + "/" + ((DryerMenu) menu()).total());
        fermer("Fermeture du séchoir");
    }

    private static void marmite() {
        pas("Carotte, pomme de terre et bol", () -> {
            commande("give " + nom() + " minecraft:carrot 1");
            commande("give " + nom() + " minecraft:potato 1");
            commande("give " + nom() + " minecraft:bowl 1");
        }, () -> compter(est(Items.CARROT)) >= 1 && compter(est(Items.POTATO)) >= 1 && compter(est(Items.BOWL)) >= 1,
                () -> "ingredients absents", null);
        ouvrir("Ouverture de la marmite de Farmer's Delight", () -> marmite, CookingPotMenu.class);
        pas("Les boeufs datés, la carotte, la pomme de terre et le bol entrent dans la marmite", () -> {
            deplacer(fenteMenu(est(Items.BEEF), true));
            deplacer(fenteMenu(est(Items.CARROT), true));
            deplacer(fenteMenu(est(Items.POTATO), true));
            deplacer(fenteMenu(est(Items.BOWL), true));
        }, () -> {
            for (int i = 0; i < 6; i++) {
                ItemStack s = menu().getSlot(i).getItem();
                if (s.is(Items.BEEF) && date(s)) {
                    return true;
                }
            }
            return false;
        }, () -> "cases de la marmite : " + menu().slots.subList(0, 8).stream().map(s -> s.getItem().toString()).toList(),
                () -> {
                    CookingPotMenu m = (CookingPotMenu) menu();
                    return "la marmite accepte un boeuf date comme ingredient : " + m.getSlot(fenteMenuMarmite()).getItem() + " (tag " + m.getSlot(fenteMenuMarmite()).getItem().getTag()
                            + "), chauffee : " + m.isHeated();
                });
        pasD(40, "Le ragoût cuit : la recette accepte l'ingrédient daté", () -> {},
                () -> menu().getSlot(CookingPotMenu.INDEX_OUTPUT).getItem().is(objet("farmersdelight:beef_stew")),
                () -> "cases " + menu().slots.subList(0, 9).stream().map(s -> s.getItem().toString()).toList() + ", chauffee " + ((CookingPotMenu) menu()).isHeated(),
                () -> "ragout de boeuf cuit avec le boeuf date : affichage " + menu().getSlot(CookingPotMenu.INDEX_MEAL).getItem() + ", sortie "
                        + menu().getSlot(CookingPotMenu.INDEX_OUTPUT).getItem());
        pas("Le ragoût est repris dans l'inventaire", () -> deplacer(CookingPotMenu.INDEX_OUTPUT),
                () -> compter(est(objet("farmersdelight:beef_stew"))) >= 1, () -> "ragout absent de l'inventaire", null);
        fermer("Fermeture de la marmite");
        pasD(10, "Le plat reçoit sa date à son tour", () -> {}, () -> date(premiere(est(objet("farmersdelight:beef_stew")))),
                () -> "ragout sans date : " + premiere(est(objet("farmersdelight:beef_stew"))).getTag(), () -> {
                    dateRagout = FoodStamp.stamp(premiere(est(objet("farmersdelight:beef_stew"))));
                    return "le ragout (plat de Farmer's Delight) est date dans l'inventaire : " + dateRagout + ", infobulle "
                            + infobulle(premiere(est(objet("farmersdelight:beef_stew"))));
                });
    }

    /** Une case d'ingrédient de la marmite qui contient du boeuf. */
    private static int fenteMenuMarmite() {
        for (int i = 0; i < 6; i++) {
            if (menu().getSlot(i).getItem().is(Items.BEEF)) {
                return i;
            }
        }
        return 0;
    }

    private static void armoire() {
        pas("Steaks et poulet datés pour l'armoire", () -> {
            long maintenant = mc.level.getGameTime();
            long s1 = FoodDecay.roundDown(maintenant) - 2 * FoodDecay.BUCKET_TICKS;
            long s2 = FoodDecay.roundDown(maintenant) - 6 * FoodDecay.BUCKET_TICKS;
            commande("give " + nom() + " " + nbtItem("minecraft:cooked_beef", s1) + " 4");
            commande("give " + nom() + " " + nbtItem("minecraft:cooked_chicken", s2) + " 2");
        }, () -> compter(est(Items.COOKED_BEEF)) == 4 && compter(est(Items.COOKED_CHICKEN)) == 2,
                () -> "piles : " + compter(est(Items.COOKED_BEEF)) + " steaks, " + compter(est(Items.COOKED_CHICKEN)) + " poulets", null);
        ouvrir("Ouverture de l'armoire", () -> armoire, FridgeMenu.class);
        pas("Les deux piles entrent dans l'armoire (clics majuscule)", () -> {
            deplacer(fenteMenu(est(Items.COOKED_BEEF), true));
            deplacer(fenteMenu(est(Items.COOKED_CHICKEN), true));
        }, () -> menu().getSlot(0).getItem().is(Items.COOKED_BEEF) && menu().getSlot(1).getItem().is(Items.COOKED_CHICKEN),
                () -> "armoire : " + menu().getSlot(0).getItem() + " / " + menu().getSlot(1).getItem(), null);
        pas("L'armoire est alimentée : elle travaille", () -> {}, () -> etat(armoire) == MachineState.RUNNING && ecoule() >= 3.0,
                () -> "etat de l'armoire " + etat(armoire), () -> {
                    CONTENU_ARMOIRE.clear();
                    CONTENU_ARMOIRE.addAll(contenuArmoire());
                    return "armoire alimentee par une source Create, avec des aliments : etat RUNNING ; contenu " + CONTENU_ARMOIRE
                            + " ; energie " + ((FridgeMenu) menu()).energy() + "/" + ((FridgeMenu) menu()).capacity()
                            + " ; barre du steak de l'armoire " + traitsPleins(menu().getSlot(0).getItem()) + "/20 " + couleurBarre(menu().getSlot(0).getItem());
                });
        pas("Courant coupé : source retirée, réserve vidée", () -> {
            commande("setblock " + xyz(armoire.east()) + " minecraft:air");
            commande("data merge block " + xyz(armoire) + " {energy:0}");
        }, () -> etat(armoire) == MachineState.NO_POWER, () -> "etat de l'armoire " + etat(armoire),
                () -> "source retiree et reserve videe : l'etat du bloc, lu par le client, est NO_POWER");
        pas("Aucune perte immédiate", () -> {}, () -> contenuArmoire().equals(CONTENU_ARMOIRE),
                () -> "avant " + CONTENU_ARMOIRE + " ; apres " + contenuArmoire(),
                () -> "juste apres la coupure, meme contenu, memes dates : " + contenuArmoire());
        pas("Cinq secondes sans courant", () -> {}, () -> ecoule() >= 5.0 && contenuArmoire().equals(CONTENU_ARMOIRE) && etat(armoire) == MachineState.NO_POWER,
                () -> "avant " + CONTENU_ARMOIRE + " ; apres " + contenuArmoire() + " ; etat " + etat(armoire),
                () -> "5 s sans courant (armoire ouverte, observee par le serveur toutes les 5 s) : aucun aliment perdu, aucune date changee");
        fermer("Fermeture de l'armoire");
    }

    /** Les piles de l'armoire ouverte : objet, nombre et date. */
    private static List<String> contenuArmoire() {
        List<String> r = new ArrayList<>();
        if (!(menu() instanceof FridgeMenu)) {
            return r;
        }
        for (int i = 0; i < 27; i++) {
            ItemStack s = menu().getSlot(i).getItem();
            if (!s.isEmpty()) {
                r.add(i + ":" + idDe(s) + "x" + s.getCount() + "@" + (FoodStamp.isStamped(s) ? FoodStamp.stamp(s) : "sans-date"));
            }
        }
        return r;
    }

    private static void sechoirFin() {
        ouvrir("Retour au séchoir", () -> sechoir, DryerMenu.class);
        pasD(90, "Le séchoir a fini : ration salée", () -> {},
                () -> ouvert(DryerMenu.class) && menu().getSlot(2).getItem().is(FoodRegistry.SALTED_FISH.get()),
                () -> "sortie " + menu().getSlot(2).getItem() + ", progression " + ((DryerMenu) menu()).progress() + "/" + ((DryerMenu) menu()).total()
                        + ", etat " + etat(sechoir),
                () -> "sechoir : sortie " + menu().getSlot(2).getItem() + " ; entree " + menu().getSlot(0).getItem() + ", sel " + menu().getSlot(1).getItem());
        pas("La ration salée est reprise : jamais datée, ne périme pas", () -> deplacer(2),
                () -> compter(est(FoodRegistry.SALTED_FISH.get())) >= 1,
                () -> "ration absente", () -> {
                    ItemStack r = premiere(est(FoodRegistry.SALTED_FISH.get()));
                    return "ration salee dans l'inventaire : " + r + ", tag " + r.getTag() + ", infobulle " + infobulle(r)
                            + " (aucune ligne d'etat : elle ne perime jamais)";
                });
        controle("La ration salée n'a ni date ni ligne d'état", () -> {
            ItemStack r = premiere(est(FoodRegistry.SALTED_FISH.get()));
            return !date(r) && infobulle(r) == null;
        }, () -> "ration " + premiere(est(FoodRegistry.SALTED_FISH.get())) + " tag " + premiere(est(FoodRegistry.SALTED_FISH.get())).getTag());
        fermer("Fermeture du séchoir");
    }

    private static Map<String, String> aEcrire() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("armoire", xyz(armoire));
        m.put("desal", xyz(desal));
        m.put("sechoir", xyz(sechoir));
        m.put("marmite", xyz(marmite));
        m.put("origine", xyz(o));
        m.put("ragout", String.valueOf(dateRagout));
        m.put("temps", String.valueOf(mc.level.getGameTime()));
        m.put("contenu", String.join(";", CONTENU_ARMOIRE));
        return m;
    }

    private static void persistance() {
        pasC("Positions et dates écrites pour la relance", () -> {
            tempsAvantArret = mc.level.getGameTime();
            try {
                List<String> lignes = new ArrayList<>();
                aEcrire().forEach((k, v) -> lignes.add(k + "=" + v));
                Files.write(fichier(), lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, () -> true, () -> "", () -> "a retrouver apres relance : armoire " + armoire.toShortString() + " (courant coupe) avec " + CONTENU_ARMOIRE
                + ", ragout date de " + dateRagout + ", ration salee, temps du monde " + tempsAvantArret);
    }

    private static Path fichier() {
        return mc.gameDirectory.toPath().resolve(FICHIER);
    }

    /** Avant le {@code /stop} : le monde du serveur retrouve ses règles. Appelé aussi après un échec. */
    static void rangerAvantArret(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        mc = minecraft;
        minecraft.options.keyUse.setDown(false);
        commande("difficulty peaceful");
        commande("gamerule doMobSpawning true");
        commande("gamerule naturalRegeneration true");
    }

    // ================================================================================================
    // Après un arrêt et une relance du serveur
    // ================================================================================================

    private static Map<String, String> lu = new LinkedHashMap<>();

    private static BlockPos position(String cle) {
        String[] t = lu.get(cle).trim().split(" ");
        return new BlockPos(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]));
    }

    static void relance() {
        pasC("Relecture des positions et des dates", () -> {
            try {
                for (String l : Files.readAllLines(fichier(), StandardCharsets.UTF_8)) {
                    int i = l.indexOf('=');
                    lu.put(l.substring(0, i), l.substring(i + 1));
                }
            } catch (IOException e) {
                throw new IllegalStateException("premiere phase absente : " + fichier(), e);
            }
            armoire = position("armoire");
            desal = position("desal");
            sechoir = position("sechoir");
            marmite = position("marmite");
            o = position("origine");
            dateRagout = Long.parseLong(lu.get("ragout"));
            CONTENU_ARMOIRE.clear();
            if (!lu.get("contenu").isEmpty()) {
                CONTENU_ARMOIRE.addAll(Arrays.asList(lu.get("contenu").split(";")));
            }
            commande("tp " + nom() + " " + o.getX() + " " + o.getY() + " " + o.getZ());
            mc.player.setXRot(-90.0F);
        }, () -> mc.level.getBlockState(armoire).is(FoodRegistry.FRIDGE.get()) && mc.level.getBlockState(desal).is(FoodRegistry.DESALINATOR.get())
                && mc.level.getBlockState(sechoir).is(FoodRegistry.DRYER.get()),
                () -> "machines absentes : " + mc.level.getBlockState(armoire) + " " + mc.level.getBlockState(desal) + " " + mc.level.getBlockState(sechoir),
                () -> "relance : armoire, dessalinisateur et sechoir sont la ; temps du monde avant l'arret " + lu.get("temps") + ", apres " + mc.level.getGameTime());
        controle("Relance : l'armoire est toujours sans courant", () -> etat(armoire) == MachineState.NO_POWER,
                () -> "relance : etat de l'armoire " + etat(armoire));
        ouvrir("Relance : ouverture de l'armoire", () -> armoire, FridgeMenu.class);
        controle("Relance : l'armoire a gardé ses piles et leurs dates", () -> contenuArmoire().equals(CONTENU_ARMOIRE),
                () -> "relance : avant l'arret " + CONTENU_ARMOIRE + " ; apres " + contenuArmoire());
        fermer("Relance : fermeture de l'armoire");
        controle("Relance : le ragoût de l'inventaire a gardé sa date", () -> {
            ItemStack r = premiere(est(objet("farmersdelight:beef_stew")));
            return date(r) && FoodStamp.stamp(r) == dateRagout;
        }, () -> "relance : ragout " + premiere(est(objet("farmersdelight:beef_stew"))).getTag() + " (date attendue " + dateRagout + ")");
        controle("Relance : la ration salée est toujours là, sans date", () -> {
            ItemStack r = premiere(est(FoodRegistry.SALTED_FISH.get()));
            return !r.isEmpty() && !date(r);
        }, () -> "relance : ration " + premiere(est(FoodRegistry.SALTED_FISH.get())));
        pas("Relance : le courant revient", () -> commande("setblock " + xyz(armoire.east()) + " createaddition:creative_energy"),
                () -> etat(armoire) == MachineState.RUNNING, () -> "etat de l'armoire " + etat(armoire),
                () -> "relance : source remise, l'armoire repart (etat RUNNING)");
        ouvrir("Relance : l'armoire alimentée, ouverte", () -> armoire, FridgeMenu.class);
        controle("Relance : les piles sont intactes après le retour du courant", () -> contenuArmoire().size() == CONTENU_ARMOIRE.size()
                && contenuArmoire().stream().map(E2eVivres::sansDate).toList().equals(CONTENU_ARMOIRE.stream().map(E2eVivres::sansDate).toList()),
                () -> "relance : " + contenuArmoire() + " (avant " + CONTENU_ARMOIRE + ")");
        fermer("Relance : fermeture de l'armoire");
        // Rangement : l'essai suivant repart d'un monde propre.
        pas("Rangement des machines et de la zone", () -> {
            vider();
            commande("coast zone remove " + ZONE);
            for (BlockPos p : new BlockPos[] {armoire, desal, sechoir, marmite, armoire.east(), desal.west(), sechoir.east()}) {
                commande("setblock " + xyz(p) + " minecraft:air");
            }
            commande("setblock " + xyz(marmite.below()) + " minecraft:stone");
            commande("difficulty peaceful");
            commande("gamerule doMobSpawning true");
            commande("gamerule naturalRegeneration true");
        }, () -> mc.level.getBlockState(armoire).isAir() && mc.level.getBlockState(desal).isAir() && mc.level.getBlockState(sechoir).isAir()
                && mc.level.getBlockState(marmite).isAir(), () -> "blocs encore la", () -> "machines retirees, zone supprimee, regles du monde rendues");
    }

    /** La pile sans sa date : après le retour du courant, les dates peuvent avancer d'un quart d'heure. */
    private static String sansDate(String pile) {
        int i = pile.indexOf('@');
        return i < 0 ? pile : pile.substring(0, i);
    }
}
