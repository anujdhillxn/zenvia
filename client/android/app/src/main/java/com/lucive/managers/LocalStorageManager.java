package com.lucive.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lucive.models.DeviceStatus;
import com.lucive.models.Rule;
import com.lucive.models.UsageTrackerHeartbeat;
import com.lucive.models.User;
import com.lucive.models.Word;
import com.lucive.utils.AppUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class LocalStorageManager {
    private static LocalStorageManager instance;
    private final SharedPreferences sharedPreferences;
    private final Gson gson;
    private final Set<LocalStorageObserver> observers = new HashSet<>();

    public static final String WORDS_KEY = "words";
    public static final String RULES_KEY = "rules";
    public static final String HEARTBEATS_KEY = "heartbeats";
    public static final String USER_KEY = "user";
    public static final String DEVICE_STATUS_KEY = "device_status";

    private LocalStorageManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public static synchronized LocalStorageManager getInstance(Context context) {
        if (instance == null) {
            instance = new LocalStorageManager(context);
        }
        return instance;
    }

    public void addObserver(LocalStorageObserver observer) {
        observers.add(observer);
    }

    private void notifyObservers() {
        for (LocalStorageObserver observer : observers) {
            observer.onLocalStorageUpdated();
        }
    }

    public void setWords(List<Word> words) {
        String json = gson.toJson(words);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(WORDS_KEY, json);
        editor.apply();
        notifyObservers();
    }

    public List<Word> getWords() {
        String json = sharedPreferences.getString(WORDS_KEY, null);
        if (json != null) {
            Type type = new TypeToken<List<Word>>() {}.getType();
            return gson.fromJson(json, type);
        } else {
            return new ArrayList<>();
        }
    }

    public void setRules(List<Rule> rules) {
        String json = gson.toJson(rules);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(RULES_KEY, json);
        editor.apply();
        notifyObservers();
    }

    public List<Rule> getRules() {
        String json = sharedPreferences.getString(RULES_KEY, null);
        if (json != null) {
            Type type = new TypeToken<List<Rule>>() {}.getType();
            return gson.fromJson(json, type);
        } else {
            return new ArrayList<>();
        }
    }

    public void saveDeviceStatus(final List<DeviceStatus> newDeviceStatuses) {
        List<DeviceStatus> deviceStatuses = getDeviceStatuses();
        for (DeviceStatus newDeviceStatus : newDeviceStatuses) {
            if(deviceStatuses.isEmpty() || deviceStatuses.get(deviceStatuses.size() - 1).timestamp() < newDeviceStatus.timestamp()) {
                deviceStatuses.add(newDeviceStatus);
            }
        }
        cleanOldDeviceStatuses(deviceStatuses);
        saveDeviceStatuses(deviceStatuses);
    }

    private void saveDeviceStatuses(List<DeviceStatus> deviceStatuses) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String json = gson.toJson(deviceStatuses);
        editor.putString(DEVICE_STATUS_KEY, json);
        editor.apply();
    }

    public List<DeviceStatus> getDeviceStatuses() {
        String json = sharedPreferences.getString(DEVICE_STATUS_KEY, "[]");
        Type type = new TypeToken<List<DeviceStatus>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public List<DeviceStatus> getDeviceStatuses(final String date) {
        final Calendar calendar = AppUtils.parseDate(date);
        final List<DeviceStatus> deviceStatuses = getDeviceStatuses();
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        final long startOfDay = calendar.getTimeInMillis() / 1000;
        calendar.add(Calendar.DAY_OF_MONTH, 2);
        final long endOfDay = calendar.getTimeInMillis() / 1000;
        List<DeviceStatus> filteredDeviceStatuses = new ArrayList<>();
        for (DeviceStatus deviceStatus : deviceStatuses) {
            if (deviceStatus.timestamp() >= startOfDay && deviceStatus.timestamp() < endOfDay) {
                filteredDeviceStatuses.add(deviceStatus);
            }
        }

        return filteredDeviceStatuses;
    }

    private void cleanOldDeviceStatuses(List<DeviceStatus> deviceStatuses) {
        long cutoffTimeSeconds = AppUtils.getDayStartNDaysBefore(10) / 1000;
        Iterator<DeviceStatus> iterator = deviceStatuses.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().timestamp() < cutoffTimeSeconds) {
                iterator.remove();
            } else {
                break;
            }
        }
    }


    public void saveHeartbeat(final UsageTrackerHeartbeat heartbeat) {
        List<UsageTrackerHeartbeat> heartbeats = getHeartbeats();
        heartbeats.add(heartbeat);
        cleanOldHeartbeats(heartbeats);
        saveHeartbeats(heartbeats);
    }

    private void saveHeartbeats(List<UsageTrackerHeartbeat> heartbeats) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String json = gson.toJson(heartbeats);
        editor.putString(HEARTBEATS_KEY, json);
        editor.apply();
    }

    public List<UsageTrackerHeartbeat> getHeartbeats() {
        String json = sharedPreferences.getString(HEARTBEATS_KEY, "[]");
        Type type = new TypeToken<List<UsageTrackerHeartbeat>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public List<UsageTrackerHeartbeat> getHeartbeats(final String date) {
        final Calendar calendar = AppUtils.parseDate(date);
        final List<UsageTrackerHeartbeat> heartbeats = getHeartbeats();
        final long startOfDay = calendar.getTimeInMillis() / 1000;
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        final long endOfDay = calendar.getTimeInMillis() / 1000;
        List<UsageTrackerHeartbeat> filteredHeartbeats = new ArrayList<>();
        for (UsageTrackerHeartbeat heartbeat : heartbeats) {
            if (heartbeat.timestamp() >= startOfDay && heartbeat.timestamp() < endOfDay) {
                filteredHeartbeats.add(heartbeat);
            }
        }

        return filteredHeartbeats;
    }

    private void cleanOldHeartbeats(List<UsageTrackerHeartbeat> heartbeats) {
        long cutoffTimeSeconds = AppUtils.getDayStartNDaysBefore(10) / 1000;
        Iterator<UsageTrackerHeartbeat> iterator = heartbeats.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().timestamp() < cutoffTimeSeconds) {
                iterator.remove();
            } else {
                break;
            }
        }
    }

    public void setUser(final User user) {
        String json = gson.toJson(user);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(USER_KEY, json);
        editor.apply();
        notifyObservers();
    }

    public User getUser() {
        String json = sharedPreferences.getString(USER_KEY, null);
        if (json != null) {
            return gson.fromJson(json, User.class);
        } else {
            return null;
        }
    }

    public void clearUser() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(USER_KEY);
        editor.apply();
        notifyObservers();
    }
}