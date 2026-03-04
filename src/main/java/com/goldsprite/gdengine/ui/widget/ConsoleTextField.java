package com.goldsprite.gdengine.ui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextField;

/**
 * 通用控制台输入栏 — 封装 VisTextField + 历史翻阅 + Enter 提交 + 长按上下文菜单。
 * <p>
 * 内置功能（继承自 VisTextField）:
 * <ul>
 *   <li>光标左右移动 (← →)</li>
 *   <li>Home / End 跳到行首/行尾</li>
 *   <li>Ctrl+A 全选</li>
 *   <li>Ctrl+C 复制 / Ctrl+X 剪切 / Ctrl+V 粘贴</li>
 *   <li>文本选中（Shift+方向键 / 鼠标拖选）</li>
 * </ul>
 * <p>
 * 额外封装功能:
 * <ul>
 *   <li>↑↓ 历史命令翻阅</li>
 *   <li>Enter 触发提交回调</li>
 *   <li>自动附加 TextFieldPasteMenu（Android 长按上下文菜单）</li>
 * </ul>
 * <p>
 * 用法:
 * <pre>
 * ConsoleTextField cmd = new ConsoleTextField();
 * cmd.setMessageText("输入命令 ...");
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

    /** 内部输入框 */
    private final VisTextField textField;

    /** 输入历史 */
    private final List<String> history = new ArrayList<>();

    /** 当前历史索引（history.size() 表示当前输入，非历史） */
    private int historyIndex = 0;

    /** 提交回调 */
    private Consumer<String> onSubmit;

    /** 文本变化回调（每次输入内容改变时触发，参数为当前文本） */
    private Consumer<String> onTextChanged;

    /** Tab 键回调（参数为当前文本，返回值通过外部 setText 完成补全） */
    private Consumer<String> onTabPressed;

    /** 历史上限 */
    private int maxHistory = 50;

    /** 暂存当前正在编辑的文本（翻阅历史时保存） */
    private String pendingInput = "";

    // ══════════════ 构造 ══════════════

    public ConsoleTextField() {
        this("");
    }

    public ConsoleTextField(String initialText) {
        textField = new VisTextField(initialText);

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
                }
            }
        });

        // 注册键盘事件: Enter 提交 + ↑↓ 历史翻阅 + Tab 补全
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
                    if (onTabPressed != null) {
                        onTabPressed.accept(textField.getText());
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                // 在下一帧触发文本变化回调（此时 TextField 文本已更新）
                if (onTextChanged != null && character != '\r' && character != '\n' && character != '\t') {
                    Gdx.app.postRunnable(() -> {
                        if (onTextChanged != null) {
                            onTextChanged.accept(textField.getText());
                        }
                    });
                }
                return false;
            }
        });

        // 布局: 输入框填满整个 Table
        add(textField).growX();

        // 初始化历史索引
        historyIndex = 0;
    }

    // ══════════════ 公开 API ══════════════

    /** 设置提交回调（Enter 键触发） */
    public void setOnSubmit(Consumer<String> callback) {
        this.onSubmit = callback;
    }

    /** 设置文本变化回调（每次内容改变时触发，参数为当前文本） */
    public void setOnTextChanged(Consumer<String> callback) {
        this.onTextChanged = callback;
    }

    /** 设置 Tab 键回调（参数为当前文本，外部可调用 setText 完成补全） */
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

    /** 设置文本 */
    public void setText(String text) {
        textField.setText(text);
        textField.setCursorPosition(text != null ? text.length() : 0);
    }

    /** 清空输入 */
    public void clear() {
        textField.setText("");
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
        return java.util.Collections.unmodifiableList(history);
    }

    /** 请求焦点 */
    public void focus() {
        if (getStage() != null) {
            getStage().setKeyboardFocus(textField);
        }
    }

    // ══════════════ 内部逻辑 ══════════════

    /** 提交当前输入 */
    private void submitInput() {
        String text = textField.getText().trim();
        textField.setText("");
        pendingInput = "";

        if (text.isEmpty()) return;

        // 记入历史
        history.add(text);
        if (history.size() > maxHistory) {
            history.remove(0);
        }
        historyIndex = history.size(); // 指向末尾之后

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
            return;
        }

        String historyText = history.get(historyIndex);
        textField.setText(historyText);
        textField.setCursorPosition(historyText.length());
    }
}
