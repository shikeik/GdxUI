package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

/**
 * 傻瓜式 Tab 面板封装
 * 自动处理布局、样式修正和内容切换
 */
public class SmartTabPane extends VisTable {

	private TabbedPane tabbedPane;
	private VisTable contentContainer;

	public SmartTabPane() {
		setBackground("window-bg"); // 整体背景

		tabbedPane = new TabbedPane();
		// 2. 布局
		// 顶部：Tab 按钮条 (给个深色背景区分)
		VisTable headerTable = new VisTable();
		headerTable.setBackground("button");
		headerTable.add(tabbedPane.getTable()).left().growX().minHeight(26);

		add(headerTable).growX().row();

		// 中间：内容容器
		contentContainer = new VisTable();
		add(contentContainer).grow();

		// 3. 事件监听
		tabbedPane.addListener(new TabbedPaneAdapter() {
			@Override
			public void switchedTab(Tab tab) {
				contentContainer.clearChildren();
				Table content = tab.getContentTable();
				if (content != null) {
					contentContainer.add(content).grow();
				}
			}
		});
	}

	/**
	 * 添加一个 Tab
	 * @param title 标题
	 * @param content 内容 (可以是任意 Actor，内部会自动包裹)
	 */
	public void addTab(String title, Actor content) {
		Table wrapper;
		// 如果本身就是 Table，直接用；否则包一层
		if (content instanceof Table table) {
			wrapper = table;
		} else {
			wrapper = new VisTable();
			wrapper.add(content).grow();
		}

		SimpleTab tab = new SimpleTab(title, wrapper);
		tabbedPane.add(tab);

		if (tabbedPane.getTabs().size == 1) {
			tabbedPane.switchTab(tab);
		}
	}

	public TabbedPane getTabbedPane() {
		return tabbedPane;
	}

	/**
	 * 内部 Tab 实现
	 */
	private static class SimpleTab extends Tab {
		private String title;
		private Table content;

		public SimpleTab(String title, Table content) {
			super(false, false);
			this.title = title;
			this.content = content;
		}

		@Override
		public String getTabTitle() {
			return title;
		}

		@Override
		public Table getContentTable() {
			return content;
		}
	}
}
