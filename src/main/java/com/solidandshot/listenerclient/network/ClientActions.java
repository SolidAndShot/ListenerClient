package com.solidandshot.listenerclient.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/** Allowlisted server-to-client actions. No arbitrary client command is executed. */
final class ClientActions {
    private ClientActions() {
    }

    static void execute(String action, String value) {
        Minecraft client = Minecraft.getInstance();
        switch (action) {
            case "message", "overlay" -> {
                if (client.player != null) client.player.sendSystemMessage(Component.literal(value));
            }
            case "actionbar" -> {
                if (client.player != null) client.player.sendOverlayMessage(Component.literal(value));
            }
            case "title" -> {
                if (client.player != null) {
                    String[] parts = value.split("\\|", 2);
                    client.player.sendSystemMessage(Component.literal(parts[0]));
                    if (parts.length > 1 && !parts[1].isBlank()) {
                        client.player.sendOverlayMessage(Component.literal(parts[1]));
                    }
                }
            }
            case "screen" -> openScreen(client, value);
            case "sound" -> playSound(client, value);
            default -> {
                // Unknown actions are intentionally ignored for forward compatibility.
            }
        }
    }

    private static void openScreen(Minecraft client, String value) {
        if ("close".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value)) {
            client.gui.setScreen(null);
        } else if ("inventory".equalsIgnoreCase(value)) {
            client.gui.setScreen(new InventoryScreen(client.player));
        }
    }

    private static void playSound(Minecraft client, String raw) {
        if (client.player == null) return;
        String[] parts = raw.split(",");
        if (parts.length == 0 || parts[0].isBlank()) return;
        try {
            float volume = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1.0F;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1.0F;
            Identifier id = Identifier.parse(parts[0].trim());
            var sound = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(SoundEvents.UI_BUTTON_CLICK.value());
            client.player.playSound(sound, volume, pitch);
        } catch (RuntimeException ignored) {
        }
    }
}
