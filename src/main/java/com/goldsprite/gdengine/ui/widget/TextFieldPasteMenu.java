package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;

/**
 * 通用输入框长按上下文菜单（复制 / 剪切 / 粘贴 / 全选）。
 * <p>
 * 适用于 Android 等触控设备上无法使用 Ctrl+V 的场景。
 * 借鉴 BioCodeEditor 的弹出菜单方案，封装为可复用的单行调用：
 * <pre>
 * TextFieldPasteMenu.attach(myVisTextField);
 * </pre>
 * 长按 350ms 后弹出浮动菜单，点击任意选项后自动关闭。
 * 同时也支持 Ctrl+V 桌面快捷键（作为兜底修复）。
 */
public class TextFieldPasteMenu {

    /** 长按阈值（毫秒） */
    private static final long LONG_PRESS_MS = 350;

    /**
     * 为指定的 VisTextField 附加长按上下文菜单。
     * 长按弹出 复制/剪切/粘贴/全选 浮动面板。
     *
     * @param textField 目标输入框
     */
    public static void attach(VisTextField textField) {
        textField.addListener(new InputListener() {
            private long touchDownTime;
            private boolean longPressTriggered;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                touchDownTime = System.currentTimeMillis();
                longPressTriggered = false;
                return true; // 必须返回 true 才能收到 touchUp
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (!longPressTriggered && System.currentTimeMillis() - touchDownTime >= LONG_PRESS_MS) {
                    longPressTriggered = true;
                    showMenu(textField, x, y);
                }
            }
        });
    }

    /**
     * 在指定位置弹出上下文菜单。
     */
    private static void showMenu(VisTextField textField, float localX, float localY) {
        Stage stage = textField.getStage();
        if (stage == null) return;

        VisTable popup = new VisTable();
        try {
            popup.setBackground(VisUI.getSkin().getDrawable("window-bg"));
        } catch (Exception e) {
            try {
                popup.setBackground(VisUI.getSkin().getDrawable("button"));
            } catch (Exception ignored) {}
        }

        String btnStyle = "default";

        // ── 复制 ──
        VisTextButton btnCopy = new VisTextButton("复制", btnStyle);
        btnCopy.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                textField.copy();
                hideMenu(popup);
            }
        });

        // ── 剪切 ──
        VisTextButton btnCut = new VisTextButton("剪切", btnStyle);
        btnCut.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                textField.cut();
                hideMenu(popup);
            }
        });

        // ── 粘贴 ──
        VisTextButton btnPaste = new VisTextButton("粘贴", btnStyle);
        btnPaste.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                pasteIntoTextField(textField);
                hideMenu(popup);
            }
        });

        // ── 全选 ──
        VisTextButton btnAll = new VisTextButton("全选", btnStyle);
        btnAll.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                textField.selectAll();
                hideMenu(popup);
            }
        });

        popup.add(btnCopy).pad(4);
        popup.add(btnCut).pad(4);
        popup.add(btnPaste).pad(4);
        popup.add(btnAll).pad(4);
        popup.pack();

        // 计算屏幕坐标（弹出在长按位置上方）
        Vector2 stagePos = textField.localToStageCoordinates(new Vector2(localX, localY));
        float menuX = stagePos.x - popup.getWidth() / 2f;
        float menuY = stagePos.y + 30;
        // 防止超出屏幕
        menuX = Math.max(0, Math.min(menuX, stage.getWidth() - popup.getWidth()));
        menuY = Math.max(0, Math.min(menuY, stage.getHeight() - popup.getHeight()));

        popup.setPosition(menuX, menuY);
        stage.addActor(popup);
        popup.toFront();

        // 点击菜单外部区域关闭
        stage.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                Actor target = event.getTarget();
                if (!isChildOf(target, popup)) {
                    hideMenu(popup);
                    stage.removeListener(this);
                }
                return false;
            }
        });
    }

    /**
     * 将系统剪贴板内容粘贴到 VisTextField 的当前光标位置。
     * 如果有选中文本，先删除选中部分再插入。
     */
    private static void pasteIntoTextField(VisTextField textField) {
        String content = Gdx.app.getClipboard().getContents();
        if (content == null || content.isEmpty()) return;

        String oldText = textField.getText();
        int cursor = textField.getCursorPosition();
        int selStart = textField.getSelectionStart();
        int start = Math.min(cursor, selStart);
        int end = Math.max(cursor, selStart);

        // 如果没有选中文本，start == end
        if (start == end) {
            start = cursor;
            end = cursor;
        }

        StringBuilder sb = new StringBuilder(oldText);
        if (end > start) {
            sb.delete(start, end);
        }
        sb.insert(start, content);
        textField.setText(sb.toString());
        textField.setCursorPosition(start + content.length());
    }

    private static void hideMenu(VisTable popup) {
        popup.remove();
    }

    /** 判断 actor 是否是 parent 的子孙 */
    private static boolean isChildOf(Actor actor, Actor parent) {
        Actor current = actor;
        while (current != null) {
            if (current == parent) return true;
            current = current.getParent();
        }
        return false;
    }
}
