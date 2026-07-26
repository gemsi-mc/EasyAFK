package com.gemsi.easyafk;

import java.util.ArrayList;
import java.util.List;

/**
 * A run of text that shares one colour and one set of format flags, plus the parser that
 * turns EasyAFK's message markup into a list of them.
 *
 * <p>This is the plain-Java seam under {@link ColorParser}: every markup rule lives here
 * and is unit tested, while ColorParser only maps spans onto Minecraft text components.
 * Runs are coalesced as they are parsed, so a colour that repeats over several characters
 * produces one span rather than one per character.
 *
 * <p>Supported markup:
 * <ul>
 *   <li>{@code &c}, {@code &l}, {@code &r} - legacy colour, format and reset codes (lower case only)</li>
 *   <li>{@code &#RRGGBB} - hex colour</li>
 *   <li>{@code <gradient:#RRGGBB:#RRGGBB>text</gradient>} - per-character interpolation</li>
 *   <li>{@code <rainbow>text</rainbow>} - per-character interpolation</li>
 * </ul>
 *
 * <p>As in Minecraft, a colour code clears the active format flags while a format code
 * keeps the active colour. Gradient and rainbow bodies are literal text: they inherit the
 * format flags in force at the opening tag and own the colour of every character in the
 * body. Spaces keep the colour of the character before them, so they never split a run.
 * Anything that does not parse as markup is kept verbatim, {@code &} included.
 */
public record ColorSpan(String text,
                        char legacyColor,
                        int rgb,
                        boolean bold,
                        boolean italic,
                        boolean underlined,
                        boolean strikethrough,
                        boolean obfuscated) {

    /** Sentinel for "this span carries no legacy colour code". */
    public static final char NO_LEGACY_COLOR = '\0';

    /** Sentinel for "this span carries no hex colour". */
    public static final int NO_RGB = -1;

    private static final String GRADIENT_OPEN = "<gradient:";
    private static final String GRADIENT_CLOSE = "</gradient>";
    private static final String RAINBOW_OPEN = "<rainbow>";
    private static final String RAINBOW_CLOSE = "</rainbow>";

    /** Legacy codes that set a colour, and therefore clear formatting. */
    private static final String COLOR_CODES = "0123456789abcdef";

    private static final int[] RAINBOW = {
            0xFF0000, 0xFF7F00, 0xFFFF00, 0x00FF00, 0x0000FF, 0x4B0082, 0x9400D3
    };

    public boolean hasLegacyColor() {
        return legacyColor != NO_LEGACY_COLOR;
    }

    public boolean hasRgb() {
        return rgb != NO_RGB;
    }

    /**
     * Splits {@code text} into styled runs.
     *
     * @return the runs in order, or an empty list if the input holds no printable text
     */
    public static List<ColorSpan> parse(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        Builder builder = new Builder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            if (c == '<') {
                int consumed = readTag(text, i, builder);
                if (consumed > 0) {
                    i += consumed;
                    continue;
                }
            } else if (c == '&' && i + 1 < text.length()) {
                int consumed = readCode(text, i, builder);
                if (consumed > 0) {
                    i += consumed;
                    continue;
                }
            }

            builder.append(c);
            i++;
        }
        return builder.finish();
    }

    /** @return how many characters the tag at {@code start} consumed, or 0 if it is not a tag */
    private static int readTag(String text, int start, Builder builder) {
        if (text.startsWith(GRADIENT_OPEN, start)) {
            // <gradient:#RRGGBB:#RRGGBB> - 7 hex chars, a colon, 7 more, then the '>'
            int bodyStart = start + GRADIENT_OPEN.length() + 16;
            if (bodyStart > text.length()
                    || text.charAt(start + GRADIENT_OPEN.length() + 7) != ':'
                    || text.charAt(bodyStart - 1) != '>') {
                return 0;
            }
            Integer from = readHexColor(text, start + GRADIENT_OPEN.length());
            Integer to = readHexColor(text, start + GRADIENT_OPEN.length() + 8);
            if (from == null || to == null) {
                return 0;
            }
            int bodyEnd = text.indexOf(GRADIENT_CLOSE, bodyStart);
            if (bodyEnd < 0) {
                return 0;
            }
            appendGradient(text.substring(bodyStart, bodyEnd), from, to, builder);
            return bodyEnd + GRADIENT_CLOSE.length() - start;
        }

        if (text.startsWith(RAINBOW_OPEN, start)) {
            int bodyStart = start + RAINBOW_OPEN.length();
            int bodyEnd = text.indexOf(RAINBOW_CLOSE, bodyStart);
            if (bodyEnd < 0) {
                return 0;
            }
            appendRainbow(text.substring(bodyStart, bodyEnd), builder);
            return bodyEnd + RAINBOW_CLOSE.length() - start;
        }

        return 0;
    }

    /** @return how many characters the code at {@code start} consumed, or 0 if it is not a code */
    private static int readCode(String text, int start, Builder builder) {
        char code = text.charAt(start + 1);

        if (code == '#') {
            Integer rgb = readHexColor(text, start + 1);
            if (rgb == null) {
                return 0;
            }
            builder.rgb(rgb);
            return 8;
        }

        if (COLOR_CODES.indexOf(code) >= 0) {
            builder.legacyColor(code);
            return 2;
        }

        switch (code) {
            case 'k' -> builder.obfuscated();
            case 'l' -> builder.bold();
            case 'm' -> builder.strikethrough();
            case 'n' -> builder.underlined();
            case 'o' -> builder.italic();
            case 'r' -> builder.reset();
            default -> {
                return 0;
            }
        }
        return 2;
    }

    /**
     * Reads {@code #RRGGBB} at {@code at}.
     *
     * @return the packed colour, or null if there is no well formed hex colour there
     */
    private static Integer readHexColor(String text, int at) {
        if (at + 7 > text.length() || text.charAt(at) != '#') {
            return null;
        }
        try {
            return Integer.parseInt(text, at + 1, at + 7, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void appendGradient(String body, int from, int to, Builder builder) {
        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == ' ') {
                builder.append(c);
                continue;
            }
            float ratio = body.length() > 1 ? (float) i / (body.length() - 1) : 0;
            builder.interpolatedRgb(pack(
                    fromR + (toR - fromR) * ratio,
                    fromG + (toG - fromG) * ratio,
                    fromB + (toB - fromB) * ratio));
            builder.append(c);
        }
    }

    private static void appendRainbow(String body, Builder builder) {
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == ' ') {
                builder.append(c);
                continue;
            }
            float position = (float) i / body.length() * RAINBOW.length;
            int index = (int) position;
            int next = (index + 1) % RAINBOW.length;
            float ratio = position - index;

            int fromColor = RAINBOW[index];
            int toColor = RAINBOW[next];
            builder.interpolatedRgb(pack(
                    ((fromColor >> 16) & 0xFF) + (((toColor >> 16) & 0xFF) - ((fromColor >> 16) & 0xFF)) * ratio,
                    ((fromColor >> 8) & 0xFF) + (((toColor >> 8) & 0xFF) - ((fromColor >> 8) & 0xFF)) * ratio,
                    (fromColor & 0xFF) + ((toColor & 0xFF) - (fromColor & 0xFF)) * ratio));
            builder.append(c);
        }
    }

    private static int pack(float r, float g, float b) {
        return ((int) r << 16) | ((int) g << 8) | (int) b;
    }

    /**
     * Accumulates characters into runs, starting a new run only when the style actually
     * changes. Every style change flushes whatever text came before it.
     */
    private static final class Builder {

        private final List<ColorSpan> spans = new ArrayList<>();
        private final StringBuilder pending = new StringBuilder();

        private char legacyColor = NO_LEGACY_COLOR;
        private int rgb = NO_RGB;
        private boolean bold;
        private boolean italic;
        private boolean underlined;
        private boolean strikethrough;
        private boolean obfuscated;

        void append(char c) {
            pending.append(c);
        }

        void legacyColor(char code) {
            flush();
            legacyColor = code;
            rgb = NO_RGB;
            clearFormats();
        }

        void rgb(int value) {
            flush();
            legacyColor = NO_LEGACY_COLOR;
            rgb = value;
            clearFormats();
        }

        /**
         * Colour from a gradient or rainbow body. Unlike an explicit colour code this keeps
         * the active format flags, and is a no-op when the colour has not changed, which is
         * what lets a run of identically coloured characters stay a single span.
         */
        void interpolatedRgb(int value) {
            if (rgb == value && legacyColor == NO_LEGACY_COLOR) {
                return;
            }
            flush();
            legacyColor = NO_LEGACY_COLOR;
            rgb = value;
        }

        void bold() {
            if (!bold) {
                flush();
                bold = true;
            }
        }

        void italic() {
            if (!italic) {
                flush();
                italic = true;
            }
        }

        void underlined() {
            if (!underlined) {
                flush();
                underlined = true;
            }
        }

        void strikethrough() {
            if (!strikethrough) {
                flush();
                strikethrough = true;
            }
        }

        void obfuscated() {
            if (!obfuscated) {
                flush();
                obfuscated = true;
            }
        }

        void reset() {
            flush();
            legacyColor = NO_LEGACY_COLOR;
            rgb = NO_RGB;
            clearFormats();
        }

        private void clearFormats() {
            bold = false;
            italic = false;
            underlined = false;
            strikethrough = false;
            obfuscated = false;
        }

        private void flush() {
            if (pending.isEmpty()) {
                return;
            }
            spans.add(new ColorSpan(pending.toString(), legacyColor, rgb,
                    bold, italic, underlined, strikethrough, obfuscated));
            pending.setLength(0);
        }

        List<ColorSpan> finish() {
            flush();
            return List.copyOf(spans);
        }
    }
}
