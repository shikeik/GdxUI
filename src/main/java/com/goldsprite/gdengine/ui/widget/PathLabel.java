package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.kotcrab.vis.ui.widget.VisLabel;

/**
 * 专门用于显示路径的 Label
 * 特性：当空间不足时，在左侧显示省略号 (...filename.java)，保留重要的文件名部分
 */
public class PathLabel extends VisLabel {
	private String fullPathText = "";
	private final GlyphLayout tempLayout = new GlyphLayout();

	public PathLabel(String text) {
		super(text);
		this.fullPathText = text;
		setEllipsis(false); // 关闭默认的尾部省略号，我们要自己控制
	}

	@Override
	public void setText(CharSequence newText) {
		this.fullPathText = newText.toString();
		super.setText(newText); // 先设置进去，触发一次布局更新
		invalidateHierarchy();
	}

	@Override
	public void layout() {
		super.layout();

		float availableWidth = getWidth();
		if (availableWidth <= 0 || fullPathText == null || fullPathText.isEmpty()) return;

		// 1. 计算完整宽度
		tempLayout.setText(getStyle().font, fullPathText);

		// 2. 如果能放下，直接显示
		if (tempLayout.width <= availableWidth) {
			super.setText(fullPathText);
			return;
		}

		// 3. 放不下，开始从左边裁剪
		String dots = "...";
		float dotsWidth = new GlyphLayout(getStyle().font, dots).width;
		float targetWidth = availableWidth - dotsWidth;

		if (targetWidth <= 0) {
			super.setText(dots);
			return;
		}

		// 简单的线性扫描：从第1个字符开始尝试截断，直到剩余部分能塞进 targetWidth
		// (为了性能，也可以改成二分查找，但对于路径长度来说，线性扫描足够快)
		for (int i = 1; i < fullPathText.length(); i++) {
			String sub = fullPathText.substring(i);
			tempLayout.setText(getStyle().font, sub);

			if (tempLayout.width <= targetWidth) {
				// 找到了能放下的最长后缀
				super.setText(dots + sub);
				return;
			}
		}

		// 实在太窄了，只显示点
		super.setText(dots);
	}
}
