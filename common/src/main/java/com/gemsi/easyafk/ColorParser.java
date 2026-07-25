package com.gemsi.easyafk;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorParser {

    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:#([0-9A-Fa-f]{6}):#([0-9A-Fa-f]{6})>(.*?)</gradient>");
    private static final Pattern RAINBOW_PATTERN = Pattern.compile("<rainbow>(.*?)</rainbow>");

    /**
     * Parse a string with Minecraft color codes and gradient tags into a Component
     */
    public static Component parseColors(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        text = handleGradients(text);

        text = handleRainbow(text);

        return parseMinecraftColors(text);
    }

    private static String handleGradients(String text) {
        Matcher matcher = GRADIENT_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String startColorHex = matcher.group(1);
            String endColorHex = matcher.group(2);
            String content = matcher.group(3);

            String gradientText = createGradientColorCodes(content, startColorHex, endColorHex);
            matcher.appendReplacement(result, Matcher.quoteReplacement(gradientText));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String handleRainbow(String text) {
        Matcher matcher = RAINBOW_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String content = matcher.group(1);
            String rainbowText = createRainbowColorCodes(content);
            matcher.appendReplacement(result, Matcher.quoteReplacement(rainbowText));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String createGradientColorCodes(String text, String startHex, String endHex) {
        if (text.isEmpty()) return text;

        int startColor = Integer.parseInt(startHex, 16);
        int endColor = Integer.parseInt(endHex, 16);

        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;

        int endR = (endColor >> 16) & 0xFF;
        int endG = (endColor >> 8) & 0xFF;
        int endB = endColor & 0xFF;

        StringBuilder result = new StringBuilder();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);

            if (c == ' ') {
                result.append(c);
                continue;
            }

            float ratio = length > 1 ? (float) i / (length - 1) : 0;

            int r = (int) (startR + (endR - startR) * ratio);
            int g = (int) (startG + (endG - startG) * ratio);
            int b = (int) (startB + (endB - startB) * ratio);

            result.append(String.format("&#%02X%02X%02X", r, g, b));
            result.append(c);
        }

        return result.toString();
    }

    private static String createRainbowColorCodes(String text) {
        if (text.isEmpty()) return text;

        StringBuilder result = new StringBuilder();
        int length = text.length();

        int[] rainbowColors = {
                0xFF0000,
                0xFF7F00,
                0xFFFF00,
                0x00FF00,
                0x0000FF,
                0x4B0082,
                0x9400D3
        };

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);

            if (c == ' ') {
                result.append(c);
                continue;
            }

            // Calculate position in rainbow
            float ratio = (float) i / length * rainbowColors.length;
            int colorIndex = (int) ratio;
            int nextColorIndex = (colorIndex + 1) % rainbowColors.length;
            float localRatio = ratio - colorIndex;

            // Interpolate between two rainbow colors
            int color1 = rainbowColors[colorIndex];
            int color2 = rainbowColors[nextColorIndex];

            int r1 = (color1 >> 16) & 0xFF;
            int g1 = (color1 >> 8) & 0xFF;
            int b1 = color1 & 0xFF;

            int r2 = (color2 >> 16) & 0xFF;
            int g2 = (color2 >> 8) & 0xFF;
            int b2 = color2 & 0xFF;

            int r = (int) (r1 + (r2 - r1) * localRatio);
            int g = (int) (g1 + (g2 - g1) * localRatio);
            int b = (int) (b1 + (b2 - b1) * localRatio);

            result.append(String.format("&#%02X%02X%02X", r, g, b));
            result.append(c);
        }

        return result.toString();
    }

    /**
     * Parse Minecraft color codes (&c, &4, &#RRGGBB, etc.) into a Component
     */
    private static Component parseMinecraftColors(String text) {
        MutableComponent result = Component.empty();
        StringBuilder currentText = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);

                // Check for hex color (&#RRGGBB)
                if (code == '#' && i + 7 < text.length()) {
                    String hexColor = text.substring(i + 2, i + 8);
                    try {
                        int color = Integer.parseInt(hexColor, 16);

                        // Append current text with current style
                        if (currentText.length() > 0) {
                            result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
                            currentText = new StringBuilder();
                        }

                        // Update style with new color
                        currentStyle = Style.EMPTY.withColor(TextColor.fromRgb(color));
                        i += 7; // Skip the &#RRGGBB
                        continue;
                    } catch (NumberFormatException e) {
                        // Invalid hex, treat as normal text
                        currentText.append('&');
                        continue;
                    }
                }

                // Handle standard Minecraft color codes and formatting
                ChatFormatting formatting = getFormattingByCode(code);
                if (formatting != null) {
                    // Append current text with current style
                    if (currentText.length() > 0) {
                        result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
                        currentText = new StringBuilder();
                    }

                    // Update style
                    if (formatting.isColor()) {
                        currentStyle = Style.EMPTY.withColor(formatting);
                    } else if (formatting == ChatFormatting.BOLD) {
                        currentStyle = currentStyle.withBold(true);
                    } else if (formatting == ChatFormatting.ITALIC) {
                        currentStyle = currentStyle.withItalic(true);
                    } else if (formatting == ChatFormatting.UNDERLINE) {
                        currentStyle = currentStyle.withUnderlined(true);
                    } else if (formatting == ChatFormatting.STRIKETHROUGH) {
                        currentStyle = currentStyle.withStrikethrough(true);
                    } else if (formatting == ChatFormatting.OBFUSCATED) {
                        currentStyle = currentStyle.withObfuscated(true);
                    } else if (formatting == ChatFormatting.RESET) {
                        currentStyle = Style.EMPTY;
                    }

                    i++;
                    continue;
                }
            }

            currentText.append(text.charAt(i));
        }

        // Append any remaining text
        if (currentText.length() > 0) {
            result.append(Component.literal(currentText.toString()).setStyle(currentStyle));
        }

        return result.getSiblings().isEmpty() && result.getContents().toString().isEmpty()
                ? Component.literal(text)
                : result;
    }

    /**
     * Get ChatFormatting by color/format code character
     */
    private static ChatFormatting getFormattingByCode(char code) {
        return switch (code) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            case 'k' -> ChatFormatting.OBFUSCATED;
            case 'l' -> ChatFormatting.BOLD;
            case 'm' -> ChatFormatting.STRIKETHROUGH;
            case 'n' -> ChatFormatting.UNDERLINE;
            case 'o' -> ChatFormatting.ITALIC;
            case 'r' -> ChatFormatting.RESET;
            default -> null;
        };
    }

    /**
     * Parse a simple hex color string to RGB integer (for backward compatibility)
     */
    public static int parseColor(String hexColor) {
        return HexColor.parse(hexColor);
    }
}