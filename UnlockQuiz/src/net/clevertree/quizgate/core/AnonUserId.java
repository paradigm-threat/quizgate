package net.clevertree.quizgate.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Device-generated anonymous capability UUID (v4). Knowing the UUID = access.
 * Never tied to name/email/advertising id.
 */
public final class AnonUserId {
    private static final String PREFS = "unlock_quiz";
    private static final String KEY = "anon_user_uuid";

    private AnonUserId() {}

    public static String getOrCreate(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String existing = sp.getString(KEY, null);
        if (existing != null && isUuid(existing)) return existing;
        String id = UUID.randomUUID().toString();
        sp.edit().putString(KEY, id).apply();
        return id;
    }

    public static boolean isUuid(String id) {
        if (id == null || id.length() != 36) return false;
        try {
            UUID.fromString(id);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
