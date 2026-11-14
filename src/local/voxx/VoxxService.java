package local.voxx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Foreground service that keeps the global DynamicsProcessing effect alive.
 *
 * Remote tuning over adb, e.g.:
 *   adb shell am start-foreground-service -n local.voxx/.VoxxService \
 *       --ef comp_thr -34 --ef comp_makeup 13 --ei preset 3 --ez enabled true
 */
public class VoxxService extends Service {
    static final String EXTRA_RESTART = "restart";
    private static final String CHANNEL = "dsp";
    private static final long WATCHDOG_MS = 15_000;
    // Cap the watchdog backoff at 16x (4 min) so a device that never grants control isn't
    // hammered with a release/re-create every 15 s.
    private static final int MAX_WATCHDOG_SHIFT = 4;

    static volatile VoxxService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DspEngine engine = new DspEngine();
    private Params params;
    private AudioManager audio;
    private boolean devicesPrimed; // first callback just lists existing devices
    private int failStreak;        // consecutive failed re-attach attempts (drives backoff)

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            boolean attached = engine.isAttached();
            boolean control = engine.hasControl();
            if (!attached || !control) {
                Log.w(DspEngine.TAG, "watchdog: re-attaching (attached=" + attached
                        + ", control=" + control + ", failStreak=" + failStreak + ")");
                engine.start(params);
                // If it still isn't healthy, back off instead of re-creating again in 15 s.
                failStreak = engine.isAttached() && engine.hasControl()
                        ? 0 : Math.min(failStreak + 1, MAX_WATCHDOG_SHIFT);
            } else {
                failStreak = 0;
            }
            handler.postDelayed(this, WATCHDOG_MS << failStreak);
        }
    };

    // Output routing changes can drop global effects; re-attach shortly afterwards.
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            if (!devicesPrimed) {
                devicesPrimed = true;
                return;
            }
            scheduleRestart();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            scheduleRestart();
        }
    };

    private final Runnable restart = () -> engine.start(params);

    static void send(Context c, Intent extras) {
        Intent i = new Intent(c, VoxxService.class);
        if (extras != null) i.putExtras(extras);
        c.startForegroundService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        params = Params.load(this);
        audio = getSystemService(AudioManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "VOXX", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("VOXX running")
                .setSmallIcon(R.drawable.icon)
                .setOngoing(true)
                .build();
        // The 3-arg startForeground() only exists on API 29+; minSdk is 28, so calling it
        // unconditionally throws NoSuchMethodError on Android 9. On 34+ the explicit type
        // must match the manifest's foregroundServiceType.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }

        engine.start(params);
        audio.registerAudioDeviceCallback(deviceCallback, handler);
        handler.postDelayed(watchdog, WATCHDOG_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getExtras() != null) {
            if (intent.hasExtra("preset")) params.applyPreset(intent.getIntExtra("preset", params.preset));
            if (intent.hasExtra("enabled")) params.enabled = intent.getBooleanExtra("enabled", true);
            for (String key : Params.FLOAT_KEYS) {
                if (intent.hasExtra(key)) params.set(key, intent.getFloatExtra(key, 0f));
            }
            params.save(this);
            if (intent.getBooleanExtra(EXTRA_RESTART, false)) engine.start(params);
            else engine.apply(params);
        }
        Log.i(DspEngine.TAG, "status: " + engine.status() + " | " + params);
        return START_STICKY;
    }

    private void scheduleRestart() {
        handler.removeCallbacks(restart);
        handler.postDelayed(restart, 1500);
    }

    Params params() {
        return params;
    }

    String status() {
        String s = engine.status();
        if (engine.isAttached() && !engine.hasControl()) {
            s += " (no control - another effect may hold the output)";
        }
        return s;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        audio.unregisterAudioDeviceCallback(deviceCallback);
        engine.release();
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
