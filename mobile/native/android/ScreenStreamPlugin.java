package fun.sisocial.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * ScreenStream.start({ url, key, audio: 'mix' | 'mic' | 'game', landscape, kbps }) asks Android for permission to
 * record the screen, then streams it to the member's SI SOCIAL stream key. ScreenStream.stop() ends it.
 * ScreenStream.status() says whether it's running. Events: addListener('state', { state, message }) with
 * state = connecting | live | reconnecting | stopped | error.
 */
@CapacitorPlugin(name = "ScreenStream", permissions = { @Permission(alias = "microphone", strings = { Manifest.permission.RECORD_AUDIO }) })
public class ScreenStreamPlugin extends Plugin {
    @Override
    public void load() {
        ScreenStreamService.listener = (state, message) -> {
            JSObject o = new JSObject();
            o.put("state", state);
            o.put("message", message);
            notifyListeners("state", o, true);
        };
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject o = new JSObject();
        o.put("running", ScreenStreamService.running());
        o.put("state", ScreenStreamService.state);
        o.put("supported", Build.VERSION.SDK_INT >= 23);
        o.put("gameSound", Build.VERSION.SDK_INT >= 29);
        call.resolve(o);
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (call.getString("url") == null || call.getString("key") == null) { call.reject("Missing stream details"); return; }
        if (getPermissionState("microphone") != PermissionState.GRANTED) requestPermissionForAlias("microphone", call, "micAnswered");
        else askForScreen(call);
    }

    @PermissionCallback
    private void micAnswered(PluginCall call) {
        if (getPermissionState("microphone") != PermissionState.GRANTED) { call.reject("Allow the microphone for SI SOCIAL to stream with sound."); return; }
        askForScreen(call);
    }

    private void askForScreen(PluginCall call) {
        MediaProjectionManager mpm = (MediaProjectionManager) getContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(call, mpm.createScreenCaptureIntent(), "screenAnswered");
    }

    @ActivityCallback
    private void screenAnswered(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) { call.reject("Screen recording wasn't allowed."); return; }
        Intent i = new Intent(getContext(), ScreenStreamService.class);
        i.putExtra("resultCode", result.getResultCode());
        i.putExtra("data", result.getData());
        i.putExtra("url", call.getString("url"));
        i.putExtra("key", call.getString("key"));
        i.putExtra("audio", call.getString("audio", "mix"));
        i.putExtra("landscape", !Boolean.FALSE.equals(call.getBoolean("landscape", true)));
        Integer kbps = call.getInt("kbps", 2500);
        i.putExtra("kbps", kbps == null ? 2500 : Math.max(500, Math.min(6000, kbps)));
        try {
            if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i); else getContext().startService(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (ScreenStreamService.running() || !"idle".equals(ScreenStreamService.state)) {
            Intent i = new Intent(getContext(), ScreenStreamService.class).setAction(ScreenStreamService.ACTION_STOP);
            try { getContext().startService(i); } catch (Exception ignored) {}
        }
        call.resolve();
    }
}
