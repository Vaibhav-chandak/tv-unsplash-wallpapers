package app.tvunsplash;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/**
 * One check: when the time of day moved to a new slot, fetch a fitting landscape from Unsplash, put it into the
 * launcher's wallpapers/ folder with its place as the caption, then delete the previous photo. Nothing is
 * deleted until the new photo is fully written, so offline the old photo simply stays.
 */
final class Fetcher {
    static final String TAG = "UnsplashWall";
    private static final String THEME = "content://com.gathin.tvhome.theme/wallpapers/";
    private static final int MAX_BYTES = 9 * 1024 * 1024;
    private static final int MAX_CAPTION = 80;

    enum Result { UNCHANGED, NO_KEY, UPDATED, FAILED }

    private final Context context;

    Fetcher(Context context) { this.context = context; }

    /** The periodic and the one-off check can start together; one at a time, the second sees the slot done. */
    private static final Object LOCK = new Object();

    Result run() throws Exception {
        synchronized (LOCK) {
            return runLocked();
        }
    }

    private Result runLocked() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        int perDay = Store.perDay(context);
        String slot = Slots.id(now, perDay);
        if (slot.equals(Store.slot(context))) return Result.UNCHANGED;
        String key = Store.key(context);
        if (key.isEmpty()) return Result.NO_KEY;

        // Collections pick the photos themselves; Unsplash takes either collections or a search, not both.
        String collections = Store.collections(context);
        String q = collections.isEmpty() ? Slots.query(now, perDay, Store.topics(context)) : "collections " + collections;
        String api = "https://api.unsplash.com/photos/random?count=10&orientation=landscape&content_filter=high"
                + (collections.isEmpty() ? "&query=" + URLEncoder.encode(q, "UTF-8")
                        : "&collections=" + URLEncoder.encode(collections, "UTF-8"));
        JSONArray photos = new JSONArray(getText(api, key));
        JSONObject photo = choose(photos, Store.recent(context), Store.placeOnly(context));
        if (photo == null) return Result.FAILED;

        String id = photo.getString("id").replaceAll("[^A-Za-z0-9_-]", "");
        String file = "u-" + id + ".jpg";
        String imageUrl = photo.getJSONObject("urls").getString("raw")
                // cs=srgb: no Adobe RGB / P3 profile, which this TV's GPU would convert on every frame.
                + "&w=1920&h=1080&fit=crop&crop=entropy&fm=jpg&q=80&cs=srgb";
        File temp = new File(context.getCacheDir(), "next.jpg");
        try {
            download(imageUrl, temp);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temp.getPath(), o);
            if (o.outWidth < 1280 || o.outHeight < 720) throw new IOException("bad image " + o.outWidth + "x" + o.outHeight);

            ContentResolver cr = context.getContentResolver();
            String old = Store.current(context);
            String caption = caption(photo, Store.credit(context));
            copyTo(cr, Uri.parse(THEME + file), temp);
            writeText(cr, Uri.parse(THEME + "captions.txt"), file + "\t" + caption + "\n");
            if (!old.equals(file)) cr.delete(Uri.parse(THEME + old), null, null);
            Store.shown(context, slot, file, id);
            Log.i(TAG, slot + " -> " + file + " (" + q + "): " + caption);
        } finally {
            temp.delete();
        }
        // Unsplash asks apps to report each photo they use.
        try {
            getText(photo.getJSONObject("links").getString("download_location"), key);
        } catch (Exception ignored) {
            // only a usage count
        }
        return Result.UPDATED;
    }

    /**
     * A photo not shown recently, preferring one with a place name; placeOnly takes only those (and keeps the
     * current photo until the next check when none of the ten has one).
     */
    private static JSONObject choose(JSONArray photos, List<String> recent, boolean placeOnly) {
        JSONObject fallback = null;
        for (int i = 0; i < photos.length(); i++) {
            JSONObject p = photos.optJSONObject(i);
            if (p == null || recent.contains(p.optString("id"))) continue;
            if (p.optInt("width") < p.optInt("height")) continue;
            if (!place(p).isEmpty()) return p;
            if (fallback == null) fallback = p;
        }
        return placeOnly ? null : fallback;
    }

    private static String place(JSONObject p) {
        JSONObject loc = p.optJSONObject("location");
        if (loc == null) return "";
        String name = loc.optString("name", "");
        if (name.equals("null")) name = "";
        if (name.isEmpty()) {
            String city = loc.optString("city", ""), country = loc.optString("country", "");
            if (city.equals("null")) city = "";
            if (country.equals("null")) country = "";
            name = city.isEmpty() ? country : country.isEmpty() ? city : city + ", " + country;
        }
        return name.trim();
    }

    /**
     * "Lake Bled, Slovenia · Ales Krivec / Unsplash": place, photographer and, as Unsplash's API guidelines ask,
     * Unsplash. credit false (setup's --ez credit false) leaves "/ Unsplash" out. Shortened to what the launcher shows.
     */
    static String caption(JSONObject p, boolean credit) {
        JSONObject user = p.optJSONObject("user");
        String who = user == null ? "" : user.optString("name", "").trim().replaceAll("\\s+", " ");
        if (credit) who = who.isEmpty() ? "Unsplash" : who + " / Unsplash";
        String where = place(p).replaceAll("\\s+", " ");
        if (where.isEmpty()) return who.isEmpty() ? "" : trim("Photo by " + who);
        String[] parts = where.split(",");
        // "Manhattan Skyline, Manhattan, United States": the first part and the country are enough.
        if (parts.length > 2) where = parts[0].trim() + ", " + parts[parts.length - 1].trim();
        String full = who.isEmpty() ? where : where + " · " + who;
        return full.length() <= MAX_CAPTION ? full : trim(where);
    }

    private static String trim(String s) {
        return s.length() <= MAX_CAPTION ? s : s.substring(0, MAX_CAPTION - 1).trim() + "…";
    }

    private static HttpsURLConnection open(String url, String key) throws IOException {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        c.setInstanceFollowRedirects(true);
        if (key != null) {
            c.setRequestProperty("Authorization", "Client-ID " + key);
            c.setRequestProperty("Accept-Version", "v1");
        }
        return c;
    }

    private static String getText(String url, String key) throws IOException {
        HttpsURLConnection c = open(url, key);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " from " + new URL(url).getHost());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                copy(in, out, 2 * 1024 * 1024);
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    private static void download(String url, File to) throws IOException {
        HttpsURLConnection c = open(url, null);
        try {
            if (c.getResponseCode() != 200) throw new IOException("image HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
                copy(in, out, MAX_BYTES);
            }
        } finally {
            c.disconnect();
        }
    }

    private static void copyTo(ContentResolver cr, Uri uri, File from) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = cr.openOutputStream(uri, "w")) {
            if (out == null) throw new IOException("launcher refused " + uri);
            copy(in, out, MAX_BYTES);
        }
    }

    private static void writeText(ContentResolver cr, Uri uri, String text) throws IOException {
        try (OutputStream out = cr.openOutputStream(uri, "w")) {
            if (out == null) throw new IOException("launcher refused " + uri);
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copy(InputStream in, OutputStream out, int max) throws IOException {
        byte[] buf = new byte[32 * 1024];
        int total = 0, n;
        while ((n = in.read(buf)) > 0) {
            if (Thread.interrupted()) throw new IOException("stopped");
            total += n;
            if (total > max) throw new IOException("too large");
            out.write(buf, 0, n);
        }
    }
}
