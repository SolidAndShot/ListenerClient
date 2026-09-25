package com.solidandshot.listenerclient.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Arrays;

/** Raw payload matching Bukkit's plugin-message body on listener:main. */
public final class ListenerPayload implements CustomPacketPayload {
    public static final Type<ListenerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("listener", "main"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ListenerPayload> CODEC =
            CustomPacketPayload.codec(ListenerPayload::write, ListenerPayload::read);

    private final byte[] data;

    public ListenerPayload(byte[] data) {
        if (data == null || data.length > 32 * 1024) {
            throw new IllegalArgumentException("Listener payload is too large");
        }
        this.data = Arrays.copyOf(data, data.length);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBytes(data);
    }

    private static ListenerPayload read(RegistryFriendlyByteBuf buffer) {
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        return new ListenerPayload(data);
    }

    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
