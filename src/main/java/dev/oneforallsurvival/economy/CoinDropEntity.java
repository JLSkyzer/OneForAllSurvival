package dev.oneforallsurvival.economy;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Des pièces au sol. Ce n'est pas un {@link ItemEntity} : un entonnoir, un tapis ou un entonnoir de Create, un
 * aimant ou un allay ne la voient pas, seul un vrai joueur la ramasse en la touchant. Même physique qu'un objet
 * au sol ; une pile unique d'une seule coupure, dont le nombre n'est pas limité à la taille d'une pile.
 *
 * <p><b>Bourse au sol.</b> La même entité porte aussi une bourse qui contient des pièces ({@link #isPurse}) : un
 * objet de bourse jeté ({@link PurseItem#createEntity}) devient une entité du mod, pour la même raison que les
 * pièces, et n'est ramassé que par un vrai joueur, dans une case libre de son inventaire. Elle suit les mêmes règles
 * de durée de vie et de comptes (feu, vide, {@value #LIFETIME} ticks) ; sa valeur est celle de son contenu. Elle ne
 * fusionne jamais.
 *
 * <p><b>Comptes.</b> Les pièces qui disparaissent pour de bon sont inscrites au registre ({@link EconomyLedger}) à
 * l'instant où elles disparaissent : feu ou lave, sous le monde, {@value #LIFETIME} ticks au sol. Ce qui les
 * distingue d'un simple retrait de l'entité est le {@link net.minecraft.world.entity.Entity.RemovalReason} :
 * {@code UNLOADED_TO_CHUNK} (chunk déchargé, l'entité est sauvée), {@code UNLOADED_WITH_PLAYER} et
 * {@code CHANGED_DIMENSION} (l'entité renaît ailleurs) ne détruisent rien ({@code shouldDestroy()} est faux) ;
 * {@code KILLED} et {@code DISCARDED} détruisent. Une pièce ramassée ou fondue dans une voisine est « réglée »
 * avant d'être retirée et ne compte pas ; toute autre destruction (une commande {@code /kill}, un autre mod) est
 * inscrite à la cause {@link MoneySink#STAFF}, pour que créé moins détruit reste juste.
 */
public class CoinDropEntity extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Durée de vie au sol, en ticks (5 minutes). */
    public static final int LIFETIME = 6000;
    /** Délai de ramassage qui veut dire « jamais » (vrai objet factice de {@code /give}). */
    public static final int NEVER_PICKED_UP = 32767;
    /** Nombre maximal de pièces d'une pile au sol (garde-fou contre le débordement d'un entier). */
    public static final int MAX_COUNT = 1 << 30;

    private static final EntityDataAccessor<Integer> DATA_COIN =
            SynchedEntityData.defineId(CoinDropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COUNT =
            SynchedEntityData.defineId(CoinDropEntity.class, EntityDataSerializers.INT);
    /** La bourse portée, ou la pile vide pour des pièces. */
    private static final EntityDataAccessor<ItemStack> DATA_PURSE =
            SynchedEntityData.defineId(CoinDropEntity.class, EntityDataSerializers.ITEM_STACK);

    /** Intervalle de la fusion avec les voisines, en ticks. */
    private static final int MERGE_INTERVAL = 20;
    /** Autour de la pièce, en blocs, où chercher une voisine de même coupure. */
    private static final double MERGE_RADIUS = 0.5D;

    // Champs privés d'ItemEntity (noms SRG, traduits en développement par ObfuscationReflectionHelper), résolus une
    // seule fois au chargement de la classe : s'il en manque un (autre version du jeu), le démarrage échoue
    // bruyamment ({@link #checkFields}) au lieu de laisser naître des pièces avec un délai par défaut.
    private static final Field ITEM_AGE = ObfuscationReflectionHelper.findField(ItemEntity.class, "f_31985_");
    private static final Field ITEM_PICKUP_DELAY = ObfuscationReflectionHelper.findField(ItemEntity.class, "f_31986_");
    private static final Field ITEM_THROWER = ObfuscationReflectionHelper.findField(ItemEntity.class, "f_31988_");
    private static final Field ITEM_TARGET = ObfuscationReflectionHelper.findField(ItemEntity.class, "f_265881_");

    public final float bobOffs;

    private int age;
    private int pickupDelay;
    /** Qui l'a lâchée (pour le registre), si on le sait. */
    @Nullable
    private UUID thrower;
    /** Si renseigné, seul ce joueur la ramasse. */
    @Nullable
    private UUID target;
    /** Les pièces de cette entité sont déjà comptées ailleurs : ramassées, fondues, ou déjà inscrites. */
    private boolean accounted;
    /** Entité sans pièce à elle (objet factice de {@code /give}) : elle disparaît à son premier tick. */
    private boolean inert;
    private ItemStack display = ItemStack.EMPTY;
    /** L'objet remplacé, jusqu'à l'ajout dans le monde (jamais sauvé). */
    @Nullable
    private ItemEntity origin;

    public CoinDropEntity(EntityType<? extends CoinDropEntity> type, Level level) {
        super(type, level);
        this.bobOffs = this.random.nextFloat() * (float) Math.PI * 2.0F;
        this.pickupDelay = 10;
    }

    /** Une pièce au sol qui retombe d'un petit saut, comme un objet lâché. */
    public static CoinDropEntity at(Level level, double x, double y, double z, Coin coin, int count) {
        CoinDropEntity entity = new CoinDropEntity(EconomyRegistry.coinDropType(), level);
        entity.setPos(x, y, z);
        entity.setYRot(entity.random.nextFloat() * 360.0F);
        entity.setDeltaMovement(entity.random.nextDouble() * 0.2D - 0.1D, 0.2D, entity.random.nextDouble() * 0.2D - 0.1D);
        entity.setCoin(coin);
        entity.setCount(count);
        return entity;
    }

    /**
     * La pièce qui prend la place d'un objet de pièce que le jeu vient de lâcher (voir
     * {@link CoinItem#createEntity}). Reprend sa position, son élan, son âge, son délai de ramassage et son
     * lanceur. Forge ajoute l'entité au tick suivant seulement, et certains lâchers modifient l'objet après
     * coup : {@code /give} le rend factice (délai « jamais ») une fois lâché, ou le réserve à un joueur sans
     * délai. L'état est donc relu une seconde fois à l'ajout dans le monde ({@link #onAddedToWorld}) ; un objet
     * factice devient alors une entité inerte, sinon la commande ferait naître une pièce.
     */
    static CoinDropEntity replacing(Level level, ItemEntity item, Coin coin, int count) {
        CoinDropEntity entity = replacing(level, item);
        entity.setCoin(coin);
        entity.setCount(count);
        return entity;
    }

    /** Comme {@link #replacing(Level, ItemEntity, Coin, int)}, pour une bourse qui contient des pièces. */
    static CoinDropEntity replacingPurse(Level level, ItemEntity item, ItemStack purse) {
        CoinDropEntity entity = replacing(level, item);
        entity.entityData.set(DATA_PURSE, purse);
        entity.setCount(1);
        return entity;
    }

    private static CoinDropEntity replacing(Level level, ItemEntity item) {
        CoinDropEntity entity = new CoinDropEntity(EconomyRegistry.coinDropType(), level);
        entity.moveTo(item.getX(), item.getY(), item.getZ(), item.getYRot(), item.getXRot());
        entity.setDeltaMovement(item.getDeltaMovement());
        entity.origin = item;
        entity.readOrigin();
        return entity;
    }

    /**
     * Force la résolution des champs privés d'{@code ItemEntity} : appelé au démarrage du mod
     * ({@code EconomyRegistry}), il fait échouer le chargement si l'un d'eux manque.
     */
    public static void checkFields() {
        if (ITEM_AGE == null || ITEM_PICKUP_DELAY == null || ITEM_THROWER == null || ITEM_TARGET == null) {
            throw new IllegalStateException("Economie : champs prives d'ItemEntity introuvables");
        }
    }

    /** Reprend l'âge, le délai de ramassage, le lanceur et le destinataire de l'objet qu'elle remplace. */
    private void readOrigin() {
        ItemEntity item = origin;
        if (item == null) {
            return;
        }
        try {
            this.pickupDelay = ITEM_PICKUP_DELAY.getInt(item);
            this.age = ITEM_AGE.getInt(item);
            this.thrower = (UUID) ITEM_THROWER.get(item);
            this.target = (UUID) ITEM_TARGET.get(item);
        } catch (IllegalAccessException e) {
            // findField rend les champs accessibles : ceci ne peut arriver que si le jeu a changé sous nos pieds.
            throw new IllegalStateException("Economie : champs de l'objet au sol illisibles", e);
        }
        markInertIfFake();
    }

    /** Un objet factice ({@code /give}, délai « jamais ») ne porte aucune pièce à lui : l'entité est inerte. */
    private void markInertIfFake() {
        if (this.pickupDelay == NEVER_PICKED_UP) {
            this.inert = true;
            this.accounted = true;
        }
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (origin != null) {
            if (!level().isClientSide) {
                readOrigin();
            }
            origin = null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // État
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_COIN, Coin.COPPER.ordinal());
        this.entityData.define(DATA_COUNT, 1);
        this.entityData.define(DATA_PURSE, ItemStack.EMPTY);
    }

    /** Vrai si l'entité porte une bourse et non des pièces. */
    public boolean isPurse() {
        return !this.entityData.get(DATA_PURSE).isEmpty();
    }

    /** La bourse portée, ou la pile vide pour des pièces. */
    public ItemStack purse() {
        return this.entityData.get(DATA_PURSE);
    }

    public Coin coin() {
        int index = this.entityData.get(DATA_COIN);
        Coin[] all = Coin.values();
        return all[Mth.clamp(index, 0, all.length - 1)];
    }

    public void setCoin(Coin coin) {
        this.entityData.set(DATA_COIN, coin.ordinal());
        this.display = ItemStack.EMPTY;
    }

    public int count() {
        return this.entityData.get(DATA_COUNT);
    }

    public void setCount(int count) {
        this.entityData.set(DATA_COUNT, Mth.clamp(count, 0, MAX_COUNT));
    }

    /** Ce que valent les pièces de cette entité (ou le contenu de sa bourse), en cuivres. */
    public long value() {
        return isPurse() ? Purse.total(purse()) : CoinMath.valueOf(coin(), count());
    }

    /** Une pièce, ou la bourse, pour l'affichage (le rendu et le nom). */
    public ItemStack getItem() {
        if (isPurse()) {
            return purse();
        }
        Coin coin = coin();
        if (display.isEmpty() || display.getItem() != coin.item()) {
            display = new ItemStack(coin.item());
        }
        return display;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public void setPickUpDelay(int ticks) {
        this.pickupDelay = ticks;
    }

    public boolean hasPickUpDelay() {
        return pickupDelay > 0;
    }

    public void setTarget(@Nullable UUID player) {
        this.target = player;
    }

    @Nullable
    public UUID thrower() {
        return thrower;
    }

    public float getSpin(float partialTick) {
        return ((float) this.age + partialTick) / 20.0F + this.bobOffs;
    }

    @Override
    public Component getName() {
        Component custom = getCustomName();
        return custom != null ? custom : Component.translatable(getItem().getDescriptionId());
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ---------------------------------------------------------------------------------------------
    // Physique et durée de vie
    // ---------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (inert) {
            discard();
            return;
        }
        if (count() <= 0) {
            accounted = true;
            discard();
            return;
        }
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (pickupDelay > 0 && pickupDelay != NEVER_PICKED_UP) {
            --pickupDelay;
        }
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
        Vec3 before = getDeltaMovement();
        float surface = getEyeHeight() - 0.11111111F;
        if (isInWater() && getFluidHeight(FluidTags.WATER) > surface) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * 0.99F, v.y + (v.y < 0.06F ? 5.0E-4F : 0.0F), v.z * 0.99F);
        } else if (isInLava() && getFluidHeight(FluidTags.LAVA) > surface) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * 0.95F, v.y + (v.y < 0.06F ? 5.0E-4F : 0.0F), v.z * 0.95F);
        } else if (!isNoGravity()) {
            setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        }
        if (level().isClientSide) {
            this.noPhysics = false;
        } else {
            this.noPhysics = !level().noCollision(this, getBoundingBox().deflate(1.0E-7D));
            if (this.noPhysics) {
                moveTowardsClosestSpace(getX(), (getBoundingBox().minY + getBoundingBox().maxY) / 2.0D, getZ());
            }
        }
        if (!onGround() || getDeltaMovement().horizontalDistanceSqr() > 1.0E-5F || (tickCount + getId()) % 4 == 0) {
            move(MoverType.SELF, getDeltaMovement());
            float friction = 0.98F;
            if (onGround()) {
                BlockPos below = getBlockPosBelowThatAffectsMyMovement();
                friction = level().getBlockState(below).getBlock().getFriction() * 0.98F;
            }
            setDeltaMovement(getDeltaMovement().multiply(friction, 0.98D, friction));
            if (onGround()) {
                Vec3 v = getDeltaMovement();
                if (v.y < 0.0D) {
                    setDeltaMovement(v.multiply(1.0D, -0.5D, 1.0D));
                }
            }
        }
        if (!level().isClientSide && (tickCount + getId()) % MERGE_INTERVAL == 0) {
            mergeWithNeighbours();
        }
        ++age;
        this.hasImpulse |= updateInWaterStateAndDoFluidPushing();
        if (!level().isClientSide) {
            if (getDeltaMovement().subtract(before).lengthSqr() > 0.01D) {
                this.hasImpulse = true;
            }
            if (age >= LIFETIME) {
                destroy(MoneySink.DESPAWN, "restee " + LIFETIME + " ticks au sol");
            }
        }
    }

    /** Fond les voisines de même coupure dans celle-ci : la plus ancienne en id absorbe, sans perdre une pièce. */
    private void mergeWithNeighbours() {
        if (isRemoved() || isPurse() || pickupDelay == NEVER_PICKED_UP || count() >= MAX_COUNT) {
            return;
        }
        Coin mine = coin();
        List<CoinDropEntity> others = level().getEntitiesOfClass(CoinDropEntity.class,
                getBoundingBox().inflate(MERGE_RADIUS, 0.0D, MERGE_RADIUS),
                other -> other != this && !other.isRemoved() && !other.inert && !other.isPurse() && other.coin() == mine
                        && Objects.equals(other.target, target) && other.pickupDelay != NEVER_PICKED_UP);
        for (CoinDropEntity other : others) {
            long sum = (long) count() + other.count();
            if (sum > MAX_COUNT) {
                continue;
            }
            setCount((int) sum);
            age = Math.min(age, other.age);
            pickupDelay = Math.max(pickupDelay, other.pickupDelay);
            other.accounted = true;
            other.discard();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Destruction : les comptes
    // ---------------------------------------------------------------------------------------------

    /** Feu et lave détruisent la pile ; tout le reste (chute, explosion, cactus...) la laisse intacte. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            destroy(MoneySink.FIRE, "feu ou lave");
            return true;
        }
        return false;
    }

    /** Sous le monde : la pile est détruite et comptée. */
    @Override
    protected void onBelowWorld() {
        destroy(MoneySink.VOID, "sous le monde");
    }

    /** Inscrit les pièces au registre à la cause donnée, puis retire l'entité. Sans effet si elles sont déjà comptées. */
    private void destroy(MoneySink sink, String reason) {
        if (!level().isClientSide && !accounted) {
            accounted = true;
            record(sink, reason);
        }
        discard();
    }

    private void record(MoneySink sink, String reason) {
        if (level().getServer() != null) {
            EconomyLedger.get(level().getServer()).destroyed(sink, value(), thrower, reason);
        }
    }

    /**
     * Toute destruction que le jeu ou un autre mod fait sans passer par ici ({@code KILLED}, {@code DISCARDED}) est
     * comptée, sauf si les pièces sont déjà réglées. Un déchargement de chunk ou un changement de dimension ne
     * détruit rien (voir la description de la classe).
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && !accounted && reason.shouldDestroy()) {
            accounted = true;
            record(MoneySink.STAFF, "pieces retirees du monde (" + reason + ")");
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------------------------------------
    // Ramassage
    // ---------------------------------------------------------------------------------------------

    /**
     * Seul un vrai joueur la ramasse : ni un faux joueur (Create, déployeur), ni un spectateur. Les pièces vont
     * là où {@link PlayerCoins#receive} les met ; si tout ne rentre pas, le reste reste au sol.
     */
    @Override
    public void playerTouch(Player player) {
        if (level().isClientSide || isRemoved() || inert || pickupDelay > 0) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer || player.isSpectator()) {
            return;
        }
        if (target != null && !target.equals(player.getUUID())) {
            return;
        }
        if (isPurse()) {
            pickUpPurse(serverPlayer);
            return;
        }
        Coin coin = coin();
        int count = count();
        int taken = (int) PlayerCoins.receive(serverPlayer, coin, count);
        if (taken <= 0) {
            return;
        }
        serverPlayer.awardStat(Stats.ITEM_PICKED_UP.get(coin.item()), taken);
        if (taken >= count) {
            // L'animation de ramassage retire l'entité côté client ; pour un ramassage partiel elle la ferait
            // disparaître alors qu'il en reste, on s'en passe.
            serverPlayer.take(this, taken);
            accounted = true;
            discard();
        } else {
            setCount(count - taken);
        }
    }

    /**
     * La bourse va dans la première case libre de l'inventaire (jamais dans une autre bourse) ; s'il n'y en a pas,
     * elle reste au sol. La case est lue avant, la bourse posée, puis l'entité retirée : ni copie ni perte.
     */
    private void pickUpPurse(ServerPlayer player) {
        int slot = player.getInventory().getFreeSlot();
        if (slot < 0) {
            return;
        }
        player.getInventory().setItem(slot, purse().copy());
        player.take(this, 1);
        accounted = true;
        discard();
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (isPurse()) {
            tag.put("Purse", purse().save(new CompoundTag()));
        }
        tag.putString("Coin", coin().key());
        tag.putInt("Count", count());
        tag.putInt("Age", age);
        tag.putInt("PickupDelay", pickupDelay);
        if (thrower != null) {
            tag.putUUID("Thrower", thrower);
        }
        if (target != null) {
            tag.putUUID("Owner", target);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Purse", 10)) {
            ItemStack stored = ItemStack.of(tag.getCompound("Purse"));
            if (Purse.isPurse(stored)) {
                this.entityData.set(DATA_PURSE, stored);
                setCount(1);
            } else {
                // Une bourse que ce monde ne connaît plus : rien à compter, rien à garder.
                LOGGER.warn("Economie : bourse au sol inconnue ignoree");
                setCount(0);
            }
            readCommon(tag);
            return;
        }
        String key = tag.getString("Coin");
        Coin found = null;
        for (Coin coin : Coin.values()) {
            if (coin.key().equals(key)) {
                found = coin;
            }
        }
        if (found == null) {
            // Une pièce que ce monde ne connaît plus : rien à compter, rien à garder.
            LOGGER.warn("Economie : piece au sol inconnue '{}' ignoree", key);
            setCount(0);
        } else {
            setCoin(found);
            setCount(tag.getInt("Count"));
        }
        readCommon(tag);
    }

    /**
     * Âge, délai, lanceur et destinataire. Une entité sauvée inerte (objet factice remplacé au mauvais tick) le
     * redevient à la relecture : sans cela elle redeviendrait une pièce, ou serait comptée comme détruite.
     */
    private void readCommon(CompoundTag tag) {
        this.age = tag.getInt("Age");
        this.pickupDelay = tag.contains("PickupDelay") ? tag.getInt("PickupDelay") : 10;
        this.thrower = tag.hasUUID("Thrower") ? tag.getUUID("Thrower") : null;
        this.target = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        markInertIfFake();
    }
}
