package com.ohhapple.monster_conquest.util;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模组版本解析与比较。
 *   只接受完整的 vMAJOR.MINOR.PATCH[-PRERELEASE][+BUILD] 形式，避免从 Minecraft
 *   版本或文件名中的其他数字误读成模组版本。
 */
public final class ModVersion implements Comparable<ModVersion> {

    private static final Pattern PATTERN = Pattern.compile(
            "^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)"
                    + "(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    private final int major;
    private final int minor;
    private final int patch;
    @Nullable private final String preRelease;

    private ModVersion(int major, int minor, int patch, @Nullable String preRelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
    }

    @Nullable
    public static ModVersion parse(@Nullable String value) {
        if (value == null) return null;
        Matcher matcher = PATTERN.matcher(value.trim());
        if (!matcher.matches()) return null;
        return new ModVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                matcher.group(4));
    }

    public boolean isNewerThan(ModVersion other) {
        int core = compareTo(other);
        if (core != 0) return core > 0;
        if (preRelease == null) return other.preRelease != null;
        if (other.preRelease == null) return false;
        return comparePreRelease(preRelease, other.preRelease) > 0;
    }

    @Override
    public int compareTo(ModVersion other) {
        int majorResult = Integer.compare(major, other.major);
        if (majorResult != 0) return majorResult;
        int minorResult = Integer.compare(minor, other.minor);
        return minorResult != 0 ? minorResult : Integer.compare(patch, other.patch);
    }

    public String display() {
        return major + "." + minor + "." + patch
                + (preRelease == null ? "" : "-" + preRelease);
    }

    private static int comparePreRelease(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int count = Math.min(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            boolean leftNumeric = leftParts[i].matches("\\d+");
            boolean rightNumeric = rightParts[i].matches("\\d+");
            int compared;
            if (leftNumeric && rightNumeric) {
                compared = Long.compare(
                        Long.parseLong(leftParts[i]), Long.parseLong(rightParts[i]));
            } else if (leftNumeric != rightNumeric) {
                compared = leftNumeric ? -1 : 1;
            } else {
                compared = leftParts[i].compareTo(rightParts[i]);
            }
            if (compared != 0) return compared;
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }
}
