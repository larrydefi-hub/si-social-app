package fun.sisocial.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A small see-through live chat that floats over your game while you stream your screen: the newest comments and
 * gifts, and how many people are watching. Drag it anywhere; tap the header to shrink it. Needs Android's
 * "Display over other apps" permission (the member turns it on once). Reads the live with the member's own login.
 */
class ChatOverlay {
    private final Context ctx;
    private final String base;
    private final String streamId;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private LinearLayout root;
    private TextView header;
    private TextView body;
    private WindowManager.LayoutParams lp;
    private volatile boolean running;
    private boolean small;
    private Thread poller;

    ChatOverlay(Context ctx, String base, String streamId) {
        this.ctx = ctx.getApplicationContext();
        this.base = base.replaceAll("/+$", "");
        this.streamId = streamId;
    }

    static boolean allowed(Context c) { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c); }

    private int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics()); }

    void show() {
        if (running || !allowed(ctx)) return;
        running = true;
        main.post(this::build);
        poller = new Thread(this::pollLoop, "si-chat-overlay");
        poller.start();
    }

    void hide() {
        running = false;
        if (poller != null) poller.interrupt();
        main.post(() -> {
            try { if (root != null && wm != null) wm.removeView(root); } catch (Exception ignored) {}
            root = null;
        });
    }

    private void build() {
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB3070710);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x667C3AED);
        root.setBackground(bg);

        header = new TextView(ctx);
        header.setTextColor(0xFFFFFFFF);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        header.setText("● LIVE  ·  💬 Chat");
        root.addView(header);

        body = new TextView(ctx);
        body.setTextColor(0xFFEDE9FE);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        body.setMaxLines(6);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, dp(4), 0, 0);
        body.setText("Comments will show here.");
        root.addView(body);

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(dp(230), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(8);
        lp.y = dp(40);
        root.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - sx), dy = (int) (e.getRawY() - sy);
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                        lp.x = ox + dx; lp.y = oy + dy;
                        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) toggle();
                        return true;
                }
                return false;
            }
        });
        try { wm.addView(root, lp); } catch (Exception e) { running = false; }
    }

    private void toggle() {
        small = !small;
        if (body != null) body.setVisibility(small ? View.GONE : View.VISIBLE);
        if (lp != null && root != null) {
            lp.width = small ? WindowManager.LayoutParams.WRAP_CONTENT : dp(230);
            try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
        }
    }

    private void pollLoop() {
        while (running) {
            try {
                JSONObject st = fetch();
                if (st != null) render(st);
            } catch (Exception ignored) {}
            try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
        }
    }

    private JSONObject fetch() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/api/live/" + streamId).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        String cookie = CookieManager.getInstance().getCookie(base);
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return new JSONObject(sb.toString());
        } finally {
            c.disconnect();
        }
    }

    private void render(JSONObject st) {
        JSONObject live = st.optJSONObject("live");
        int viewers = live == null ? 0 : live.optInt("viewers", 0);
        boolean ended = live != null && "ended".equals(live.optString("status"));
        List<String> lines = new ArrayList<>();
        JSONArray gifts = st.optJSONArray("gifts");
        if (gifts != null && gifts.length() > 0) {
            JSONObject g = gifts.optJSONObject(0); // newest first
            if (g != null) lines.add("🎁 @" + g.optString("sender_handle") + " sent " + g.optString("icon") + " " + g.optString("gift_name"));
        }
        JSONArray cs = st.optJSONArray("comments"); // oldest first
        if (cs != null) {
            int from = Math.max(0, cs.length() - 5);
            for (int i = from; i < cs.length(); i++) {
                JSONObject c = cs.optJSONObject(i);
                if (c != null) lines.add(c.optString("handle") + ": " + c.optString("body"));
            }
        }
        final String h = ended ? "■ Live ended" : "● LIVE  ·  👁 " + viewers + "  ·  💬 Chat";
        final String b = lines.isEmpty() ? "No comments yet." : TextUtils.join("\n", lines);
        main.post(() -> {
            if (header != null) header.setText(h);
            if (body != null) body.setText(b);
        });
    }
}
