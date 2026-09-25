package com.solidandshot.listenerclient;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Local, server-independent rule draft used by the in-game editor. */
public final class ClientRuleDraft {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("listenerclient-draft.properties");

    public String id = "client_rule";
    public String event = "screen_open";
    public String filterKey = "screen_contains";
    public String filterValue = "";
    public String action = "message";
    public String actionValue = "客户端事件触发：%screen%";
    public boolean enabled = true;

    public static ClientRuleDraft load() {
        ClientRuleDraft draft = new ClientRuleDraft();
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            draft.id = p.getProperty("id", draft.id);
            draft.event = p.getProperty("event", draft.event);
            draft.filterKey = p.getProperty("filter_key", draft.filterKey);
            draft.filterValue = p.getProperty("filter_value", draft.filterValue);
            draft.action = p.getProperty("action", draft.action);
            draft.actionValue = p.getProperty("action_value", draft.actionValue);
            draft.enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
        } catch (IOException ignored) { }
        return draft;
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("id", id);
        p.setProperty("event", event);
        p.setProperty("filter_key", filterKey);
        p.setProperty("filter_value", filterValue);
        p.setProperty("action", action);
        p.setProperty("action_value", actionValue);
        p.setProperty("enabled", Boolean.toString(enabled));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) { p.store(out, "Listener Client rule draft"); }
        } catch (IOException ignored) { }
    }

    public String exportPayload() {
        return "id=" + encode(id) + ";enabled=" + enabled + ";event=" + encode(event)
                + ";filter." + encode(filterKey) + "=" + encode(filterValue)
                + ";action=" + encode(action) + ";value=" + encode(actionValue);
    }

    private static String encode(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace(";", "\\;").replace("=", "\\=");
    }
}
