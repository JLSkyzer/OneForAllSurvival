package dev.oneforallsurvival.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.loot.MobCoinTables;
import dev.oneforallsurvival.economy.loot.MobOrigin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le côté serveur de l'essai de bout en bout du butin des mobs (développement seulement, jamais empaqueté). Des commandes
 * réservées à la console (niveau 4, donc au RCON : le joueur de l'essai n'est pas opérateur pendant ses gestes), sur le
 * modèle d'{@link E2eTresorServeur} :
 *
 * <ul>
 *   <li>{@code e2ebutin certain on|off} : la table « certaine » des GameTests ({@link ButinGameTests#tableCertaine}, un
 *       zombie donne 3 cuivres à coup sûr) par {@link MobCoinTables#setForTest} ; l'override ne survit pas à un arrêt ;
 *   <li>{@code e2ebutin apparaitre <type> <pos> <apparition> [garde]} : un mob par {@link EntityType#spawn}, qui passe par
 *       le même événement d'apparition que le jeu ({@code MobSpawnEvent.FinalizeSpawn}) ; un zombie est rendu adulte, sans
 *       monture ni équipement (temps de l'essai) ; {@code garde} : {@code setPersistenceRequired} ;
 *   <li>{@code e2ebutin figer <de> <a>} : les monstres de la boîte sans IA (le joueur de l'essai est en survie) ;
 *   <li>{@code e2ebutin grandir <uuid>} : un animal rendu adulte (ou un parent rendu prêt à un nouvel élevage) ;
 *   <li>{@code e2ebutin mobs <de> <a> | mob <uuid> | sol <de> <a> | origines | registre} : ce que le serveur garde, lu
 *       sans rien changer.
 * </ul>
 *
 * <p>Le type d'apparition de chaque mob est relevé ici à {@code FinalizeSpawn} (mémoire seulement, perdue à l'arrêt) :
 * c'est ce qui permet de dire qu'un animal est apparu à la génération des chunks. Chaque réponse est une suite
 * {@code cle=valeur} (sans espace, ou entre crochets) terminée par {@code FIN_E2E}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class E2eButinServeur {

    private static final String FIN = " FIN_E2E";
    /** Le type d'apparition vu à {@code FinalizeSpawn}, par mob (la génération des chunks tourne hors du fil du serveur). */
    private static final Map<UUID, MobSpawnType> ORIGINES = new ConcurrentHashMap<>();

    private E2eButinServeur() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        ORIGINES.put(event.getEntity().getUUID(), event.getSpawnType());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("e2ebutin").requires(s -> s.hasPermission(4))
                .then(Commands.literal("certain")
                        .then(Commands.literal("on").executes(ctx -> certain(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> certain(ctx, false))))
                .then(Commands.literal("apparaitre").then(Commands.argument("type", ResourceLocationArgument.id())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("apparition", StringArgumentType.word())
                                        .executes(ctx -> apparaitre(ctx, false))
                                        .then(Commands.literal("garde").executes(ctx -> apparaitre(ctx, true)))))))
                .then(Commands.literal("figer").then(Commands.argument("de", BlockPosArgument.blockPos())
                        .then(Commands.argument("a", BlockPosArgument.blockPos()).executes(E2eButinServeur::figer))))
                .then(Commands.literal("grandir").then(Commands.argument("uuid", StringArgumentType.word())
                        .executes(E2eButinServeur::grandir)))
                .then(Commands.literal("mobs").then(Commands.argument("de", BlockPosArgument.blockPos())
                        .then(Commands.argument("a", BlockPosArgument.blockPos()).executes(E2eButinServeur::mobs))))
                .then(Commands.literal("mob").then(Commands.argument("uuid", StringArgumentType.word())
                        .executes(E2eButinServeur::mob)))
                .then(Commands.literal("sol").then(Commands.argument("de", BlockPosArgument.blockPos())
                        .then(Commands.argument("a", BlockPosArgument.blockPos()).executes(E2eButinServeur::sol))))
                .then(Commands.literal("origines").executes(E2eButinServeur::origines))
                .then(Commands.literal("registre").executes(E2eButinServeur::registre)));
    }

    private static int dire(CommandContext<CommandSourceStack> ctx, String texte) {
        ctx.getSource().sendSuccess(() -> Component.literal(texte + FIN), false);
        return 1;
    }

    private static String nettoyer(String texte) {
        return texte.replace(';', ',').replace('[', '(').replace(']', ')').replace('\n', '|');
    }

    private static AABB boite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos de = BlockPosArgument.getLoadedBlockPos(ctx, "de");
        BlockPos a = BlockPosArgument.getLoadedBlockPos(ctx, "a");
        return new AABB(de, a).expandTowards(1, 1, 1);
    }

    private static String origine(Entity e) {
        MobSpawnType t = ORIGINES.get(e.getUUID());
        return t == null ? "aucune" : t.name();
    }

    private static String decrire(Entity e) {
        boolean bebe = e instanceof AgeableMob a && a.isBaby() || e instanceof Zombie z && z.isBaby();
        boolean garde = e instanceof Mob m && m.isPersistenceRequired();
        boolean amour = e instanceof Animal a && a.isInLove();
        return e.getUUID() + ":" + EntityType.getKey(e.getType()).getPath() + ":" + MobOrigin.isWild(e) + ":" + origine(e) + ":"
                + bebe + ":" + garde + ":" + amour + ":" + e.isAlive() + ":" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                e.getX(), e.getY(), e.getZ());
    }

    // --------------------------------------------------------------------------------------------- réglages

    private static int certain(CommandContext<CommandSourceStack> ctx, boolean on) {
        MobCoinTables.setForTest(on ? ButinGameTests.tableCertaine() : null);
        return dire(ctx, "CERTAIN " + (on ? "on" : "off") + " common=" + MobCoinTables.current().tier("common"));
    }

    private static int apparaitre(CommandContext<CommandSourceStack> ctx, boolean garde) throws CommandSyntaxException {
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        EntityType<?> type = EntityType.byString(ResourceLocationArgument.getId(ctx, "type").toString()).orElse(null);
        if (type == null) {
            return dire(ctx, "REFUS type inconnu");
        }
        MobSpawnType apparition = MobSpawnType.valueOf(StringArgumentType.getString(ctx, "apparition"));
        Entity e = type.spawn(monde, pos, apparition);
        if (e == null) {
            return dire(ctx, "REFUS rien n'est apparu");
        }
        if (e instanceof Zombie zombie) {
            zombie.setBaby(false);
            if (zombie.getVehicle() != null) {
                zombie.getVehicle().discard();
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                zombie.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        if (e instanceof AgeableMob a && a.isBaby()) {
            a.setAge(0);
        }
        if (garde && e instanceof Mob m) {
            m.setPersistenceRequired();
        }
        return dire(ctx, "MOB m=" + decrire(e));
    }

    private static int figer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int n = 0;
        for (Mob m : ctx.getSource().getLevel().getEntitiesOfClass(Mob.class, boite(ctx), m -> m instanceof Enemy)) {
            m.setNoAi(true);
            n++;
        }
        return dire(ctx, "FIGER n=" + n);
    }

    private static Entity trouver(MinecraftServer serveur, String uuid) {
        UUID id = UUID.fromString(uuid);
        for (ServerLevel l : serveur.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private static int grandir(CommandContext<CommandSourceStack> ctx) {
        Entity e = trouver(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "uuid"));
        if (!(e instanceof AgeableMob a)) {
            return dire(ctx, "ABSENT");
        }
        a.setAge(0);
        return dire(ctx, "GRANDI " + decrire(e));
    }

    // --------------------------------------------------------------------------------------------- lectures

    private static int mobs(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        StringBuilder sb = new StringBuilder();
        for (Mob m : ctx.getSource().getLevel().getEntitiesOfClass(Mob.class, boite(ctx), m -> true)) {
            sb.append(decrire(m)).append(';');
        }
        return dire(ctx, "MOBS liste=[" + sb + "]");
    }

    private static int mob(CommandContext<CommandSourceStack> ctx) {
        Entity e = trouver(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "uuid"));
        return dire(ctx, e == null ? "MOB absent=true" : "MOB absent=false m=" + decrire(e));
    }

    private static int sol(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        long valeur = 0;
        int n = 0;
        StringBuilder pos = new StringBuilder();
        for (CoinDropEntity c : ctx.getSource().getLevel().getEntities(EconomyRegistry.coinDropType(), boite(ctx), c -> true)) {
            valeur += c.value();
            n++;
            pos.append(String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", c.getX(), c.getY(), c.getZ())).append(';');
        }
        return dire(ctx, "SOL n=" + n + " valeur=" + valeur + " positions=[" + pos + "]");
    }

    /** Les animaux chargés : combien sont marqués sauvages, combien sont apparus à la génération des chunks (et marqués). */
    private static int origines(CommandContext<CommandSourceStack> ctx) {
        int animaux = 0;
        int sauvages = 0;
        int generation = 0;
        int generationMarques = 0;
        String exemple = "aucun";
        for (ServerLevel l : ctx.getSource().getServer().getAllLevels()) {
            for (Entity e : l.getAllEntities()) {
                if (!(e instanceof Animal)) {
                    continue;
                }
                animaux++;
                boolean marque = MobOrigin.isWild(e);
                if (marque) {
                    sauvages++;
                }
                if (ORIGINES.get(e.getUUID()) == MobSpawnType.CHUNK_GENERATION) {
                    generation++;
                    if (marque) {
                        generationMarques++;
                        if ("aucun".equals(exemple)) {
                            exemple = EntityType.getKey(e.getType()).getPath() + "@" + e.blockPosition().toShortString().replace(" ", "");
                        }
                    }
                }
            }
        }
        return dire(ctx, "ORIGINES animaux=" + animaux + " sauvages=" + sauvages + " generation=" + generation
                + " generationMarques=" + generationMarques + " exemple=" + exemple + " releves=" + ORIGINES.size());
    }

    /**
     * Le butin du registre : total, morts payantes, part de Dev, et les lignes telles que {@code /eco stats} et
     * {@code /eco loot} les écrivent (même composant, même langue que la console).
     */
    private static int registre(CommandContext<CommandSourceStack> ctx) {
        EconomyLedger ledger = EconomyLedger.get(ctx.getSource().getServer());
        long total = ledger.totalCreated(MoneySource.MOB_LOOT);
        long dev = ledger.lootByPlayer().getOrDefault(E2eTresorServeur.uuidDe("Dev"), 0L);
        String ligne = Component.translatable("command.oneforallsurvival.eco.stats.line",
                Component.translatable(MoneySource.MOB_LOOT.langKey()), CoinMath.format(total)).getString();
        String ligneDev = Component.translatable("command.oneforallsurvival.eco.stats.line", "Dev", CoinMath.format(dev)).getString();
        return dire(ctx, "REGISTRE total=" + total + " morts=" + ledger.lootKills() + " dev=" + dev + " ligne=[" + nettoyer(ligne)
                + "] ligneDev=[" + nettoyer(ligneDev) + "]");
    }
}
