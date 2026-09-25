package com.solidandshot.listenerclient.mixin;

import com.solidandshot.listenerclient.network.ListenerNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow private double xpos;
    @Shadow private double ypos;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    private long listener$lastMoveNanos;

    @Inject(method = "onButton", at = @At("RETURN"))
    private void listener$button(long window, MouseButtonInfo info, int action, CallbackInfo callback) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return;
        double[] pos = scaledPosition();
        ListenerNetworking.sendEvent(action == GLFW.GLFW_PRESS ? "mouse_button_clicked" : "mouse_button_released",
                "button", buttonName(info.button()), "button_code", Integer.toString(info.button()),
                "mouse_pos_x", Double.toString(pos[0]), "mouse_pos_y", Double.toString(pos[1]));
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void listener$scroll(long window, double scrollX, double scrollY, CallbackInfo callback) {
        double[] pos = scaledPosition();
        ListenerNetworking.sendEvent("mouse_scrolled", "mouse_pos_x", Double.toString(pos[0]), "mouse_pos_y", Double.toString(pos[1]),
                "scroll_delta_x", Double.toString(scrollX), "scroll_delta_y", Double.toString(scrollY));
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void listener$move(CallbackInfo callback) {
        long now = System.nanoTime();
        if (now - listener$lastMoveNanos < 50_000_000L) return;
        listener$lastMoveNanos = now;
        double[] pos = scaledPosition();
        double[] size = scaledSize();
        double dx = accumulatedDX * size[0] / Math.max(1.0, Minecraft.getInstance().getWindow().getScreenWidth());
        double dy = accumulatedDY * size[1] / Math.max(1.0, Minecraft.getInstance().getWindow().getScreenHeight());
        ListenerNetworking.sendEvent("mouse_moved", "mouse_pos_x", Double.toString(pos[0]), "mouse_pos_y", Double.toString(pos[1]),
                "delta_x", Double.toString(dx), "delta_y", Double.toString(dy));
    }

    private double[] scaledPosition() {
        double[] size = scaledSize();
        Minecraft client = Minecraft.getInstance();
        double screenWidth = Math.max(1.0, client.getWindow().getScreenWidth());
        double screenHeight = Math.max(1.0, client.getWindow().getScreenHeight());
        return new double[]{xpos * size[0] / screenWidth, ypos * size[1] / screenHeight};
    }

    private double[] scaledSize() {
        Minecraft client = Minecraft.getInstance();
        return new double[]{client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()};
    }

    private static String buttonName(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "left";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "right";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "middle";
            default -> Integer.toString(button);
        };
    }
}
