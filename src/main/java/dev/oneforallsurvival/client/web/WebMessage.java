package dev.oneforallsurvival.client.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.util.Optional;

/** Lecture d'un message envoyé par une page : jamais cru sur parole, tout défaut donne « vide ». */
public final class WebMessage {

    public static final int MAX_LENGTH = 4096;
    private static final int MAX_ACTION = 32;
    private static final Gson GSON = new Gson();

    private WebMessage() {}

    /** Objet JSON strict, d'au plus {@link #MAX_LENGTH} caractères, dont le champ {@code "a"} est une chaîne de 1 à 32. */
    public static Optional<JsonObject> parse(String raw) {
        if (raw == null || raw.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        try (JsonReader reader = new JsonReader(new StringReader(raw))) {
            reader.setLenient(false);
            JsonElement element = GSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || element == null || !element.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = element.getAsJsonObject();
            JsonElement action = object.get("a");
            if (action == null || !action.isJsonPrimitive() || !action.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }
            int length = action.getAsString().length();
            return length >= 1 && length <= MAX_ACTION ? Optional.of(object) : Optional.empty();
        } catch (Exception | StackOverflowError e) {
            return Optional.empty();
        }
    }
}
