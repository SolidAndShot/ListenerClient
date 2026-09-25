package com.solidandshot.listenerclient.network;

import com.solidandshot.listenerclient.ClientStateTracker;
import com.solidandshot.listenerclient.ClientSettings;
import com.solidandshot.listenerclient.ClientRuleDraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Protocol v1 implementation shared with the Listener Bukkit plugin. */
public final class ListenerNetworking {
    public static final int PROTOCOL_VERSION = 1;
    private static final int OP_HELLO = 1;
    private static final int OP_HELLO_ACK = 2;
    private static final int OP_ACTION = 3;
    private static final int OP_EVENT = 4;
    private static final int OP_EDIT_REQUEST = 5;
    private static final int OP_EDIT_RESPONSE = 6;
    private static final int MAX_MESSAGE_BYTES = 32 * 1024;
    private static final int MAX_STRING_CHARS = 4096;

    private static volatile boolean registered;
    private static volatile boolean accepted;
    private static volatile String handshakeReason = "尚未收到服务器握手";
    private static volatile String editorReason = "";
    private static volatile String serverCapabilities = "";
    private static volatile List<RemoteRule> remoteRules = List.of();

    /** Features advertised by this client in the HELLO frame. */
    private static final List<String> CLIENT_FEATURES = List.of(
            "keyboard", "mouse", "screen", "client_tick", "position",
            "look", "player_state", "chat", "world", "sound");

    private ListenerNetworking() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.serverboundPlay().register(ListenerPayload.TYPE, ListenerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ListenerPayload.TYPE, ListenerPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ListenerPayload.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload.data())));
    }

    public static void onJoin(Minecraft client) {
        accepted = false;
        handshakeReason = "等待服务器握手";
        serverCapabilities = "";
        client.execute(() -> sendHello());
    }

    public static void onDisconnect() {
        accepted = false;
        handshakeReason = "未连接服务器";
        serverCapabilities = "";
        ClientStateTracker.reset();
    }

    public static boolean isAccepted() {
        return accepted;
    }

    public static String handshakeReason() {
        return handshakeReason;
    }

    public static String serverCapabilities() {
        return serverCapabilities;
    }

    public static List<String> clientFeatures() {
        return CLIENT_FEATURES;
    }

    public static List<RemoteRule> remoteRules() {
        return remoteRules;
    }

    public static String editorReason() {
        return editorReason;
    }

    /** Requests rules visible to the current player (requires listener.admin on the server). */
    public static void requestRuleSync() {
        sendEditFrame(1, (ClientRuleDraft) null);
    }

    /** Sends an editor draft using the extensible v1 EDIT_REQUEST operation. */
    public static void sendRuleUpsert(ClientRuleDraft draft) {
        if (!accepted || draft == null) return;
        sendEditFrame(2, draft);
    }

    public static void sendRuleDelete(String id) {
        if (!accepted || id == null || id.isBlank()) return;
        sendEditFrame(3, id.trim());
    }

    public static void sendRuleTest(String id) {
        if (!accepted || id == null || id.isBlank()) return;
        sendEditFrame(4, id.trim());
    }

    /** FancyMenu providers are local to the client; the server bridge stores
     * them under the client_ namespace so a remote rule cannot collide with a
     * normal Bukkit event. */
    private static String serverEvent(String event) {
        String value = event == null ? "" : event.trim().toLowerCase();
        return value.startsWith("client_") ? value : "client_" + value;
    }

    private static void sendEditFrame(int mode, ClientRuleDraft draft) {
        if (!accepted) return;
        try {
            byte[] payload = frame(OP_EDIT_REQUEST, out -> {
                out.writeByte(mode);
                if (draft == null) {
                    out.writeUTF("");
                    return;
                }
                out.writeUTF(limit(draft.id));
                out.writeUTF(limit(serverEvent(draft.event)));
                out.writeBoolean(draft.enabled);
                out.writeLong(20L);
                String filterKey = draft.filterKey == null ? "" : draft.filterKey.trim();
                if (filterKey.isBlank() || "*".equals(filterKey)) {
                    out.writeByte(0);
                } else {
                    out.writeByte(1);
                    out.writeUTF(limit(filterKey));
                    out.writeUTF(limit(draft.filterValue));
                }
                out.writeByte(1);
                out.writeUTF(limit(draft.action));
                out.writeUTF(limit(draft.actionValue));
                out.writeLong(0L);
            });
            if (ClientPlayNetworking.canSend(ListenerPayload.TYPE)) ClientPlayNetworking.send(new ListenerPayload(payload));
        } catch (IOException | RuntimeException ignored) { }
    }

    private static void sendEditFrame(int mode, String id) {
        if (!accepted) return;
        try {
            byte[] payload = frame(OP_EDIT_REQUEST, out -> {
                out.writeByte(mode);
                out.writeUTF(limit(id));
            });
            if (ClientPlayNetworking.canSend(ListenerPayload.TYPE)) ClientPlayNetworking.send(new ListenerPayload(payload));
        } catch (IOException | RuntimeException ignored) { }
    }

    public static void sendEvent(String event, String... fields) {
        if (!accepted || event == null || event.isBlank() || !ClientSettings.allows(event)) return;
        try {
            int count = Math.min(fields.length / 2, 64);
            byte[] payload = frame(OP_EVENT, out -> {
                out.writeUTF(limit(event.toLowerCase()));
                out.writeByte(count);
                for (int i = 0; i < count; i++) {
                    out.writeUTF(limit(fields[i * 2].toLowerCase()));
                    out.writeUTF(limit(fields[i * 2 + 1]));
                }
            });
            if (ClientPlayNetworking.canSend(ListenerPayload.TYPE)) {
                ClientPlayNetworking.send(new ListenerPayload(payload));
            }
        } catch (IOException | RuntimeException ignored) {
            // A server may not have the Listener plugin or may disconnect mid-tick.
        }
    }

    /**
     * Exports a local editor draft through the existing EVENT envelope. The
     * server currently treats this as client_rule_export, so old servers can
     * safely ignore it while newer Listener builds may persist the draft.
     */
    public static void sendRuleExport(String payload) {
        sendEvent("client_rule_export", "rule", payload == null ? "" : payload);
    }

    private static void sendHello() {
        try {
            byte[] payload = frame(OP_HELLO, out -> {
                out.writeByte(PROTOCOL_VERSION);
                out.writeUTF("1.0.0");
                out.writeByte(CLIENT_FEATURES.size());
                for (String feature : CLIENT_FEATURES) out.writeUTF(feature);
            });
            if (ClientPlayNetworking.canSend(ListenerPayload.TYPE)) {
                ClientPlayNetworking.send(new ListenerPayload(payload));
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static void receive(byte[] message) {
        if (message == null || message.length > MAX_MESSAGE_BYTES || message.length < 2) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            int version = in.readUnsignedByte();
            int op = in.readUnsignedByte();
            if (version != PROTOCOL_VERSION) return;
            if (op == OP_HELLO_ACK) {
                accepted = in.readBoolean();
                handshakeReason = readString(in);
                serverCapabilities = readString(in);
                editorReason = accepted && serverCapabilities.contains("editor") ? "可编辑服务器规则" : "服务器未授予编辑权限";
            } else if (op == OP_ACTION && accepted) {
                String action = readString(in).toLowerCase();
                String value = readString(in);
                ClientActions.execute(action, value);
            } else if (op == OP_EDIT_RESPONSE && accepted) {
                readEditResponse(in);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static void readEditResponse(DataInputStream in) throws IOException {
        boolean acceptedResponse = in.readBoolean();
        editorReason = readString(in);
        int count = Math.min(in.readUnsignedByte(), 64);
        List<RemoteRule> loaded = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = readString(in);
            String event = readString(in);
            boolean enabled = in.readBoolean();
            long interval = in.readLong();
            int filters = Math.min(in.readUnsignedByte(), 32);
            java.util.Map<String, String> filterMap = new java.util.LinkedHashMap<>();
            for (int j = 0; j < filters; j++) filterMap.put(readString(in), readString(in));
            int actions = Math.min(in.readUnsignedByte(), 32);
            java.util.Map<String, String> actionMap = new java.util.LinkedHashMap<>();
            for (int j = 0; j < actions; j++) {
                actionMap.put(readString(in), readString(in));
                // Server includes a delay_ticks long after every action. Keep
                // consuming it even though the first editor view renders one
                // action at a time and does not expose per-action delays yet.
                in.readLong();
            }
            loaded.add(new RemoteRule(id, event, enabled, interval, filterMap, actionMap));
        }
        if (acceptedResponse) remoteRules = List.copyOf(loaded);
    }

    private static byte[] frame(int op, Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(PROTOCOL_VERSION);
            out.writeByte(op);
            writer.write(out);
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length > MAX_MESSAGE_BYTES) throw new IOException("payload too large");
        return payload;
    }

    static String readString(DataInputStream in) throws IOException {
        return limit(in.readUTF());
    }

    private static String limit(String value) {
        if (value == null) return "";
        return value.length() <= MAX_STRING_CHARS ? value : value.substring(0, MAX_STRING_CHARS);
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    public record RemoteRule(String id, String event, boolean enabled, long intervalTicks,
                             java.util.Map<String, String> filters, java.util.Map<String, String> actions) { }
}
