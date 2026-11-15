package chatapp.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;

/** Popup with a grid of emoji grouped into categories. */
public final class EmojiPicker {
    private EmojiPicker() { }

    private static final String[][] CATEGORIES = {
        {"Smileys", "😀 😁 😂 🤣 😊 😍 😘 😎 🤔 😴 😭 😡 🥳 😇 🤩 😅 😉 🙃 😬 🤯 😱 🥺 😢 😤 🙄 😏 😌 🤗 🤤 😋 🤪 🥰 😜 🤓 😐 😶 😳 🥶 😈 💀"},
        {"Gestures", "👍 👎 👏 🙌 🙏 💪 👌 🤞 🤙 👋 🤝 👀 👊 ✊ 🤘 🖖 👉 👈 👆 👇 🤟 ✋ 🤛 🤜 🧠 👂 👃 👅 👄 🤲 🙋 🤦 🤷 🙈 🙉 🙊 🎅 🧑 👶 👑"},
        {"Hearts", "💙 💜 💚 💛 🧡 🖤 💔 💕 💖 💗 💘 💝 💯 🔥 ⭐ 🌟 ✨ 💥 💫 🎉 🎊 🏆 🚀 💡 ⚡ ✅ ❌ ❓ ❗ 💬 💭 🔔 🎯 🎈 🎁 🥇 💎 🔒 🔑"},
        {"Things", "💻 📱 🎧 🎮 📚 📷 🎤 🎵 🍕 🍔 ☕ 🍺 🎂 🌙 🌈 🌹 🐱 🐶 🦊 🐼 🐸 🦄 🐢 🚗 ✈ 🏠 ⚽ 🏀 🎸 🎬 📝 📎 📌 🔧 🔋 🌍 🌊 🍀 🍎 🍉"},
    };

    public static void show(Component invoker, Consumer<String> onPick) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(Theme.P.border));
        popup.setBackground(Theme.P.surface);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(Theme.P.surface);
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel tabs = new JPanel(new GridLayout(1, CATEGORIES.length, 4, 0));
        tabs.setOpaque(false);
        JPanel grid = new JPanel(new GridLayout(0, 8, 2, 2));
        grid.setOpaque(false);
        JLabel[] tabLabels = new JLabel[CATEGORIES.length];

        for (int i = 0; i < CATEGORIES.length; i++) {
            int index = i;
            JLabel tab = new JLabel(CATEGORIES[i][0], SwingConstants.CENTER);
            tab.setFont(Theme.SMALL_BOLD);
            tab.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 4));
            tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tab.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    fill(grid, index, onPick);
                    for (int j = 0; j < tabLabels.length; j++) style(tabLabels[j], j == index);
                    popup.pack();
                }
            });
            tabLabels[i] = tab;
            tabs.add(tab);
        }
        fill(grid, 0, onPick);
        for (int j = 0; j < tabLabels.length; j++) style(tabLabels[j], j == 0);

        root.add(tabs, BorderLayout.NORTH);
        root.add(grid, BorderLayout.CENTER);
        popup.add(root);

        Dimension d = popup.getPreferredSize();
        popup.show(invoker, 0, -d.height - 8);
    }

    private static void style(JLabel tab, boolean selected) {
        tab.setForeground(selected ? Theme.P.accent : Theme.P.muted);
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? Theme.P.accent : Theme.P.surface),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
    }

    private static void fill(JPanel grid, int category, Consumer<String> onPick) {
        grid.removeAll();
        for (String token : CATEGORIES[category][1].split(" ")) {
            if (token.isEmpty()) continue;
            JButton b = new JButton(token);
            b.setFont(Theme.emojiFont(22f));
            b.setForeground(Theme.P.text);
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setOpaque(false);
            b.setMargin(new java.awt.Insets(2, 2, 2, 2));
            b.setPreferredSize(new Dimension(38, 36));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    b.setOpaque(true);
                    b.setBackground(Theme.P.surface2);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    b.setOpaque(false);
                    b.setBackground(new Color(0, 0, 0, 0));
                }
            });
            b.addActionListener(e -> onPick.accept(token));
            grid.add(b);
        }
        grid.revalidate();
        grid.repaint();
    }
}
