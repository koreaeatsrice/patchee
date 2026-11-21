package com.jointspaceforce.patchee.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.potion.Potion;

import org.junit.jupiter.api.Test;

import com.jointspaceforce.patchee.asm.FMLPlugin;

/**
 * Pins the two pieces of wiring the Super Tank fix cannot lose silently.
 *
 * <p>
 * The fix only clears debuffs for a client that runs Patchee: the FML handshake
 * transformer records that answer, and the server-side tick handler reads it. If
 * the transformer is dropped from the coremod's list, every other test still
 * passes but Patchee clients quietly get plain GregTech hunger on the server.
 *
 * <p>
 * The other half is the data the handler uses: the four effect ids it clears and
 * the 130-139 metas that decide which carried item counts as a Super Tank or
 * Super Chest. A wrong id or a shifted range is a silent no-op at runtime, so it
 * is checked here against GregTech 5.09.54.265's own numbers.
 */
class SuperTankWiringTest {

    @Test
    void handshakeTransformerIsRegistered() {
        String[] transformers = new FMLPlugin().getASMTransformerClass();
        String wanted = com.jointspaceforce.patchee.asm.HandshakeModListTransformer.class.getName();
        boolean found = false;
        for (String transformer : transformers) {
            if (wanted.equals(transformer)) {
                found = true;
                break;
            }
        }
        assertTrue(
            found,
            "FMLPlugin.getASMTransformerClass() must still list " + wanted
                + " — without it the server never learns which clients run Patchee, so Patchee clients"
                + " silently keep GregTech's hunger while carrying a Super Tank. Listed: "
                + Arrays.toString(transformers));
    }

    @Test
    void debuffIdsAreTheFourGregtechEffects() throws ReflectiveOperationException {
        Set<Integer> expected = new LinkedHashSet<>(
            Arrays.asList(Potion.hunger.id, Potion.moveSlowdown.id, Potion.digSlowdown.id, Potion.weakness.id));
        Set<Integer> actual = readDebuffIds();
        assertEquals(
            expected,
            actual,
            "SuperTankFeature.DEBUFF_IDS must be exactly GT's four effects (hunger, moveSlowdown,"
                + " digSlowdown, weakness); expected ids "
                + expected
                + " but the field holds "
                + actual);
    }

    @Test
    void qualifyingMetaRangeIsSuperTankAndSuperChest() throws ReflectiveOperationException {
        int low = readIntConstant("SUPER_STORAGE_MIN_META");
        int high = readIntConstant("SUPER_STORAGE_MAX_META");
        assertEquals(
            130,
            low,
            "SUPER_STORAGE_MIN_META must be 130 — in GT 5.09.54.265 the Super Tank LV..IV are metas 130-134"
                + " and the Super Chest LV..IV are 135-139; a lower bound above 130 would skip the Super Tank");
        assertEquals(
            139,
            high,
            "SUPER_STORAGE_MAX_META must be 139 — in GT 5.09.54.265 the Super Tank LV..IV are metas 130-134"
                + " and the Super Chest LV..IV are 135-139; a different bound would skip a whole tier");
    }

    /** The private DEBUFF_IDS field, as a set — accepts either an int[] or a List of Integers. */
    private static Set<Integer> readDebuffIds() throws ReflectiveOperationException {
        Field field = SuperTankFeature.class.getDeclaredField("DEBUFF_IDS");
        field.setAccessible(true);
        Object value = field.get(null);
        Set<Integer> ids = new LinkedHashSet<>();
        if (value instanceof int[]) {
            for (int id : (int[]) value) {
                ids.add(id);
            }
            return ids;
        }
        if (value instanceof List) {
            for (Object element : (List<?>) value) {
                if (!(element instanceof Integer)) {
                    fail(
                        "SuperTankFeature.DEBUFF_IDS is a List but holds " + element.getClass()
                            + " instead of Integer: "
                            + value);
                }
                ids.add((Integer) element);
            }
            return ids;
        }
        fail(
            "SuperTankFeature.DEBUFF_IDS must be an int[] or a List<Integer>, but it is " + (value == null ? "null"
                : value.getClass()
                    .getName()));
        return ids;
    }

    /** The private static int constant with the given name. */
    private static int readIntConstant(String name) throws ReflectiveOperationException {
        Field field = SuperTankFeature.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(null);
        if (!(value instanceof Integer)) {
            fail(
                "SuperTankFeature." + name
                    + " must be an int, but it is "
                    + (value == null ? "null"
                        : value.getClass()
                            .getName()));
        }
        return (Integer) value;
    }
}
