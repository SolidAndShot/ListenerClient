package com.solidandshot.listenerclient;

import com.solidandshot.listenerclient.network.ListenerNetworking;
import com.solidandshot.listenerclient.gui.ListenerDashboardScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Client entry point for the optional Listener/FancyMenu-compatible bridge. */
public final class ListenerClient implements ClientModInitializer {
    public static final String MOD_VERSION = "1.0.0";
    private static KeyMapping dashboardKey;
    @Override
    public void onInitializeClient() {
        ClientSettings.load();
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("listenerclient", "general"));
        dashboardKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.listenerclient.dashboard", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        ListenerNetworking.register();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ListenerNetworking.onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ListenerNetworking.onDisconnect());
        ClientTickEvents.END_CLIENT_TICK.register(ListenerClient::tick);
    }

    private static void tick(Minecraft client) {
        ClientStateTracker.tick(client);
        while (dashboardKey.consumeClick()) {
            if (client.gui.screen() instanceof ListenerDashboardScreen) {
                client.gui.setScreen(null);
            } else {
                client.gui.setScreen(new ListenerDashboardScreen());
            }
        }
    }
}
