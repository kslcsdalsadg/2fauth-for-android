package com.twofauth.android.utils;

import android.content.Context;

import com.twofauth.android.R;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.util.Date;

public class Strings {
    public static int compare(@Nullable final String string_1, @Nullable final String string_2, final boolean ignore_case) {
        if (string_1 == null) { return (string_2 == null) ? 0 : -1; }
        return ignore_case ? string_1.compareToIgnoreCase(string_2) : string_1.compareTo(string_2);
    }

    public static int compare(@Nullable final String string_1, @Nullable final String string_2) {
        return compare(string_1, string_2, false);
    }

    public static int compare(@Nullable final Character char_1, @Nullable final Character char_2, final boolean ignore_case) {
        return compare(char_1 == null ? null : char_1.toString(), char_2 == null ? null : char_2.toString(), ignore_case);
    }

    public static int compare(@Nullable final Character char_1, @Nullable final Character char_2) {
        return compare(char_1, char_2, false);
    }

    public static boolean equals(@Nullable final String string_1, @Nullable final String string_2, final boolean ignore_case) {
        if (string_1 == null) { return string_2 == null; }
        return ignore_case ? string_1.equalsIgnoreCase(string_2) : string_1.equals(string_2);
    }

    public static boolean equals(@Nullable final String string_1, @Nullable final String string_2) {
        return equals(string_1, string_2, false);
    }

    public static boolean similar(@Nullable final String string_1, @Nullable final String string_2) {
        return (equals(string_1, string_2) || (isEmptyOrNull(string_1) && isEmptyOrNull(string_2)));
    }

    public static boolean in(@NotNull final String string, @NotNull final String part, final boolean ignore_case) {
        if (ignore_case) { return string.toLowerCase().contains(part.toLowerCase()); }
        return string.contains(part);
    }

    public static boolean in(@NotNull final String string, @NotNull final String part) {
        return in(string, part, false);
    }

    public static String toHiddenString(final int length) {
        if (length != 0) {
            final StringBuilder value = new StringBuilder();
            for (int i = 0; i < length; i ++) {
                value.append("\u25CF");
            }
            return value.toString();
        }
        return null;
    }

    public static String toHiddenString(@Nullable final String value) {
        return toHiddenString(value == null ? 0 : value.length());
    }

    public static String getDateTimeString(@NotNull final Context context, final long time) {
        final DateFormat date_format = DateFormat.getDateInstance(DateFormat.MEDIUM), time_format = DateFormat.getTimeInstance(DateFormat.MEDIUM);
        final Date date = new Date(time);
        return context.getString(R.string.date_time, date_format.format(date), time_format.format(date));
    }

    public static boolean isEmptyOrNull(@Nullable final String string) {
        return ((string == null) || (string.isEmpty()));
    }

    public static int parseInt(@Nullable final String value, final int fallback) {
        if (value != null) {
            try {
                return Integer.parseInt(value);
            }
            catch (Exception e) {
            }
        }
        return fallback;
    }
}
