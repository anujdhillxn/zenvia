package com.lucive.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import com.lucive.managers.EventManager;
import com.lucive.models.Event;
import com.lucive.models.Rule;
import com.lucive.utils.AppUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UsageTrackerService extends Service {
    private final Map<String, Rule> ruleMap = new ConcurrentHashMap<>();
    private UsageStatsManager usageStatsManager;
    private final String TAG = "UsageTrackerService";
    private final IBinder binder = new LocalBinder();
    private Handler handler;
    private Runnable trackingRunnable;
    private static final long INTERVAL = 300;
    private static final long STARTUP_DELAY = 10 * 1000;
    private static final String CHANNEL_ID = "AppUsageTrackingChannel";
    private int delayCounter = 0;
    private long lastTimestamp = AppUtils.get24HoursBefore();
    private EventManager eventManager;

    public class LocalBinder extends Binder {
        public UsageTrackerService getService() {
            return UsageTrackerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Service bound");
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        eventManager = new EventManager();
        Log.i(TAG, "Service created");
        startForeground(1, createNotification());
        startTracking();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");
        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Service unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(trackingRunnable);
        Log.i(TAG, "Service destroyed");
        super.onDestroy();
    }

    private Notification createNotification() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "App Usage Tracking", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tracking App Usage")
                .setContentText("Running app usage tracking in the background")
                .build();
    }

    private void startTracking() {
        handler = new Handler(Looper.getMainLooper());
        trackingRunnable = new Runnable() {
            @Override
            public void run() {
                checkScreenUsages();
                handler.postDelayed(this, INTERVAL);
            }
        };
        handler.post(trackingRunnable);
    }

    private void checkScreenUsages() {
        final long endTime = System.currentTimeMillis();
        long startTime = lastTimestamp + 1;
        String currentApp = "Unknown";
        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        UsageEvents.Event usageEvent = new UsageEvents.Event();
        List<Event> allEvents = new ArrayList<>();
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(usageEvent);
            if (usageEvent.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED
                    || usageEvent.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED
                    || usageEvent.getEventType() == UsageEvents.Event.ACTIVITY_STOPPED
                    || usageEvent.getEventType() == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                    || usageEvent.getEventType() == UsageEvents.Event.SCREEN_INTERACTIVE) {
                allEvents.add(new Event(usageEvent.getPackageName(), usageEvent.getEventType(), usageEvent.getTimeStamp(), usageEvent.getClassName()));
            }
        }

        int eventIndex = 0;
        for (long chunkStartTime = startTime; chunkStartTime < endTime; chunkStartTime += INTERVAL) {
            final long chunkEndTime = chunkStartTime + INTERVAL;
            List<Event> chunk = new ArrayList<>();

            while (eventIndex < allEvents.size() && allEvents.get(eventIndex).getTimeStamp() < chunkEndTime) {
                Event event = allEvents.get(eventIndex);

                if (event.getTimeStamp() >= chunkStartTime) {
                    chunk.add(event);
                    lastTimestamp = event.getTimeStamp();
                }
                eventIndex++;
            }
            currentApp = eventManager.processEventsChunk(chunk);
        }
        if (!currentApp.equals("Unknown")) {
            if (isHourlyLimitExceeded(currentApp)) {
                String message = "Hourly screen time limit of " + AppUtils.formatTime(ruleMap.get(currentApp).hourlyMaxSeconds()) + " exceeded!";
                sendModalIntent(message);
            } else if (isDailyLimitExceeded(currentApp)) {
                String message = "Daily screen time limit of " + AppUtils.formatTime(ruleMap.get(currentApp).dailyMaxSeconds()) + " exceeded!";
                sendModalIntent(message);
            } else if (isSessionLimitExceeded(currentApp)) {
                String message = "Session screen time limit of " + AppUtils.formatTime(ruleMap.get(currentApp).sessionMaxSeconds()) + " exceeded!";
                sendModalIntent(message);
            } else if (delayStartup(currentApp)) {
                String message = "App starts in " + (STARTUP_DELAY -  eventManager.getSessionTime(currentApp)) / 1000 + " seconds!";
                sendModalIntent(message);
            } else {
                Intent hideScreenTimeExceeded = new Intent(this, FloatingWindowService.class);
                hideScreenTimeExceeded.putExtra("EXTRA_SHOW_MODAL", false);
                startService(hideScreenTimeExceeded);
            }
        }
        Log.i(TAG, "Time taken to process events: " + (System.currentTimeMillis() - endTime) + "ms");
    }

    public int getHourlyScreentime(final String packageName) {
        long currentTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startOfCurrentHour = calendar.getTimeInMillis();
        return (int) (eventManager.getScreentime(startOfCurrentHour, currentTime, packageName) / 1000);
    }

    public int getDailyScreentime(final String packageName) {
        final Rule rule = ruleMap.get(packageName);
        if (rule == null) {
            return 0;
        }
        final Calendar dailyReset = AppUtils.parseTimeString(rule.dailyStartsAt());
        long resetTime = dailyReset.getTimeInMillis();
        long currentTime = System.currentTimeMillis();
        if (resetTime > currentTime) {
            dailyReset.add(Calendar.DAY_OF_MONTH, -1);
        }
        long startTime = dailyReset.getTimeInMillis();
        return (int) (eventManager.getScreentime(startTime, currentTime, packageName) / 1000);
    }

    public boolean isHourlyLimitExceeded(final String packageName) {
        Rule rule = ruleMap.get(packageName);
        if (rule == null || !rule.isActive() || !rule.isHourlyMaxSecondsEnforced()) {
            return false;
        }
        final int hourlyUsage = getHourlyScreentime(packageName);
        return hourlyUsage >= rule.hourlyMaxSeconds();
    }

    public boolean isDailyLimitExceeded(final String packageName) {
        Rule rule = ruleMap.get(packageName);
        if (rule == null || !rule.isActive() || !rule.isDailyMaxSecondsEnforced()) {
            return false;
        }
        final int dailyUsage = getDailyScreentime(packageName);
        return dailyUsage >= rule.dailyMaxSeconds();
    }

    public boolean isSessionLimitExceeded(final String packageName) {
        Rule rule = ruleMap.get(packageName);
        if (rule == null || !rule.isActive() || !rule.isSessionMaxSecondsEnforced()) {
            return false;
        }
        final int sessionDuration = (int) eventManager.getSessionTime(packageName) / 1000;
        return sessionDuration >= rule.sessionMaxSeconds();
    }

    public boolean delayStartup(final String packageName) {
        Rule rule = ruleMap.get(packageName);
        if (rule == null || !rule.isActive() || !rule.isStartupDelayEnabled()) {
            return false;
        }
        return eventManager.getSessionTime(packageName) < STARTUP_DELAY;
    }

    public void updateRules(final Map<String, Rule> newRules) {
        ruleMap.clear();
        ruleMap.putAll(newRules);
    }

    private void sendModalIntent(String message) {
        Intent showScreenTimeExceeded = new Intent(this, FloatingWindowService.class);
        showScreenTimeExceeded.putExtra("EXTRA_SHOW_MODAL", true);
        showScreenTimeExceeded.putExtra("EXTRA_MODAL_MESSAGE", message);
        startService(showScreenTimeExceeded);
    }
}