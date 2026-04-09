package com.demonz.velocitynavigator;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemanticVersion implements Comparable<SemanticVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(.*)");

    private final String raw;
    private final int major;
    private final int minor;
    private final int patch;
    private final Qualifier qualifier;

    private SemanticVersion(String raw, int major, int minor, int patch, Qualifier qualifier) {
        this.raw = raw;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.qualifier = qualifier;
    }

    public static SemanticVersion parse(String raw) {
        String input = raw == null ? "" : raw.trim();
        Matcher matcher = VERSION_PATTERN.matcher(input);
        if (!matcher.find()) {
            return new SemanticVersion(input, 0, 0, 0, Qualifier.ALPHA);
        }

        int major = parsePart(matcher.group(1));
        int minor = parsePart(matcher.group(2));
        int patch = parsePart(matcher.group(3));
        String suffix = matcher.group(4) == null ? "" : matcher.group(4).trim();
        return new SemanticVersion(input, major, minor, patch, Qualifier.fromSuffix(suffix));
    }

    public boolean isPrerelease() {
        return qualifier == Qualifier.BETA || qualifier == Qualifier.ALPHA;
    }

    public String raw() {
        return raw;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int majorCompare = Integer.compare(major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        int minorCompare = Integer.compare(minor, other.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        int patchCompare = Integer.compare(patch, other.patch);
        if (patchCompare != 0) {
            return patchCompare;
        }
        return Integer.compare(qualifier.rank, other.qualifier.rank);
    }

    private static int parsePart(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        return Integer.parseInt(raw);
    }

    private enum Qualifier {
        ALPHA(0),
        BETA(1),
        STABLE(2),
        RELEASE(3);

        private final int rank;

        Qualifier(int rank) {
            this.rank = rank;
        }

        private static Qualifier fromSuffix(String suffix) {
            if (suffix == null || suffix.isBlank()) {
                return RELEASE;
            }
            String normalized = suffix.toLowerCase(Locale.ROOT);
            if (normalized.contains("alpha")) {
                return ALPHA;
            }
            if (normalized.contains("beta")) {
                return BETA;
            }
            if (normalized.contains("stable")) {
                return STABLE;
            }
            return RELEASE;
        }
    }
}

