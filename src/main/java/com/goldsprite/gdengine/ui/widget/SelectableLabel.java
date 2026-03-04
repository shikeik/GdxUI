package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;

/**
 * 可选中文本标签 — 支持长按弹出上下文菜单（复制全部 / 全选）。
 * <p>
 * MVP 版本: 长按 350ms 弹出 "复制全部" 菜单，将整段文本写入系统剪贴板。
 * 后续迭代: 增加拖动精确选区功能。
 * <p>
 * 用法:
 * <pre>
 * SelectableLabel label = new SelectableLabel("日志文本...", "small");
 * label.setWrap(true);
 * scrollPane = new HoverFocusScrollPane(label);
 * </pre>
 * 同时支持 PC 端 Ctrl+A 全选 + Ctrl+C 复制。
 */
public class SelectableLabel extends VisLabel {

    /** 长按阈值（毫秒） */
    private static final long LONG_PRESS_MS = 350;

    /** 选区高亮颜色 */
    private static final Color SELECTION_COLOR = new Color(0.2f, 0.5f, 1.0f, 0.3f);

    /** 是否处于全选状态 */
    private boolean allSelected = false;

    /** 当前弹出的上下文菜单（用于关闭判断） */
    private VisTable currentPopup = null;

    /** GlyphLayout 缓存，用于计算文本尺寸 */
    private final GlyphLayout glyphLayoutCache = new GlyphLayout();

    // ══════════════ 构造方法 ══════════════

    public SelectableLabel(CharSequence text) {
        super(text);
        init();
    }

    public SelectableLabel(CharSequence text, String styleName) {
        super(text, styleName);
        init();
    }

    public SelectableLabel(CharSequence text, Color color) {
        super(text, color);
        init();
    }

    private void init() {
        // 注册触摸事件（长按弹出菜单）
        addListener(new InputListener() {
            private long touchDownTime;
            private boolean longPressTriggered;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                touchDownTime = System.currentTimeMillis();
                longPressTriggered = false;
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (!longPressTriggered && System.currentTimeMillis() - touchDownTime >= LONG_PRESS_MS) {
                    longPressTriggered = true;
                    selectAll();
                    showContextMenu(x, y);
                }
            }
        });

        // 注册键盘事件（Ctrl+A / Ctrl+C）
        addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);

                if (ctrl && keycode == Input.Keys.A) {
                    selectAll();
                    return true;
                }
                if (ctrl && keycode == Input.Keys.C) {
                    copyToClipboard();
                    return true;
                }
                return false;
            }
        });
    }

    // ══════════════ 选中操作 ══════════════

    /** 全选文本 */
    public void selectAll() {
        allSelected = true;
    }

    /** 取消选中 */
    public void clearSelection() {
        allSelected = false;
    }

    /** 复制文本到系统剪贴板 */
    public void copyToClipboard() {
        CharSequence text = getText();
        if (text != null && text.length() > 0) {
            Gdx.app.getClipboard().setContents(text.toString());
        }
    }

    // ══════════════ 上下文菜单 ══════════════

    /** 在指定位置弹出上下文菜单 */
    private void showContextMenu(float localX, float localY) {
        Stage stage = getStage();
        if (stage == null) return;

        // 关闭已有菜单
        hideContextMenu();

        VisTable popup = new VisTable();
        try {
            popup.setBackground(VisUI.getSkin().getDrawable("window-bg"));
        } catch (Exception e) {
            try {
                popup.setBackground(VisUI.getSkin().getDrawable("button"));
            } catch (Exception ignored) {}
        }

        String btnStyle = "default";

        // ── 复制全部 ──
        VisTextButton btnCopy = new VisTextButton("复制全部", btnStyle);
        btnCopy.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                copyToClipboard();
                hideContextMenu();
            }
        });

        // ── 全选 ──
        VisTextButton btnAll = new VisTextButton("全选", btnStyle);
        btnAll.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectAll();
                hideContextMenu();
            }
        });

        popup.add(btnCopy).pad(4);
        popup.add(btnAll).pad(4);
        popup.pack();

        // 计算屏幕坐标（弹出在长按位置上方）
        Vector2 stagePos = localToStageCoordinates(new Vector2(localX, localY));
        float menuX = stagePos.x - popup.getWidth() / 2f;
        float menuY = stagePos.y + 30;
        // 防止超出屏幕
        menuX = Math.max(0, Math.min(menuX, stage.getWidth() - popup.getWidth()));
        menuY = Math.max(0, Math.min(menuY, stage.getHeight() - popup.getHeight()));

        popup.setPosition(menuX, menuY);
        stage.addActor(popup);
        popup.toFront();
        currentPopup = popup;

        // 点击菜单外部区域关闭
        stage.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                Actor target = event.getTarget();
                if (!isChildOf(target, popup)) {
                    hideContextMenu();
                    clearSelection();
                    stage.removeListener(this);
                }
                return false;
            }
        });
    }

    /** 关闭上下文菜单 */
    private void hideContextMenu() {
        if (currentPopup != null) {
            currentPopup.remove();
            currentPopup = null;
        }
    }

    // ══════════════ 绘制 ══════════════

    @Override
    public void draw(Batch batch, float parentAlpha) {
        // 如果全选，先绘制选区高亮背景
        if (allSelected) {
            drawSelectionHighlight(batch, parentAlpha);
        }
        super.draw(batch, parentAlpha);
    }

    /** 绘制全选高亮背景 */
    private void drawSelectionHighlight(Batch batch, float parentAlpha) {
        // 保存当前批次颜色
        Color oldColor = batch.getColor().cpy();

        batch.setColor(SELECTION_COLOR.r, SELECTION_COLOR.g, SELECTION_COLOR.b,
            SELECTION_COLOR.a * parentAlpha);

        // 使用 VisUI 的纯白纹理做高亮
        Drawable white = null;
        try {
            white = VisUI.getSkin().getDrawable("white");
        } catch (Exception e) {
            // 没有 white drawable，使用简单绘制
        }
        if (white != null) {
            white.draw(batch, getX(), getY(), getWidth(), getHeight());
        }

        // 恢复颜色
        batch.setColor(oldColor);
    }

    // ══════════════ 工具方法 ══════════════

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
