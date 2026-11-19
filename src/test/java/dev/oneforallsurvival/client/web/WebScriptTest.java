package dev.oneforallsurvival.client.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

class WebScriptTest {

    @Test
    void appelBienForme() {
        JsonObject obj = new JsonObject();
        obj.addProperty("k", 1);
        String js = WebScript.call("phone.render", obj);
        assertTrue(js.startsWith("phone.render("), js);
        assertTrue(js.endsWith(")"), js);
        assertEquals(obj, JsonParser.parseString(js.substring("phone.render(".length(), js.length() - 1)));
    }

    @Test
    void sansArgumentEtAvecPlusieurs() {
        assertEquals("f()", WebScript.call("f"));
        assertEquals("f(1,\"a\")", WebScript.call("f", new JsonPrimitive(1), new JsonPrimitive("a")));
    }

    @Test
    void chaineHostileEchappeeEtRelue() {
        String hostile = "</script>\u2028x\u2029y'&=\"\\";
        String js = WebScript.call("f", new JsonPrimitive(hostile));
        assertTrue(js.contains("\\u2028"), js);
        assertTrue(js.contains("\\u2029"), js);
        assertFalse(js.contains("\u2028"), "U+2028 brut");
        assertFalse(js.contains("\u2029"), "U+2029 brut");
        assertFalse(js.contains("<"), "chevron brut : " + js);
        assertFalse(js.contains("</script>"), js);
        JsonElement relu = JsonParser.parseString(js.substring(2, js.length() - 1));
        assertEquals(hostile, relu.getAsString());
    }

    @Test
    void nomDeFonctionHostileRefuse() {
        for (String mauvais : new String[] {"alert(1);x", "", "1a", "a b", "a-b", "a;b", "a.b)"}) {
            assertThrows(IllegalArgumentException.class, () -> WebScript.call(mauvais, new JsonPrimitive(1)), mauvais);
        }
        assertThrows(IllegalArgumentException.class, () -> WebScript.call(null));
        assertEquals("$a_1.b()", WebScript.call("$a_1.b"));
    }
}
