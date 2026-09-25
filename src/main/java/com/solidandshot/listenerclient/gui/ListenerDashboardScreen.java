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
    private static final List<EventEntry> EVENTS = eventCatalog();
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

    private final ClientRuleDraft draft = ClientRuleDraft.load();
    private final List<Button> eventButtons = new ArrayList<>(), categoryButtons = new ArrayList<>();
    private EditBox searchBox, idBox, filterKeyBox, filterValueBox, actionValueBox;
    private Button actionTypeButton, enabledButton;
    private Button remoteLoadButton;
    private String category = "全部", notice = "本地草稿未同步";
    private int eventOffset, remoteIndex;
    private int panelLeft, panelTop, panelWidth, panelHeight;

    public ListenerDashboardScreen() { super(Component.literal("监听器规则编辑器")); }

    @Override protected void init() {
        panelWidth = Math.min(1120, Math.max(760, width - 24));
        panelHeight = Math.min(620, Math.max(430, height - 24));
        panelLeft = (width - panelWidth) / 2; panelTop = (height - panelHeight) / 2;
        int leftWidth = 245, centerWidth = 390, rightLeft = panelLeft + leftWidth + centerWidth + 24;
        searchBox = new EditBox(font, panelLeft + 16, panelTop + 76, leftWidth - 32, 20, Component.literal("搜索事件"));
        searchBox.setHint(Component.literal("搜索事件…"));
        searchBox.setResponder(value -> { eventOffset = 0; refreshEventVisibility(); });
        addRenderableWidget(searchBox);
        int categoryY = panelTop + 105;
        for (int i = 0; i < CATEGORIES.length; i++) {
            final String selected = CATEGORIES[i];
            Button button = Button.builder(Component.literal(selected), ignored -> { category = selected; eventOffset = 0; refreshCategoryButtons(); refreshEventVisibility(); })
                    .bounds(panelLeft + 12 + (i % 3) * 76, categoryY + (i / 3) * 23, 70, 20).build();
            categoryButtons.add(button); addRenderableWidget(button);
        }
        int eventY = panelTop + 181;
        for (int i = 0; i < EVENTS.size(); i++) {
            EventEntry event = EVENTS.get(i);
            Button button = Button.builder(Component.literal(event.label), ignored -> selectEvent(event.id))
                    .bounds(panelLeft + 16, eventY + (i % 8) * 25, leftWidth - 32, 21).build();
            eventButtons.add(button); addRenderableWidget(button);
        }
        idBox = new EditBox(font, panelLeft + leftWidth + 16, panelTop + 92, centerWidth - 32, 20, Component.literal("规则 ID"));
        idBox.setValue(draft.id); idBox.setMaxLength(64); addRenderableWidget(idBox);
        filterKeyBox = new EditBox(font, panelLeft + leftWidth + 16, panelTop + 195, 156, 20, Component.literal("条件字段"));
        filterKeyBox.setValue(draft.filterKey); filterKeyBox.setMaxLength(64); addRenderableWidget(filterKeyBox);
        filterValueBox = new EditBox(font, panelLeft + leftWidth + 184, panelTop + 195, centerWidth - 200, 20, Component.literal("条件值"));
        filterValueBox.setValue(draft.filterValue); filterValueBox.setMaxLength(256); addRenderableWidget(filterValueBox);
        actionTypeButton = Button.builder(Component.literal("动作：" + draft.action), ignored -> cycleAction())
                .bounds(rightLeft, panelTop + 136, 190, 22).build(); addRenderableWidget(actionTypeButton);
        actionValueBox = new EditBox(font, rightLeft, panelTop + 181, panelLeft + panelWidth - 18 - rightLeft, 20, Component.literal("动作内容"));
        actionValueBox.setValue(draft.actionValue); actionValueBox.setMaxLength(4096); addRenderableWidget(actionValueBox);
        enabledButton = Button.builder(enabledText(), ignored -> { draft.enabled = !draft.enabled; enabledButton.setMessage(enabledText()); })
                .bounds(rightLeft, panelTop + 226, 190, 22).build(); addRenderableWidget(enabledButton);
        addRenderableWidget(Button.builder(Component.literal("保存本地草稿"), ignored -> saveDraft()).bounds(rightLeft, panelTop + panelHeight - 57, 145, 22).build());
        addRenderableWidget(Button.builder(Component.literal("保存并导出"), ignored -> exportDraft()).bounds(rightLeft + 153, panelTop + panelHeight - 57, 145, 22).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), ignored -> onClose()).bounds(panelLeft + panelWidth - 110, panelTop + panelHeight - 57, 94, 22).build());
        remoteLoadButton = Button.builder(Component.literal("载入服务器规则"), ignored -> loadRemoteRule())
                .bounds(rightLeft, panelTop + 302, 190, 22).build();
        addRenderableWidget(remoteLoadButton);
        addRenderableWidget(Button.builder(Component.literal("测试当前规则"), ignored -> testRemoteRule())
                .bounds(rightLeft + 198, panelTop + 302, 125, 22).build());
        addRenderableWidget(Button.builder(Component.literal("删除当前规则"), ignored -> deleteRemoteRule())
                .bounds(rightLeft, panelTop + 329, 190, 22).build());
        selectEvent(draft.event); refreshCategoryButtons(); refreshEventVisibility();
        ListenerNetworking.requestRuleSync();
    }

    private void selectEvent(String event) { draft.event = event; }
    private void cycleAction() {
        int current = 0; for (int i = 0; i < ACTIONS.length; i++) if (ACTIONS[i].equals(draft.action)) current = i;
        draft.action = ACTIONS[(current + 1) % ACTIONS.length]; actionTypeButton.setMessage(Component.literal("动作：" + draft.action));
    }
    private Component enabledText() { return Component.literal((draft.enabled ? "✓ 已启用" : "○ 已停用") + "（点击切换）"); }
    private void refreshCategoryButtons() { for (Button b : categoryButtons) b.active = !b.getMessage().getString().equals(category); }
    private void refreshEventVisibility() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT);
        eventOffset = Math.min(eventOffset, Math.max(0, filteredEventCount() - 8));
        int shown = 0, matched = 0;
        for (int i = 0; i < EVENTS.size(); i++) {
            EventEntry event = EVENTS.get(i);
            boolean matches = (category.equals("全部") || event.category.equals(category)) && (query.isBlank() || event.id.contains(query) || event.label.contains(query));
            Button button = eventButtons.get(i);
            boolean visible = matches && matched++ >= eventOffset && shown < 8;
            if (visible) button.setY(panelTop + 181 + shown++ * 21);
            button.visible = visible; button.active = visible;
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelLeft && mouseX <= panelLeft + 245 && mouseY >= panelTop + 150 && mouseY <= panelTop + panelHeight - 70) {
            int matches = filteredEventCount();
            eventOffset = Math.max(0, Math.min(Math.max(0, matches - 8), eventOffset + (verticalAmount < 0 ? 1 : -1)));
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
        int leftWidth = 245, centerWidth = 390, rightLeft = panelLeft + leftWidth + centerWidth + 24;
        drawPanel(graphics, panelLeft + 8, panelTop + 58, leftWidth - 16, panelHeight - 76, "监听条件");
        drawPanel(graphics, panelLeft + leftWidth + 8, panelTop + 58, centerWidth - 16, panelHeight - 76, "条件卡片");
        drawPanel(graphics, rightLeft - 8, panelTop + 58, panelLeft + panelWidth - rightLeft - 8, panelHeight - 76, "动作预览");
        graphics.text(font, Component.literal("监听器 · 规则编辑器"), panelLeft + 18, panelTop + 15, TEXT);
        graphics.text(font, Component.literal("FancyMenu 风格客户端事件工作台"), panelLeft + 18, panelTop + 34, MUTED);
        graphics.text(font, Component.literal("K"), panelLeft + panelWidth - 150, panelTop + 19, ACCENT); graphics.text(font, Component.literal("打开/关闭编辑器"), panelLeft + panelWidth - 133, panelTop + 19, MUTED);
        graphics.text(font, Component.literal("规则 ID"), panelLeft + leftWidth + 16, panelTop + 78, MUTED);
        graphics.text(font, Component.literal("FancyMenu 目录：" + EVENTS.size() + " 个 · 滚轮浏览"), panelLeft + 18, panelTop + 169, MUTED);
        graphics.text(font, Component.literal("当前事件：" + draft.event), panelLeft + leftWidth + 16, panelTop + 132, ACCENT);
        graphics.text(font, Component.literal("筛选条件（字段支持 *_contains）"), panelLeft + leftWidth + 16, panelTop + 177, MUTED);
        graphics.text(font, Component.literal("服务器规则（只读预览）"), panelLeft + leftWidth + 16, panelTop + 238, MUTED);
        int ruleLine = 0;
        for (ListenerNetworking.RemoteRule rule : ListenerNetworking.remoteRules()) {
            if (ruleLine >= 6) break;
            graphics.text(font, Component.literal("• " + rule.id() + " · " + rule.event()), panelLeft + leftWidth + 18, panelTop + 254 + ruleLine++ * 14, TEXT);
        }
        graphics.text(font, Component.literal("动作类型（点击循环）"), rightLeft, panelTop + 117, MUTED); graphics.text(font, Component.literal("动作值 / 模板"), rightLeft, panelTop + 164, MUTED);
        graphics.text(font, Component.literal("服务器规则：" + ListenerNetworking.remoteRules().size() + " 条"), rightLeft, panelTop + 262, MUTED);
        graphics.text(font, Component.literal("导出格式：event + filter + action"), rightLeft, panelTop + 280, MUTED); graphics.text(font, Component.literal(notice), panelLeft + 18, panelTop + panelHeight - 26, GOOD);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String title) { graphics.fill(x, y, x + w, y + h, CARD); graphics.outline(x, y, w, h, EDGE); graphics.text(font, Component.literal(title), x + 10, y + 9, TEXT); }
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
