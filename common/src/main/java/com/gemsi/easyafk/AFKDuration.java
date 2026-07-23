package com.gemsi.easyafk;

/**
 * Formats an AFK duration (in seconds) into a compact human-readable string,
 * e.g. {@code 42s}, {@code 5m 12s}, {@code 1h 3m 7s}.
 */
public final class AFKDuration {

    private AFKDuration() {
    }

    public static String format(long totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours).append("h ");
        }
        if (hours > 0 || minutes > 0) {
            builder.append(minutes).append("m ");
        }
        builder.append(seconds).append("s");

        return builder.toString();
    }
}
