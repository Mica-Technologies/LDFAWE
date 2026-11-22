package com.boydti.fawe;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version this jar was built as, read from fawe.properties.
 * <p>
 * The build writes whatever version Gradle produced, which for this fork is one of
 * {@code 2026.03.31-61-g9c5aea8} (a dev build from git describe, possibly with a dirty suffix),
 * {@code 2026.11.22} (a release tag) or {@code 2026.11.22-pre.1430.UTC+0000+abc1234} (a
 * pre-release). Jars built by upstream FAWE used {@code yy.mm.dd-hash-build-major.minor.patch}.
 * The raw text is always kept for display, and the other fields are filled only when they can be
 * read from it, so they are 0 (or null for {@link #commit}) when unknown. Parsing never throws,
 * because a version string the code does not recognise must not stop the mod from starting.
 */
public class FaweVersion {
    // A four digit year, so the two digit dates of upstream jars are not taken for year 24.
    private static final Pattern DATE = Pattern.compile("^(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})(?![\\d])");
    // The commit is the last part of the string, after "-g" (git describe) or "+" (pre-release);
    // anchoring it at the end keeps the "+0000" of the time zone from being read as a commit.
    private static final Pattern COMMIT = Pattern.compile("(?:-g|\\+)([0-9a-fA-F]{7,40})(?:[.-]dirty)?$");
    private static final Pattern UPSTREAM = Pattern.compile("^(\\d{2})\\.(\\d{1,2})\\.(\\d{1,2})-([0-9a-fA-F]+)-(\\d+)-(\\d+)\\.(\\d+)\\.(\\d+)$");

    /**
     * Build date (full four digit year, month 1-12, day 1-31), or 0 when the version has no date.
     */
    public final int year, month, day;
    /**
     * The first eight hex digits of {@link #commit} as a number, or 0 when there is no commit.
     * Use {@link #commit} for display, since this drops leading zeros.
     */
    public final int hash;
    /**
     * Upstream build number and version, only known for jars in upstream's format, otherwise 0.
     */
    public final int build, major, minor, patch;
    /**
     * The abbreviated git commit the jar was built from, or null when the version does not say.
     */
    public final String commit;

    private final String display;

    public FaweVersion(String version) {
        String text = version == null ? "" : version.trim();
        int equals = text.indexOf('=');
        if (equals != -1) {
            text = text.substring(equals + 1).trim();
        }

        int year = 0, month = 0, day = 0, build = 0, major = 0, minor = 0, patch = 0;
        String commit = null;
        String display;
        if (text.isEmpty() || text.equalsIgnoreCase("unknown")) {
            display = "unknown";
        } else if (text.contains("${")) {
            // Running from an IDE without resource processing leaves the placeholder unexpanded.
            display = "dev";
        } else {
            display = text;
            Matcher upstream = UPSTREAM.matcher(text);
            if (upstream.matches()) {
                year = 2000 + Integer.parseInt(upstream.group(1));
                month = Integer.parseInt(upstream.group(2));
                day = Integer.parseInt(upstream.group(3));
                commit = upstream.group(4).toLowerCase();
                build = parseInt(upstream.group(5));
                major = parseInt(upstream.group(6));
                minor = parseInt(upstream.group(7));
                patch = parseInt(upstream.group(8));
            } else {
                Matcher date = DATE.matcher(text);
                if (date.find()) {
                    year = Integer.parseInt(date.group(1));
                    month = Integer.parseInt(date.group(2));
                    day = Integer.parseInt(date.group(3));
                }
                Matcher hash = COMMIT.matcher(text);
                if (hash.find()) {
                    commit = hash.group(1).toLowerCase();
                }
            }
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                year = month = day = 0;
            }
        }
        this.year = year;
        this.month = month;
        this.day = day;
        this.build = build;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.commit = commit;
        this.hash = commit == null ? 0 : (int) Long.parseLong(commit.substring(0, Math.min(8, commit.length())), 16);
        this.display = display;
    }

    // Numbers too long for an int are treated as unknown rather than failing the whole version.
    private static int parseInt(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * @return true when a build date was read from the version
     */
    public boolean hasDate() {
        return year != 0;
    }

    /**
     * @return the build date as yyyy-mm-dd, or null when the version has no date
     */
    public String getDate() {
        return hasDate() ? String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day) : null;
    }

    /**
     * @return the version as the build wrote it, "dev" for an unexpanded placeholder, or "unknown"
     */
    @Override
    public String toString() {
        return display;
    }
}
