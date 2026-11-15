package dev.oneforallsurvival.economy.town;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/**
 * Une des six parties du tableau de la mairie (3 de large, 2 de haut), repérée par sa colonne vue de face (-1 à gauche, 0
 * au milieu, 1 à droite, pour qui lit le tableau) et sa rangée (0 en bas, 1 en haut). La partie du bas au milieu est la
 * <b>maîtresse</b> : posée à l'endroit visé, elle porte la seule entité de bloc (rendu et données) et le seul objet que
 * rend le tableau cassé.
 */
public enum TownBoardPart implements StringRepresentable {
    BOTTOM_LEFT("bottom_left", -1, 0),
    BOTTOM_CENTER("bottom_center", 0, 0),
    BOTTOM_RIGHT("bottom_right", 1, 0),
    TOP_LEFT("top_left", -1, 1),
    TOP_CENTER("top_center", 0, 1),
    TOP_RIGHT("top_right", 1, 1);

    /** La partie maîtresse. */
    public static final TownBoardPart MASTER = BOTTOM_CENTER;

    private final String name;
    private final int column;
    private final int row;

    TownBoardPart(String name, int column, int row) {
        this.name = name;
        this.column = column;
        this.row = row;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** -1 à gauche, 0 au milieu, 1 à droite, vu de face. */
    public int column() {
        return column;
    }

    /** 0 en bas, 1 en haut. */
    public int row() {
        return row;
    }

    public boolean isMaster() {
        return this == MASTER;
    }

    /**
     * La droite de qui lit un tableau tourné vers {@code facing} : il le regarde dans le sens opposé (un tableau tourné
     * vers le nord se lit en regardant le sud, la droite est alors l'ouest).
     */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** La position de cette partie quand la maîtresse est en {@code master}. */
    public BlockPos from(BlockPos master, Direction facing) {
        return master.relative(right(facing), column).above(row);
    }

    /** La position de la maîtresse quand cette partie est en {@code pos}. */
    public BlockPos master(BlockPos pos, Direction facing) {
        return pos.relative(right(facing), -column).below(row);
    }

    /** La partie de cette colonne et de cette rangée, ou nul hors du tableau. */
    @Nullable
    public static TownBoardPart at(int column, int row) {
        for (TownBoardPart part : values()) {
            if (part.column == column && part.row == row) {
                return part;
            }
        }
        return null;
    }

    /** La partie voisine dans la direction {@code direction} (monde), ou nul si le tableau s'arrête là. */
    @Nullable
    public TownBoardPart neighbor(Direction direction, Direction facing) {
        if (direction == Direction.UP) {
            return at(column, row + 1);
        }
        if (direction == Direction.DOWN) {
            return at(column, row - 1);
        }
        Direction right = right(facing);
        if (direction == right) {
            return at(column + 1, row);
        }
        if (direction == right.getOpposite()) {
            return at(column - 1, row);
        }
        return null;
    }
}
