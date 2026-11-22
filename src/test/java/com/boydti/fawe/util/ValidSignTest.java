package com.boydti.fawe.util;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.blocks.SignBlock;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standing signs (id 63) keep their NBT only when it passes this check. A sign typed as
 * standing_sign|text carries just the four text lines, and must keep them like a wall sign does.
 */
class ValidSignTest {

    @Test
    void typedSignTextIsKept() {
        SignBlock sign = new SignBlock(63, 0, new String[]{"line1", "line2", "", ""});
        assertTrue(MainUtil.isValidSign(sign.getNbtData()));
    }

    @Test
    void signReadFromTheWorldIsKept() {
        Map<String, Tag> values = new HashMap<>(new SignBlock(63, 0, new String[]{"a", "", "", ""}).getNbtData().getValue());
        values.put("id", new StringTag("minecraft:sign"));
        values.put("x", new IntTag(1));
        values.put("y", new IntTag(2));
        values.put("z", new IntTag(3));
        assertTrue(MainUtil.isValidSign(new CompoundTag(values)));
    }

    @Test
    void tagWithoutTextIsDropped() {
        assertFalse(MainUtil.isValidSign(new CompoundTag(new HashMap<>())));
    }
}
