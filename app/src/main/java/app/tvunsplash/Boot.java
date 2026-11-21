package app.tvunsplash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The periodic job is persisted, but schedule again at boot in case the firmware dropped it. */
public class Boot extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Jobs.schedule(context);
        Jobs.runNow(context);
    }
}
