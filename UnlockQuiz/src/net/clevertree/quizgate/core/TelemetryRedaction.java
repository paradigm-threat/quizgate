package net.clevertree.quizgate.core;

/**
 * Privacy redaction for diagnostic / telemetry strings.
 * Never keep emails, phones, IPs, SSIDs, or long free-text that could identify a person.
 */
public final class TelemetryRedaction {
    private static final int MAX_SAFE_TOKEN = 64;
    private static final int MAX_SAFE_MESSAGE = 160;

    private TelemetryRedaction() {}

    public static String safeToken(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = raw.trim();
        if (s.length() > MAX_SAFE_TOKEN) s = s.substring(0, MAX_SAFE_TOKEN);
        if (looksLikePii(s)) return "[redacted]";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == ':' || c == '/') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    public static String safeMessage(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > MAX_SAFE_MESSAGE) s = s.substring(0, MAX_SAFE_MESSAGE);
        return scrubPatterns(s);
    }

    public static String safeChoiceForUpload(String choice) {
        if (choice == null || choice.isEmpty()) return "";
        String s = choice.trim();
        if (s.length() > 24) return "";
        if (looksLikePii(s)) return "";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) return "";
        }
        return s;
    }

    public static String coarseModel(String manufacturer, String model) {
        String mfr = safeToken(manufacturer == null ? "" : manufacturer);
        String mod = safeToken(model == null ? "" : model);
        if (mfr.isEmpty() && mod.isEmpty()) return "unknown";
        if (mfr.isEmpty()) return mod;
        if (mod.isEmpty()) return mfr;
        String lower = mod.toLowerCase();
        String mfrLower = mfr.toLowerCase();
        if (lower.startsWith(mfrLower)) return mod;
        return mfr + " " + mod;
    }

    public static boolean looksLikePii(String s) {
        if (s == null || s.isEmpty()) return false;
        String lower = s.toLowerCase();
        if (lower.contains("@") && lower.contains(".")) return true;
        if (lower.matches(".*\\b\\d{1,3}(\\.\\d{1,3}){3}\\b.*")) return true;
        if (lower.matches(".*\\+?\\d[\\d\\-().\\s]{7,}\\d.*")) return true;
        if (lower.contains("ssid") || lower.startsWith("wifi")) return true;
        return false;
    }

    private static String scrubPatterns(String s) {
        String out = s.replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[email]");
        out = out.replaceAll("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b", "[ip]");
        out = out.replaceAll("\\+?\\d[\\d\\-().\\s]{7,}\\d", "[phone]");
        return out;
    }
}
