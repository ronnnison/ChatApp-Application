package chatapp.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Builds a read-only text pane where emoji are drawn with an emoji font and everything else with
 * the normal font. (A single Java font rarely contains both, so each run gets its own font.)
 */
public final class EmojiText {
    private EmojiText() { }

    public static boolean isEmoji(int cp) {
        return (cp >= 0x1F300 && cp <= 0x1FAFF) || (cp >= 0x1F000 && cp <= 0x1F2FF)
                || (cp >= 0x2600 && cp <= 0x27BF) || cp == 0x2B50 || cp == 0x2B55 || cp == 0x200D || cp == 0xFE0F
                || (cp >= 0x1F1E6 && cp <= 0x1F1FF) || cp == 0x2764;
    }

    public static boolean hasEmoji(String s) {
        return s.codePoints().anyMatch(EmojiText::isEmoji);
    }

    private static SimpleAttributeSet attrs(String family, int size, Color color) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, family);
        StyleConstants.setFontSize(a, size);
        StyleConstants.setForeground(a, color);
        return a;
    }

    /** Wrapped, transparent text sized to fit the text (but never wider than {@code maxWidth}). */
    public static JTextPane pane(String text, Font base, Color fg, int maxWidth) {
        JTextPane p = new JTextPane();
        p.setEditable(false);
        p.setOpaque(false);
        p.setBorder(null);
        p.setFont(base);
        p.setForeground(fg);
        p.setBackground(new Color(0, 0, 0, 0));
        p.setSelectionColor(Ui.alpha(Theme.P.accent, 120));
        p.setSelectedTextColor(fg);

        StyledDocument doc = p.getStyledDocument();
        SimpleAttributeSet plain = attrs(base.getFamily(), base.getSize(), fg);
        SimpleAttributeSet emoji = attrs(Theme.emojiFamily(), Math.round(base.getSize() * 1.15f), fg);
        try {
            if (!hasEmoji(text)) {
                doc.insertString(0, text, plain);
            } else {
                StringBuilder run = new StringBuilder();
                boolean runIsEmoji = false;
                int[] cps = text.codePoints().toArray();
                for (int cp : cps) {
                    boolean e = isEmoji(cp);
                    if (run.length() > 0 && e != runIsEmoji && !(cp == 0x200D || cp == 0xFE0F)) {
                        doc.insertString(doc.getLength(), run.toString(), runIsEmoji ? emoji : plain);
                        run.setLength(0);
                    }
                    if (run.length() == 0) runIsEmoji = e;
                    run.appendCodePoint(cp);
                }
                if (run.length() > 0) doc.insertString(doc.getLength(), run.toString(), runIsEmoji ? emoji : plain);
            }
        } catch (BadLocationException ignored) {
        }
        p.setPreferredSize(measure(p, maxWidth));
        return p;
    }

    /** Works out the width/height the text needs, wrapping lines at {@code maxWidth}. */
    private static Dimension measure(JTextPane p, int maxWidth) {
        p.setSize(Short.MAX_VALUE, Short.MAX_VALUE);          // wide: gives the one-line width
        int natural = p.getPreferredSize().width;
        int w = Math.min(maxWidth, Math.max(natural + 2, 24));
        p.setSize(w, Short.MAX_VALUE);                        // now wrap at that width
        int h = p.getPreferredSize().height;
        return new Dimension(w, h);
    }
}
