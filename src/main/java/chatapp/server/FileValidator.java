package chatapp.server;

import chatapp.common.ChatMessageDTO;
import java.util.Map;

/** Server-side file checks: whitelist of extensions + "magic bytes" so a renamed .exe is rejected. */
final class FileValidator {
    private FileValidator() { }

    private static final Map<String, ChatMessageDTO.Kind> ALLOWED = Map.of(
            "png", ChatMessageDTO.Kind.IMAGE,
            "jpg", ChatMessageDTO.Kind.IMAGE,
            "jpeg", ChatMessageDTO.Kind.IMAGE,
            "gif", ChatMessageDTO.Kind.IMAGE,
            "bmp", ChatMessageDTO.Kind.IMAGE,
            "docx", ChatMessageDTO.Kind.DOCUMENT,
            "wav", ChatMessageDTO.Kind.VOICE); // voice notes are recorded as WAV by the client

    /** Removes path parts and risky characters from a client-supplied file name. */
    static String sanitizeName(String name) {
        if (name == null) return "file";
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (n.length() > 100) n = n.substring(n.length() - 100);
        return n.isEmpty() ? "file" : n;
    }

    static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    /** @return the file kind, or null when the extension or content is not acceptable. */
    static ChatMessageDTO.Kind detectKind(String fileName, byte[] data) {
        String ext = extension(fileName);
        ChatMessageDTO.Kind kind = ALLOWED.get(ext);
        if (kind == null || data.length < 12) return null;
        boolean ok;
        switch (ext) {
            case "png":  ok = startsWith(data, 0x89, 'P', 'N', 'G'); break;
            case "jpg":
            case "jpeg": ok = startsWith(data, 0xFF, 0xD8, 0xFF); break;
            case "gif":  ok = startsWith(data, 'G', 'I', 'F', '8'); break;
            case "bmp":  ok = startsWith(data, 'B', 'M'); break;
            case "docx": ok = startsWith(data, 'P', 'K', 0x03, 0x04); break; // .docx is a ZIP container
            case "wav":  ok = startsWith(data, 'R', 'I', 'F', 'F') && data[8] == 'W' && data[9] == 'A'
                    && data[10] == 'V' && data[11] == 'E'; break;
            default:     ok = false;
        }
        return ok ? kind : null;
    }

    static String mimeType(String ext) {
        switch (ext) {
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "bmp": return "image/bmp";
            case "wav": return "audio/wav";
            default: return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
    }

    private static boolean startsWith(byte[] data, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }
}
