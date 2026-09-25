package com.solidandshot.listenerclient.gui;

import com.solidandshot.listenerclient.ClientRuleDraft;
import com.solidandshot.listenerclient.network.ListenerNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Three-column local rule editor inspired by FancyMenu's provider/action workflow. */
public final class ListenerDashboardScreen extends Screen {
    private static final int BG = 0xFF09111F, PANEL = 0xF0142136, CARD = 0xE6213048;
    private static final int EDGE = 0xFF3E5878, TEXT = 0xFFF3F7FF, MUTED = 0xFFA9B8CD;
    private static final int ACCENT = 0xFF72D5FF, GOOD = 0xFF7DE2A8;
    private static final int FANCY_MENU_PROVIDER_COUNT = 85;
    private static final String[] CATEGORIES = {"全部", "屏幕", "键盘", "鼠标", "世界", "状态", "更多"};
    /** Actions understood by ListenerManager. The client_* entries are sent
     * through the mod bridge; the remaining entries execute on the server. */
    private static final String[] ACTIONS = {
            "message", "actionbar", "title", "sound", "broadcast", "player_command",
            "client_message", "client_overlay", "client_screen", "client_sound", "client_action",
            "set_variable", "log", "console_command"
    };
    private static final Set<String> IMPLEMENTED_EVENTS = Set.of(
            "keyboard_key_pressed", "keyboard_key_released", "keyboard_char_typed",
            "mouse_moved", "mouse_button_clicked", "mouse_button_released", "mouse_scrolled",
            "screen_open", "screen_close", "enter_dimension", "dimension_entered", "position_changed",
            "start_looking_at_block", "stop_looking_at_block", "start_looking_at_entity", "stop_looking_at_entity",
            "started_running", "stopped_running", "start_swimming", "stop_swimming",
            "started_burning", "damage_taken", "experience_changed", "weather_changed",
            "started_drowning", "stopped_drowning", "started_freezing", "stopped_freezing", "fully_frozen",
            "start_touching_fluid", "stop_touching_fluid", "player_death");
    // Must be initialized after IMPLEMENTED_EVENTS because eventCatalog()
    // uses the support set while building display labels.
    private static final List<EventEntry> EVENTS = eventCatalog();

    private final ClientRuleDraft draft = ClientRuleDraft.load();
    private final List<Button> eventButtons = new ArrayList<>(), categoryButtons = new ArrayList<>(), pageButtons = new ArrayList<>();
    private EditBox searchBox, idBox, filterKeyBox, filterValueBox, actionValueBox;
    private Button actionTypeButton, enabledButton;
    private Button remoteLoadButton, remoteTestButton, remoteDeleteButton;
    private String category = "全部", notice = "本地草稿未同步";
    private int eventOffset, remoteIndex;
    private int panelLeft, panelTop, panelWidth, panelHeight;
    /** Calculated column geometry.  The old editor used fixed 245/390/190px
     * columns, which overflowed on the default 854x480 Minecraft window. */
    private int leftWidth, centerWidth, rightLeft, rightWidth;
    private int footerButtonY, remoteButtonY;
    private int eventY, eventRows;
    private boolean compactMode;
    private int compactPage;

    public ListenerDashboardScreen() { super(Component.literal("监听器规则编辑器")); }

    @Override protected void init() {
        // Always keep the frame inside the actual framebuffer.  In
        // particular, do not force a 760x430 minimum: that is larger than
        // many players' default GUI-scaled windows.
        panelWidth = Math.min(1120, Math.max(1, width - 16));
        panelHeight = Math.min(620, Math.max(1, height - 16));
        panelLeft = Math.max(0, (width - panelWidth) / 2);
        panelTop = Math.max(0, (height - panelHeight) / 2);
        compactMode = width < 900 || height < 500;

        // Allocate the available width instead of assuming a 1120px screen.
        // The left and centre columns retain enough room for their controls;
        // the action column receives the remainder.  At 854x480 this yields
        // roughly 230/310/266px and every control remains inside its panel.
        int usableWidth = Math.max(1, panelWidth - 24);
        leftWidth = Math.max(1, Math.min(245, Math.round(usableWidth * 0.30f)));
        centerWidth = Math.max(1, Math.min(390, Math.round(usableWidth * 0.40f)));
        // Keep a visible action column even on very narrow logical viewports.
        int minimumRight = Math.min(150, Math.max(1, usableWidth / 4));
        if (leftWidth + centerWidth > usableWidth - minimumRight) {
            centerWidth = Math.max(1, usableWidth - leftWidth - minimumRight);
        }
        if (leftWidth + centerWidth > usableWidth) {
            leftWidth = Math.max(1, Math.min(leftWidth, usableWidth / 2));
            centerWidth = Math.max(1, usableWidth - leftWidth);
        }
        rightWidth = Math.max(1, usableWidth - leftWidth - centerWidth);
        rightLeft = panelLeft + leftWidth + centerWidth + 24;
        footerButtonY = Math.max(panelTop + 1, panelTop + panelHeight - 28);
        // On short windows move the server controls upward so they cannot
        // overlap the footer buttons.
        remoteButtonY = Math.max(panelTop + 1, Math.min(panelTop + 302, footerButtonY - 50));

        if (compactMode) {
            String[] pages = {"事件", "规则", "动作"};
            int pageWidth = Math.max(1, (panelWidth - 24) / pages.length);
            int pageGap = pageWidth + 4;
            for (int i = 0; i < pages.length; i++) {
                final int page = i;
                Button pageButton = Button.builder(Component.literal(pages[i]), ignored -> {
                            compactPage = page;
                            refreshCompactVisibility();
                        })
                        .bounds(panelLeft + 8 + i * pageGap, panelTop + 4, pageWidth, 20).build();
                pageButtons.add(pageButton);
                addRenderableWidget(pageButton);
            }
        }

        int leftInnerWidth = Math.max(1, leftWidth - 32);
        searchBox = new EditBox(font, compactMode ? panelLeft + 8 : panelLeft + 16,
                compactMode ? panelTop + 30 : panelTop + 76,
                compactMode ? Math.max(1, panelWidth - 16) : leftInnerWidth, 20, Component.literal("搜索事件"));
        searchBox.setHint(Component.literal("搜索事件…"));
        searchBox.setResponder(value -> { eventOffset = 0; refreshEventVisibility(); });
        addRenderableWidget(searchBox);
        int categoryY = compactMode ? panelTop + 57 : panelTop + 105;
        for (int i = 0; i < CATEGORIES.length; i++) {
            final String selected = CATEGORIES[i];
            int categoryWidth = Math.max(1, Math.min(70, (leftWidth - 30) / 3));
            int categoryGap = Math.max(1, (leftWidth - 24) / 3);
            Button button = Button.builder(Component.literal(selected), ignored -> { category = selected; eventOffset = 0; refreshCategoryButtons(); refreshEventVisibility(); })
                    .bounds((compactMode ? panelLeft + 8 : panelLeft + 12) + (i % 3) * categoryGap, categoryY + (i / 3) * 23,
                            categoryWidth, 20).build();
            categoryButtons.add(button); addRenderableWidget(button);
        }
        eventY = compactMode ? panelTop + 130 : panelTop + 181;
        eventRows = compactMode ? Math.max(1, Math.min(5, (panelHeight - (eventY - panelTop) - 38) / 24)) : 8;
        for (int i = 0; i < EVENTS.size(); i++) {
            EventEntry event = EVENTS.get(i);
            Button button = Button.builder(Component.literal(event.label), ignored -> selectEvent(event.id))
                    .bounds(panelLeft + (compactMode ? 8 : 16), eventY + (i % eventRows) * 23,
                            compactMode ? Math.max(1, panelWidth - 16) : leftInnerWidth, 21).build();
            eventButtons.add(button); addRenderableWidget(button);
        }
        int formX = compactMode ? panelLeft + 8 : panelLeft + leftWidth + 16;
        int formWidth = compactMode ? Math.max(1, panelWidth - 16) : Math.max(1, centerWidth - 32);
        int formTop = compactMode ? panelTop + 58 : panelTop + 92;
        idBox = new EditBox(font, formX, formTop, formWidth, 20, Component.literal("规则 ID"));
        idBox.setValue(draft.id); idBox.setMaxLength(64); addRenderableWidget(idBox);
        int centerInnerWidth = Math.max(1, centerWidth - 32);
        int filterKeyWidth = Math.max(1, Math.min(156, centerInnerWidth / 2 - 4));
        int filterTop = compactMode ? panelTop + 86 : panelTop + 195;
        filterKeyBox = new EditBox(font, formX, filterTop, compactMode ? Math.max(1, formWidth / 2 - 4) : filterKeyWidth, 20, Component.literal("条件字段"));
        filterKeyBox.setValue(draft.filterKey); filterKeyBox.setMaxLength(64); addRenderableWidget(filterKeyBox);
        int compactFilterKeyWidth = Math.max(1, formWidth / 2 - 4);
        filterValueBox = new EditBox(font, formX + (compactMode ? compactFilterKeyWidth + 8 : filterKeyWidth + 8), filterTop,
                compactMode ? Math.max(1, formWidth - compactFilterKeyWidth - 8) : Math.max(1, centerInnerWidth - filterKeyWidth - 8), 20, Component.literal("条件值"));
        filterValueBox.setValue(draft.filterValue); filterValueBox.setMaxLength(256); addRenderableWidget(filterValueBox);
        int rightInnerWidth = Math.max(1, rightWidth - 16);
        int actionButtonWidth = Math.max(1, Math.min(190, rightInnerWidth));
        int actionX = compactMode ? panelLeft + 8 : rightLeft;
        int actionWidth = compactMode ? Math.max(1, panelWidth - 16) : actionButtonWidth;
        actionTypeButton = Button.builder(Component.literal("动作：" + draft.action), ignored -> cycleAction())
                .bounds(actionX, compactMode ? panelTop + 58 : panelTop + 136, actionWidth, 22).build(); addRenderableWidget(actionTypeButton);
        actionValueBox = new EditBox(font, actionX, compactMode ? panelTop + 86 : panelTop + 181,
                compactMode ? Math.max(1, panelWidth - 16) : rightInnerWidth, 20, Component.literal("动作内容"));
        actionValueBox.setValue(draft.actionValue); actionValueBox.setMaxLength(4096); addRenderableWidget(actionValueBox);
        enabledButton = Button.builder(enabledText(), ignored -> { draft.enabled = !draft.enabled; enabledButton.setMessage(enabledText()); })
                .bounds(actionX, compactMode ? panelTop + 114 : panelTop + 226, actionWidth, 22).build(); addRenderableWidget(enabledButton);
        int footerWidth = Math.max(1, panelWidth - 32);
        int closeWidth = Math.min(94, Math.max(1, footerWidth / 5));
        int footerGap = 6;
        int footerActionWidth = Math.max(1, (footerWidth - closeWidth - footerGap * 2) / 3);
        int footerX = panelLeft + 16;
        addRenderableWidget(Button.builder(Component.literal("保存本地草稿"), ignored -> saveDraft()).bounds(footerX, footerButtonY, footerActionWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("保存并导出"), ignored -> exportDraft()).bounds(footerX + footerActionWidth + footerGap, footerButtonY, footerActionWidth, 22).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), ignored -> onClose()).bounds(panelLeft + panelWidth - closeWidth - 16, footerButtonY, closeWidth, 22).build());
        // The server row has its own split.  Reusing the 190px action width
        // made the test button collapse to 1px on 480px logical viewports.
        int remoteX = compactMode ? panelLeft + 8 : rightLeft;
        int remoteLoadWidth = compactMode ? Math.max(1, (panelWidth - 24) / 2)
                : Math.max(1, Math.min(190, (rightInnerWidth - 8) * 3 / 5));
        int testX = compactMode ? remoteX + remoteLoadWidth + 8 : rightLeft + remoteLoadWidth + 8;
        int testWidth = compactMode ? Math.max(1, panelWidth - remoteLoadWidth - 24)
                : Math.max(1, rightInnerWidth - remoteLoadWidth - 8);
        remoteLoadButton = Button.builder(Component.literal("载入服务器规则"), ignored -> loadRemoteRule())
                .bounds(remoteX, remoteButtonY, remoteLoadWidth, 22).build();
        addRenderableWidget(remoteLoadButton);
        remoteTestButton = Button.builder(Component.literal("测试当前规则"), ignored -> testRemoteRule())
                .bounds(testX, remoteButtonY, testWidth, 22).build();
        addRenderableWidget(remoteTestButton);
        remoteDeleteButton = Button.builder(Component.literal("删除当前规则"), ignored -> deleteRemoteRule())
                .bounds(remoteX, remoteButtonY + 27, compactMode ? remoteLoadWidth : Math.max(1, Math.min(190, rightInnerWidth)), 22).build();
        addRenderableWidget(remoteDeleteButton);
        // In a short GUI-scaled window the fixed-height editor sections leave
        // no safe row for server operations.  Keep editing available and hide
        // these optional controls instead of letting them overlap fields.
        if (panelHeight < 240) {
            remoteLoadButton.visible = false;
            remoteTestButton.visible = false;
            remoteDeleteButton.visible = false;
        }
        selectEvent(draft.event); refreshCategoryButtons(); refreshEventVisibility(); refreshCompactVisibility();
        ListenerNetworking.requestRuleSync();
    }

    private void selectEvent(String event) { draft.event = event; }
    private void cycleAction() {
        int current = 0; for (int i = 0; i < ACTIONS.length; i++) if (ACTIONS[i].equals(draft.action)) current = i;
        draft.action = ACTIONS[(current + 1) % ACTIONS.length]; actionTypeButton.setMessage(Component.literal("动作：" + draft.action));
    }
    private Component enabledText() { return Component.literal((draft.enabled ? "✓ 已启用" : "○ 已停用") + "（点击切换）"); }
    private void refreshCategoryButtons() { for (Button b : categoryButtons) b.active = !b.getMessage().getString().equals(category); }
    private void refreshCompactVisibility() {
        if (!compactMode) return;
        boolean events = compactPage == 0;
        boolean rule = compactPage == 1;
        boolean actions = compactPage == 2;
        searchBox.visible = events;
        for (Button button : categoryButtons) button.visible = events;
        for (Button button : eventButtons) button.visible = events && button.active;
        idBox.visible = rule;
        filterKeyBox.visible = rule;
        filterValueBox.visible = rule;
        actionTypeButton.visible = actions;
        actionValueBox.visible = actions;
        enabledButton.visible = actions;
        boolean remote = actions && panelHeight >= 240;
        remoteLoadButton.visible = remote;
        remoteTestButton.visible = remote;
        remoteDeleteButton.visible = remote;
        for (int i = 0; i < pageButtons.size(); i++) pageButtons.get(i).active = i != compactPage;
    }
    private void refreshEventVisibility() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT);
        int slots = eventSlots();
        eventOffset = Math.min(eventOffset, Math.max(0, filteredEventCount() - slots));
        int shown = 0, matched = 0;
        for (int i = 0; i < EVENTS.size(); i++) {
            EventEntry event = EVENTS.get(i);
            boolean matches = (category.equals("全部") || event.category.equals(category)) && (query.isBlank() || event.id.contains(query) || event.label.contains(query));
            Button button = eventButtons.get(i);
            boolean visible = matches && matched++ >= eventOffset && shown < slots;
            if (visible) button.setY(eventY + shown++ * (compactMode ? 23 : 21));
            button.visible = visible; button.active = visible;
        }
        refreshCompactVisibility();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if ((!compactMode || compactPage == 0) && mouseX >= panelLeft && mouseX <= panelLeft + (compactMode ? panelWidth : leftWidth)
                && mouseY >= eventY - 8 && mouseY <= panelTop + panelHeight - 32) {
            int matches = filteredEventCount();
            int slots = eventSlots();
            eventOffset = Math.max(0, Math.min(Math.max(0, matches - slots), eventOffset + (verticalAmount < 0 ? 1 : -1)));
            refreshEventVisibility();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int filteredEventCount() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT);
        int count = 0;
        for (EventEntry event : EVENTS) if ((category.equals("全部") || event.category.equals(category)) && (query.isBlank() || event.id.contains(query) || event.label.contains(query))) count++;
        return count;
    }
    private int eventSlots() {
        // Event buttons start at panelTop+181 while the card ends at
        // panelTop+panelHeight-76.  Derive the number of rows from that
        // available height rather than always drawing eight off-screen rows.
        return compactMode ? eventRows : Math.max(1, Math.min(8, (panelHeight - 199) / 21));
    }
    private void copyFields() {
        draft.id = idBox.getValue().isBlank() ? "client_rule" : idBox.getValue().trim();
        draft.filterKey = filterKeyBox.getValue().isBlank() ? "*" : filterKeyBox.getValue().trim();
        draft.filterValue = filterValueBox.getValue(); draft.actionValue = actionValueBox.getValue();
    }
    private void saveDraft() { copyFields(); draft.save(); notice = "已保存到 config/listenerclient-draft.properties"; }
    private void exportDraft() {
        copyFields(); draft.save(); ListenerNetworking.sendRuleUpsert(draft);
        notice = !ListenerNetworking.isAccepted() ? "已保存；未连接 Listener 服务端，稍后可重试同步"
                : !ListenerNetworking.serverCapabilities().contains("editor") ? "已保存；当前账号没有 listener.admin 编辑权限"
                : "已保存，正在通过 listener:main 同步";
    }
    private void loadRemoteRule() {
        List<ListenerNetworking.RemoteRule> rules = ListenerNetworking.remoteRules();
        if (rules.isEmpty()) { notice = "服务器没有返回可编辑规则（需要 listener.admin）"; return; }
        ListenerNetworking.RemoteRule rule = rules.get(Math.floorMod(remoteIndex, rules.size()));
        remoteIndex = (remoteIndex + 1) % rules.size();
        draft.id = rule.id();
        draft.event = rule.event().startsWith("client_") ? rule.event().substring("client_".length()) : rule.event();
        draft.enabled = rule.enabled();
        if (rule.filters().isEmpty()) { draft.filterKey = "*"; draft.filterValue = ""; }
        else {
            var filter = rule.filters().entrySet().iterator().next();
            draft.filterKey = filter.getKey(); draft.filterValue = filter.getValue();
        }
        if (!rule.actions().isEmpty()) {
            var action = rule.actions().entrySet().iterator().next();
            draft.action = containsAction(action.getKey()) ? action.getKey() : "message";
            draft.actionValue = action.getValue();
        }
        if (idBox != null) idBox.setValue(draft.id);
        if (filterKeyBox != null) filterKeyBox.setValue(draft.filterKey);
        if (filterValueBox != null) filterValueBox.setValue(draft.filterValue);
        if (actionValueBox != null) actionValueBox.setValue(draft.actionValue);
        if (actionTypeButton != null) actionTypeButton.setMessage(Component.literal("动作：" + draft.action));
        if (enabledButton != null) enabledButton.setMessage(enabledText());
        notice = "已载入服务器规则：" + rule.id();
    }
    private void testRemoteRule() {
        copyFields(); ListenerNetworking.sendRuleTest(draft.id);
        notice = "已请求服务器测试规则：" + draft.id;
    }
    private void deleteRemoteRule() {
        copyFields(); ListenerNetworking.sendRuleDelete(draft.id);
        notice = "已请求删除服务器规则：" + draft.id;
    }
    private static boolean containsAction(String value) {
        for (String action : ACTIONS) if (action.equals(value)) return true;
        return false;
    }
    @Override public void onClose() { copyFields(); draft.save(); super.onClose(); }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, BG, 0xFF142A43); graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL); graphics.outline(panelLeft, panelTop, panelWidth, panelHeight, EDGE);
        if (compactMode) {
            graphics.text(font, Component.literal("监听器编辑器"), panelLeft + 8, panelTop + panelHeight - 13, TEXT);
            String pageTitle = compactPage == 0 ? "事件目录（滚轮浏览）"
                    : compactPage == 1 ? "规则与过滤条件" : "动作与服务器操作";
            graphics.text(font, Component.literal(pageTitle), panelLeft + 8, panelTop + 28, MUTED);
            graphics.text(font, Component.literal(notice), panelLeft + 8, Math.max(panelTop + 1, footerButtonY - 12), GOOD);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }
        drawPanel(graphics, panelLeft + 8, panelTop + 58, leftWidth - 16, panelHeight - 76, "监听条件");
        drawPanel(graphics, panelLeft + leftWidth + 8, panelTop + 58, centerWidth - 16, panelHeight - 76, "条件卡片");
        drawPanel(graphics, rightLeft - 8, panelTop + 58, rightWidth, panelHeight - 76, "动作预览");
        graphics.text(font, Component.literal("监听器 · 规则编辑器"), panelLeft + 18, panelTop + 15, TEXT);
        graphics.text(font, Component.literal("FancyMenu 风格客户端事件工作台"), panelLeft + 18, panelTop + 34, MUTED);
        graphics.text(font, Component.literal("K"), panelLeft + panelWidth - 150, panelTop + 19, ACCENT); graphics.text(font, Component.literal("打开/关闭编辑器"), panelLeft + panelWidth - 133, panelTop + 19, MUTED);
        graphics.text(font, Component.literal("规则 ID"), panelLeft + leftWidth + 16, panelTop + 78, MUTED);
        graphics.text(font, Component.literal("FancyMenu 目录：" + FANCY_MENU_PROVIDER_COUNT + " 个 + 1 个兼容别名 · 滚轮浏览"), panelLeft + 18, panelTop + 169, MUTED);
        graphics.text(font, Component.literal("当前事件：" + draft.event), panelLeft + leftWidth + 16, panelTop + 132, ACCENT);
        graphics.text(font, Component.literal("筛选条件（字段支持 *_contains）"), panelLeft + leftWidth + 16, panelTop + 177, MUTED);
        graphics.text(font, Component.literal("服务器规则（只读预览）"), panelLeft + leftWidth + 16, panelTop + 238, MUTED);
        int ruleLine = 0;
        for (ListenerNetworking.RemoteRule rule : ListenerNetworking.remoteRules()) {
            if (ruleLine >= 6) break;
            graphics.text(font, Component.literal("• " + rule.id() + " · " + rule.event()), panelLeft + leftWidth + 18, panelTop + 254 + ruleLine++ * 14, TEXT);
        }
        graphics.text(font, Component.literal("动作类型（点击循环）"), rightLeft, panelTop + 117, MUTED); graphics.text(font, Component.literal("动作值 / 模板"), rightLeft, panelTop + 164, MUTED);
        graphics.text(font, Component.literal("服务器规则：" + ListenerNetworking.remoteRules().size() + " 条"), rightLeft, Math.min(panelTop + 262, footerButtonY - 70), MUTED);
        graphics.text(font, Component.literal("导出格式：event + filter + action"), rightLeft, Math.min(panelTop + 280, footerButtonY - 52), MUTED); graphics.text(font, Component.literal(notice), panelLeft + 18, panelTop + panelHeight - 26, GOOD);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String title) {
        // A GUI-scaled viewport can be only a few hundred logical pixels
        // wide/high.  Never pass negative geometry to GuiGraphicsExtractor;
        // its scissor implementation rejects that with an exception.
        int safeX = Math.max(0, Math.min(x, width));
        int safeY = Math.max(0, Math.min(y, height));
        int safeW = Math.max(1, Math.min(w, width - safeX));
        int safeH = Math.max(1, Math.min(h, height - safeY));
        graphics.fill(safeX, safeY, safeX + safeW, safeY + safeH, CARD);
        graphics.outline(safeX, safeY, safeW, safeH, EDGE);
        graphics.text(font, Component.literal(title), safeX + 10, Math.min(safeY + 9, height - 1), TEXT);
    }
    @Override public boolean isPauseScreen() { return false; }

    /** Full FancyMenu 26.2 provider catalog; unsupported client-local providers remain selectable for future bridges. */
    private static List<EventEntry> eventCatalog() {
        String[] rows = {
                "keyboard_key_pressed|键盘", "keyboard_key_released|键盘", "keyboard_char_typed|键盘",
                "mouse_moved|鼠标", "mouse_button_clicked|鼠标", "mouse_button_released|鼠标", "mouse_scrolled|鼠标",
                "text_clicked|界面", "text_hovered|界面", "screen_open|屏幕", "screen_close|屏幕", "quit_minecraft|连接",
                "fm_variable_updated|数据", "file_downloaded_via_action|资源", "file_selected_via_action|资源", "zip_extracted_via_action|资源",
                "element_spawned_via_action|界面", "animated_texture_started_playing|媒体", "animated_texture_finished_playing|媒体",
                "video_playback_status_changed|媒体", "chat_message_received|聊天", "chat_message_sent|聊天", "system_message_received_in_chat|聊天",
                "effect_gained|玩家", "effect_lost|玩家", "experience_changed|状态", "damage_taken|状态", "started_freezing|状态",
                "stopped_freezing|状态", "fully_frozen|状态", "start_looking_at_block|世界", "stop_looking_at_block|世界",
                "start_looking_at_entity|世界", "stop_looking_at_entity|世界", "entity_spawned|实体", "entity_died|实体",
                "entity_starts_being_in_sight|实体", "entity_stops_being_in_sight|实体", "entity_interacted|实体", "entity_mounted|实体",
                "entity_unmounted|实体", "block_broke|方块", "block_placed|方块", "interacted_with_block|方块", "stepping_on_block|方块",
                "enter_biome|世界", "leave_biome|世界", "enter_structure|世界", "leave_structure|世界",
                "enter_structure_high_precision|世界", "leave_structure_high_precision|世界", "enter_dimension|世界", "dimension_entered|世界", "start_swimming|状态",
                "stop_swimming|状态", "start_touching_fluid|状态", "stop_touching_fluid|状态", "music_track_started|媒体",
                "music_track_stopped|媒体", "world_sound_triggered|媒体", "weather_changed|状态", "started_burning|状态",
                "stopped_burning|状态", "started_drowning|状态", "position_changed|世界", "started_running|状态", "stopped_running|状态",
                "jump|玩家", "server_joined|连接", "server_left|连接", "fm_data_received|数据", "remote_server_connected|连接",
                "remote_server_data_received|连接", "remote_server_connection_closed|连接", "world_entered|世界", "world_left|世界",
                "other_player_joined_world|实体", "other_player_left_world|实体", "player_death|玩家", "other_player_died|实体",
                "item_picked_up|物品", "item_dropped|物品", "item_hovered_in_inventory|物品", "item_consumed|物品",
                "item_used|物品", "item_broke|物品"
        };
        List<EventEntry> result = new ArrayList<>(rows.length);
        for (String row : rows) {
            String[] parts = row.split("\\|", 2);
            String id = parts[0];
            String group = switch (parts[1]) {
                case "屏幕", "键盘", "鼠标", "世界", "状态" -> parts[1];
                default -> "更多";
            };
            result.add(new EventEntry(group, id, displayEvent(id)));
        }
        return List.copyOf(result);
    }

    private static String displayEvent(String id) {
        String label = switch (id) {
            case "screen_open" -> "打开屏幕";
            case "screen_close" -> "关闭屏幕";
            case "keyboard_key_pressed" -> "按下按键";
            case "keyboard_key_released" -> "释放按键";
            case "keyboard_char_typed" -> "输入字符";
            case "dimension_entered" -> "进入维度（兼容别名）";
            default -> id;
        };
        if (isLocalResourceProvider(id)) return label + "（FancyMenu 专属，需扩展）";
        return IMPLEMENTED_EVENTS.contains(id) ? label + "（客户端已实现）" : label + "（目录可选，需扩展）";
    }

    private static boolean isLocalResourceProvider(String id) {
        return id.contains("file_") || id.contains("zip_") || id.contains("animated_texture")
                || id.contains("video_") || id.equals("element_spawned_via_action")
                || id.contains("remote_server") || id.equals("fm_data_received") || id.equals("fm_variable_updated");
    }

    private record EventEntry(String category, String id, String label) { }
}
