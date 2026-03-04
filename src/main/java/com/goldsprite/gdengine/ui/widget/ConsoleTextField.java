package com.goldsprite.gdengine.ui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextField;

/**
 * 通用控制台输入栏 — 封装 VisTextField + 历史翻阅 + Enter 提交 + MC 风格命令提示。
 * <p>
 * 内置功能（继承自 VisTextField）:
 * <ul>
 *   <li>光标左右移动 (← →)、Home / End 跳转行首行尾</li>
 *   <li>Ctrl+A 全选、Ctrl+C 复制 / Ctrl+X 剪切 / Ctrl+V 粘贴</li>
 *   <li>文本选中（Shift+方向键 / 鼠标拖选）</li>
 * </ul>
 * <p>
 * 额外封装功能:
 * <ul>
 *   <li>↑↓ 历史命令翻阅</li>
 *   <li>Enter 触发提交回调</li>
 *   <li>MC 风格命令自动补全：灰色幽灵文本内嵌 + 浮动提示弹窗</li>
 *   <li>Tab 键循环切换命令建议</li>
 *   <li>自动附加 TextFieldPasteMenu（Android 长按上下文菜单）</li>
 * </ul>
 * <p>
 * 命令提示用法:
 * <pre>
 * ConsoleTextField cmd = new ConsoleTextField();
 * cmd.setHintProvider(prefix -> {
 *     List&lt;HintEntry&gt; entries = new ArrayList&lt;&gt;();
 *     entries.add(new HintEntry("help", "显示帮助信息"));
 *     entries.add(new HintEntry("tp", "传送到指定坐标"));
 *     return entries.stream()
 *         .filter(e -> e.name.startsWith(prefix))
 *         .collect(Collectors.toList());
 * });
 * cmd.setCommandPrefix("/"); // 默认 "/"
 * cmd.setOnSubmit(text -> executeCommand(text));
 * table.add(cmd).growX().height(28);
 * </pre>
 */
public class ConsoleTextField extends VisTable {

    // ══════════════ 静态焦点追踪 ══════════════

    /** 当前获得键盘焦点的 ConsoleTextField 数量 */
    private static int focusedCount = 0;

    /**
     * 是否有任意 ConsoleTextField 持有键盘焦点。
     * 外部可据此禁用全局按键处理（如 WASD 移动）。
     */
    public static boolean isAnyFocused() {
        return focusedCount > 0;
    }

    // ══════════════ 命令提示数据类型 ══════════════

    /** 命令提示条目 — 包含命令名和描述 */
    public static class HintEntry {
        public final String name;
        public final String description;

        public HintEntry(String name, String description) {
            this.name = name;
            this.description = description != null ? description : "";
        }
    }

    /**
     * 命令提示供应器 — 外部实现此接口以提供命令建议。
     * <p>
     * 通过 {@link #setHintProvider(HintProvider)} 注入，
     * ConsoleTextField 会在输入变化时自动调用以获取建议列表。
     */
    public interface HintProvider {
        /**
         * 获取匹配前缀的命令建议列表。
         *
         * @param prefix 命令名前缀（不含命令标识符如 "/"）
         * @return 匹配的命令条目列表，空列表表示无匹配
         */
        List<HintEntry> getSuggestions(String prefix);
    }

    // ══════════════ 字段 ══════════════

    /** 内部输入框（支持幽灵文本渲染） */
    private final HintTextField textField;

    /** 输入历史 */
    private final List<String> history = new ArrayList<>();
    /** 当前历史索引（history.size() 表示当前编辑，非历史） */
    private int historyIndex = 0;
    /** 暂存当前正在编辑的文本（翻阅历史时保存） */
    private String pendingInput = "";
    /** 历史上限 */
    private int maxHistory = 50;

    // ── 回调 ──
    /** 提交回调 */
    private Consumer<String> onSubmit;
    /** 文本变化回调 */
    private Consumer<String> onTextChanged;
    /** Tab 键回调（仅在无内部命令建议时触发，作为外部扩展点） */
    private Consumer<String> onTabPressed;

    // ── 命令提示 ──
    /** 提示供应器 */
    private HintProvider hintProvider;
    /** 命令前缀（默认 "/"） */
    private String commandPrefix = "/";
    /** 当前匹配的命令建议列表 */
    private final List<HintEntry> currentSuggestions = new ArrayList<>();
    /** 当前选中的建议索引（Tab 循环切换） */
    private int suggestionIndex = 0;

    /** 浮动提示弹窗（所有匹配命令列表，悬浮在输入栏上方） */
    private VisTable hintPopup;
    /** 弹窗内的文本标签 */
    private VisLabel hintPopupLabel;
    /** 缓存坐标计算向量（避免每帧分配） */
    private final Vector2 tempVec = new Vector2();

    // ══════════════ 构造 ══════════════

    public ConsoleTextField() {
        this("");
    }

    public ConsoleTextField(String initialText) {
        textField = new HintTextField(initialText);

        // 自动附加长按上下文菜单（Android 友好）
        TextFieldPasteMenu.attach(textField);

        // 焦点追踪：获得/失去键盘焦点时更新静态计数器
        textField.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (focused) {
                    focusedCount++;
                } else {
                    focusedCount = Math.max(0, focusedCount - 1);
                    hideHintPopup();
                }
            }
        });

        // 注册键盘事件: Enter 提交 + ↑↓ 历史翻阅 + Tab 补全 + Esc 关闭提示
        textField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER) {
                    submitInput();
                    return true;
                }
                if (keycode == Input.Keys.UP) {
                    navigateHistory(-1);
                    return true;
                }
                if (keycode == Input.Keys.DOWN) {
                    navigateHistory(1);
                    return true;
                }
                if (keycode == Input.Keys.TAB) {
                    handleTab();
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    textField.setGhostText("");
                    hideHintPopup();
                    return false;
                }
                return false;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                // 在下一帧触发回调（此时 TextField 文本已更新）
                if (character != '\r' && character != '\n' && character != '\t') {
                    Gdx.app.postRunnable(() -> {
                        String text = textField.getText();
                        updateCommandHint(text);
                        if (onTextChanged != null) {
                            onTextChanged.accept(text);
                        }
                    });
                }
                return false;
            }
        });

        // 初始化浮动提示弹窗
        initHintPopup();

        // 布局: 输入框填满整个 Table
        add(textField).growX();

        // 初始化历史索引
        historyIndex = 0;
    }

    // ══════════════ 浮动提示弹窗 ══════════════

    /** 初始化浮动提示弹窗（半透明深色背景 + 灰白文字） */
    private void initHintPopup() {
        hintPopup = new VisTable();
        try {
            Drawable bg = VisUI.getSkin().newDrawable("white",
                new Color(0.15f, 0.15f, 0.15f, 0.92f));
            hintPopup.setBackground(bg);
        } catch (Exception ignored) {
            // 若 VisUI Skin 无 "white" drawable 则无背景
        }
        hintPopup.setTouchable(Touchable.disabled);
        hintPopup.setVisible(false);

        hintPopupLabel = new VisLabel("", "small");
        hintPopupLabel.setColor(0.85f, 0.85f, 0.85f, 0.95f);
        hintPopupLabel.setWrap(true);
        hintPopup.add(hintPopupLabel).growX().pad(4, 6, 4, 6);
    }

    // ══════════════ 命令提示核心逻辑 ══════════════

    /**
     * 输入变化时更新命令提示（内嵌幽灵文本 + 浮动弹窗）。
     * <p>
     * 逻辑:
     * <ul>
     *   <li>文本不以 commandPrefix 开头 → 清除所有提示</li>
     *   <li>正在输入命令名（无空格）→ 显示匹配列表 + 幽灵补全</li>
     *   <li>已输入参数（有空格）→ 显示该命令的描述</li>
     * </ul>
     */
    private void updateCommandHint(String text) {
        if (hintProvider == null || text == null || !text.startsWith(commandPrefix)) {
            textField.setGhostText("");
            hideHintPopup();
            currentSuggestions.clear();
            return;
        }

        String afterPrefix = text.substring(commandPrefix.length());
        String cmdPart = afterPrefix.split("\\s+", 2)[0];
        boolean hasArgs = afterPrefix.contains(" ");

        if (hasArgs) {
            // ── 已输入参数区域 — 显示该命令的描述 ──
            textField.setGhostText("");
            List<HintEntry> all = hintProvider.getSuggestions(cmdPart);
            HintEntry match = null;
            for (HintEntry e : all) {
                if (e.name.equalsIgnoreCase(cmdPart)) {
                    match = e;
                    break;
                }
            }
            if (match != null) {
                showHintPopup(commandPrefix + match.name + " — " + match.description);
            } else {
                hideHintPopup();
            }
            currentSuggestions.clear();
        } else {
            // ── 仍在输入命令名 — 显示匹配的命令列表 ──
            currentSuggestions.clear();
            currentSuggestions.addAll(hintProvider.getSuggestions(cmdPart));
            suggestionIndex = 0;

            if (currentSuggestions.isEmpty()) {
                textField.setGhostText("");
                hideHintPopup();
            } else {
                // 内嵌灰色幽灵提示：第一个匹配命令的完整文本
                textField.setGhostText(commandPrefix + currentSuggestions.get(0).name);

                // 浮动弹窗：所有匹配命令列表（每条独占一行，便于阅读）
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < currentSuggestions.size(); i++) {
                    HintEntry entry = currentSuggestions.get(i);
                    if (i > 0) sb.append("\n");
                    sb.append(commandPrefix).append(entry.name);
                    if (!entry.description.isEmpty()) sb.append(" ").append(entry.description);
                }
                showHintPopup(sb.toString());
            }
        }
    }

    /** 处理 Tab 键 — 优先内部命令补全，否则调用外部回调 */
    private void handleTab() {
        String text = textField.getText();

        // 有命令建议时循环补全
        if (!currentSuggestions.isEmpty() && text != null && text.startsWith(commandPrefix)) {
            HintEntry suggestion = currentSuggestions.get(
                suggestionIndex % currentSuggestions.size());
            setText(commandPrefix + suggestion.name + " ");
            suggestionIndex = (suggestionIndex + 1) % currentSuggestions.size();
            updateCommandHint(textField.getText());
            return;
        }

        // 无内部建议时调用外部 Tab 回调
        if (onTabPressed != null) {
            onTabPressed.accept(text);
        }
    }

    /** 显示浮动提示弹窗 */
    private void showHintPopup(String text) {
        hintPopupLabel.setText(text);
        hintPopup.setVisible(true);
    }

    /** 隐藏浮动提示弹窗 */
    private void hideHintPopup() {
        hintPopup.setVisible(false);
        hintPopupLabel.setText("");
    }

    // ══════════════ 生命周期 ══════════════

    @Override
    public void act(float delta) {
        super.act(delta);

        Stage stage = getStage();
        if (stage == null) {
            if (hintPopup.getStage() != null) hintPopup.remove();
            return;
        }

        // 确保弹窗已添加到当前 Stage
        if (hintPopup.getStage() != stage) {
            if (hintPopup.getStage() != null) hintPopup.remove();
            stage.addActor(hintPopup);
        }

        // 定位弹窗到输入栏上方
        if (hintPopup.isVisible()) {
            tempVec.set(0, getHeight());
            localToStageCoordinates(tempVec);
            hintPopup.setSize(getWidth(), hintPopup.getPrefHeight());
            hintPopup.setPosition(tempVec.x, tempVec.y + 2);
        }
    }

    @Override
    public boolean remove() {
        hideHintPopup();
        if (hintPopup.getStage() != null) hintPopup.remove();
        return super.remove();
    }

    // ══════════════ 公开 API ══════════════

    /**
     * 设置命令提示供应器（启用 MC 风格自动补全）。
     * <p>
     * 设置后，输入以 commandPrefix 开头的文本时，
     * 自动显示内嵌幽灵文本和浮动提示弹窗。
     *
     * @param provider 提示供应器，null 表示禁用命令提示
     */
    public void setHintProvider(HintProvider provider) {
        this.hintProvider = provider;
    }

    /**
     * 设置命令前缀（默认 "/"）。
     * 仅以此前缀开头的输入才会触发命令提示。
     */
    public void setCommandPrefix(String prefix) {
        this.commandPrefix = prefix != null ? prefix : "/";
    }

    /** 设置提交回调（Enter 键触发） */
    public void setOnSubmit(Consumer<String> callback) {
        this.onSubmit = callback;
    }

    /** 设置文本变化回调（每次内容改变时触发，参数为当前文本） */
    public void setOnTextChanged(Consumer<String> callback) {
        this.onTextChanged = callback;
    }

    /** 设置 Tab 键回调（仅在无内部命令建议时触发，作为外部扩展点） */
    public void setOnTabPressed(Consumer<String> callback) {
        this.onTabPressed = callback;
    }

    /** 设置占位符提示文字 */
    public void setMessageText(String text) {
        textField.setMessageText(text);
    }

    /** 获取当前文本 */
    public String getText() {
        return textField.getText();
    }

    /** 设置文本并将光标移到末尾 */
    public void setText(String text) {
        textField.setText(text);
        textField.setCursorPosition(text != null ? text.length() : 0);
    }

    /** 清空输入并隐藏所有提示 */
    public void clear() {
        textField.setText("");
        textField.setGhostText("");
        hideHintPopup();
        currentSuggestions.clear();
    }

    /** 获取内部 VisTextField（用于自定义配置） */
    public VisTextField getTextField() {
        return textField;
    }

    /** 设置历史上限 */
    public void setMaxHistory(int max) {
        this.maxHistory = Math.max(1, max);
    }

    /** 获取输入历史列表（只读） */
    public List<String> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /** 请求焦点 */
    public void focus() {
        if (getStage() != null) {
            getStage().setKeyboardFocus(textField);
        }
    }

    /** 获取浮动提示弹窗 Actor（可用于自定义样式） */
    public VisTable getHintPopup() {
        return hintPopup;
    }

    // ══════════════ 内部逻辑 ══════════════

    /** 提交当前输入 */
    private void submitInput() {
        String text = textField.getText().trim();
        textField.setText("");
        textField.setGhostText("");
        hideHintPopup();
        currentSuggestions.clear();
        pendingInput = "";

        if (text.isEmpty()) return;

        // 记入历史
        history.add(text);
        if (history.size() > maxHistory) {
            history.remove(0);
        }
        historyIndex = history.size();

        // 触发回调
        if (onSubmit != null) {
            onSubmit.accept(text);
        }
    }

    /** 浏览历史命令 (direction: -1 上翻, +1 下翻) */
    private void navigateHistory(int direction) {
        if (history.isEmpty()) return;

        // 首次上翻时保存当前编辑内容
        if (historyIndex == history.size() && direction == -1) {
            pendingInput = textField.getText();
        }

        historyIndex += direction;

        if (historyIndex < 0) {
            historyIndex = 0;
        }

        if (historyIndex >= history.size()) {
            // 回到当前编辑
            historyIndex = history.size();
            textField.setText(pendingInput);
            textField.setCursorPosition(pendingInput.length());
        } else {
            String historyText = history.get(historyIndex);
            textField.setText(historyText);
            textField.setCursorPosition(historyText.length());
        }

        // 历史翻阅后更新提示
        updateCommandHint(textField.getText());
    }

    // ══════════════ HintTextField 内部类 ══════════════

    /**
     * 自定义 VisTextField — 在已输入文本之后绘制灰色幽灵提示文本（MC 风格自动补全）。
     * <p>
     * 例如用户输入 "/h"，ghostText 为 "/help" 时，
     * 输入框同时显示白色 "/h" 和灰色 "elp"。
     */
    private static class HintTextField extends VisTextField {
        private String ghostText = "";
        private final GlyphLayout ghostLayout = new GlyphLayout();
        private final Color ghostColor = new Color(0.6f, 0.6f, 0.6f, 0.5f);
        private final Color tempColor = new Color();

        HintTextField(String text) {
            super(text);
        }

        /** 设置幽灵提示文本（完整的建议文本，如 "/help"） */
        void setGhostText(String text) {
            this.ghostText = text != null ? text : "";
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            drawGhostHint(batch, parentAlpha);
        }

        /**
         * 绘制幽灵提示后缀 — 仅绘制用户尚未输入的部分。
         * <p>
         * 实现原理: 计算已输入文本的像素宽度，从该位置开始绘制灰色后缀。
         * 坐标与 TextField 内部文本渲染对齐。
         */
        private void drawGhostHint(Batch batch, float parentAlpha) {
            if (ghostText.isEmpty()) return;
            String typed = getText();
            if (typed == null || typed.isEmpty()) return;

            // ghostText 必须以已输入文本为前缀（大小写不敏感）
            if (!ghostText.toLowerCase().startsWith(typed.toLowerCase())) return;
            if (ghostText.length() <= typed.length()) return;

            String suffix = ghostText.substring(typed.length());
            BitmapFont font = getStyle().font;

            // 计算已输入文本的像素宽度
            ghostLayout.setText(font, typed);
            float typedWidth = ghostLayout.width;

            // 获取文本起始坐标（与 TextField 内部渲染对齐）
            Drawable bg = getStyle().background;
            float bgLeft = bg != null ? bg.getLeftWidth() : 0;
            float textX = getX() + bgLeft + typedWidth;
            float textY = getTextY(font, bg);

            // 以灰色半透明绘制后缀（避免每帧分配新 Color 对象）
            tempColor.set(font.getColor());
            ghostColor.a = 0.5f * parentAlpha;
            font.setColor(ghostColor);
            font.draw(batch, suffix, textX, textY);
            font.setColor(tempColor);
        }
    }
}
