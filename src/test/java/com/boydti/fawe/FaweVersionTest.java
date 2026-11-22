package com.boydti.fawe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Version strings the build writes into fawe.properties. Every shape this fork produces must be
 * read without throwing, keep its text for /fawe version, and give a date or commit only when the
 * string has one; a throw here leaves the mod with no version at all.
 */
class FaweVersionTest {

    @Test
    void readsDevBuildFromGitDescribe() {
        FaweVersion version = new FaweVersion("version=2026.03.31-61-g9c5aea8");
        assertEquals("2026.03.31-61-g9c5aea8", version.toString());
        assertEquals("2026-03-31", version.getDate());
        assertEquals("9c5aea8", version.commit);
        assertEquals(0x9c5aea8, version.hash);
    }

    @Test
    void readsDirtyDevBuild() {
        FaweVersion version = new FaweVersion("2026.03.31-61-g9c5aea8.dirty");
        assertEquals("2026-03-31", version.getDate());
        assertEquals("9c5aea8", version.commit);
    }

    @Test
    void readsReleaseTagWithoutCommit() {
        FaweVersion version = new FaweVersion("version=2026.11.22");
        assertEquals("2026.11.22", version.toString());
        assertEquals(2026, version.year);
        assertEquals(11, version.month);
        assertEquals(22, version.day);
        assertNull(version.commit);
        assertEquals(0, version.hash);
    }

    @Test
    void readsPreReleaseCommitNotTimeZone() {
        FaweVersion version = new FaweVersion("version=2026.11.22-pre.1430.UTC+0000+abc1234");
        assertEquals("2026.11.22-pre.1430.UTC+0000+abc1234", version.toString());
        assertEquals("2026-11-22", version.getDate());
        assertEquals("abc1234", version.commit);
    }

    @Test
    void readsUpstreamFormat() {
        FaweVersion version = new FaweVersion("24.01.12-326545e-1305-23.0.0");
        assertEquals("24.01.12-326545e-1305-23.0.0", version.toString());
        assertEquals("2024-01-12", version.getDate());
        assertEquals("326545e", version.commit);
        assertEquals(1305, version.build);
        assertEquals(23, version.major);
    }

    @Test
    void unexpandedPlaceholderIsDev() {
        FaweVersion version = new FaweVersion("version=${version}");
        assertEquals("dev", version.toString());
        assertFalse(version.hasDate());
        assertNull(version.commit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "version=", "unknown", "garbage", "2026.13.40", "1.0-gzzzzzzz",
            "99.99.99-zz-1-1.2.3", "--++==.."})
    void neverThrowsAndGuessesNothing(String input) {
        FaweVersion version = new FaweVersion(input);
        assertFalse(version.hasDate());
        assertNull(version.getDate());
        assertNull(version.commit);
    }

    @Test
    void keepsWhatCanBeReadFromPartialStrings() {
        FaweVersion noCommit = new FaweVersion("2026.11.22-pre.1430.UTC+0000");
        assertEquals("2026-11-22", noCommit.getDate());
        assertNull(noCommit.commit);

        FaweVersion hugeBuild = new FaweVersion("24.01.12-326545e-99999999999-1.2.3");
        assertEquals("326545e", hugeBuild.commit);
        assertEquals(0, hugeBuild.build);
    }

    @Test
    void nullIsUnknown() {
        assertEquals("unknown", new FaweVersion(null).toString());
    }
}
