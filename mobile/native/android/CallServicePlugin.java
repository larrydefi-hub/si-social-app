package fun.sisocial.app;

import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Lets the web app start and stop the "ongoing call" service: CallService.start({ title }) / CallService.stop(),
 * and route the call's sound like a phone call: CallService.setSpeaker({ on }) switches between the earpiece
 * (phone at your ear) and the loudspeaker.
 */
@CapacitorPlugin(name = "CallService")
public class CallServicePlugin extends Plugin {
    @PluginMethod
    public void start(PluginCall call) {
        Intent i = new Intent(getContext(), CallForegroundService.class);
        i.putExtra("title", call.getString("title", "Voice call"));
        // Listeners in a Live only need sound; asking for the microphone type without mic permission would fail.
        i.putExtra("mic", !Boolean.FALSE.equals(call.getBoolean("mic", true)));
        try {
            if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i);
            else getContext().startService(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        getContext().stopService(new Intent(getContext(), CallForegroundService.class));
        resetAudio();
        call.resolve();
    }

    @PluginMethod
    public void setSpeaker(PluginCall call) {
        boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if (am == null) { call.reject("No audio"); return; }
        try {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (Build.VERSION.SDK_INT >= 31) {
                int want = on ? AudioDeviceInfo.TYPE_BUILTIN_SPEAKER : AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
                AudioDeviceInfo pick = null;
                for (AudioDeviceInfo d : am.getAvailableCommunicationDevices()) if (d.getType() == want) { pick = d; break; }
                if (pick != null) am.setCommunicationDevice(pick); else am.clearCommunicationDevice();
            } else {
                am.setSpeakerphoneOn(on);
            }
            com.getcapacitor.JSObject r = new com.getcapacitor.JSObject();
            r.put("on", on);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    private void resetAudio() {
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice(); else am.setSpeakerphoneOn(false);
            am.setMode(AudioManager.MODE_NORMAL);
        } catch (Exception ignored) {}
    }
}
