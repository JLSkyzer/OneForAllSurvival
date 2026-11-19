package dev.oneforallsurvival.client.web.mcef;

/**
 * Création d'un navigateur déjà inscrit : si la création lève, l'inscription et le navigateur sont défaits, puis l'erreur
 * repart telle quelle (une erreur du rangement lui est attachée, jamais substituée). Sans type de MCEF, pour l'essai.
 */
final class CreateGuard {

    private CreateGuard() {}

    static void run(Runnable create, Runnable undo) {
        try {
            create.run();
        } catch (RuntimeException | LinkageError e) {
            try {
                undo.run();
            } catch (RuntimeException | LinkageError suite) {
                e.addSuppressed(suite);
            }
            throw e;
        }
    }
}
