package app.tvunsplash;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The access key, the time slot the shown photo belongs to, its file name, and recently used photo ids. */
final class Store {
    /** The one picture the base theme ships with, replaced by the first Unsplash photo. */
    static final String START_FILE = "00-start.jpg";
    private static final int RECENT = 60;

    private Store() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("wall", Context.MODE_PRIVATE);
    }

    static String key(Context c) { return prefs(c).getString("key", ""); }
    static void setKey(Context c, String key) { prefs(c).edit().putString("key", key).apply(); }

    /** "/ Unsplash" after the photographer in the caption; on unless setup turned it off. */
    static boolean credit(Context c) { return prefs(c).getBoolean("credit", true); }
    static void setCredit(Context c, boolean on) { prefs(c).edit().putBoolean("credit", on).apply(); }

    static String slot(Context c) { return prefs(c).getString("slot", ""); }
    static void forceNext(Context c) { prefs(c).edit().putString("slot", "").apply(); }

    static String current(Context c) { return prefs(c).getString("current", START_FILE); }

    static List<String> recent(Context c) {
        String s = prefs(c).getString("recent", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    static void shown(Context c, String slot, String file, String photoId) {
        List<String> recent = recent(c);
        recent.remove(photoId);
        recent.add(0, photoId);
        while (recent.size() > RECENT) recent.remove(recent.size() - 1);
        prefs(c).edit().putString("slot", slot).putString("current", file)
                .putString("recent", String.join(",", recent)).commit();
    }
}
