package com.solidandshot.listenerclient;

import com.solidandshot.listenerclient.network.ListenerNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Client entry point for the optional Listener/FancyMenu-compatible bridge. */
public final class ListenerClient implements ClientModInitializer {
    public static final String MOD_VERSION = "1.0.0";
    @Override
    public void onInitializeClient() {
        ListenerNetworking.register();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ListenerNetworking.onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ListenerNetworking.onDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(ClientStateTracker::tick);
    }
}
