package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.kotcrab.vis.ui.widget.VisScrollPane;

/**
 * 鼠标悬浮即获取焦点的 ScrollPane
 * 解决标准 ScrollPane 需要点击才能响应滚轮的问题
 */
public class HoverFocusScrollPane extends VisScrollPane {

	public HoverFocusScrollPane(Actor widget) {
		super(widget);
		initListener();
	}

	public HoverFocusScrollPane(Actor widget, ScrollPaneStyle style) {
		super(widget, style);
		initListener();
	}

	public HoverFocusScrollPane(Actor widget, String styleName) {
		super(widget, styleName);
		initListener();
	}

	private void initListener() {
		addListener(new InputListener() {
			@Override
			public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
				if (pointer == -1) { // 鼠标移动，非触摸
					if (getStage() != null) {
						getStage().setScrollFocus(HoverFocusScrollPane.this);
					}
				}
			}

			@Override
			public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
				if (pointer == -1) {
					if (getStage() != null && getStage().getScrollFocus() == HoverFocusScrollPane.this) {
						getStage().setScrollFocus(null);
					}
				}
			}
		});
	}
}
