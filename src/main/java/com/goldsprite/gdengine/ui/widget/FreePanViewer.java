package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.Cullable;

/**
 * 自由拖拽查看器 v2.1 (最终修复版)
 * 修复：
 * 1. 补全 stopFling 方法
 * 2. 确保缩放逻辑生效 (Ctrl+滚轮)
 * 3. 优化惯性和边界限制
 */
public class FreePanViewer extends WidgetGroup implements Cullable {
	private Actor content;
	private final Rectangle cullingArea = new Rectangle();

	// --- 缩放配置 ---
	private float zoomLevel = 1.0f; // 当前缩放倍率 (相对于原始大小)
	private float minZoom = 0.2f;
	private float maxZoom = 4.0f;   // 稍微调大一点上限
	private float baseScaleX = 1f;  // 记录内容进入时的原始缩放值 (保留你的"很小的字体")
	private float baseScaleY = 1f;

	// --- 物理/惯性 ---
	private final Vector2 velocity = new Vector2();
	private final float friction = 0.92f; // 摩擦力 (0~1)，0.92 手感比较顺滑
	private boolean isFlinging = false;

	public FreePanViewer(Actor content) {
		this.content = content;
		addActor(content);

		// 1. 记录原始缩放
		// 这样如果你的 Label 预先设置了 fontScale=0.5，这里 getScale 可能是 1.0 (Actor scale)
		// 最终渲染大小 = fontScale * actorScale，所以逻辑是成立的
		this.baseScaleX = content.getScaleX();
		this.baseScaleY = content.getScaleY();

		// 2. 滚轮缩放监听 (Ctrl + Scroll)
		addListener(new InputListener() {
			@Override
			public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
				// 只有按住 Ctrl 才缩放，否则不处理 (让外层 ScrollPane 处理或者忽略)
				if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
					// amountY: 1缩小, -1放大
					zoomBy(-amountY * 0.1f, x, y);
					return true; // 【关键】拦截事件，防止冒泡导致界面乱滚
				}
				return false;
			}

			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				if (button == Input.Buttons.LEFT) {
					stopFling(); // 按下时停止惯性，防止瞬移
					// 请求焦点，确保能接收键盘事件(虽然Gdx.input直接查键盘不需要焦点，但为了规范)
					getStage().setScrollFocus(FreePanViewer.this);
					return true;
				}
				return false;
			}
		});

		// 3. 手势监听 (拖拽 + 惯性)
		addListener(new ActorGestureListener() {
			@Override
			public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
				moveContent(deltaX, deltaY);
			}

			@Override
			public void fling(InputEvent event, float velocityX, float velocityY, int button) {
				// 触发惯性
				velocity.set(velocityX, velocityY);
				isFlinging = true;
			}
		});
	}

	@Override
	public void act(float delta) {
		super.act(delta);

		// 惯性物理模拟
		if (isFlinging) {
			velocity.scl(friction); // 减速

			// 应用速度
			moveContent(velocity.x * delta, velocity.y * delta);

			// 速度极小时停止
			if (velocity.len() < 10f) {
				stopFling();
			}
		}

		// 每一帧都约束边界 (防止惯性飞出界)
		clampPosition();
	}

	// [修复] 补全缺失的方法
	private void stopFling() {
		isFlinging = false;
		velocity.setZero();
	}

	private void moveContent(float dx, float dy) {
		content.moveBy(dx, dy);
		clampPosition();
	}

	private void zoomBy(float amount, float pivotX, float pivotY) {
		float oldZoom = zoomLevel;
		zoomLevel += amount;
		zoomLevel = MathUtils.clamp(zoomLevel, minZoom, maxZoom);

		if (oldZoom != zoomLevel) {
			// 应用缩放：原始缩放 * 当前倍率
			// 这会叠加在 Label 自身的 FontScale 上
			content.setScale(baseScaleX * zoomLevel, baseScaleY * zoomLevel);

			// 简单处理：缩放后重新限制位置，防止内容跑偏
			clampPosition();
		}
	}

	/**
	 * 核心边界限制逻辑
	 */
	private void clampPosition() {
		float viewW = getWidth();
		float viewH = getHeight();

		// 获取内容实际占用的视觉大小
		float contentW = content.getWidth() * content.getScaleX();
		float contentH = content.getHeight() * content.getScaleY();

		float x = content.getX();
		float y = content.getY();

		// --- X轴限制 ---
		if (contentW < viewW) {
			x = 0; // 内容比视口窄，左对齐
		} else {
			// 内容比视口宽
			// 右边界：x 不能小于 viewW - contentW
			// 左边界：x 不能大于 0
			if (x > 0) x = 0;
			if (x + contentW < viewW) x = viewW - contentW;
		}

		// --- Y轴限制 ---
		if (contentH < viewH) {
			// 内容比视口矮 -> 顶对齐
			// 原理：y=0是底部，顶部是y+h。希望 y+h = viewH
			y = viewH - contentH;
		} else {
			// 内容比视口高
			// 顶部限制：y 不能小于 viewH - contentH (即底部不能提太高)
			// 底部限制：y 不能大于 0 (即顶部不能掉下来)

			// 修正逻辑：
			// 允许往上拖(看下面)，直到顶部贴顶 (y + h = viewH) -> y = viewH - h
			// 允许往下拖(看上面)，直到顶部贴底? 不，是 y=0 (底部贴底)

			// 正确的滚动逻辑 (Top-Down 习惯)：
			// y 最大值: 0 (此时 content 底部贴着 view 底部，上面漏空) -- 不对
			// y 最大值: 应该是让 content 顶部与 view 顶部对齐 -> y = viewH - contentH
			// 等等，Scene2D 是左下角为 0。
			// Content 初始位置通常是 TopAlign。

			// 让我们简化逻辑：
			// 无论如何，内容区域 (y, y+h) 必须尽可能覆盖视口 (0, viewH)

			// 限制 1: 顶部不能掉到视口下方 (y + contentH < viewH 不允许，除非 contentH < viewH)
			// 限制 2: 底部不能飞到视口上方 (y > 0 不允许)

			if (y > 0) y = 0;
			if (y + contentH < viewH) y = viewH - contentH;
		}

		content.setPosition(x, y);
	}

	@Override
	public void layout() {
		// 初始化布局：左上角对齐
		// 只有当从未布局过时才重置
		if (content.getY() == 0 && content.getX() == 0) {
			float contentH = content.getHeight() * content.getScaleY();
			content.setPosition(0, getHeight() - contentH);
		}
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		// 开启裁剪 (Scissor)，防止内容画出框外
		if (clipBegin()) {
			super.draw(batch, parentAlpha);
			batch.flush();
			clipEnd();
		}
	}

	@Override
	public void setCullingArea(Rectangle cullingArea) {
		this.cullingArea.set(cullingArea);
	}
}
