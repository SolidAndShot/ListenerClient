# 监听器客户端（Listener Client）

这是 [Listener 服务端插件](https://github.com/SolidAndShot/Listener) 的可选 Fabric 客户端 Mod。它把服务端无法直接观察的键盘、鼠标、屏幕、注视目标和客户端状态，通过原生 Plugin Messaging 转发给插件，也能接收插件下发的安全客户端动作。

目标环境：Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.153.0+26.2、Java 25。

## 安装

1. 在客户端安装与 Minecraft 26.2 匹配的 Fabric Loader 和 Fabric API。
2. 下载 `build/libs/listenerclient-1.0.0.jar`，放入客户端 `.minecraft/mods`。
3. 服务端安装 [Listener](https://github.com/SolidAndShot/Listener) 插件并重启；客户端没有本 Mod 时，服务端原有监听器仍可正常运行。

本 Mod 没有额外端口，也不需要 ProtocolLib。连接服务器后会自动进行一次协议握手；只有握手成功后才会上报客户端事件。

## 已实现的客户端事件

事件会被服务端自动规范化为 `client_<事件名>`，例如 `keyboard_key_pressed` 对应规则事件 `client_keyboard_key_pressed`。

- 输入：`keyboard_key_pressed`、`keyboard_key_released`、`keyboard_char_typed`
- 鼠标：`mouse_button_clicked`、`mouse_button_released`、`mouse_scrolled`、`mouse_moved`
- 界面与连接：`screen_open`、`screen_close`、`dimension_entered`、`client_tick`
- 玩家状态：`position_changed`、`start_looking_at_block`、`stop_looking_at_block`、`start_looking_at_entity`、`stop_looking_at_entity`
- 状态差分：`started_running`、`stopped_running`、`started_swimming`、`stopped_swimming`、`started_burning`、`stopped_burning`、`damage_taken`、`experience_changed`、`weather_changed`

常见字段包括：`key_keycode`、`key_scancode`、`key_modifiers`、`button`、`mouse_pos_x`、`mouse_pos_y`、`delta_x`、`delta_y`、`screen`、`dimension_key`、`old_pos_x`、`new_pos_x`、`target`、`damage_amount`、`new_health` 和 `weather_type`。具体字段可在服务端 `/listener info <id>` 和日志中确认。

## 配置示例

在服务端 `plugins/Listener/config.yml` 中：

```yaml
listeners:
  open_menu_key:
    event: client_keyboard_key_pressed
    filters:
      key_keycode: "69" # E 键，具体键值由客户端上报
    actions:
      - type: client_screen
        value: inventory

  click_notice:
    event: client_mouse_button_clicked
    filters:
      button: left
    actions:
      - type: client_overlay
        value: "&a收到鼠标点击"
```

插件支持的客户端动作：

- `client_message`：客户端聊天栏消息；
- `client_overlay`：ActionBar 覆盖层消息；
- `client_screen`：`inventory` 打开背包，`close`/`none` 关闭界面；
- `client_sound`：播放受客户端允许列表控制的提示音（音量、音调仍读取 value）；
- `client_action`：`动作名|参数`，未知动作会被客户端忽略。

客户端事件来自玩家自己的进程，不能作为反作弊凭据或管理员认证。不要仅凭 `client_*` 事件执行封禁、权限变更等高权限控制台命令。

## 协议摘要

通道为 `listener:main`，每帧首字节是 `version=1`，第二字节是操作码，字符串采用 Java `DataOutputStream.writeUTF/readUTF`，单帧上限 32 KiB、单字符串上限 4096 字符：

| 方向 | opcode | 内容 |
| --- | ---: | --- |
| 客户端 → 服务端 | 1 HELLO | 客户端协议版本、Mod 版本、功能列表 |
| 服务端 → 客户端 | 2 HELLO_ACK | 是否接受、原因、能力列表 |
| 服务端 → 客户端 | 3 ACTION | 动作名、动作值 |
| 客户端 → 服务端 | 4 EVENT | 事件名、键值字段 |

## 构建

```powershell
gradlew.bat clean build
```

产物：`build/libs/listenerclient-1.0.0.jar`。
