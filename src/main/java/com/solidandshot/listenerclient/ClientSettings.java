package com.solidandshot.listenerclient;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Small local settings store used by the in-game dashboard. */
public final class ClientSettings {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("listenerclient.properties");
    public static boolean inputEvents = true;
    public static boolean mouseEvents = true;
    public static boolean positionEvents = true;
    public static boolean stateEvents = true;

    private ClientSettings() {
    }

    public static void load() {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            inputEvents = Boolean.parseBoolean(properties.getProperty("input_events", "true"));
            mouseEvents = Boolean.parseBoolean(properties.getProperty("mouse_events", "true"));
            positionEvents = Boolean.parseBoolean(properties.getProperty("position_events", "true"));
            stateEvents = Boolean.parseBoolean(properties.getProperty("state_events", "true"));
        } catch (IOException ignored) {
            // First launch: keep the safe, useful defaults.
        }
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty("input_events", Boolean.toString(inputEvents));
        properties.setProperty("mouse_events", Boolean.toString(mouseEvents));
        properties.setProperty("position_events", Boolean.toString(positionEvents));
        properties.setProperty("state_events", Boolean.toString(stateEvents));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Listener Client settings");
            }
        } catch (IOException ignored) {
        }
    }

    public static boolean allows(String event) {
        if (event == null) return false;
        if (event.startsWith("keyboard_") || event.equals("char_typed")) return inputEvents;
        if (event.startsWith("mouse_")) return mouseEvents;
        if (event.equals("position_changed") || event.equals("dimension_entered")
                || event.startsWith("start_looking_at_") || event.startsWith("stop_looking_at_")) return positionEvents;
        if (event.equals("client_tick") || event.equals("weather_changed") || event.equals("experience_changed")
                || event.equals("damage_taken") || event.equals("player_death") || event.contains("running")
                || event.contains("swimming") || event.contains("burning") || event.contains("drowning")
                || event.contains("freezing") || event.contains("touching_fluid")) return stateEvents;
        return true;
    }
}
