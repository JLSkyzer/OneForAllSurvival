package dev.oneforallsurvival.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PhoneDataTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    @Test
    void allerRetourDeDeuxTelephones() {
        PhoneData data = new PhoneData();
        PhoneData.PhoneRecord one = new PhoneData.PhoneRecord(OWNER, "Killian", "ab12", "cd34", 3, 99_000L, 4, "lagon",
                true);
        PhoneData.PhoneRecord two = new PhoneData.PhoneRecord(null, "", null, null, 0, 0L, 0, "nuit", false);
        data.put(A, one);
        data.put(B, two);

        PhoneData back = PhoneData.load(data.save(new CompoundTag()));

        assertEquals(one, back.record(A));
        assertEquals(two, back.record(B));
    }

    @Test
    void identifiantInconnuDonneUnTelephoneNeuf() {
        PhoneData.PhoneRecord fresh = new PhoneData().record(A);
        assertNull(fresh.owner());
        assertNull(fresh.salt());
        assertNull(fresh.codeHash());
        assertEquals(0, fresh.failures());
        assertEquals(0L, fresh.lockedUntil());
        assertEquals(0, fresh.lockoutLevel());
        assertEquals(PhoneWallpapers.DEFAULT, fresh.wallpaper());
        assertEquals(false, fresh.airplane());
    }

    @Test
    void effacerRendUnTelephoneNeuf() {
        PhoneData data = new PhoneData();
        data.put(A, new PhoneData.PhoneRecord(OWNER, "Killian", "s", "h", 1, 0L, 0, "jungle", false));
        data.erase(A);
        assertNull(data.record(A).owner());
        assertEquals(PhoneWallpapers.DEFAULT, data.record(A).wallpaper());
        assertTrue(data.save(new CompoundTag()).getList("phones", 10).isEmpty());
    }

    @Test
    void fondInconnuRelDevientLeFondParDefaut() {
        PhoneData data = new PhoneData();
        data.put(A, new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 0, 0L, 0, "lagon", false));
        CompoundTag tag = data.save(new CompoundTag());
        tag.getList("phones", 10).getCompound(0).putString("wallpaper", "inconnu");
        assertEquals(PhoneWallpapers.DEFAULT, PhoneData.load(tag).record(A).wallpaper());
    }

    @Test
    void putEtEraseMarquentLaDonneeSale() {
        PhoneData data = new PhoneData();
        assertTrue(!data.isDirty());
        data.put(A, new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 0, 0L, 0, "lagon", false));
        assertTrue(data.isDirty());
        data.setDirty(false);
        data.erase(A);
        assertTrue(data.isDirty());
    }

    @Test
    void effacerUnInconnuNeSalitPas() {
        PhoneData data = new PhoneData();
        data.erase(A);
        assertTrue(!data.isDirty());
    }

    @Test
    void blocageNegatifRelDevientZero() {
        PhoneData data = new PhoneData();
        data.put(A, new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 0, 5L, 0, "lagon", false));
        CompoundTag tag = data.save(new CompoundTag());
        tag.getList("phones", 10).getCompound(0).putLong("lockedUntil", -42L);
        assertEquals(0L, PhoneData.load(tag).record(A).lockedUntil());
    }

    @Test
    void ligneSansIdOuMalTypeeIgnoreeSansException() {
        PhoneData data = new PhoneData();
        data.put(A, new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 2, 0L, 0, "lagon", false));
        CompoundTag tag = data.save(new CompoundTag());
        net.minecraft.nbt.ListTag list = tag.getList("phones", 10);
        CompoundTag noId = new CompoundTag();
        noId.putString("ownerName", "intrus");
        list.add(noId);
        CompoundTag badTypes = new CompoundTag();
        badTypes.putUUID("id", B);
        badTypes.putString("failures", "beaucoup");
        badTypes.putInt("salt", 7);
        badTypes.putString("lockedUntil", "x");
        badTypes.putInt("wallpaper", 3);
        list.add(badTypes);

        PhoneData back = PhoneData.load(tag);

        assertEquals(2, back.record(A).failures());
        PhoneData.PhoneRecord bad = back.record(B);
        assertNull(bad.salt());
        assertEquals(0, bad.failures());
        assertEquals(0L, bad.lockedUntil());
        assertEquals(PhoneWallpapers.DEFAULT, bad.wallpaper());
    }

    @Test
    void proprietaireSansSelNiEmpreinteEstSansCode() {
        PhoneData data = new PhoneData();
        data.put(A, new PhoneData.PhoneRecord(OWNER, "K", null, "h", 0, 0L, 3, "lagon", false));
        data.put(B, new PhoneData.PhoneRecord(OWNER, "K", "s", null, 0, 0L, 0, "lagon", false));
        assertNull(data.record(A).codeHash());
        assertNull(data.record(A).salt());
        assertNull(data.record(B).codeHash());
        assertNull(data.record(B).salt());
        assertEquals(OWNER, data.record(A).owner());
        assertEquals(3, data.record(A).lockoutLevel(), "le niveau reste quand le code manque");
    }

    /** Le niveau écrit puis relu, valeur extrême comprise. */
    @Test
    void leNiveauDeBlocageEstEcritEtRelu() {
        PhoneData data = new PhoneData();
        data.put(A, new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 0, 9L, 6, "lagon", false));
        data.put(B, new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 0, 9L, Integer.MAX_VALUE, "lagon", false));
        PhoneData back = PhoneData.load(data.save(new CompoundTag()));
        assertEquals(6, back.record(A).lockoutLevel());
        assertEquals(Integer.MAX_VALUE, back.record(B).lockoutLevel());
    }

    /** Une sauvegarde d'avant le niveau n'a pas la clé : niveau 0, le reste inchangé. */
    @Test
    void uneAncienneSauvegardeSeRelitAuNiveauZero() {
        PhoneData data = new PhoneData();
        data.put(A, new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 2, 99_000L, 5, "lagon", true));
        CompoundTag tag = data.save(new CompoundTag());
        CompoundTag line = tag.getList("phones", 10).getCompound(0);
        assertTrue(line.contains("lockoutLevel"), "la clé écrite : " + line.getAllKeys());
        line.remove("lockoutLevel");
        assertEquals(new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 2, 99_000L, 0, "lagon", true),
                PhoneData.load(tag).record(A));
    }

    @Test
    void unNiveauCorrompuRelDevientZero() {
        PhoneData data = new PhoneData();
        data.put(A, new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 0, 0L, 1, "lagon", false));
        CompoundTag tag = data.save(new CompoundTag());
        CompoundTag line = tag.getList("phones", 10).getCompound(0);
        line.putInt("lockoutLevel", -7);
        assertEquals(0, PhoneData.load(tag).record(A).lockoutLevel());
        line.putInt("lockoutLevel", Integer.MIN_VALUE);
        assertEquals(0, PhoneData.load(tag).record(A).lockoutLevel());
        line.putString("lockoutLevel", "beaucoup");
        assertEquals(0, PhoneData.load(tag).record(A).lockoutLevel());
    }

    /**
     * Face ID (retours d'essai du 27/11) : gardé à la sauvegarde ; une sauvegarde d'avant Face ID se lit sans
     * conversion, Face ID coupé ; un téléphone sans code n'a jamais Face ID.
     */
    @Test
    void faceIdSeGardeEtSeLitCoupeQuandIlManque() {
        PhoneData data = new PhoneData();
        PhoneData.PhoneRecord actif = new PhoneData.PhoneRecord(OWNER, "Killian", "ab12", "cd34", 0, 0L, 0, "lagon",
                false, true);
        data.put(A, actif);
        CompoundTag sauve = data.save(new CompoundTag());
        assertEquals(actif, PhoneData.load(sauve).record(A));

        // La forme d'un monde d'aujourd'hui : aucune clé « faceId ».
        CompoundTag ancien = sauve.copy();
        ((CompoundTag) ancien.getList("phones", 10).get(0)).remove("faceId");
        PhoneData.PhoneRecord relu = PhoneData.load(ancien).record(A);
        assertEquals(false, relu.faceId());
        assertEquals(new PhoneData.PhoneRecord(OWNER, "Killian", "ab12", "cd34", 0, 0L, 0, "lagon", false), relu);

        PhoneData sansCode = new PhoneData();
        sansCode.put(B, new PhoneData.PhoneRecord(OWNER, "Killian", null, "cd34", 0, 0L, 0, "lagon", false, true));
        assertEquals(false, sansCode.record(B).faceId(), "sans code, Face ID ne vaut rien");
    }
}
