package dev.oneforallsurvival.economy.loot;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Le butin des mobs : des pièces ajoutées aux objets lâchés à la mort d'un mob tué par un vrai joueur, selon la table
 * ({@link MobCoinTables}) et l'origine du mob ({@link MobOrigin}). Les pièces sont comptées à la source
 * {@link dev.oneforallsurvival.economy.MoneySource#MOB_LOOT} dans le même appel qui les ajoute aux objets lâchés ;
 * chacune devient une pièce au sol ({@code CoinDropEntity}) en entrant dans le monde.
 *
 * <p>Écouté en {@code LivingDropsEvent}, priorité {@code LOWEST}, sans les événements annulés : une mort dont les
 * objets sont annulés par un autre écouteur ne crée ni ne compte rien.
 *
 * <p>Bornes (décisions de Killian, 2026-11-15) : un raid paie au plus {@link EconomyConfig#raidLootCap()} cuivres tous
 * joueurs confondus, et un joueur touche le butin d'un même type de boss (palier qui ignore l'origine) au plus
 * {@link EconomyConfig#bossLootPerDay()} fois par jour de paie ({@link EconomyCalendar}). Les deux sont gardées dans
 * {@link MobLootLimits}, avec le monde.
 */
public final class MobLoot {

    /** Le palier des animaux sauvages : rien pour un petit. */
    public static final String WILD_ANIMAL = "wild_animal";

    /**
     * Un butin décidé. {@code raid} : le raid qui le paie (plafond), ou {@code null} ; {@code bossType} : le type de
     * boss compté pour {@code day}, ou {@code null}.
     */
    public record Award(ServerPlayer player, String tierId, EnumMap<Coin, Long> coins, long value,
            @Nullable String raid, @Nullable String bossType, LocalDate day) {}

    /** Les comptes où écrire le butin d'un joueur d'essai, au lieu de ceux du serveur. */
    private record Books(EconomyLedger ledger, MobLootLimits limits) {}

    private static final Map<UUID, Books> TEST_BOOKS = new ConcurrentHashMap<>();

    private MobLoot() {}

    /**
     * Essais seulement : le butin crédité à ce joueur s'écrit dans ces comptes détachés (rendus par
     * {@link #clearBooksForTest}). Par joueur, pour que les essais d'un même lot ne se mélangent pas.
     */
    public static void setBooksForTest(UUID player, EconomyLedger ledger, MobLootLimits limits) {
        TEST_BOOKS.put(player, new Books(ledger, limits));
    }

    public static void clearBooksForTest(UUID player) {
        TEST_BOOKS.remove(player);
    }

    /**
     * Le joueur crédité de la mort, ou {@code null} : la source du dégât si c'est un vrai joueur (coup, flèche,
     * trident, TNT allumée : {@code getEntity()}) ou le maître d'un animal apprivoisé qui a tué ; sinon, si le mob a
     * été frappé par un joueur dans les 5 dernières secondes ({@code recentlyHit}), ce joueur
     * ({@code getKillCredit}). Un faux joueur (déployeur de Create compris) ou un spectateur n'est jamais crédité, et
     * un faux joueur qui porte le dernier coup ne laisse le crédit à personne.
     */
    @Nullable
    public static ServerPlayer creditedPlayer(LivingEntity victim, DamageSource source, boolean recentlyHit) {
        Entity attacker = source.getEntity();
        if (attacker instanceof FakePlayer) {
            return null;
        }
        if (attacker instanceof ServerPlayer player) {
            return real(player);
        }
        if (attacker instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner) {
            return real(owner);
        }
        if (recentlyHit && victim.getKillCredit() instanceof ServerPlayer player) {
            return real(player);
        }
        return null;
    }

    @Nullable
    private static ServerPlayer real(ServerPlayer player) {
        return player instanceof FakePlayer || player.isSpectator() ? null : player;
    }

    /**
     * Le butin de cette mort, ou rien. Dans l'ordre : vivant non joueur ; {@code doMobLoot} vrai ; joueur crédité ;
     * palier non nul ; origine sauvage sauf palier {@code anyOrigin} ; ni apprivoisé, ni petit au palier
     * {@link #WILD_ANIMAL} ; boss pas encore payé à ce joueur ce jour ; tirage non vide ; plafond du raid (le butin
     * est ramené à ce qui reste, rien s'il ne reste rien). Ne modifie rien : {@link #drop} écrit.
     */
    public static Optional<Award> decide(LivingEntity victim, DamageSource source, boolean recentlyHit,
            MobCoinTable table, RandomSource random, MobLootLimits limits, long nowMillis) {
        if (victim instanceof Player || !(victim.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return Optional.empty();
        }
        ServerPlayer player = creditedPlayer(victim, source, recentlyHit);
        if (player == null) {
            return Optional.empty();
        }
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
        if (typeId == null) {
            return Optional.empty();
        }
        Set<ResourceLocation> tags = victim.getType().builtInRegistryHolder().tags().map(TagKey::location)
                .collect(Collectors.toSet());
        // Le drapeau du creeper chargé ne vaut que pour un vrai creeper.
        boolean charged = victim instanceof Creeper creeper && creeper.isPowered();
        MobCoinTable.Tier tier = table.tierFor(typeId, tags, victim.getType().getCategory(), charged);
        if (tier == null) {
            return Optional.empty();
        }
        if (!tier.anyOrigin() && !MobOrigin.isWild(victim)) {
            return Optional.empty();
        }
        if (isTamed(victim) || (WILD_ANIMAL.equals(tier.id()) && victim.isBaby())) {
            return Optional.empty();
        }
        LocalDate day = EconomyCalendar.payDay(nowMillis, EconomyCalendar.configuredZone(), EconomyConfig.payHour());
        String bossType = null;
        if (tier.anyOrigin()) {
            bossType = typeId.toString();
            if (!limits.bossAllowed(player.getUUID(), bossType, day, EconomyConfig.bossLootPerDay())) {
                return Optional.empty();
            }
        }
        EnumMap<Coin, Long> coins = MobCoinTable.roll(tier, random);
        long value = CoinMath.valueOf(coins);
        if (value <= 0L) {
            return Optional.empty();
        }
        String raid = MobOrigin.raidOf(victim);
        if (raid != null) {
            long remaining = limits.raidRemaining(raid, EconomyConfig.raidLootCap());
            if (remaining <= 0L) {
                return Optional.empty();
            }
            if (value > remaining) {
                coins = CoinMath.decompose(remaining);
                value = remaining;
            }
        }
        return Optional.of(new Award(player, tier.id(), coins, value, raid, bossType, day));
    }

    /** Un animal qui appartient à quelqu'un (chien, chat, cheval, lama...). */
    private static boolean isTamed(LivingEntity victim) {
        if (victim instanceof TamableAnimal tamable && tamable.isTame()) {
            return true;
        }
        if (victim instanceof AbstractHorse horse && horse.isTamed()) {
            return true;
        }
        return victim instanceof OwnableEntity owned && owned.getOwnerUUID() != null;
    }

    /**
     * Ajoute les pièces aux objets lâchés (une {@link ItemEntity} par coupure, à la position du mob, délai de
     * ramassage par défaut) et les compte au même instant : registre ({@code lootCreated}, ligne de journal au-delà
     * de {@link EconomyConfig#mobLootJournalThreshold()}), plafond du raid et compte du boss.
     */
    public static void drop(LivingEntity victim, Award award, Collection<ItemEntity> drops, EconomyLedger ledger,
            MobLootLimits limits) {
        for (Map.Entry<Coin, Long> entry : award.coins().entrySet()) {
            long count = entry.getValue();
            if (count <= 0L) {
                continue;
            }
            ItemEntity item = new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(),
                    new ItemStack(entry.getKey().item(), Math.toIntExact(count)));
            // Le délai par défaut, jamais 32 767 : ce délai-là fait d'une pièce au sol une entité inerte (/give).
            item.setDefaultPickUpDelay();
            drops.add(item);
        }
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
        ledger.lootCreated(award.player().getUUID(), award.tierId(), award.value(), "butin : " + typeId,
                award.value() >= EconomyConfig.mobLootJournalThreshold());
        if (award.raid() != null) {
            limits.addRaid(award.raid(), award.value());
        }
        if (award.bossType() != null) {
            limits.addBoss(award.player().getUUID(), award.bossType(), award.day());
        }
    }

    /** Côté serveur seulement : décide avec la table en service et l'aléatoire du mob, puis lâche et compte. */
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer credited = creditedPlayer(victim, event.getSource(), event.isRecentlyHit());
        Books books = credited == null ? null : TEST_BOOKS.get(credited.getUUID());
        MobLootLimits limits = books != null ? books.limits() : MobLootLimits.get(level.getServer());
        decide(victim, event.getSource(), event.isRecentlyHit(), MobCoinTables.current(), victim.getRandom(), limits,
                EconomyClock.now()).ifPresent(award -> drop(victim, award, event.getDrops(),
                        books != null ? books.ledger() : EconomyLedger.get(level.getServer()), limits));
    }
}
