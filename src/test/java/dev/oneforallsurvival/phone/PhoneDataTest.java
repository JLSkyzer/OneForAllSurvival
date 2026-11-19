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
        PhoneData.PhoneRecord one = new PhoneData.PhoneRecord(OWNER, "Killian", "ab12", "cd34", 3, 99_000L, "lagon", true);
        PhoneData.PhoneRecord two = new PhoneData.PhoneRecord(null, "", null, null, 0, 0L, "nuit", false);
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
        assertEquals(PhoneWallpapers.DEFAULT, fresh.wallpaper());
        assertEquals(false, fresh.airplane());
    }

    @Test
    void effacerRendUnTelephoneNeuf() {
        PhoneData data = new PhoneData();
        data.put(A, new PhoneData.PhoneRecord(OWNER, "Killian", "s", "h", 1, 0L, "jungle", false));
        data.erase(A);
        assertNull(data.record(A).owner());
        assertEquals(PhoneWallpapers.DEFAULT, data.record(A).wallpaper());
        assertTrue(data.save(new CompoundTag()).getList("phones", 10).isEmpty());
    }

    @Test
    void fondInconnuRelDevientLeFondParDefaut() {
        PhoneData data = new PhoneData();
        data.put(A, new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 0, 0L, "lagon", false));
        CompoundTag tag = data.save(new CompoundTag());
        tag.getList("phones", 10).getCompound(0).putString("wallpaper", "inconnu");
        assertEquals(PhoneWallpapers.DEFAULT, PhoneData.load(tag).record(A).wallpaper());
    }

    @Test
    void putEtEraseMarquentLaDonneeSale() {
        PhoneData data = new PhoneData();
        assertTrue(!data.isDirty());
        data.put(A, new PhoneData.PhoneRecord(OWNER, "K", "s", "h", 0, 0L, "lagon", false));
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
}
