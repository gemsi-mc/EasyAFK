package com.gemsi.easyafk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorSpanTest {

    @Test
    @DisplayName("plain text is one unstyled span")
    void plainText() {
        List<ColorSpan> spans = ColorSpan.parse("Steve is now AFK.");

        assertEquals(1, spans.size());
        ColorSpan span = spans.getFirst();
        assertEquals("Steve is now AFK.", span.text());
        assertFalse(span.hasRgb());
        assertFalse(span.hasLegacyColor());
        assertFalse(span.bold());
    }

    @Test
    @DisplayName("nothing to parse yields no spans")
    void empty() {
        assertEquals(List.of(), ColorSpan.parse(null));
        assertEquals(List.of(), ColorSpan.parse(""));
        assertEquals(List.of(), ColorSpan.parse("&c"));
    }

    @Test
    @DisplayName("a legacy colour code colours the text after it")
    void legacyColor() {
        List<ColorSpan> spans = ColorSpan.parse("&cdanger");

        assertEquals(1, spans.size());
        assertEquals("danger", spans.getFirst().text());
        assertEquals('c', spans.getFirst().legacyColor());
    }

    @Test
    @DisplayName("a format code keeps the active colour")
    void formatKeepsColor() {
        List<ColorSpan> spans = ColorSpan.parse("&cRed &lBold");

        assertEquals(2, spans.size());
        assertEquals("Red ", spans.get(0).text());
        assertEquals('c', spans.get(0).legacyColor());
        assertFalse(spans.get(0).bold());

        assertEquals("Bold", spans.get(1).text());
        assertEquals('c', spans.get(1).legacyColor());
        assertTrue(spans.get(1).bold());
    }

    @Test
    @DisplayName("a colour code clears the active formatting, as in vanilla")
    void colorClearsFormatting() {
        List<ColorSpan> spans = ColorSpan.parse("&lBold&cRed");

        assertEquals(2, spans.size());
        assertTrue(spans.get(0).bold());
        assertFalse(spans.get(1).bold());
        assertEquals('c', spans.get(1).legacyColor());
    }

    @Test
    @DisplayName("&r clears colour and formatting")
    void reset() {
        List<ColorSpan> spans = ColorSpan.parse("&c&lshout&rwhisper");

        assertEquals(2, spans.size());
        assertEquals("shout", spans.get(0).text());
        assertEquals("whisper", spans.get(1).text());
        assertFalse(spans.get(1).hasLegacyColor());
        assertFalse(spans.get(1).bold());
    }

    @Test
    @DisplayName("hex colours are read as packed rgb")
    void hexColor() {
        List<ColorSpan> spans = ColorSpan.parse("&#EDEDEDType /afk");

        assertEquals(1, spans.size());
        assertEquals("Type /afk", spans.getFirst().text());
        assertEquals(0xEDEDED, spans.getFirst().rgb());
    }

    @Test
    @DisplayName("text that only looks like markup is kept verbatim")
    void malformedMarkupIsLiteral() {
        assertEquals("&#GGGGGGx", single(ColorSpan.parse("&#GGGGGGx")));
        assertEquals("&z code", single(ColorSpan.parse("&z code")));
        assertEquals("ends with &", single(ColorSpan.parse("ends with &")));
        assertEquals("&#FFF", single(ColorSpan.parse("&#FFF")));
        assertEquals("a < b", single(ColorSpan.parse("a < b")));
        assertEquals("<rainbow>unclosed", single(ColorSpan.parse("<rainbow>unclosed")));
        assertEquals("<gradient:#FF0000:#0000FF>unclosed",
                single(ColorSpan.parse("<gradient:#FF0000:#0000FF>unclosed")));
        assertEquals("<gradient:#FF0000>short</gradient>",
                single(ColorSpan.parse("<gradient:#FF0000>short</gradient>")));
    }

    @Test
    @DisplayName("a gradient colours each character between its endpoints")
    void gradient() {
        List<ColorSpan> spans = ColorSpan.parse("<gradient:#000000:#FFFFFF>ab</gradient>");

        assertEquals(2, spans.size());
        assertEquals("a", spans.get(0).text());
        assertEquals(0x000000, spans.get(0).rgb());
        assertEquals("b", spans.get(1).text());
        assertEquals(0xFFFFFF, spans.get(1).rgb());
    }

    @Test
    @DisplayName("characters that end up the same colour stay one span")
    void gradientCoalescesEqualColors() {
        List<ColorSpan> spans = ColorSpan.parse("<gradient:#FF0000:#FF0000>a b c</gradient>");

        assertEquals(1, spans.size());
        assertEquals("a b c", spans.getFirst().text());
        assertEquals(0xFF0000, spans.getFirst().rgb());
    }

    @Test
    @DisplayName("a gradient keeps the formatting that was in force at the opening tag")
    void gradientInheritsFormatting() {
        List<ColorSpan> spans = ColorSpan.parse("&l<gradient:#FF0000:#FF0000>loud</gradient>");

        assertEquals(1, spans.size());
        assertTrue(spans.getFirst().bold());
        assertEquals(0xFF0000, spans.getFirst().rgb());
    }

    @Test
    @DisplayName("text either side of a gradient keeps its own style")
    void gradientBetweenText() {
        List<ColorSpan> spans = ColorSpan.parse("&7[<gradient:#FF0000:#FF0000>AFK</gradient>&7] ");

        assertEquals(3, spans.size());
        assertEquals("[", spans.get(0).text());
        assertEquals('7', spans.get(0).legacyColor());
        assertEquals("AFK", spans.get(1).text());
        assertEquals(0xFF0000, spans.get(1).rgb());
        assertEquals("] ", spans.get(2).text());
        assertEquals('7', spans.get(2).legacyColor());
    }

    @Test
    @DisplayName("a rainbow starts red and changes colour along the text")
    void rainbow() {
        List<ColorSpan> spans = ColorSpan.parse("<rainbow>rainbow</rainbow>");

        assertTrue(spans.size() > 1, "expected the rainbow to change colour: " + spans);
        assertEquals(0xFF0000, spans.getFirst().rgb());
        assertEquals("rainbow", spans.stream().map(ColorSpan::text).reduce("", String::concat));
    }

    @Test
    @DisplayName("placeholders substituted before parsing are left alone")
    void placeholderText() {
        List<ColorSpan> spans = ColorSpan.parse("&eYou will be kicked in 60 seconds!");

        assertEquals(1, spans.size());
        assertEquals("You will be kicked in 60 seconds!", spans.getFirst().text());
        assertEquals('e', spans.getFirst().legacyColor());
    }

    private static String single(List<ColorSpan> spans) {
        assertEquals(1, spans.size(), "expected exactly one span but got: " + spans);
        return spans.getFirst().text();
    }
}
