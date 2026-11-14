package dev.oneforallsurvival.economy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.oneforallsurvival.economy.town.TownBoardBlock;
import dev.oneforallsurvival.economy.town.TownBoardBlockEntity;
import dev.oneforallsurvival.economy.town.TownFinances;
import dev.oneforallsurvival.economy.town.TownFinances.Snapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Les trois lignes du tableau de la mairie, sur sa face avant : « Trésor de la ville », « Solde : … », « Salaires dus : …
 * » ou « Aucun salaire dû ». Les comptes viennent de {@link TownFinancesCache} (« … » tant que rien n'est reçu). Chaque
 * ligne est mesurée ({@code font.width}) et rétrécie pour tenir dans la face ; le texte est assez grand pour se lire à
 * quatre blocs, et n'est dessiné que dans les {@value #RANGE} blocs.
 */
public final class TownBoardRenderer implements BlockEntityRenderer<TownBoardBlockEntity> {

    /** Distance de rendu, en blocs. */
    static final int RANGE = 16;

    /** Pixels de texte par bloc : un caractère de 8 px mesure 0,125 bloc. */
    private static final float PIXELS_PER_BLOCK = 64.0F;
    /** Largeur utile de la face (15/16 de bloc), en pixels de texte. */
    private static final float MAX_WIDTH = 15.0F / 16.0F * PIXELS_PER_BLOCK;
    /** Le haut du panneau (15/16) par rapport au centre du bloc, et l'avance de la face (6/16) sur le centre. */
    private static final float TOP_FROM_CENTER = 15.0F / 16.0F - 0.5F;
    private static final float FACE_FROM_CENTER = 0.5F - 6.0F / 16.0F;
    /** Un souffle devant la face, pour ne pas se confondre avec elle. */
    private static final float EPSILON = 0.004F;
    /** Le haut de chaque ligne sous le haut du panneau, en pixels de texte (le panneau en compte 40). */
    private static final int[] LINE_TOP = {4, 16, 28};

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
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        // Le texte regarde vers +z : le bloc est tourné pour que sa face avant y soit.
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0.0D, TOP_FROM_CENTER, FACE_FROM_CENTER + EPSILON);
        float scale = 1.0F / PIXELS_PER_BLOCK;
        pose.scale(scale, -scale, scale);
        line(pose, buffers, light, TownFinances.titleLine(), LINE_TOP[0], TITLE_COLOR);
        line(pose, buffers, light, TownFinances.balanceLine(snapshot), LINE_TOP[1], BALANCE_COLOR);
        line(pose, buffers, light, TownFinances.owedLine(snapshot), LINE_TOP[2], owed ? OWED_COLOR : NONE_COLOR);
        pose.popPose();
    }

    /** Une ligne centrée, rétrécie pour tenir dans la face si elle est trop large. */
    private void line(PoseStack pose, MultiBufferSource buffers, int light, Component text, int top, int color) {
        int width = font.width(text);
        float shrink = width > MAX_WIDTH ? MAX_WIDTH / width : 1.0F;
        pose.pushPose();
        // Centrée sur la ligne de 9 px, que le rétrécissement soit appliqué ou non.
        pose.translate(-width * shrink / 2.0F, top + (font.lineHeight * (1.0F - shrink)) / 2.0F, 0.0F);
        pose.scale(shrink, shrink, 1.0F);
        Matrix4f matrix = pose.last().pose();
        font.drawInBatch(text, 0.0F, 0.0F, color, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
