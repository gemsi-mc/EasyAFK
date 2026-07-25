package com.gemsi.easyafk;

/**
 * The server-wide AFK bookkeeping shared by every loader.
 */
public final class AFKState {

    public static final AFKTracker TRACKER = new AFKTracker();

    private AFKState() {
    }
}
