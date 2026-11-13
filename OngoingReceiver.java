package PACKAGE_NAME;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * يعرض إشعارًا دائمًا بعدٍّ تنازلي للصلاة التالية.
 * العدّ يتولاه النظام (Chronometer)، ويُحدَّث الإشعار عند كل صلاة بمنبّه.
 */
public class OngoingReceiver extends BroadcastReceiver {

    static final String PREFS = "yaqeen_ongoing";
    static final String KEY = "entries";
    static final String CHANNEL = "yaqeen_ongoing";
    static final String ACTION_REFRESH = "yaqeen.ONGOING_REFRESH";
    static final String ACTION_DELETED = "yaqeen.ONGOING_DELETED";
    static final int NOTIFICATION_ID = 77001;

    @Override
    public void onReceive(Context context, Intent intent) {
        refresh(context);
    }

    static void save(Context context, String json) {
        SharedPreferences prefs =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY, json).apply();
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply();

        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }

        AlarmManager alarms = (AlarmManager)
            context.getSystemService(Context.ALARM_SERVICE);

        if (alarms != null) {
            alarms.cancel(refreshIntent(context));
        }
    }

    static void refresh(Context context) {
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null);

        if (json == null) {
            return;
        }

        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);

        try {
            JSONArray entries = new JSONArray(json);
            long now = System.currentTimeMillis();
            JSONObject current = null;
            long nextAlarm = Long.MAX_VALUE;

            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                long from = entry.getLong("from");
                long target = entry.getLong("target");

                if (from <= now && now < target) {
                    current = entry;
                    nextAlarm = Math.min(nextAlarm, target);
                } else if (from > now) {
                    nextAlarm = Math.min(nextAlarm, from);
                }
            }

            if (current != null) {
                post(context, manager, current);
            } else if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }

            if (nextAlarm != Long.MAX_VALUE) {
                schedule(context, nextAlarm);
            }
        } catch (Exception ignored) {
            // بيانات غير صالحة: لا نعرض شيئًا
        }
    }

    private static PendingIntent refreshIntent(Context context) {
        Intent intent = new Intent(context, OngoingReceiver.class);
        intent.setAction(ACTION_REFRESH);

        return PendingIntent.getBroadcast(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    /** يُستدعى عندما يمسح المستخدم الإشعار: نعيد عرضه فورًا. */
    private static PendingIntent deletedIntent(Context context) {
        Intent intent = new Intent(context, OngoingReceiver.class);
        intent.setAction(ACTION_DELETED);

        return PendingIntent.getBroadcast(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void schedule(Context context, long at) {
        AlarmManager alarms = (AlarmManager)
            context.getSystemService(Context.ALARM_SERVICE);

        if (alarms == null) {
            return;
        }

        PendingIntent pending = refreshIntent(context);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarms.canScheduleExactAlarms()) {
                alarms.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, at, pending);
            } else {
                alarms.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, at, pending);
            }
        } catch (SecurityException error) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        }
    }

    private static void post(
        Context context, NotificationManager manager, JSONObject entry
    ) throws Exception {
        if (manager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "الإشعار الدائم للصلاة",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("الصلاة التالية والوقت المتبقي");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        String title = entry.getString("title");
        String text = entry.getString("text");
        long target = entry.getLong("target");

        Intent launch = context.getPackageManager()
            .getLaunchIntentForPackage(context.getPackageName());

        PendingIntent open = PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int icon = context.getResources().getIdentifier(
            "ic_stat_notify", "drawable", context.getPackageName());

        if (icon == 0) {
            icon = context.getApplicationInfo().icon;
        }

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(0xFFB89452)
                .setShowWhen(true)
                .setWhen(target)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setContentIntent(open)
                .setDeleteIntent(deletedIntent(context));

        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // لم يُمنح إذن الإشعارات
        }
    }
}
