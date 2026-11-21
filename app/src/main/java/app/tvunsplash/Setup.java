package app.tvunsplash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Sent from a computer over ADB only (the receiver needs DUMP, which the shell has and apps do not). Any mix of:
 *   --es key <Unsplash access key>        saves the key, schedules the checks and fetches a photo now
 *   --ei per_day <1..48>                  photos a day (4, the default, follows the light: morning/day/evening/night)
 *   --es topics "architecture,street"     your own searches, comma-separated, taken in turn; none = the built-in ones
 *   --es collections "1234,5678"          Unsplash collection ids instead of searches; none turns them off
 *   --ez place_only true                  only photos with a place name (false, the default, prefers them)
 *   --ez credit false                     leaves "/ Unsplash" out of the caption (true puts it back)
 *   --ez force true                       a new photo now, even inside the current time slot
 * Changing per_day, topics or collections also fetches a new photo now.
 */
public class Setup extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String key = intent.getStringExtra("key");
        if (key != null && !key.trim().isEmpty()) Store.setKey(context, key.trim());
        boolean changed = intent.getBooleanExtra("force", false);
        if (intent.hasExtra("per_day")) {
            Store.setPerDay(context, intent.getIntExtra("per_day", Slots.DEFAULT_PER_DAY));
            changed = true;
        }
        if (intent.hasExtra("topics")) {
            Store.setTopics(context, text(intent, "topics"));
            changed = true;
        }
        if (intent.hasExtra("collections")) {
            Store.setCollections(context, text(intent, "collections"));
            changed = true;
        }
        if (intent.hasExtra("place_only")) Store.setPlaceOnly(context, intent.getBooleanExtra("place_only", false));
        if (intent.hasExtra("credit")) Store.setCredit(context, intent.getBooleanExtra("credit", true));
        if (changed) Store.forceNext(context);
        Jobs.schedule(context);
        Jobs.runNow(context);
    }

    /** A text option; "none" (an empty string does not survive the shell) clears it. */
    private static String text(Intent intent, String name) {
        String s = intent.getStringExtra(name);
        return s == null || s.trim().equalsIgnoreCase("none") ? "" : s.trim();
    }
}
