package fun.sisocial.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import com.pedro.common.ConnectChecker;
import com.pedro.common.socket.base.SocketType;
import com.pedro.encoder.input.sources.audio.AudioSource;
import com.pedro.encoder.input.sources.audio.MicrophoneSource;
import com.pedro.encoder.input.sources.audio.MixAudioSource;
import com.pedro.encoder.input.sources.audio.InternalAudioSource;
import com.pedro.encoder.input.sources.video.NoVideoSource;
import com.pedro.encoder.input.sources.video.ScreenSource;
import com.pedro.library.generic.GenericStream;
import com.pedro.library.util.BitrateAdapter;

/**
 * Mobile Gaming Live: streams this phone's screen (and the game's sound plus your mic, on Android 10+) to the member's
 * SI SOCIAL stream key over RTMP. Runs as a foreground service so it keeps going while you're in the game, with an
 * ongoing notification that has a Stop button. Android asks the member for permission to record the screen first.
 */
public class ScreenStreamService extends Service implements ConnectChecker {
    public interface Listener { void onState(String state, String message); }

    static final String CHANNEL = "si_screen_stream";
    static final int NOTIFY_ID = 4271;
    static final String ACTION_STOP = "fun.sisocial.app.SCREEN_STREAM_STOP";

    static volatile Listener listener;
    static volatile String state = "idle";
    private static volatile ScreenStreamService instance;

    private GenericStream stream;
    private MediaProjection projection;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String endpoint;
    private int retries = 0;
    private ChatOverlay overlay;
    private BitrateAdapter adapter;

    static boolean running() { return instance != null && instance.stream != null && instance.stream.isStreaming(); }

    private static void emit(String s, String msg) {
        state = s;
        Listener l = listener;
        if (l != null) l.onState(s, msg == null ? "" : msg);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopEverything("stopped", "You stopped the stream.");
            return START_NOT_STICKY;
        }
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        instance = this;
        // Android 14+: the foreground service (type mediaProjection) must be running before the projection is used.
        startAsForeground();
        try {
            int resultCode = intent.getIntExtra("resultCode", 0);
            Intent data = Build.VERSION.SDK_INT >= 33 ? intent.getParcelableExtra("data", Intent.class) : intent.getParcelableExtra("data");
            String url = intent.getStringExtra("url");
            String key = intent.getStringExtra("key");
            String audio = intent.getStringExtra("audio");
            boolean landscape = intent.getBooleanExtra("landscape", true);
            int kbps = intent.getIntExtra("kbps", 2000);
            if (data == null || url == null || key == null) throw new IllegalArgumentException("Missing stream details");

            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(resultCode, data);
            if (projection == null) throw new IllegalStateException("Screen recording permission was not given");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { main.post(() -> stopEverything("stopped", "Screen recording stopped.")); }
            }, main);

            stream = new GenericStream(getApplicationContext(), this, new NoVideoSource(), new MicrophoneSource(MediaRecorder.AudioSource.DEFAULT));
            // Plain Java sockets send the server name (SNI) on secure RTMPS connections; LiveKit's servers need it,
            // otherwise the connection lands on the wrong server ("Unexpected EOF" during the handshake).
            stream.getStreamClient().setSocketType(SocketType.JAVA);
            // The screen only produces frames when something changes; keep a steady frame rate for viewers.
            stream.getGlInterface().setForceRender(true, 30);
            boolean ok = stream.prepareVideo(1280, 720, kbps * 1000, 30, 2, landscape ? 0 : 90)
                && stream.prepareAudio(44100, true, 128 * 1000, true, true);
            if (!ok) throw new IllegalStateException("This phone can't encode the stream at 720p");
            stream.changeVideoSource(new ScreenSource(getApplicationContext(), projection));
            // Adaptive bitrate: when the upload can't keep up (weak data, busy Wi-Fi), lower the quality instead of
            // letting video pile up and lag behind; raise it again when the connection recovers.
            final int maxBitrate = kbps * 1000;
            adapter = new BitrateAdapter(b -> { if (stream != null) stream.setVideoBitrateOnFly(Math.max(400_000, Math.min(maxBitrate, b))); });
            adapter.setMaxBitrate(maxBitrate);
            stream.changeAudioSource(audioSource(audio));
            endpoint = url.endsWith("/") ? url + key : url + "/" + key;
            emit("connecting", "");
            stream.startStream(endpoint);
            // Live chat floating over the game, if the member allowed "Display over other apps".
            String streamId = intent.getStringExtra("streamId");
            String base = intent.getStringExtra("base");
            if (intent.getBooleanExtra("overlay", true) && streamId != null && base != null && ChatOverlay.allowed(this)) {
                overlay = new ChatOverlay(this, base, streamId);
                overlay.show();
            }
        } catch (Exception e) {
            stopEverything("error", e.getMessage() == null ? "Couldn't start the stream" : e.getMessage());
        }
        return START_NOT_STICKY;
    }

    private AudioSource audioSource(String mode) {
        // Game sound + mic needs Android 10+ (and only games that allow their sound to be recorded).
        if (Build.VERSION.SDK_INT >= 29 && projection != null) {
            if ("game".equals(mode)) return new InternalAudioSource(projection, null);
            if (!"mic".equals(mode)) return new MixAudioSource(projection, null, MediaRecorder.AudioSource.DEFAULT);
        }
        return new MicrophoneSource(MediaRecorder.AudioSource.DEFAULT);
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Live streaming", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shown while you're streaming your screen live");
            nm.createNotificationChannel(ch);
        }
        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent openPi = open == null ? null : PendingIntent.getActivity(this, 0, open, piFlags | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, ScreenStreamService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, piFlags | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(getApplicationInfo().icon)
            .setContentTitle("You're live on SI SOCIAL")
            .setContentText("Your screen is being streamed. Tap Stop to end it.")
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(0, "Stop", stopPi)
            .build();
        if (Build.VERSION.SDK_INT >= 29) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (Build.VERSION.SDK_INT >= 30) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(NOTIFY_ID, n, type);
        } else {
            startForeground(NOTIFY_ID, n);
        }
    }

    private void stopEverything(String s, String msg) {
        try { if (overlay != null) overlay.hide(); } catch (Exception ignored) {}
        overlay = null;
        try { if (stream != null && stream.isStreaming()) stream.stopStream(); } catch (Exception ignored) {}
        try { if (stream != null) stream.release(); } catch (Exception ignored) {}
        stream = null;
        try { if (projection != null) projection.stop(); } catch (Exception ignored) {}
        projection = null;
        instance = null;
        emit(s, msg);
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (instance == this) stopEverything("stopped", "");
        super.onDestroy();
    }

    // ── RTMP connection events ──
    @Override public void onNewBitrate(long bitrate) {
        try {
            if (adapter != null && stream != null) adapter.adaptBitrate(bitrate, stream.getStreamClient().hasCongestion());
        } catch (Exception ignored) {}
    }
    @Override public void onConnectionStarted(String url) {}
    @Override public void onConnectionSuccess() { retries = 0; main.post(() -> emit("live", "")); }
    @Override public void onConnectionFailed(String reason) {
        main.post(() -> {
            // A short drop on mobile data: try again a few times before giving up.
            if (stream != null && retries < 5) {
                retries++;
                emit("reconnecting", reason);
                main.postDelayed(() -> {
                    try { if (stream != null && endpoint != null) { if (stream.isStreaming()) stream.stopStream(); stream.startStream(endpoint); } }
                    catch (Exception e) { stopEverything("error", e.getMessage()); }
                }, 3000L * retries);
            } else stopEverything("error", "Lost the connection: " + reason);
        });
    }
    @Override public void onDisconnect() {}
    @Override public void onAuthError() { main.post(() -> stopEverything("error", "Your stream key was refused. Make a new key on the Go Live page.")); }
    @Override public void onAuthSuccess() {}
}
