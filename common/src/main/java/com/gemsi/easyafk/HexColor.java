package com.gemsi.easyafk;

/**
 * Hex colour conversion, deliberately free of any Minecraft types so config handling
 * and its tests can run without bootstrapping the game.
 */
public final class HexColor {

    public static final int WHITE = 0xFFFFFF;

    private HexColor() {
    }

    public static int parse(String hexColor) {
        if (hexColor == null) {
            return WHITE;
        }
        String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return WHITE;
        }
    }

    public static String format(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
