/**
 * @(#) src/games/stendhal/client/gui/wt/BuddyListPanel.java
 *
 * $Id$
 */

package games.stendhal.client.gui.wt;

//
//

import games.stendhal.client.StendhalClient;
import games.stendhal.client.StendhalUI;
import games.stendhal.client.gui.styled.WoodStyle;
import games.stendhal.client.gui.styled.swing.StyledJPopupMenu;
import games.stendhal.client.sprite.Sprite;
import games.stendhal.client.sprite.SpriteStore;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPanel;

import marauroa.common.game.RPAction;
import marauroa.common.game.RPObject;
import marauroa.common.game.RPSlot;

/**
 * A panel representing a buddy list.
 */
public class BuddyListPanel extends JPanel {
	private static final long serialVersionUID = -1607102841664745919L;

	/**
	 * The UI.
	 */
	protected StendhalUI ui;

	/**
	 * The online icon image.
	 */
	private final Sprite online;

	/**
	 * The offline icon image.
	 */
	private final Sprite offline;

	/**
	 * A list of buddies.
	 */
	private final List<Entry> buddies;

	/**
	 * Create a buddy list panel.
	 * @param ui the user Interface
	 */
	public BuddyListPanel(final StendhalUI ui) {
		this.ui = ui;

		setOpaque(false);

		final SpriteStore st = SpriteStore.get();
		online = st.getSprite("data/gui/buddy_online.png");
		offline = st.getSprite("data/gui/buddy_offline.png");

		buddies = new LinkedList<Entry>();

		setPreferredSize(new Dimension(132, 1));
		addMouseListener(new MouseClickCB());
	}

	/**
	 * Rebuild the buddy list. Note: This needs to be called when updates are
	 * [possibly] needed.
	 */
	public void updateList() {
		final RPObject object = StendhalClient.get().getPlayer();

		if (object != null) {
			final RPSlot slot = object.getSlot("!buddy");
			updateList(slot.getFirst());
		}
	}

	/**
	 * Rebuild the buddy list from a list object.
	 *
	 * @param buddy
	 *            The buddy list object.
	 */
	protected void updateList(final RPObject buddy) {
		buddies.clear();

		for (final String key : buddy) {
			if (!"id".equals(key)) {
				buddies.add(new Entry(key.substring(1), buddy.getInt(key) != 0));
			}
		}

		final int height = buddies.size() * 20 + 3;

		if (height != getHeight()) {
			setPreferredSize(new Dimension(132, height));

			/*
			 * Tell the parent to re-pack() itself
			 *
			 * TODO Maybe there's a better way (without introducing
			 * dependencies/code-coupling) XXX
			 */
			putClientProperty("size-change", Integer.valueOf(height));
		}
	}

	/**
	 * Handle a popup click.
	 *
	 * @param comp
	 *            The component clicked on.
	 * @param x
	 *            The X coordinate of the mouse click.
	 * @param y
	 *            The X coordinate of the mouse click.
	 */
	protected void doPopup(final Component comp, final int x, final int y) {
		JMenuItem mi;

		final int i = y / 20;

		if ((i < 0) || (i >= buddies.size())) {
			return;
		}

		final Entry entry = buddies.get(i);

		final StyledJPopupMenu menu = new StyledJPopupMenu(WoodStyle.getInstance(),
				entry.getName());

		final ActionListener listener = new ActionSelectedCB(entry.getName());

		if (entry.isOnline()) {
			mi = new JMenuItem("Talk");
			mi.setActionCommand("talk");
			mi.addActionListener(listener);
			menu.add(mi);

			mi = new JMenuItem("Where");
			mi.setActionCommand("where");
			mi.addActionListener(listener);
			menu.add(mi);
		} else {
			mi = new JMenuItem("Leave Message");
			mi.setActionCommand("leave-message");
			mi.addActionListener(listener);
			menu.add(mi);
		}

		mi = new JMenuItem("Remove");
		mi.setActionCommand("remove");
		mi.addActionListener(listener);
		menu.add(mi);

		menu.show(comp, x, y);
	}

	/**
	 * Handle a chosen popup item.
	 *
	 * @param command
	 *            The command mnemonic selected.
	 * @param buddieName
	 *            The buddy name to act on.
	 */
	protected void doAction(final String command, final String buddieName) {
		final StendhalClient client = ui.getClient();

		if ("talk".equals(command)) {
			
			String tempBuddieName = addApostrophes(buddieName);

			ui.setChatLine("/tell " + tempBuddieName + " ");
		} else if ("leave-message".equals(command)) {
			String tempBuddieName = addApostrophes(buddieName);

			ui.setChatLine("/msg postman tell " + tempBuddieName + " ");
		} else if ("where".equals(command)) {
			final RPAction where = new RPAction();
			where.put("type", "where");
			where.put("target", buddieName);
			client.send(where);
		} else if ("remove".equals(command)) {
			final RPAction where = new RPAction();
			where.put("type", "removebuddy");
			where.put("target", buddieName);
			client.send(where);
		}
	}

	/**
	 * Surrounds string with apostrophes if the string contains spaces.
	 * Compatibility to grandfathered accounts with spaces. New accounts
	 * cannot contain spaces.
	 * @param buddieName the string to check 
	 * @return the string with or without apostrophes.
	 */
	private String addApostrophes(final String buddieName) {
		if (buddieName.indexOf(' ') > -1) {
			return "'" + buddieName + "'";
		}
		return buddieName;
	}

	//
	// JComponent
	//

	/**
	 * Render the buddy list. Eventually this will be replaced by a JList that
	 * can be scrolled (for popular players with many friends).
	 *
	 * @param g
	 *            The graphics context.
	 */
	@Override
	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);

		int y = 0;

		for (final Entry entry : buddies) {
			if (entry.isOnline()) {
				g.setColor(Color.GREEN);
				online.draw(g, 3, 2 + y);
			} else {
				g.setColor(Color.RED);
				offline.draw(g, 3, 2 + y);
			}

			g.drawString(entry.getName(), 24, 16 + y);

			y += 20;
		}
	}

	//
	//

	/**
	 * A buddy entry.
	 */
	protected static class Entry {

		/**
		 * The buddy name.
		 */
		protected String name;

		/**
		 * Whether the buddy is online.
		 */
		protected boolean online;

		/**
		 * Create a buddy entry.
		 *
		 * @param name
		 *            The buddy name.
		 * @param online
		 *            Whether the buddy is online.
		 */
		public Entry(final String name, final boolean online) {
			this.name = name;
			this.online = online;
		}

		//
		// Entry
		//

		/**
		 * Get the buddy name.
		 *
		 * @return The buddy name.
		 */
		public String getName() {
			return name;
		}

		/**
		 * Determine is the buddy is online.
		 *
		 * @return <code>true</code> if online.
		 */
		public boolean isOnline() {
			return online;
		}
	}

	/**
	 * Handle action selection.
	 */
	protected class ActionSelectedCB implements ActionListener {

		/**
		 * The buddy to act on.
		 */
		protected String buddy;

		/**
		 * Create a listener for action items.
		 *
		 * @param buddy
		 *            The buddy to act on.
		 */
		public ActionSelectedCB(final String buddy) {
			this.buddy = buddy;
		}

		//
		// ActionListener
		//

		public void actionPerformed(final ActionEvent ev) {
			doAction(ev.getActionCommand(), buddy);
		}
	}

	/**
	 * Handle mouse clicks.
	 */
	protected class MouseClickCB extends MouseAdapter {

		//
		// MouseListener
		//

		/**
		 * Track mouse presses.
		 *  @param ev the mouse event
		 */
		@Override
		public void mousePressed(final MouseEvent ev) {
			if (ev.isPopupTrigger()) {
				doPopup(ev.getComponent(), ev.getX(), ev.getY());
			}
		}

		/**
		 * Track mouse releases.
		 *  @param ev the mouse event
		 */
		@Override
		public void mouseReleased(final MouseEvent ev) {
			if (ev.isPopupTrigger()) {
				doPopup(ev.getComponent(), ev.getX(), ev.getY());
			}
		}
	}
}
