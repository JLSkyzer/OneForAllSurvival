package dev.oneforallsurvival.economy.client;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.oneforallsurvival.economy.town.TownBoardBlock;
import dev.oneforallsurvival.economy.town.TownBoardBlockEntity;
import dev.oneforallsurvival.economy.town.TownFinances;
import dev.oneforallsurvival.economy.town.TownFinances.Parts;
import dev.oneforallsurvival.economy.town.TownFinances.Snapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Le texte du tableau de la mairie, sur sa face avant : « Trésor de la ville », « Solde : … » et « Salaires dus : … » ou
 * « Aucun salaire dû ». Les comptes viennent de {@link TownFinancesCache} (« … » tant que rien n'est reçu). La face
 * n'offre que 56 x 32 pixels de texte au dedans du cadre de bois : {@link TownBoardLayout} range le solde et les
 * salaires par coupures sur autant de lignes qu'il en faut, à la plus grande échelle (de 1 à 0,7) qui les fait tenir,
 * sans jamais couper une coupure ni descendre sous le seuil de lecture à quatre blocs : au pire, le titre disparaît,
 * puis les plus petites coupures sont laissées de côté (« … »). Dessiné dans les {@value #RANGE}
 * blocs seulement.
 */
public final class TownBoardRenderer implements BlockEntityRenderer<TownBoardBlockEntity> {

    /** Distance de rendu, en blocs. */
    static final int RANGE = 16;

    /** Pixels de texte par bloc : un caractère de 8 px mesure 0,125 bloc. */
    private static final float PIXELS_PER_BLOCK = 64.0F;
    /** Le haut du panneau (15/16) par rapport au centre du bloc, et l'avance de la face (6/16) sur le centre. */
    private static final float TOP_FROM_CENTER = 15.0F / 16.0F - 0.5F;
    private static final float FACE_FROM_CENTER = 0.5F - 6.0F / 16.0F;
    /** Un souffle devant la face, pour ne pas se confondre avec elle. */
    private static final float EPSILON = 0.004F;
    /** Le haut de la zone de texte sous le haut du panneau, en pixels de texte : le cadre de bois en prend 4. */
    private static final float TEXT_TOP = 4.0F;

    private static final int TITLE_COLOR = 0x2B1B0E;
    private static final int BALANCE_COLOR = 0x1E5A1E;
    private static final int OWED_COLOR = 0x8B1A1A;
    private static final int NONE_COLOR = 0x1E5A1E;

    private final Font font;

    public TownBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public int getViewDistance() {
        return RANGE;
    }

    @Override
    public void render(TownBoardBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        if (be.getLevel() == null || !(be.getBlockState().getBlock() instanceof TownBoardBlock)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(TownBoardBlock.FACING);
        Snapshot snapshot = TownFinancesCache.get();
        boolean owed = snapshot != null && snapshot.owedCount() > 0;
        Parts balance = TownFinances.balanceParts(snapshot);
        Parts due = TownFinances.owedParts(snapshot);
        TownBoardLayout.Result layout = TownBoardLayout.layout(List.of(
                new TownBoardLayout.Section(TownFinances.titleLine(), List.of(), TITLE_COLOR, true, false),
                new TownBoardLayout.Section(balance.head(), balance.amount(), BALANCE_COLOR, false, false),
                new TownBoardLayout.Section(due.head(), due.amount(), owed ? OWED_COLOR : NONE_COLOR, false, true)), font::width);
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        // Le texte regarde vers +z : le bloc est tourné pour que sa face avant y soit.
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0.0D, TOP_FROM_CENTER, FACE_FROM_CENTER + EPSILON);
        float scale = 1.0F / PIXELS_PER_BLOCK;
        pose.scale(scale, -scale, scale);
        // Le bloc de lignes est centré dans la zone de texte, chaque ligne sur son axe.
        float top = TEXT_TOP + (TownBoardLayout.HEIGHT - layout.height()) / 2.0F;
        for (int i = 0; i < layout.lines().size(); i++) {
            TownBoardLayout.Line line = layout.lines().get(i);
            line(pose, buffers, light, line.text(), top + i * TownBoardLayout.LINE_HEIGHT * layout.scale(), layout.scale(),
                    line.color());
        }
        pose.popPose();
    }

    /** Une ligne centrée, à l'échelle choisie par la mise en page. */
    private void line(PoseStack pose, MultiBufferSource buffers, int light, Component text, float top, float shrink,
            int color) {
        int width = font.width(text);
        pose.pushPose();
        pose.translate(-width * shrink / 2.0F, top, 0.0F);
        pose.scale(shrink, shrink, 1.0F);
        Matrix4f matrix = pose.last().pose();
        font.drawInBatch(text, 0.0F, 0.0F, color, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
