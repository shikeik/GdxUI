package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisTable;
import com.badlogic.gdx.scenes.scene2d.Action;

/**
 * 抽屉式面板组件
 * 支持上下左右四个方向的展开/收起
 */
public class DrawerPanel extends VisTable {

	public enum Direction {
		UP, DOWN, LEFT, RIGHT
	}

	public static class DrawerStyle {
		public Drawable background;
		public Drawable arrowUp;
		public Drawable arrowDown;
		public Drawable arrowLeft;
		public Drawable arrowRight;
		public float animDuration = 0.3f;
		public Interpolation animInterpolation = Interpolation.pow2Out;
		
		public DrawerStyle() {}
	}

	private Direction direction;
	private DrawerStyle style;
	private boolean expanded = false;
	private boolean animating = false;

	private VisTable contentTable;
	private VisImageButton toggleButton;
	private Cell<VisTable> contentCell;
	
	// Configured sizes
	private float contentWidth, contentHeight;

	public DrawerPanel(Direction direction, DrawerStyle style) {
		this.direction = direction;
		this.style = style;
		
		initUI();
		setExpanded(false, false); // Start collapsed
	}

	private void initUI() {
		this.contentTable = new VisTable();
		if (style.background != null) {
			this.contentTable.setBackground(style.background);
		}
		
		// Create Toggle Button
		this.toggleButton = new VisImageButton(getArrowDrawable(false));
		this.toggleButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				if (!animating) {
					toggle();
				}
			}
		});

		rebuildLayout();
	}
	
	private void rebuildLayout() {
		clearChildren();
		
		// Layout based on direction
		switch (direction) {
			case UP:
				contentCell = add(contentTable).grow();
				row();
				add(toggleButton).center();
				break;
			case DOWN:
				add(toggleButton).center();
				row();
				contentCell = add(contentTable).grow();
				break;
			case LEFT:
				contentCell = add(contentTable).grow();
				add(toggleButton).center();
				break;
			case RIGHT:
				add(toggleButton).center();
				contentCell = add(contentTable).grow();
				break;
		}
	}

	private Drawable getArrowDrawable(boolean isExpanded) {
		// Determine arrow direction based on panel direction and state
		// UP panel: Collapsed (Show content is UP) -> Arrow UP. Expanded (Hide content is DOWN) -> Arrow DOWN.
		// DOWN panel: Collapsed (Show content is DOWN) -> Arrow DOWN. Expanded (Hide content is UP) -> Arrow UP.
		// LEFT panel: Collapsed (Show content is LEFT) -> Arrow LEFT. Expanded (Hide content is RIGHT) -> Arrow RIGHT.
		// RIGHT panel: Collapsed (Show content is RIGHT) -> Arrow RIGHT. Expanded (Hide content is LEFT) -> Arrow LEFT.
		
		switch (direction) {
			case UP: return isExpanded ? style.arrowDown : style.arrowUp;
			case DOWN: return isExpanded ? style.arrowUp : style.arrowDown;
			case LEFT: return isExpanded ? style.arrowRight : style.arrowLeft;
			case RIGHT: return isExpanded ? style.arrowLeft : style.arrowRight;
		}
		return null;
	}

	public void setContent(Actor content, float width, float height) {
		contentTable.clearChildren();
		contentTable.add(content).size(width, height);
		this.contentWidth = width;
		this.contentHeight = height;
		
		// If expanded, update cell size immediately
		if (expanded) {
			contentCell.size(width, height);
			invalidateHierarchy();
		}
	}

	public void toggle() {
		setExpanded(!expanded, true);
	}

	public void setExpanded(boolean expanded, boolean animate) {
		if (this.expanded == expanded) return;
		this.expanded = expanded;
		
		// Update Button Icon
		VisImageButton.VisImageButtonStyle btnStyle = new VisImageButton.VisImageButtonStyle(toggleButton.getStyle());
		btnStyle.imageUp = getArrowDrawable(expanded);
		toggleButton.setStyle(btnStyle);

		if (animate) {
			animating = true;
			// Animation logic depends on how we want to "slide".
			// Since we are in a Table layout, animating cell size is one way.
			// Or we can use Actions on the contentTable if we weren't using strict Table layout.
			// But strict Table layout forces size.
			
			// For a Drawer, often we want it to slide out.
			// If we use Value.percent, we can animate it?
			// LibGDX Tables don't animate cell sizes easily with Actions.
			// We usually have to update it in act().
			
			// Alternative: Use a Value that we tween.
			// But simple implementation:
			// Just clear contentCell size (0) or set to full size.
			// But we want animation.
			
			// Let's use a custom Action to update cell size.
			float targetW = (direction == Direction.UP || direction == Direction.DOWN) ? contentWidth : (expanded ? contentWidth : 0);
			float targetH = (direction == Direction.LEFT || direction == Direction.RIGHT) ? contentHeight : (expanded ? contentHeight : 0);
			
			// If vertical drawer (UP/DOWN), width is constant (contentWidth), height animates (0 <-> contentHeight).
			// If horizontal drawer (LEFT/RIGHT), height is constant (contentHeight), width animates (0 <-> contentWidth).
			
			// Correction:
			// UP/DOWN: Width should be contentWidth. Height 0 -> contentHeight.
			// LEFT/RIGHT: Height should be contentHeight. Width 0 -> contentWidth.
			
			float startW = contentCell.getMinWidth();
			float startH = contentCell.getMinHeight();
			
			// Fix start values if they were unset or -1 (pref)
			if (startW < 0) startW = expanded ? 0 : contentWidth; // If expanding, start 0. If collapsing, start Max.
			// Wait, getMinWidth() might not be reliable if not set.
			// Let's use current size.
			startW = contentTable.getWidth();
			startH = contentTable.getHeight();
			
			// Actually, Table layout will size contentTable to 0 if cell size is 0.
			
			if (!expanded) {
				targetW = (direction == Direction.UP || direction == Direction.DOWN) ? contentWidth : 0;
				targetH = (direction == Direction.LEFT || direction == Direction.RIGHT) ? contentHeight : 0;
			} else {
				targetW = contentWidth;
				targetH = contentHeight;
			}

			// We need to animate the pref size of the cell?
			// Cell.size(w, h) sets min, pref, max.
			
			this.addAction(new Action() {
				float time = 0;
				float duration = style.animDuration;
				float startVal = expanded ? 0 : 1; // 0 to 1 if expanding
				float endVal = expanded ? 1 : 0;
				
				@Override
				public boolean act(float delta) {
					time += delta;
					float progress = Math.min(1, time / duration);
					float alpha = style.animInterpolation.apply(progress);
					float currentScale = startVal + (endVal - startVal) * alpha;
					
					float curW = (direction == Direction.UP || direction == Direction.DOWN) ? contentWidth : contentWidth * currentScale;
					float curH = (direction == Direction.LEFT || direction == Direction.RIGHT) ? contentHeight : contentHeight * currentScale;
					
					contentCell.size(curW, curH);
					contentTable.setVisible(currentScale > 0.01f);
					invalidateHierarchy();
					pack(); // Important for parent layout to update
					
					if (progress >= 1) {
						animating = false;
						return true;
					}
					return false;
				}
			});
			
		} else {
			// Instant
			float w = (direction == Direction.UP || direction == Direction.DOWN) ? contentWidth : (expanded ? contentWidth : 0);
			float h = (direction == Direction.LEFT || direction == Direction.RIGHT) ? contentHeight : (expanded ? contentHeight : 0);
			contentCell.size(w, h);
			contentTable.setVisible(expanded);
			invalidateHierarchy();
			pack();
		}
	}
}
