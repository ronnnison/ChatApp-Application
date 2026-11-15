package chatapp.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;

/**
 * Colours, fonts and component factories. The whole look of the app comes from the current
 * {@link Palette}; switching theme just stores another palette and rebuilds the windows.
 */
public final class Theme {
    private Theme() { }

    /** A complete colour scheme (immutable). */
    public static final class Palette {
        public final String name;
        public final boolean dark;
        public final Color bg, surface, surface2, border, text, muted, accent, accent2, onAccent;
        public final Color mine, mineText, other, otherText, chatBg, danger;

        Palette(String name, boolean dark, int bg, int surface, int surface2, int border, int text, int muted,
                int accent, int accent2, int onAccent, int mine, int mineText, int other, int otherText,
                int chatBg, int danger) {
            this.name = name;
            this.dark = dark;
            this.bg = new Color(bg);
            this.surface = new Color(surface);
            this.surface2 = new Color(surface2);
            this.border = new Color(border);
            this.text = new Color(text);
            this.muted = new Color(muted);
            this.accent = new Color(accent);
            this.accent2 = new Color(accent2);
            this.onAccent = new Color(onAccent);
            this.mine = new Color(mine);
            this.mineText = new Color(mineText);
            this.other = new Color(other);
            this.otherText = new Color(otherText);
            this.chatBg = new Color(chatBg);
            this.danger = new Color(danger);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final Palette CYBER = new Palette("Cyber", true,
            0x0B1020, 0x101831, 0x172244, 0x24335C, 0xE8EEFC, 0x8A9AC0,
            0x22D3EE, 0x8B5CF6, 0x04101C, 0x0E7490, 0xFFFFFF, 0x18233F, 0xE8EEFC, 0x0D1428, 0xF87171);
    public static final Palette LIGHT = new Palette("Light", false,
            0xF4F6FB, 0xFFFFFF, 0xEEF1F8, 0xD8DEEB, 0x0F172A, 0x64748B,
            0x2563EB, 0x7C3AED, 0xFFFFFF, 0x2563EB, 0xFFFFFF, 0xFFFFFF, 0x0F172A, 0xE9EEF7, 0xDC2626);
    public static final Palette AURORA = new Palette("Aurora", true,
            0x140B24, 0x1D1236, 0x2A1B4D, 0x42306F, 0xF3E8FF, 0xB19CD9,
            0xF472B6, 0xA78BFA, 0x1A0B2E, 0x9D174D, 0xFFFFFF, 0x2A1B4D, 0xF3E8FF, 0x180D2B, 0xFB7185);
    public static final Palette MATRIX = new Palette("Matrix", true,
            0x050A07, 0x0A120D, 0x0F1D15, 0x1B3A28, 0xD1FAE5, 0x6FA88A,
            0x34D399, 0x2DD4BF, 0x03140A, 0x14532D, 0xECFDF5, 0x0F1D15, 0xD1FAE5, 0x07110B, 0xF87171);

    public static final Palette[] ALL = {CYBER, LIGHT, AURORA, MATRIX};

    // ---------------------------------------------------------------- saved settings

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(Theme.class);
    }

    public static String pref(String key, String fallback) {
        try {
            return prefs().get(key, fallback);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static void setPref(String key, String value) {
        try {
            prefs().put(key, value);
        } catch (RuntimeException ignored) {
        }
    }

    /** The palette every new window is built from. */
    public static volatile Palette P = load();

    private static Palette load() {
        String saved = pref("theme", CYBER.name);
        for (Palette p : ALL) if (p.name.equals(saved)) return p;
        return CYBER;
    }

    public static void set(Palette p) {
        P = p;
        setPref("theme", p.name);
        applyDefaults();
    }

    // ---------------------------------------------------------------- fonts

    /** Logical "SansSerif" font: Java falls back to a system font for Bangla, Arabic, CJK and so on. */
    public static Font font(int style, float size) {
        return new Font(Font.SANS_SERIF, style, 12).deriveFont(style, size);
    }

    public static final Font BASE = font(Font.PLAIN, 14f);
    public static final Font BOLD = font(Font.BOLD, 14f);
    public static final Font SMALL = font(Font.PLAIN, 12f);
    public static final Font SMALL_BOLD = font(Font.BOLD, 12f);
    public static final Font HEADING = font(Font.BOLD, 26f);

    private static String emojiFamily;

    /** Looks for a colour-emoji font once; falls back to the normal font. */
    public static synchronized String emojiFamily() {
        if (emojiFamily == null) {
            Set<String> families = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            emojiFamily = Font.SANS_SERIF;
            for (String f : new String[]{"Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", "Twemoji Mozilla", "Symbola"}) {
                if (families.contains(f)) {
                    emojiFamily = f;
                    break;
                }
            }
        }
        return emojiFamily;
    }

    public static Font emojiFont(float size) {
        return new Font(emojiFamily(), Font.PLAIN, 12).deriveFont(size);
    }

    // ---------------------------------------------------------------- setup

    public static void install() {
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        try {
            // Metal looks the same on Windows, macOS and Linux, so every teammate sees the same app
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        applyDefaults();
    }

    private static void applyDefaults() {
        UIManager.put("ToolTip.background", P.surface2);
        UIManager.put("ToolTip.foreground", P.text);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(P.border));
        UIManager.put("ToolTip.font", SMALL);
        UIManager.put("PopupMenu.background", P.surface);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(P.border));
        UIManager.put("MenuItem.background", P.surface);
        UIManager.put("MenuItem.foreground", P.text);
        UIManager.put("MenuItem.selectionBackground", P.surface2);
        UIManager.put("MenuItem.selectionForeground", P.accent);
        UIManager.put("MenuItem.font", BASE);
        UIManager.put("OptionPane.background", P.surface);
        UIManager.put("Panel.background", P.surface);
        UIManager.put("FileChooser.readOnly", Boolean.FALSE);
    }

    // ---------------------------------------------------------------- factories

    private static Border fieldBorder(Color line) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(line, 1), BorderFactory.createEmptyBorder(8, 11, 8, 11));
    }

    static void styleField(JTextComponent f) {
        f.setFont(BASE);
        f.setOpaque(true);
        f.setBackground(P.surface2);
        f.setForeground(P.text);
        f.setCaretColor(P.accent);
        f.setSelectionColor(Ui.alpha(P.accent, 120));
        f.setSelectedTextColor(P.text);
        f.setBorder(fieldBorder(P.border));
        f.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                f.setBorder(fieldBorder(P.accent));
            }

            @Override
            public void focusLost(FocusEvent e) {
                f.setBorder(fieldBorder(P.border));
            }
        });
    }

    public static JTextField field() {
        JTextField f = new JTextField();
        styleField(f);
        return f;
    }

    public static Ui.HintField hintField(String hint) {
        Ui.HintField f = new Ui.HintField(hint);
        styleField(f);
        return f;
    }

    public static JPasswordField passwordField() {
        JPasswordField f = new JPasswordField();
        styleField(f);
        return f;
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(SMALL);
        l.setForeground(P.muted);
        return l;
    }

    public static JButton button(String text) {
        return new Ui.GradientButton(text, true);
    }

    public static JButton secondaryButton(String text) {
        return new Ui.GradientButton(text, false);
    }
}
