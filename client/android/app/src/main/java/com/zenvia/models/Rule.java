package com.zenvia.models;

import java.util.Calendar;

public record Rule(String app, boolean isActive, int dailyMaxSeconds, int hourlyMaxSeconds,
                   String dailyStartsAt) {

}
