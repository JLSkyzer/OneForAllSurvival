package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

/**
 * Réglages de l'économie. Fichier SERVER ({@code oneforallsurvival-economy.toml}) : le serveur en décide et
 * Forge le synchronise, pour que l'infobulle suive. Les valeurs validées sont mises en cache au chargement et au
 * rechargement du fichier ; tant qu'il n'est pas chargé (aucun monde, ou JUnit), ce sont les valeurs par défaut.
 *
 * <p>Le cuivre vaut toujours 1 : il n'est pas réglable. Chaque autre pièce est un multiple de celle du dessous.
 */
public final class EconomyConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cuivre, argent, or, émeraude, rubis. */
    static final long[] DEFAULTS = {1L, 10L, 100L, 1_000L, 10_000L};

    public static final ForgeConfigSpec SPEC;

    /** Indexé comme {@link Coin#ordinal()} ; l'entrée du cuivre reste nulle (non réglable). */
    private static final ForgeConfigSpec.LongValue[] VALUES = new ForgeConfigSpec.LongValue[Coin.values().length];

    static final int DEFAULT_LEDGER_MAX_LINES = 10_000;
    static final int DEFAULT_STARTING_MONEY = 500;
    static final double DEFAULT_TRADE_MAX_DISTANCE = 5.0D;
    static final int DEFAULT_TRADE_REQUEST_SECONDS = 30;
    static final int DEFAULT_SALES_TAX_PERCENT = 0;
    static final int DEFAULT_STALL_MAX_OFFERS = 9;
    static final int DEFAULT_STALL_LEDGER_LINES = 200;
    static final double DEFAULT_INVOICE_MAX_DISTANCE = 5.0D;
    static final long DEFAULT_INVOICE_MAX_AMOUNT = 100_000_000L;
    static final int DEFAULT_INVOICE_REASON_MAX = 80;
    static final int DEFAULT_INVOICE_KEEP_DAYS = 90;
    static final int DEFAULT_INVOICE_MAX_DUE_PER_PAIR = 5;
    static final int DEFAULT_INVOICE_MAX_DUE_PER_DEBTOR = 27;
    static final int DEFAULT_INVOICE_ISSUE_COOLDOWN_TICKS = 40;
    static final int DEFAULT_TREASURY_LEDGER_LINES = 1_000;
    static final int DEFAULT_TREASURY_REASON_MAX = 80;
    static final List<String> DEFAULT_SALARY_PER_HOUR = List.of("policier=60");
    static final long DEFAULT_MAYOR_SALARY_PER_HOUR = 0L;
    static final int DEFAULT_SALARY_MAX_HOURS_PER_DAY = 8;
    static final int DEFAULT_PAY_HOUR = 0;
    static final String DEFAULT_PAY_TIME_ZONE = "";
    static final int DEFAULT_PAY_MAX_MISSED_DAYS = 400;
    static final long DEFAULT_MOB_LOOT_JOURNAL_THRESHOLD = 100L;
    static final long DEFAULT_RAID_LOOT_CAP = 100L;
    static final int DEFAULT_BOSS_LOOT_PER_DAY = 1;
    /** Plafond d'un taux de salaire, en cuivres par heure. */
    public static final long MAX_SALARY_PER_HOUR = 1_000_000L;

    private static final ForgeConfigSpec.IntValue LEDGER_MAX_LINES;
    private static final ForgeConfigSpec.IntValue STARTING_MONEY;
    private static final ForgeConfigSpec.DoubleValue TRADE_MAX_DISTANCE;
    private static final ForgeConfigSpec.IntValue TRADE_REQUEST_SECONDS;
    private static final ForgeConfigSpec.IntValue SALES_TAX_PERCENT;
    private static final ForgeConfigSpec.IntValue STALL_MAX_OFFERS;
    private static final ForgeConfigSpec.IntValue STALL_LEDGER_LINES;
    private static final ForgeConfigSpec.DoubleValue INVOICE_MAX_DISTANCE;
    private static final ForgeConfigSpec.LongValue INVOICE_MAX_AMOUNT;
    private static final ForgeConfigSpec.IntValue INVOICE_REASON_MAX;
    private static final ForgeConfigSpec.IntValue INVOICE_KEEP_DAYS;
    private static final ForgeConfigSpec.IntValue INVOICE_MAX_DUE_PER_PAIR;
    private static final ForgeConfigSpec.IntValue INVOICE_MAX_DUE_PER_DEBTOR;
    private static final ForgeConfigSpec.IntValue INVOICE_ISSUE_COOLDOWN_TICKS;
    private static final ForgeConfigSpec.IntValue TREASURY_LEDGER_LINES;
    private static final ForgeConfigSpec.IntValue TREASURY_REASON_MAX;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SALARY_PER_HOUR;
    private static final ForgeConfigSpec.LongValue MAYOR_SALARY_PER_HOUR;
    private static final ForgeConfigSpec.IntValue SALARY_MAX_HOURS_PER_DAY;
    private static final ForgeConfigSpec.IntValue PAY_HOUR;
    private static final ForgeConfigSpec.ConfigValue<String> PAY_TIME_ZONE;
    private static final ForgeConfigSpec.IntValue PAY_MAX_MISSED_DAYS;
    private static final ForgeConfigSpec.LongValue MOB_LOOT_JOURNAL_THRESHOLD;
    private static final ForgeConfigSpec.LongValue RAID_LOOT_CAP;
    private static final ForgeConfigSpec.IntValue BOSS_LOOT_PER_DAY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_ALLOWED_MENUS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_FORBIDDEN_MENUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Valeur de chaque pièce, en cuivres. Le cuivre vaut toujours 1.",
                "Chaque valeur doit être un multiple strict de celle de la pièce du dessous, sinon les valeurs",
                "par défaut (1, 10, 100, 1000, 10000) sont utilisées et un avertissement est écrit au journal.",
                "Le change à la table (1 pièce pour 10 de celle du dessous) n'est actif que si chaque pièce vaut 10 fois",
                "celle du dessous ; sinon ses recettes sont retirées.");
        for (Coin coin : Coin.values()) {
            if (coin != Coin.COPPER) {
                VALUES[coin.ordinal()] = builder.defineInRange(coin.key() + "CoinValue", DEFAULTS[coin.ordinal()],
                        2L, 1_000_000_000L);
            }
        }
        LEDGER_MAX_LINES = builder
                .comment("Lignes du registre gardées ; les plus anciennes sont effacées d'abord, mais les totaux",
                        "par source et par cause ne sont jamais effacés.")
                .defineInRange("ledgerMaxLines", DEFAULT_LEDGER_MAX_LINES, 100, 100_000);
        STARTING_MONEY = builder
                .comment("Somme de départ, en cuivres, donnée à la première connexion de chaque joueur (500 = 5 or).")
                .defineInRange("startingMoney", DEFAULT_STARTING_MONEY, 0, 1_000_000);
        TRADE_MAX_DISTANCE = builder
                .comment("Distance maximale, en blocs, entre deux joueurs qui échangent (proposition et échange).")
                .defineInRange("tradeMaxDistance", DEFAULT_TRADE_MAX_DISTANCE, 1.0D, 32.0D);
        TRADE_REQUEST_SECONDS = builder
                .comment("Durée, en secondes, pendant laquelle une proposition d'échange reste valable.")
                .defineInRange("tradeRequestSeconds", DEFAULT_TRADE_REQUEST_SECONDS, 5, 600);
        SALES_TAX_PERCENT = builder
                .comment("Taxe sur les ventes aux étals, en pour cent du prix (0 = aucune), arrondie vers le bas.",
                        "Elle va au compte du trésor ; un rachat n'est pas taxé.")
                .defineInRange("salesTaxPercent", DEFAULT_SALES_TAX_PERCENT, 0, 100);
        STALL_MAX_OFFERS = builder
                .comment("Offres de vente, et offres de rachat, au plus par étal.")
                .defineInRange("stallMaxOffers", DEFAULT_STALL_MAX_OFFERS, 1, 18);
        STALL_LEDGER_LINES = builder
                .comment("Lignes du registre d'un étal ; les plus anciennes sont effacées d'abord.")
                .defineInRange("stallLedgerLines", DEFAULT_STALL_LEDGER_LINES, 10, 5_000);
        INVOICE_MAX_DISTANCE = builder
                .comment("Distance maximale, en blocs, entre l'émetteur d'une facture et son débiteur.")
                .defineInRange("invoiceMaxDistance", DEFAULT_INVOICE_MAX_DISTANCE, 1.0D, 32.0D);
        INVOICE_MAX_AMOUNT = builder
                .comment("Montant maximal d'une facture, en cuivres.")
                .defineInRange("invoiceMaxAmount", DEFAULT_INVOICE_MAX_AMOUNT, 1L, 1_000_000_000L);
        INVOICE_REASON_MAX = builder
                .comment("Longueur maximale du motif d'une facture, en caractères.")
                .defineInRange("invoiceReasonMax", DEFAULT_INVOICE_REASON_MAX, 1, 200);
        INVOICE_KEEP_DAYS = builder
                .comment("Jours pendant lesquels une facture réglée reste connue du serveur avant d'être oubliée.")
                .defineInRange("invoiceKeepDays", DEFAULT_INVOICE_KEEP_DAYS, 1, 3_650);
        INVOICE_MAX_DUE_PER_PAIR = builder
                .comment("Factures dues au plus d'un même émetteur vers un même débiteur ; au-delà, la suivante est refusée.")
                .defineInRange("invoiceMaxDuePerPair", DEFAULT_INVOICE_MAX_DUE_PER_PAIR, 1, 100);
        INVOICE_MAX_DUE_PER_DEBTOR = builder
                .comment("Factures dues au plus par un même débiteur, tous émetteurs confondus.")
                .defineInRange("invoiceMaxDuePerDebtor", DEFAULT_INVOICE_MAX_DUE_PER_DEBTOR, 1, 1_000);
        INVOICE_ISSUE_COOLDOWN_TICKS = builder
                .comment("Délai, en ticks, entre deux factures émises par un même joueur (40 = 2 secondes).")
                .defineInRange("invoiceIssueCooldownTicks", DEFAULT_INVOICE_ISSUE_COOLDOWN_TICKS, 0, 12_000);
        TREASURY_LEDGER_LINES = builder
                .comment("Lignes du registre du trésor ; les plus anciennes sont effacées d'abord.")
                .defineInRange("treasuryLedgerLines", DEFAULT_TREASURY_LEDGER_LINES, 100, 100_000);
        TREASURY_REASON_MAX = builder
                .comment("Longueur maximale du motif d'une dépense publique, en caractères.")
                .defineInRange("treasuryReasonMax", DEFAULT_TREASURY_REASON_MAX, 1, 200);
        SALARY_PER_HOUR = builder
                .comment("Salaire de chaque rôle, en cuivres par heure passée connecté dans le rôle : \"<rôle>=<taux>\",",
                        "le rôle par son identifiant (ex. \"policier=60\", 60 = 1 cuivre par minute). Un rôle absent n'est",
                        "pas payé (par défaut, seule la police l'est). Une entrée illisible est ignorée avec un avertissement.",
                        "Le maire se règle à part.",
                        "Modifiable en jeu par /eco salary set <rôle> <taux>.")
                .defineListAllowEmpty("salaryPerHour", DEFAULT_SALARY_PER_HOUR, o -> o instanceof String);
        MAYOR_SALARY_PER_HOUR = builder
                .comment("Salaire du maire, en cuivres par heure, en plus de celui de son métier (0 = aucun).")
                .defineInRange("mayorSalaryPerHour", DEFAULT_MAYOR_SALARY_PER_HOUR, 0L, MAX_SALARY_PER_HOUR);
        SALARY_MAX_HOURS_PER_DAY = builder
                .comment("Heures comptées au plus par joueur, par jour et par salaire.")
                .defineInRange("salaryMaxHoursPerDay", DEFAULT_SALARY_MAX_HOURS_PER_DAY, 1, 24);
        PAY_HOUR = builder
                .comment("Heure locale (0 à 23) où le jour de paie se clôt et où les salaires sont réglés (0 = minuit).")
                .defineInRange("payHour", DEFAULT_PAY_HOUR, 0, 23);
        PAY_TIME_ZONE = builder
                .comment("Fuseau horaire de la paie, identifiant IANA (ex. \"Europe/Paris\") ; vide = fuseau du système.",
                        "Un fuseau illisible donne celui du système, avec un avertissement.")
                .define("payTimeZone", DEFAULT_PAY_TIME_ZONE);
        PAY_MAX_MISSED_DAYS = builder
                .comment("Jours de paie manqués (serveur éteint) rattrapés au plus au démarrage ; les plus anciens",
                        "au-delà sont sautés avec un avertissement.")
                .defineInRange("payMaxMissedDays", DEFAULT_PAY_MAX_MISSED_DAYS, 1, 3_650);
        MOB_LOOT_JOURNAL_THRESHOLD = builder
                .comment("Butin des mobs : un butin d'au moins ce montant, en cuivres, a sa ligne au registre (/eco ledger) ;",
                        "en dessous, il ne compte que dans les totaux (/eco loot). 0 = une ligne pour chaque butin.")
                .defineInRange("mobLootJournalThreshold", DEFAULT_MOB_LOOT_JOURNAL_THRESHOLD, 0L, 1_000_000_000L);
        RAID_LOOT_CAP = builder
                .comment("Butin des mobs nés d'un raid : un même raid paie au plus ce montant, en cuivres, tous joueurs",
                        "confondus ; au-delà, ses mobs ne donnent plus rien. 0 = les raids ne paient rien.")
                .defineInRange("raidLootCap", DEFAULT_RAID_LOOT_CAP, 0L, 1_000_000_000L);
        BOSS_LOOT_PER_DAY = builder
                .comment("Butin des boss sans marque sauvage (paliers qui ignorent l'origine du mob : Wither construit, dragon",
                        "ranimé...) : un joueur touche le butin d'un même type de boss au plus ce nombre de fois par jour de paie",
                        "(payHour, payTimeZone). 0 = jamais. Un boss né de lui-même (gardien ancien) paie à chaque fois.")
                .defineInRange("bossLootPerDay", DEFAULT_BOSS_LOOT_PER_DAY, 0, 1_000);
        EXTRA_ALLOWED_MENUS = builder
                .comment("Types d'écran (identifiants, ex. \"mod:coffre\") où une pièce a le droit d'être rangée, en plus",
                        "de l'inventaire du joueur, de la table de fabrication et des rangements de ce mod.")
                .defineListAllowEmpty("extraAllowedMenus", List.of(), EconomyConfig::isResourceLocation);
        EXTRA_FORBIDDEN_MENUS = builder
                .comment("Types d'écran (identifiants) où une pièce est refusée même si l'écran serait permis.",
                        "Les cases de l'inventaire du joueur restent toujours permises.")
                .defineListAllowEmpty("extraForbiddenMenus", List.of(), EconomyConfig::isResourceLocation);
        SPEC = builder.build();
    }

    private static volatile long[] cache = DEFAULTS.clone();
    private static volatile Set<ResourceLocation> allowedCache = Set.of();
    private static volatile Set<ResourceLocation> forbiddenCache = Set.of();

    private EconomyConfig() {}

    /** Valeur de la pièce en cuivres : la configuration si elle est cohérente, sinon la valeur par défaut. */
    public static long coinValue(Coin coin) {
        return cache[coin.ordinal()];
    }

    /** Nombre de lignes du registre gardées (10000 par défaut). */
    public static int ledgerMaxLines() {
        try {
            return LEDGER_MAX_LINES.get();
        } catch (IllegalStateException e) {
            return DEFAULT_LEDGER_MAX_LINES;
        }
    }

    /** Somme de départ, en cuivres (500 par défaut). */
    public static long startingMoney() {
        try {
            return STARTING_MONEY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_STARTING_MONEY;
        }
    }

    /** Distance maximale d'un échange, en blocs (5 par défaut, de 1 à 32). */
    public static double tradeMaxDistance() {
        try {
            return TRADE_MAX_DISTANCE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_TRADE_MAX_DISTANCE;
        }
    }

    /** Validité d'une proposition d'échange, en secondes (30 par défaut). */
    public static int tradeRequestSeconds() {
        try {
            return TRADE_REQUEST_SECONDS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_TRADE_REQUEST_SECONDS;
        }
    }

    /** Taxe sur les ventes, en pour cent (0 par défaut, de 0 à 100). */
    public static int salesTaxPercent() {
        try {
            return SALES_TAX_PERCENT.get();
        } catch (IllegalStateException e) {
            return DEFAULT_SALES_TAX_PERCENT;
        }
    }

    /** Offres de vente (et de rachat) au plus par étal (9 par défaut, de 1 à 18). */
    public static int stallMaxOffers() {
        try {
            return STALL_MAX_OFFERS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_STALL_MAX_OFFERS;
        }
    }

    /** Lignes du registre d'un étal (200 par défaut). */
    public static int stallLedgerLines() {
        try {
            return STALL_LEDGER_LINES.get();
        } catch (IllegalStateException e) {
            return DEFAULT_STALL_LEDGER_LINES;
        }
    }

    /** Distance maximale entre l'émetteur d'une facture et son débiteur, en blocs (5 par défaut). */
    public static double invoiceMaxDistance() {
        try {
            return INVOICE_MAX_DISTANCE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_INVOICE_MAX_DISTANCE;
        }
    }

    /** Montant maximal d'une facture, en cuivres (100 000 000 par défaut). */
    public static long invoiceMaxAmount() {
        try {
            return INVOICE_MAX_AMOUNT.get();
        } catch (IllegalStateException e) {
            return DEFAULT_INVOICE_MAX_AMOUNT;
        }
    }

    /** Longueur maximale du motif d'une facture (80 par défaut). */
    public static int invoiceReasonMax() {
        try {
            return INVOICE_REASON_MAX.get();
        } catch (IllegalStateException e) {
            return DEFAULT_INVOICE_REASON_MAX;
        }
    }

    /** Jours pendant lesquels une facture réglée est gardée (90 par défaut). */
    public static int invoiceKeepDays() {
        try {
            return INVOICE_KEEP_DAYS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_INVOICE_KEEP_DAYS;
        }
    }

    /** Factures dues au plus d'un même émetteur vers un même débiteur (5 par défaut). */
    public static int invoiceMaxDuePerPair() {
        try {
            return INVOICE_MAX_DUE_PER_PAIR.get();
        } catch (IllegalStateException e) {
            return DEFAULT_INVOICE_MAX_DUE_PER_PAIR;
        }
    }

    /** Factures dues au plus par un même débiteur, toutes origines (27 par défaut). */
    public static int invoiceMaxDuePerDebtor() {
        try {
            return INVOICE_MAX_DUE_PER_DEBTOR.get();
        } catch (IllegalStateException e) {
            return DEFAULT_INVOICE_MAX_DUE_PER_DEBTOR;
        }
    }

    /** Lignes du registre du trésor (1 000 par défaut, de 100 à 100 000). */
    public static int treasuryLedgerLines() {
        try {
            return TREASURY_LEDGER_LINES.get();
        } catch (IllegalStateException e) {
            return DEFAULT_TREASURY_LEDGER_LINES;
        }
    }

    /** Longueur maximale du motif d'une dépense publique (80 par défaut, de 1 à 200). */
    public static int treasuryReasonMax() {
        try {
            return TREASURY_REASON_MAX.get();
        } catch (IllegalStateException e) {
            return DEFAULT_TREASURY_REASON_MAX;
        }
    }

    /** Les taux de salaire réglés, tels qu'écrits (\"policier=60\"…) ; les valeurs par défaut sans fichier. */
    public static List<? extends String> salaryPerHour() {
        try {
            return SALARY_PER_HOUR.get();
        } catch (IllegalStateException e) {
            return DEFAULT_SALARY_PER_HOUR;
        }
    }

    /** Remplace la liste des taux et écrit le fichier (commande de l'équipe). */
    public static void setSalaryPerHour(List<String> entries) {
        SALARY_PER_HOUR.set(List.copyOf(entries));
        SALARY_PER_HOUR.save();
    }

    /** Salaire du maire, en cuivres par heure (0 par défaut : seule la police est salariée). */
    public static long mayorSalaryPerHour() {
        try {
            return MAYOR_SALARY_PER_HOUR.get();
        } catch (IllegalStateException e) {
            return DEFAULT_MAYOR_SALARY_PER_HOUR;
        }
    }

    /** Règle le salaire du maire et écrit le fichier (commande de l'équipe). */
    public static void setMayorSalaryPerHour(long rate) {
        MAYOR_SALARY_PER_HOUR.set(rate);
        MAYOR_SALARY_PER_HOUR.save();
    }

    /** Heures comptées au plus par joueur, par jour et par salaire (8 par défaut). */
    public static int salaryMaxHoursPerDay() {
        try {
            return SALARY_MAX_HOURS_PER_DAY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_SALARY_MAX_HOURS_PER_DAY;
        }
    }

    /** Heure locale de la clôture du jour de paie (0 par défaut, de 0 à 23). */
    public static int payHour() {
        try {
            return PAY_HOUR.get();
        } catch (IllegalStateException e) {
            return DEFAULT_PAY_HOUR;
        }
    }

    /** Fuseau de la paie tel qu'écrit (vide par défaut : fuseau du système) ; lu par {@link EconomyCalendar#configuredZone()}. */
    public static String payTimeZone() {
        try {
            return PAY_TIME_ZONE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_PAY_TIME_ZONE;
        }
    }

    /** Jours de paie manqués rattrapés au plus (400 par défaut). */
    public static int payMaxMissedDays() {
        try {
            return PAY_MAX_MISSED_DAYS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_PAY_MAX_MISSED_DAYS;
        }
    }

    /** Butin d'au moins ce montant, en cuivres : une ligne au registre (100 par défaut ; 0 = chaque butin). */
    public static long mobLootJournalThreshold() {
        try {
            return MOB_LOOT_JOURNAL_THRESHOLD.get();
        } catch (IllegalStateException e) {
            return DEFAULT_MOB_LOOT_JOURNAL_THRESHOLD;
        }
    }

    /** Ce qu'un même raid paie au plus en butin, en cuivres, tous joueurs confondus (100 par défaut). */
    public static long raidLootCap() {
        try {
            return RAID_LOOT_CAP.get();
        } catch (IllegalStateException e) {
            return DEFAULT_RAID_LOOT_CAP;
        }
    }

    /** Butins d'un même type de boss par joueur et par jour de paie (1 par défaut ; 0 = jamais). */
    public static int bossLootPerDay() {
        try {
            return BOSS_LOOT_PER_DAY.get();
        } catch (IllegalStateException e) {
            return DEFAULT_BOSS_LOOT_PER_DAY;
        }
    }

    /** Délai entre deux émissions d'un même joueur, en ticks (40 par défaut). */
    public static int invoiceIssueCooldownTicks() {
        try {
            return INVOICE_ISSUE_COOLDOWN_TICKS.get();
        } catch (IllegalStateException e) {
            return DEFAULT_INVOICE_ISSUE_COOLDOWN_TICKS;
        }
    }

    /** Types d'écran ajoutés par la configuration aux rangements permis (lus au chargement du fichier, vides avant). */
    public static Set<ResourceLocation> extraAllowedMenus() {
        return allowedCache;
    }

    /** Types d'écran que la configuration interdit à l'argent (lus au chargement du fichier, vides avant). */
    public static Set<ResourceLocation> extraForbiddenMenus() {
        return forbiddenCache;
    }

    private static Set<ResourceLocation> ids(ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
        try {
            Set<ResourceLocation> out = new HashSet<>();
            for (String text : value.get()) {
                ResourceLocation id = ResourceLocation.tryParse(text);
                if (id != null) {
                    out.add(id);
                }
            }
            return out;
        } catch (IllegalStateException e) {
            return Set.of();
        }
    }

    private static boolean isResourceLocation(Object text) {
        return text instanceof String string && ResourceLocation.tryParse(string) != null;
    }

    /** Relit le fichier à son chargement et à son rechargement. */
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            cache = validated(read());
            allowedCache = Set.copyOf(ids(EXTRA_ALLOWED_MENUS));
            forbiddenCache = Set.copyOf(ids(EXTRA_FORBIDDEN_MENUS));
            CoinRecipeGuard.reapplyOnLiveServer();
        }
    }

    /**
     * Les valeurs si le cuivre vaut 1 et si chacune est un multiple strict de la précédente, sinon les valeurs
     * par défaut avec un avertissement (règle pure, essayée en JUnit).
     */
    static long[] validated(long[] values) {
        boolean ok = values.length == DEFAULTS.length && values[0] == 1L;
        for (int i = 1; ok && i < values.length; i++) {
            ok = values[i] > values[i - 1] && values[i] % values[i - 1] == 0;
        }
        if (ok) {
            return values;
        }
        LOGGER.warn("Economie : valeurs des pieces {} incoherentes (cuivre = 1, chaque piece multiple strict de "
                + "celle du dessous), valeurs par defaut utilisees", Arrays.toString(values));
        return DEFAULTS.clone();
    }

    private static long[] read() {
        long[] values = DEFAULTS.clone();
        for (int i = 1; i < values.length; i++) {
            try {
                values[i] = VALUES[i].get();
            } catch (IllegalStateException e) {
                values[i] = DEFAULTS[i];
            }
        }
        return values;
    }
}
