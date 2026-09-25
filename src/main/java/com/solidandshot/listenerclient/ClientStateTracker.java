package com.solidandshot.listenerclient;

import com.solidandshot.listenerclient.network.ListenerNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Low-cost client state transitions that do not require mixins. */
public final class ClientStateTracker {
    private static double oldX, oldY, oldZ;
    private static float oldHealth, oldExperience;
    private static String oldDimension, oldLook;
    private static boolean initialized;
    private static boolean sprinting, swimming, burning, raining;
    private static int heartbeatTicks;

    private ClientStateTracker() {
    }

    public static void reset() {
        initialized = false;
        heartbeatTicks = 0;
        oldDimension = oldLook = null;
    }

    public static void tick(Minecraft client) {
        if (!ListenerNetworking.isAccepted() || client.player == null || client.level == null) {
            if (client.player == null || client.level == null) reset();
            return;
        }
        LocalPlayer player = client.player;
        String dimension = client.level.dimension().toString();
        String screen = client.gui.screen() == null ? "" : client.gui.screen().getClass().getName();
        if (!initialized) {
            initialized = true;
            oldX = player.getX(); oldY = player.getY(); oldZ = player.getZ();
            oldHealth = player.getHealth(); oldExperience = player.experienceProgress;
            oldDimension = dimension;
            sprinting = player.isSprinting(); swimming = player.isSwimming(); burning = player.isOnFire();
            raining = client.level.isRaining();
        } else {
            if (oldDimension != null && !oldDimension.equals(dimension)) {
                ListenerNetworking.sendEvent("dimension_entered", "dimension_key", dimension);
                oldDimension = dimension;
            }
            if (player.getX() != oldX || player.getY() != oldY || player.getZ() != oldZ) {
                ListenerNetworking.sendEvent("position_changed",
                        "old_pos_x", Double.toString(oldX), "old_pos_y", Double.toString(oldY), "old_pos_z", Double.toString(oldZ),
                        "new_pos_x", Double.toString(player.getX()), "new_pos_y", Double.toString(player.getY()), "new_pos_z", Double.toString(player.getZ()));
                oldX = player.getX(); oldY = player.getY(); oldZ = player.getZ();
            }
            boolean nextSprinting = player.isSprinting();
            if (nextSprinting != sprinting) ListenerNetworking.sendEvent(nextSprinting ? "started_running" : "stopped_running");
            sprinting = nextSprinting;
            boolean nextSwimming = player.isSwimming();
            if (nextSwimming != swimming) ListenerNetworking.sendEvent(nextSwimming ? "started_swimming" : "stopped_swimming");
            swimming = nextSwimming;
            boolean nextBurning = player.isOnFire();
            if (nextBurning != burning) ListenerNetworking.sendEvent(nextBurning ? "started_burning" : "stopped_burning");
            burning = nextBurning;
            float health = player.getHealth();
            if (health < oldHealth) {
                ListenerNetworking.sendEvent("damage_taken", "damage_amount", Float.toString(oldHealth - health), "new_health", Float.toString(health));
            }
            oldHealth = health;
            float experience = player.experienceProgress;
            if (experience != oldExperience) {
                ListenerNetworking.sendEvent("experience_changed", "new_experience_amount", Float.toString(experience), "old_experience_amount", Float.toString(oldExperience));
                oldExperience = experience;
            }
            boolean nextRaining = client.level.isRaining();
            if (nextRaining != raining) ListenerNetworking.sendEvent("weather_changed", "weather_type", nextRaining ? "rain" : "clear");
            raining = nextRaining;
        }
        sendLookTarget(client);
        // Heartbeat once per second; state transitions above remain immediate.
        if (++heartbeatTicks >= 20) {
            heartbeatTicks = 0;
            ListenerNetworking.sendEvent("client_tick", "screen", screen, "dimension", dimension);
        }
    }

    private static void sendLookTarget(Minecraft client) {
        HitResult hit = client.hitResult;
        String next = "";
        if (hit instanceof BlockHitResult block && client.level != null) {
            BlockState state = client.level.getBlockState(block.getBlockPos());
            next = "block:" + BuiltInRegistries.BLOCK.getKey(state.getBlock());
        } else if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            next = "entity:" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        }
        if (!next.equals(oldLook)) {
            if (oldLook != null && !oldLook.isEmpty()) {
                String type = oldLook.startsWith("block:") ? "block" : "entity";
                ListenerNetworking.sendEvent("stop_looking_at_" + type, "target", oldLook.substring(oldLook.indexOf(':') + 1));
            }
            if (!next.isEmpty()) {
                String type = next.startsWith("block:") ? "block" : "entity";
                ListenerNetworking.sendEvent("start_looking_at_" + type, "target", next.substring(next.indexOf(':') + 1));
            }
            oldLook = next;
        }
    }
}
