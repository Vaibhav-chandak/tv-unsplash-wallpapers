package app.tvunsplash;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

/** Runs one check on a background thread. A failure (offline, Unsplash down) keeps the current photo. */
public class WallJob extends JobService {
    private volatile Thread worker;

    @Override
    public boolean onStartJob(JobParameters params) {
        worker = new Thread(() -> {
            boolean retry = false;
            try {
                Fetcher.Result result = new Fetcher(getApplicationContext()).run();
                Log.i(Fetcher.TAG, "check: " + result);
                retry = result == Fetcher.Result.FAILED && params.getJobId() == Jobs.NOW;
            } catch (Throwable t) {
                Log.w(Fetcher.TAG, "check failed, keeping the current photo", t);
                retry = params.getJobId() == Jobs.NOW;
            }
            jobFinished(params, retry);
        }, "unsplash-wall");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread t = worker;
        if (t != null) t.interrupt();
        return params.getJobId() == Jobs.NOW;
    }
}
