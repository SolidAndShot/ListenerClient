package com.solidandshot.listenerclient.network;

import com.solidandshot.listenerclient.ClientStateTracker;
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
    private static final int MAX_MESSAGE_BYTES = 32 * 1024;
    private static final int MAX_STRING_CHARS = 4096;

    private static volatile boolean registered;
    private static volatile boolean accepted;

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
        client.execute(() -> sendHello());
    }

    public static void onDisconnect() {
        accepted = false;
        ClientStateTracker.reset();
    }

    public static boolean isAccepted() {
        return accepted;
    }

    public static void sendEvent(String event, String... fields) {
        if (!accepted || event == null || event.isBlank()) return;
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

    private static void sendHello() {
        try {
            byte[] payload = frame(OP_HELLO, out -> {
                out.writeByte(PROTOCOL_VERSION);
                out.writeUTF("1.0.0");
                List<String> features = List.of(
                        "keyboard", "mouse", "screen", "client_tick", "position",
                        "look", "player_state", "chat", "world", "sound");
                out.writeByte(features.size());
                for (String feature : features) out.writeUTF(feature);
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
                readString(in); // reason
                readString(in); // capabilities
            } else if (op == OP_ACTION && accepted) {
                String action = readString(in).toLowerCase();
                String value = readString(in);
                ClientActions.execute(action, value);
            }
        } catch (IOException | RuntimeException ignored) {
        }
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
}
