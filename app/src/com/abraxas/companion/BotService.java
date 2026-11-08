package com.abraxas.companion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;

/**
 * ABRASAX always-on Telegram backend, running natively on the phone.
 * Polls getUpdates, answers /ping /status /help locally, and routes chat
 * to the PC brain over the USB tunnel when BASEDGOD is reachable.
 * Config from assets/abraxas.conf; logs to logcat tag ABRASAX-BOT.
 */
public class BotService extends Service {
    private static final String TAG = "ABRASAX-BOT";
    private volatile boolean running = true;
    private Thread thread;
    private String token = "";
    private String gatewayToken = "";
    private String model = "gemma-4-e2b-uncensored-hauhaucs-aggressive";
    private int lastUpdate = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(42, buildNotification());
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(this::run, "abraxas-bot");
            thread.start();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    private Notification buildNotification() {
        String chId = "abraxas_bot";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    chId, "ABRASAX Bot", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, chId)
                : new Notification.Builder(this);
        return b.setContentTitle("ABRASAX")
                .setContentText("Telegram backend online")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .build();
    }

    private void run() {
        try {
            Properties p = new Properties();
            InputStream in = getAssets().open("abraxas.conf");
            p.load(in);
            in.close();
            token = p.getProperty("token", "");
            gatewayToken = p.getProperty("gateway_token", "");
            model = p.getProperty("model", model);
            Log.i(TAG, "start token=" + (token.isEmpty() ? "MISSING" : "present"));
            while (running) {
                try {
                    poll();
                } catch (Exception e) {
                    Log.i(TAG, "poll: " + e);
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "fatal: " + e);
        }
    }

    private void poll() throws Exception {
        if (token.isEmpty()) {
            return;
        }
        URL u = new URL("https://api.telegram.org/bot" + token
                + "/getUpdates?timeout=10&offset=" + (lastUpdate + 1));
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        int code = c.getResponseCode();
        if (code != 200) {
            Log.i(TAG, "getUpdates HTTP " + code);
            c.disconnect();
            return;
        }
        String body = readAll(c.getInputStream());
        c.disconnect();
        JSONObject o = new JSONObject(body);
        if (!o.optBoolean("ok")) {
            return;
        }
        JSONArray arr = o.optJSONArray("result");
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject up = arr.getJSONObject(i);
            int uid = up.optInt("update_id", 0);
            if (uid > lastUpdate) {
                lastUpdate = uid;
            }
            JSONObject msg = up.optJSONObject("message");
            if (msg == null) {
                continue;
            }
            String chat = msg.optJSONObject("chat").optString("id");
            String text = msg.optString("text", "");
            Log.i(TAG, "msg from " + chat + ": "
                    + text.substring(0, Math.min(40, text.length())));
            String reply = handle(text);
            if (reply != null && !reply.isEmpty()) {
                sendMessage(chat, reply);
            }
        }
    }

    private String handle(String text) {
        String t = text.trim().toLowerCase();
        if (t.startsWith("/ping")) {
            return "pong - ABRASAX phone backend - " + new Date();
        }
        if (t.startsWith("/status")) {
            return "ABRASAX phone backend online. Brain link: "
                    + (brainOk() ? "UP" : "DOWN (PC offline)")
                    + " - model " + model;
        }
        if (t.startsWith("/start") || t.startsWith("/help")) {
            return "ABRASAX phone backend. /ping /status, or just chat. "
                    + "Chat routes to the PC brain over the USB tunnel when BASEDGOD is on.";
        }
        String brain = brainReply(text);
        if (brain != null) {
            return brain;
        }
        return "Brain offline: BASEDGOD unreachable. /ping /status still answer from the phone.";
    }

    private boolean brainOk() {
        try {
            URL u = new URL("http://127.0.0.1:2645/health");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(5000);
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String brainReply(String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray msgs = new JSONArray();
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", text);
            msgs.put(m);
            body.put("messages", msgs);
            body.put("max_tokens", 700);
            body.put("temperature", 0.7);
            URL u = new URL("http://127.0.0.1:2645/v1/chat/completions");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(120000);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            if (!gatewayToken.isEmpty()) {
                c.setRequestProperty("Authorization", "Bearer " + gatewayToken);
            }
            c.setDoOutput(true);
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.close();
            int code = c.getResponseCode();
            if (code != 200) {
                c.disconnect();
                return null;
            }
            String resp = readAll(c.getInputStream());
            c.disconnect();
            JSONObject o = new JSONObject(resp);
            JSONArray choices = o.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return null;
            }
            String content = choices.getJSONObject(0)
                    .optJSONObject("message").optString("content", "");
            return content.isEmpty() ? null : content;
        } catch (Exception e) {
            return null;
        }
    }

    private void sendMessage(String chat, String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("chat_id", chat);
            body.put("text", text.length() > 3900 ? text.substring(0, 3900) : text);
            URL u = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.close();
            int code = c.getResponseCode();
            Log.i(TAG, "sendMessage HTTP " + code);
            if (c.getInputStream() != null) {
                c.getInputStream().close();
            }
            c.disconnect();
        } catch (Exception e) {
            Log.i(TAG, "send: " + e);
        }
    }

    private String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }
}
