package com.gemsi.easyafk;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.EnumSet;

/**
 * Builds and publishes the AFK tab list name.
 *
 * <p>The name itself is injected by {@code MixinServerPlayer} overriding
 * {@link ServerPlayer#getTabListDisplayName()}, which every loader shares, so pushing an
 * update is just a matter of telling clients to re-read it.
 */
public final class TabList {

    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> DISPLAY_NAME =
            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);

    private TabList() {
    }

    /** Builds the AFK tab list entry for a player, e.g. {@code "[AFK] Steve (5m 12s)"}. */
    public static Component nameFor(ServerPlayer player) {
        Component name = Component.literal(player.getName().getString())
                .setStyle(Style.EMPTY.withBold(false).withColor(TextColor.fromRgb(Config.colorAfkPlayerName)));

        Component result = ColorParser.parseColors(Config.afkPrefix).copy().append(name);

        if (Config.showAFKDurationInTab) {
            long seconds = AFKCommands.getAFKDurationSeconds(player.getUUID());
            String durationText = Config.afkDurationFormat.replace("{time}", AFKDuration.format(seconds));
            result = result.copy().append(ColorParser.parseColors(durationText));
        }

        return result;
    }

    /** Publishes one player's tab list name to every client. */
    public static void push(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
    }

    /**
     * Publishes several players' tab list names in a single packet.
     *
     * <p>The duration in the tab list ticks once a second for every AFK player. Refreshing
     * them one at a time meant a whole broadcast per AFK player per second; one packet
     * carrying every entry costs the same as one player used to.
     */
    public static void pushAll(MinecraftServer server, Collection<ServerPlayer> players) {
        if (players.isEmpty()) {
            return;
        }
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(DISPLAY_NAME, players));
    }
}
