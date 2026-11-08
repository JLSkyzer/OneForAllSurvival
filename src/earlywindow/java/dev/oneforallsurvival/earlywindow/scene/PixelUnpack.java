package dev.oneforallsurvival.earlywindow.scene;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Lecture des pixels neutre le temps d'un envoi de texture depuis la mémoire du programme, puis rendue telle quelle.
 *
 * <p>Dans le contexte de Minecraft, l'état de lecture n'est pas celui par défaut : {@code NativeImage.upload} règle
 * {@code GL_UNPACK_ROW_LENGTH}, {@code SKIP_ROWS} et {@code SKIP_PIXELS} et ne les remet pas à zéro. Constaté au
 * retour d'un monde : {@code GL_UNPACK_SKIP_ROWS = 90} au premier envoi de l'atlas de la police, et le pilote a lu
 * 90 lignes au-delà du tampon (violation d'accès dans {@code glTexImage2D}). Tout envoi qui peut tourner dans ce
 * contexte passe donc par ici : décalages à zéro, alignement à 1, aucun tampon de pixels lié (sinon l'adresse du
 * tampon serait lue comme un décalage dans ce tampon). Thread GL.
 */
public final class PixelUnpack {
    private final int alignment;
    private final int rowLength;
    private final int skipRows;
    private final int skipPixels;
    private final int imageHeight;
    private final int skipImages;
    private final int unpackBuffer;

    private PixelUnpack() {
        alignment = glGetInteger(GL_UNPACK_ALIGNMENT);
        rowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
        skipRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
        skipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        imageHeight = glGetInteger(GL_UNPACK_IMAGE_HEIGHT);
        skipImages = glGetInteger(GL_UNPACK_SKIP_IMAGES);
        unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
    }

    /** Photographie l'état de lecture puis le rend neutre (alignement 1, décalages nuls, aucun tampon lié). */
    public static PixelUnpack neutral() {
        PixelUnpack saved = new PixelUnpack();
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);
        return saved;
    }

    /** Rend l'état photographié par {@link #neutral()}. */
    public void restore() {
        glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, imageHeight);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, skipImages);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
    }
}
