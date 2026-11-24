package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Kind;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Le dernier état reçu ne survit pas à la sortie du monde (il peut porter le nom du propriétaire). */
class PhoneClientTest {

    @Test
    void laDeconnexionOublieLeDernierEtat() {
        PhoneClient.remember(new PhoneStatePacket(3, Kind.UPDATE, Lock.UNLOCKED, 50, 0L, 0, "nuit", true, true, "Alex",
                5, 0, Result.NONE, CloseReason.NONE, dev.oneforallsurvival.phone.PhoneNetView.NONE));
        assertTrue(PhoneClient.last().isPresent());
        PhoneClient.onLoggingOut(new ClientPlayerNetworkEvent.LoggingOut(null, null, null));
        assertEquals(Optional.empty(), PhoneClient.last());
    }

    /**
     * L'annotation se lit dans le fichier de classe (ASM) : hors de FML, charger l'énumération {@code Bus} de Forge
     * échoue.
     */
    @Test
    void lOubliEstBrancheSurLeBusDuClient() throws IOException {
        Map<String, String> abonne = new HashMap<>();
        boolean[] souscrit = {false};
        try (InputStream in = PhoneClient.class.getResourceAsStream("PhoneClient.class")) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (!desc.equals("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;")) {
                        return null;
                    }
                    abonne.put("present", "oui");
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitEnum(String name, String d, String value) {
                            abonne.put(name, value);
                        }

                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visitEnum(String n, String d, String value) {
                                    abonne.merge(name, value, (a, b) -> a + "," + b);
                                }
                            };
                        }
                    };
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                    if (!name.equals("onLoggingOut")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String d, boolean visible) {
                            if (d.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")) {
                                souscrit[0] = true;
                            }
                            return null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE);
        }
        assertTrue(souscrit[0], "onLoggingOut sans @SubscribeEvent");
        assertEquals("oui", abonne.get("present"), "PhoneClient n'est pas abonné au bus de Forge");
        assertEquals("FORGE", abonne.get("bus"));
        assertEquals("CLIENT", abonne.get("value"));
    }
}
