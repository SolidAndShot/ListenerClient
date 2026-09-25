package com.solidandshot.listenerclient.gui;

import com.solidandshot.listenerclient.ClientSettings;
import com.solidandshot.listenerclient.ListenerClient;
import com.solidandshot.listenerclient.network.ListenerNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** In-game dashboard for connection diagnostics and local event controls. */
public final class ListenerDashboardScreen extends Screen {
    private static final int PANEL_WIDTH = 580;
    private static final int PANEL_HEIGHT = 304;
    private static final int PANEL = 0xF0142035;
    private static final int CARD = 0xE6283852;
    private static final int CARD_EDGE = 0xFF435574;
    private static final int TEXT = 0xFFF1F5FF;
    private static final int MUTED = 0xFFB8C4D9;
    private static final int GOOD = 0xFF72E6A5;
    private static final int WARN = 0xFFFFD37A;

    private int panelLeft;
    private int panelTop;

    public ListenerDashboardScreen() {
        super(Component.literal("监听器客户端"));
    }

    @Override
    protected void init() {
        panelLeft = Math.max(12, (width - PANEL_WIDTH) / 2);
        panelTop = Math.max(12, (height - PANEL_HEIGHT) / 2);

        int columnWidth = 258;
        int left = panelLeft + 18;
        int right = panelLeft + 304;
        int firstRow = panelTop + 110;
        int rowGap = 30;

        addRenderableWidget(toggleButton("输入事件", "键盘与字符输入", ClientSettings.inputEvents,
                left, firstRow, columnWidth, button -> {
                    ClientSettings.inputEvents = !ClientSettings.inputEvents;
                    button.setMessage(toggle("输入事件", "键盘与字符输入", ClientSettings.inputEvents));
                }));
        addRenderableWidget(toggleButton("鼠标事件", "点击、滚轮与移动", ClientSettings.mouseEvents,
                right, firstRow, columnWidth, button -> {
                    ClientSettings.mouseEvents = !ClientSettings.mouseEvents;
                    button.setMessage(toggle("鼠标事件", "点击、滚轮与移动", ClientSettings.mouseEvents));
                }));
        addRenderableWidget(toggleButton("位置与注视", "位置、维度与目标", ClientSettings.positionEvents,
                left, firstRow + rowGap, columnWidth, button -> {
                    ClientSettings.positionEvents = !ClientSettings.positionEvents;
                    button.setMessage(toggle("位置与注视", "位置、维度与目标", ClientSettings.positionEvents));
                }));
        addRenderableWidget(toggleButton("状态事件", "移动、伤害与天气", ClientSettings.stateEvents,
                right, firstRow + rowGap, columnWidth, button -> {
                    ClientSettings.stateEvents = !ClientSettings.stateEvents;
                    button.setMessage(toggle("状态事件", "移动、伤害与天气", ClientSettings.stateEvents));
                }));

        addRenderableWidget(Button.builder(Component.literal("保存设置"), button -> ClientSettings.save())
                .bounds(panelLeft + 18, panelTop + 252, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), button -> onClose())
                .bounds(panelLeft + PANEL_WIDTH - 148, panelTop + 252, 130, 20).build());
    }

    private static Button toggleButton(String title, String description, boolean enabled, int x, int y, int width,
                                       java.util.function.Consumer<Button> action) {
        return Button.builder(toggle(title, description, enabled), action::accept)
                .bounds(x, y, width, 20).build();
    }

    private static Component toggle(String title, String description, boolean enabled) {
        return Component.literal((enabled ? "✓ " : "○ ") + title + " · " + description + "：" + (enabled ? "开启" : "关闭"));
    }

    @Override
    public void onClose() {
        ClientSettings.save();
        super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = panelLeft;
        int top = panelTop;
        graphics.fillGradient(0, 0, width, height, 0xFF0B1221, 0xFF1B3150);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, CARD_EDGE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(font, Component.literal("监听器客户端控制台"), width / 2, top + 13, TEXT);
        graphics.centeredText(font, Component.literal("Listener bridge · 本地事件上报控制"), width / 2, top + 29, MUTED);
        drawConnectionCard(graphics, left + 18, top + 50, 258, 48);
        drawProtocolCard(graphics, left + 304, top + 50, 258, 48);
        graphics.text(font, Component.literal("事件上报开关（关闭后不会发送对应类别）"), left + 18, top + 94, TEXT);
        graphics.text(font, Component.literal("配置文件：config/listenerclient.properties"), left + 18, top + 276, MUTED);
        graphics.text(font, Component.literal("快捷键 K · 重新按一次可关闭"), left + 205, top + 276, MUTED);
    }

    private void drawConnectionCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        card(graphics, x, y, w, h);
        boolean connected = minecraft.getConnection() != null;
        boolean accepted = ListenerNetworking.isAccepted();
        String status = !connected ? "未连接服务器" : accepted ? "已连接并启用" : "已连接，等待握手";
        int color = accepted ? GOOD : connected ? WARN : MUTED;
        graphics.text(font, Component.literal("连接状态"), x + 10, y + 8, TEXT);
        graphics.text(font, Component.literal("● " + status), x + 10, y + 25, color);
    }

    private void drawProtocolCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        card(graphics, x, y, w, h);
        String reason = trim(ListenerNetworking.handshakeReason(), 30);
        graphics.text(font, Component.literal("协议 v" + ListenerNetworking.PROTOCOL_VERSION + " · Mod " + ListenerClient.MOD_VERSION), x + 10, y + 8, TEXT);
        graphics.text(font, Component.literal(trim(reason, 36)), x + 10, y + 25, MUTED);
    }

    private void card(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, CARD);
        graphics.outline(x, y, w, h, 0xFF31435F);
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) return "—";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
