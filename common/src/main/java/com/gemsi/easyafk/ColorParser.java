package com.gemsi.easyafk;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns EasyAFK's colour markup into text components.
 *
 * <p>All of the markup rules live in {@link ColorSpan}; this class only maps the spans it
 * produces onto {@link Style}. Results are cached because the same handful of config
 * strings is parsed over and over - the tab list prefix alone is rebuilt for every AFK
 * player every second - and a gradient or rainbow is not cheap to expand.
 *
 * <p>Cached components are shared, so callers must treat the return value of
 * {@link #parseColors(String)} as immutable and {@link Component#copy() copy} it before
 * appending to it.
 */
public class ColorParser {

    /**
     * Config strings are few, but messages carry substituted player names and kick
     * countdowns, so the cache is bounded rather than unbounded.
     */
    private static final int CACHE_LIMIT = 256;

    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    /**
     * Parse a string with Minecraft colour codes, hex colours and gradient tags into a
     * Component. The result is shared and must not be mutated in place.
     */
    public static Component parseColors(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        Component cached = CACHE.get(text);
        if (cached != null) {
            return cached;
        }

        Component parsed = build(text);
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(text, parsed);
        return parsed;
    }

    /** Drops cached components. Call whenever the config is (re)loaded. */
    public static void invalidateCache() {
        CACHE.clear();
    }

    private static Component build(String text) {
        List<ColorSpan> spans = ColorSpan.parse(text);
        if (spans.isEmpty()) {
            // Nothing but markup, so there is no styled text to show; keep the raw string
            // rather than silently rendering nothing.
            return Component.literal(text);
        }

        MutableComponent result = Component.empty();
        for (ColorSpan span : spans) {
            result.append(Component.literal(span.text()).setStyle(styleOf(span)));
        }
        return result;
    }

    private static Style styleOf(ColorSpan span) {
        Style style = Style.EMPTY;

        if (span.hasRgb()) {
            style = style.withColor(TextColor.fromRgb(span.rgb()));
        } else if (span.hasLegacyColor()) {
            style = style.withColor(ChatFormatting.getByCode(span.legacyColor()));
        }

        if (span.bold()) {
            style = style.withBold(true);
        }
        if (span.italic()) {
            style = style.withItalic(true);
        }
        if (span.underlined()) {
            style = style.withUnderlined(true);
        }
        if (span.strikethrough()) {
            style = style.withStrikethrough(true);
        }
        if (span.obfuscated()) {
            style = style.withObfuscated(true);
        }

        return style;
    }

    /**
     * Parse a simple hex color string to RGB integer (for backward compatibility)
     */
    public static int parseColor(String hexColor) {
        return HexColor.parse(hexColor);
    }
}
