package app.tvunsplash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Sent from a computer over ADB only (the receiver needs DUMP, which the shell has and apps do not):
 *   --es key <Unsplash access key>   saves the key, schedules the checks and fetches a photo now
 *   --ez force true                  replaces the current photo now, even inside its time slot
 *   --ez credit false                leaves "/ Unsplash" out of the caption (true puts it back)
 */
public class Setup extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String key = intent.getStringExtra("key");
        if (key != null && !key.trim().isEmpty()) Store.setKey(context, key.trim());
        if (intent.hasExtra("credit")) Store.setCredit(context, intent.getBooleanExtra("credit", true));
        if (intent.getBooleanExtra("force", false)) Store.forceNext(context);
        Jobs.schedule(context);
        Jobs.runNow(context);
    }
}
