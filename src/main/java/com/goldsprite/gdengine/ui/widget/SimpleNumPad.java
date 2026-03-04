package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import java.util.function.Consumer;

public class SimpleNumPad extends Table {
	private VisTextField targetField;
	private String buffer = "";
	private boolean isFirstInput = true;
	private Consumer<String> onConfirm;
	private final float btnW = 40, btnH = 25;

	private final InputListener stageListener = new InputListener() {
		@Override
		public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
			Actor target = event.getTarget();
			if (target != SimpleNumPad.this && !SimpleNumPad.this.isAscendantOf(target) && target != targetField) {
				setVisible(false);
			}
			return false;
		}
	};

	public SimpleNumPad() {
		super(VisUI.getSkin());
		setBackground("window-bg"); // 确保你的 Skin 里有这个 Drawable，没有就用 button
		pad(5);

		int[] nums = {7, 8, 9, 4, 5, 6, 1, 2, 3};
		for (int i = 0; i < nums.length; i++) {
			final int num = nums[i];
			addButton(String.valueOf(num), () -> append(String.valueOf(num)));
			if ((i + 1) % 3 == 0) row();
		}
		addButton(".", () -> append("."));
		addButton("0", () -> append("0"));
		addButton("C", this::clearBuffer);
		row();

		addButton("-", this::toggleNegative);
		VisTextButton okBtn = new VisTextButton("OK");
		okBtn.addListener(new ClickListener() {
				public void clicked(InputEvent event, float x, float y) { confirm(); }
			});
		add(okBtn).colspan(2).fillX().height(btnH).pad(1);
		pack();
		setVisible(false);
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (getStage() == null) return;
		if (visible) {
			getStage().addCaptureListener(stageListener);
			toFront();
		} else {
			getStage().removeListener(stageListener);
		}
	}

	private void addButton(String text, Runnable action) {
		VisTextButton btn = new VisTextButton(text);
		btn.addListener(new ClickListener() {
				public void clicked(InputEvent event, float x, float y) { action.run(); }
			});
		add(btn).size(btnW, btnH).pad(1);
	}

	public void show(VisTextField target, Consumer<String> onConfirmCallback) {
		this.targetField = target;
		this.onConfirm = onConfirmCallback;
		this.buffer = target.getText();
		this.isFirstInput = true;

		Vector2 pos = target.localToStageCoordinates(new Vector2(0, 0));
		float x = pos.x + target.getWidth() / 2 - getWidth() / 2;
		float y = pos.y - getHeight();

		// 简单的边界检查
		if (y < 0) y = pos.y + target.getHeight();

		setPosition(x, y);
		setVisible(true);
	}

	private void append(String str) {
		if (isFirstInput) { buffer = ""; isFirstInput = false; }
		buffer += str;
		if (targetField != null) targetField.setText(buffer);
	}

	private void clearBuffer() {
		buffer = "";
		if (targetField != null) targetField.setText("");
		isFirstInput = false;
	}

	private void toggleNegative() {
		if (isFirstInput) { buffer = ""; isFirstInput = false; }
		if (buffer.startsWith("-")) buffer = buffer.substring(1);
		else buffer = "-" + buffer;
		if (targetField != null) targetField.setText(buffer);
	}

	private void confirm() {
		if (buffer.isEmpty()) buffer = "0";
		if (onConfirm != null) onConfirm.accept(buffer);
		setVisible(false);
	}
}
