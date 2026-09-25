package com.solidandshot.listenerclient.mixin;

import com.solidandshot.listenerclient.network.ListenerNetworking;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("RETURN"))
    private void listener$keyPress(long window, int action, KeyEvent event, CallbackInfo info) {
        if (action != 0 && action != 1 && action != 2) return;
        ListenerNetworking.sendEvent(action == 0 ? "keyboard_key_released" : "keyboard_key_pressed",
                "key_keycode", Integer.toString(event.key()),
                "key_scancode", Integer.toString(event.scancode()),
                "key_modifiers", Integer.toString(event.modifiers()),
                "action", action == 2 ? "repeat" : action == 1 ? "press" : "release");
    }

    @Inject(method = "charTyped", at = @At("HEAD"))
    private void listener$charTyped(long window, CharacterEvent event, CallbackInfo info) {
        ListenerNetworking.sendEvent("keyboard_char_typed", "char", Integer.toString(event.codepoint()));
    }
}
