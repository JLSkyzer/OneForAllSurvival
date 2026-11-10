package dev.oneforallsurvival.town;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * La pomme de douche. Une cuve de {@value #TANK_CAPACITY} mB qui n'accepte que l'eau chaude et que rien ne vide.
 *
 * <p>Toutes les 20 ticks, côté serveur : elle relève les joueurs dessous ({@link ShowerRules#isUnder}, rien de plein
 * entre la pomme et eux, spectateurs exclus). S'il y en a au moins un et que la cuve a de quoi couler une seconde
 * ({@code showerMbPerTick × 20} mB), elle les consomme et chacun gagne une seconde de douche ; sinon tous les
 * compteurs repartent à zéro. Un joueur qui n'est plus dessous repart de zéro aussi. À {@code showerSeconds} le
 * joueur reçoit le Bien-être et son compteur retombe à zéro. Les compteurs ne sont pas sauvegardés ; la cuve l'est.
 * Pas d'usure, pas de courant.
 */
public final class ShowerHeadBlockEntity extends BlockEntity {

    public static final int TANK_CAPACITY = 1000;
    private static final int PERIOD = 20;
    private static final String TAG_TANK = "tank";

    private final FluidTank tank = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid().isSame(TownRegistry.HOT_WATER.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    /** Ce que voient les tuyaux : de l'eau chaude entre, rien ne sort. */
    private final IFluidHandler pipes = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int index) {
            return tank.getFluid().copy();
        }

        @Override
        public int getTankCapacity(int index) {
            return TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int index, @NotNull FluidStack stack) {
            return tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tank.isFluidValid(resource) ? tank.fill(resource, action) : 0;
        }

        @NotNull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @NotNull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> pipes);
    private final Map<UUID, Integer> seconds = new HashMap<>();
    private int cooldown;

    public ShowerHeadBlockEntity(BlockPos pos, BlockState state) {
        super(TownRegistry.SHOWER_HEAD_BE.get(), pos, state);
    }

    /** La cuve d'eau chaude, côté machine (le tuyau passe par {@link #fluids()}). */
    public FluidTank tank() {
        return tank;
    }

    /** Ce que voient les tuyaux sur toutes les faces. */
    public IFluidHandler fluids() {
        return pipes;
    }

    /** Les secondes continues d'eau chaude que ce joueur a reçues sous la pomme. */
    public int secondsUnder(UUID player) {
        return seconds.getOrDefault(player, 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ShowerHeadBlockEntity be) {
        if (++be.cooldown < PERIOD) {
            return;
        }
        be.cooldown = 0;
        be.second(level, pos, state);
    }

    private void second(Level level, BlockPos pos, BlockState state) {
        List<Player> under = playersUnder(level, pos);
        int cost = TownConfig.showerMbPerTick() * PERIOD;
        boolean flows = !under.isEmpty() && tank.getFluidAmount() >= cost;
        seconds.keySet().removeIf(id -> under.stream().noneMatch(p -> p.getUUID().equals(id)));
        if (flows) {
            tank.drain(cost, IFluidHandler.FluidAction.EXECUTE);
            for (Player player : under) {
                int total = seconds.merge(player.getUUID(), 1, Integer::sum);
                if (total >= TownConfig.showerSeconds()) {
                    WellBeing.grant(player, TownConfig.showerWellBeingMinutes() * 60 * 20);
                    seconds.put(player.getUUID(), 0);
                }
            }
        } else {
            seconds.clear();
        }
        if (state.getValue(ShowerHeadBlock.ACTIVE) != flows) {
            level.setBlock(pos, state.setValue(ShowerHeadBlock.ACTIVE, flows), 3);
        }
    }

    private List<Player> playersUnder(Level level, BlockPos pos) {
        Vec3 head = Vec3.atCenterOf(pos);
        AABB zone = new AABB(head.x - 1.0, head.y - ShowerRules.MAX_BELOW, head.z - 1.0,
                head.x + 1.0, head.y - ShowerRules.MIN_BELOW + 2.0, head.z + 1.0);
        return level.getEntitiesOfClass(Player.class, zone, player -> player.isAlive() && !player.isSpectator()
                && ShowerRules.isUnder(head, player.getBoundingBox()) && clearAbove(level, pos, player));
    }

    /** Rien de plein entre la pomme et le haut du joueur. */
    private static boolean clearAbove(Level level, BlockPos pos, Player player) {
        int top = BlockPos.containing(player.getX(), player.getBoundingBox().maxY - 1.0E-3, player.getZ()).getY();
        for (int y = pos.getY() - 1; y >= top; y--) {
            BlockPos at = new BlockPos(pos.getX(), y, pos.getZ());
            if (level.getBlockState(at).isCollisionShapeFullBlock(level, at)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Faces
    // ---------------------------------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluidCap = LazyOptional.of(() -> pipes);
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_TANK, tank.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag.getCompound(TAG_TANK));
    }
}
