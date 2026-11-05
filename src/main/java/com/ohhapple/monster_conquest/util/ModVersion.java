package com.ohhapple.monster_conquest.util;

import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 宽松版本排序。数字段按数值比较，其余文本按自然顺序比较；
 * 常见预发布标记低于正式版，未知后缀视为同核心版本的后续构建。
 */
public final class ModVersion implements Comparable<ModVersion> {

    private static final Set<String> PRERELEASE_MARKERS = Set.of(
            "alpha", "beta", "rc", "pre", "preview",
            "snapshot", "snap", "dev", "nightly");

    private final String display;
    private final List<Token> tokens;

    private ModVersion(String display, List<Token> tokens) {
        this.display = display;
        this.tokens = List.copyOf(tokens);
    }

    @Nullable
    public static ModVersion parse(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        String normalized = trimmed;
        if (normalized.length() > 1
                && (normalized.charAt(0) == 'v'
                || normalized.charAt(0) == 'V')
                && isAsciiDigit(normalized.charAt(1))) {
            normalized = normalized.substring(1);
        }
        List<Token> tokens = tokenize(normalized);
        return tokens.isEmpty()
                ? null : new ModVersion(trimmed, tokens);
    }

    public boolean isNewerThan(ModVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(ModVersion other) {
        int count = Math.min(tokens.size(), other.tokens.size());
        for (int index = 0; index < count; index++) {
            int compared = tokens.get(index)
                    .compareTo(other.tokens.get(index));
            if (compared != 0) return compared;
        }
        if (tokens.size() == other.tokens.size()) return 0;
        if (tokens.size() < other.tokens.size()) {
            return startsWithPrerelease(
                    other.tokens, tokens.size()) ? 1 : -1;
        }
        return startsWithPrerelease(
                tokens, other.tokens.size()) ? -1 : 1;
    }

    public String display() {
        return display;
    }

    private static boolean startsWithPrerelease(
            List<Token> tokens, int offset) {
        if (offset < 0 || offset >= tokens.size()) return false;
        Token marker = tokens.get(offset);
        return !marker.numeric()
                && PRERELEASE_MARKERS.contains(
                marker.text().toLowerCase(Locale.ROOT));
    }

    private static List<Token> tokenize(String value) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (isAsciiDigit(current)) {
                int start = index++;
                while (index < value.length()
                        && isAsciiDigit(value.charAt(index))) {
                    index++;
                }
                tokens.add(Token.number(
                        value.substring(start, index)));
                continue;
            }
            if (Character.isLetterOrDigit(current)) {
                int start = index++;
                while (index < value.length()) {
                    char next = value.charAt(index);
                    if (isAsciiDigit(next)
                            || !Character.isLetterOrDigit(next)) {
                        break;
                    }
                    index++;
                }
                tokens.add(Token.text(
                        value.substring(start, index)));
                continue;
            }
            index++;
        }
        return tokens;
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private record Token(
            boolean numeric, @Nullable BigInteger number,
            String text) implements Comparable<Token> {

        private static Token number(String value) {
            return new Token(true, new BigInteger(value), "");
        }

        private static Token text(String value) {
            return new Token(false, null, value);
        }

        @Override
        public int compareTo(Token other) {
            if (numeric != other.numeric) {
                return numeric ? -1 : 1;
            }
            if (numeric) {
                return number.compareTo(other.number);
            }
            return text.compareToIgnoreCase(other.text);
        }
    }
}
