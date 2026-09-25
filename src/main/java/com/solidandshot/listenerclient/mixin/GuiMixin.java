package com.solidandshot.listenerclient.mixin;

import com.solidandshot.listenerclient.network.ListenerNetworking;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the mapped 26.2 Gui#setScreen transition. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow private Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void listener$screen(Screen next, CallbackInfo callback) {
        if (screen != null && screen != next) {
            ListenerNetworking.sendEvent("screen_close", "screen", screen.getClass().getName());
        }
        if (next != null && next != screen) {
            ListenerNetworking.sendEvent("screen_open", "screen", next.getClass().getName());
        }
    }
}
