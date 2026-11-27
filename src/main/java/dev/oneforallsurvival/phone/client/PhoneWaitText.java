package dev.oneforallsurvival.phone.client;

/**
 * La durée restante d'un blocage telle que le téléphone l'écrit, sans Minecraft (testable) : en secondes jusqu'à
 * {@value #MINUTES_FROM} exclu, puis en minutes et secondes (un blocage peut durer une heure : « 3600 s » ne se lit
 * pas). La même règle sert l'écran de repli et la page ({@code WAIT_TEXTS} de {@code html/phone/phone.js}), avec en
 * français les textes de {@code fr_fr.json} (garde : {@code PhoneWaitTextTest}).
 */
public final class PhoneWaitText {

    /** La première durée, en secondes, écrite en minutes. */
    public static final int MINUTES_FROM = 120;

    /** La forme du texte, avec sa clé de langue. */
    public enum Form {
        SECONDS("screen.oneforallsurvival.phone.wait_s"),
        MINUTES("screen.oneforallsurvival.phone.wait_min"),
        MINUTES_SECONDS("screen.oneforallsurvival.phone.wait_min_s");

        private final String key;

        Form(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /**
     * @param first le premier nombre du texte : les secondes ({@link Form#SECONDS}), sinon les minutes
     * @param second les secondes au-delà des minutes ({@link Form#MINUTES_SECONDS} seulement, 0 sinon)
     */
    public record Wait(Form form, long first, long second) {

        /** Les nombres à poser dans le texte de {@link Form#key()}, dans l'ordre. */
        public Object[] arguments() {
            return form == Form.MINUTES_SECONDS ? new Object[] {first, second} : new Object[] {first};
        }
    }

    private PhoneWaitText() {}

    public static Wait of(long seconds) {
        if (seconds < MINUTES_FROM) {
            return new Wait(Form.SECONDS, seconds, 0);
        }
        long rest = seconds % 60;
        return new Wait(rest == 0 ? Form.MINUTES : Form.MINUTES_SECONDS, seconds / 60, rest);
    }
}
