package com.twofauth.android.preferences_activity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.twofauth.android.CancellableEditTextPreference;
import com.twofauth.android.Constants;
import com.twofauth.android.Database;
import com.twofauth.android.HtmlActivity;
import com.twofauth.android.Main;
import com.twofauth.android.MainService;
import com.twofauth.android.PreferencesActivity;
import com.twofauth.android.R;
import com.twofauth.android.SharedPreferencesUtilities;
import com.twofauth.android.StringUtils;
import com.twofauth.android.UiUtils;
import com.twofauth.android.VibratorUtils;
import com.twofauth.android.main_activity.AuthenticWithBiometrics;
import com.twofauth.android.main_activity.AuthenticWithPin;
import com.twofauth.android.MainService.SyncResultType;
import com.twofauth.android.main_service.StatusChangedBroadcastReceiver;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class MainPreferencesFragment extends PreferenceFragmentCompat implements StatusChangedBroadcastReceiver.OnMainServiceStatusChangedListener, Main.OnBackgroundTaskExecutionListener, Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener, AuthenticWithPin.OnPinAuthenticationFinishedListener, AuthenticWithPin.OnPinRequestFinishedListener, AuthenticWithBiometrics.OnBiometricAuthenticationFinishedListener {
    public static final String EXTRA_CHANGED_SETTINGS = "changes";

    private static final String OPEN_SERVER_LOCATION_KEY = "open-server-location";
    private static final String SYNC_DETAILS_KEY = "sync-details";
    private static final String RESET_ACCOUNTS_LAST_USE_KEY = "reset-accounts-last-use-data";
    private static final String PIN_ACCESS_ENABLED_KEY = "pin-access";
    private static final String GITHUB_REPO_KEY = "github-repo";
    private static final String OPEN_SOURCE_LICENSES_KEY = "open-source-licenses";
    private static final String APP_VERSION_KEY = "app-version";

    private static final String RESET_LAST_USE_TIMES_BACKGROUND_TASK = "reset-last-use-times";
    private static final String DELETE_ALL_ACCOUNTS_BACKGROUND_TASK = "delete-all-accounts";

    private final Intent mIntent = new Intent();

    private final ArrayList<String> mChanges = new ArrayList<String>();

    private final StatusChangedBroadcastReceiver mReceiver = new StatusChangedBroadcastReceiver(this);

    @Override
    public void onCreatePreferences(@Nullable final Bundle saved_instance_state, @Nullable final String root_key) {
        setPreferencesFromResource(R.xml.preferences, root_key);
    }

    @Override
    public void onPause() {
        super.onPause();
        mReceiver.disable(getContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        mReceiver.enable(getContext());
        setSyncDetailsPreferenceState();
    }

    @Override
    public void onServiceStarted() {
        setSyncDetailsPreferenceState();
    }

    @Override
    public void onServiceFinished(@Nullable final SyncResultType result_type) {
        setSyncDetailsPreferenceState();
    }

    @Override
    public void onDataSyncedFromServer(@Nullable final SyncResultType result_type) {}

    private void setSyncDetailsPreferenceState() {
        if (isAdded()) {
            final Context context = getContext();
            if (context != null) {
                final SharedPreferences preferences = SharedPreferencesUtilities.getDefaultSharedPreferences(context);
                final boolean is_service_running = MainService.isRunning(context);
                String last_sync_details = getString(R.string.sync_is_in_progress);
                if (! is_service_running) {
                    last_sync_details = getString(R.string.click_to_sync_data);
                    if (preferences.contains(Constants.LAST_SYNC_ERROR_TIME_KEY)) {
                        last_sync_details = getString(R.string.last_sync_error, preferences.getString(Constants.LAST_SYNC_ERROR_KEY, null), StringUtils.getDateTimeString(context, preferences.getLong(Constants.LAST_SYNC_ERROR_TIME_KEY, 0)), last_sync_details);
                    }
                    else if (preferences.contains(Constants.LAST_SYNC_TIME_KEY)) {
                        final int number_of_accounts = Database.TwoFactorAccountOperations.count();
                        last_sync_details = getResources().getQuantityString(R.plurals.sync_details, number_of_accounts, number_of_accounts, StringUtils.getDateTimeString(context, preferences.getLong(Constants.LAST_SYNC_TIME_KEY, 0)), last_sync_details);
                    }
                }
                Preference sync_details_preference = findPreference(SYNC_DETAILS_KEY);
                sync_details_preference.setEnabled(MainService.canSyncServerData(context) && (! is_service_running));
                sync_details_preference.setSummary(last_sync_details);
            }
        }
    }

    private void setMutablePreferencesSummariesAndAvailability() {
        if (isAdded()) {
            final Context context = getContext();
            if (context != null) {
                final SharedPreferences preferences = SharedPreferencesUtilities.getDefaultSharedPreferences(context);
                final Preference server_location_preference = findPreference(Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY), token_preference = findPreference(Constants.TWO_FACTOR_AUTH_TOKEN_KEY);
                server_location_preference.setSummary(SharedPreferencesUtilities.getEncryptedString(context, preferences, Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY, getString(R.string.server_location_is_not_set)));
                final boolean server_location_set = preferences.contains(Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY), token_value_set = preferences.contains(Constants.TWO_FACTOR_AUTH_TOKEN_KEY);
                token_preference.setEnabled(server_location_set);
                token_preference.setSummary(token_value_set ? R.string.token_value_is_set_summary : R.string.token_value_is_not_set_summary);
                findPreference(Constants.SYNC_ON_STARTUP_KEY).setEnabled(server_location_set && token_value_set);
                findPreference(OPEN_SERVER_LOCATION_KEY).setEnabled(server_location_set);
                setSyncDetailsPreferenceState();
                ((CheckBoxPreference) findPreference(PIN_ACCESS_ENABLED_KEY)).setChecked(preferences.getBoolean(PIN_ACCESS_ENABLED_KEY, false));
                final CheckBoxPreference fingerprint_access_preference = (CheckBoxPreference) findPreference(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY);
                if (fingerprint_access_preference != null) {
                    if (preferences.getBoolean(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY, false) && (! AuthenticWithBiometrics.areBiometricsLinked())) {
                        preferences.edit().remove(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY).apply();
                        UiUtils.showToast(context, R.string.fingerprint_validation_disabled_due_to_biometric_enrollment);
                    }
                    fingerprint_access_preference.setEnabled(preferences.contains(Constants.PIN_CODE_KEY));
                    fingerprint_access_preference.setChecked(preferences.getBoolean(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY, false));
                }
            }
        }
    }

    private void initializePreferencesListeners(@NotNull final Context context) {
        final SharedPreferences preferences = SharedPreferencesUtilities.getDefaultSharedPreferences(context);
        final EditTextPreference server_location_preference = (EditTextPreference) findPreference(Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY), token_preference = (EditTextPreference) findPreference(Constants.TWO_FACTOR_AUTH_TOKEN_KEY);
        server_location_preference.setOnPreferenceClickListener(this);
        server_location_preference.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText edit_text) {
                edit_text.setText(SharedPreferencesUtilities.getEncryptedString(context, preferences, Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY, null));
                edit_text.setSelection(edit_text.getText().length());
            }
        });
        server_location_preference.setOnPreferenceChangeListener(this);
        token_preference.setOnPreferenceClickListener(this);
        token_preference.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            public void onBindEditText(@NonNull final EditText edit_text) {
                edit_text.setHint(SharedPreferencesUtilities.getDefaultSharedPreferences(edit_text.getContext()).contains(Constants.TWO_FACTOR_AUTH_TOKEN_KEY) ? getString(R.string.token_unchanged) : "");
                edit_text.setText(null);
            }
        });
        token_preference.setOnPreferenceChangeListener(this);
        findPreference(Constants.SYNC_ON_STARTUP_KEY).setOnPreferenceChangeListener(this);
        findPreference(OPEN_SERVER_LOCATION_KEY).setOnPreferenceClickListener(this);
        findPreference(SYNC_DETAILS_KEY).setOnPreferenceClickListener(this);
        findPreference(Constants.SORT_ACCOUNTS_BY_LAST_USE_KEY).setOnPreferenceChangeListener(this);
        findPreference(RESET_ACCOUNTS_LAST_USE_KEY).setOnPreferenceClickListener(this);
        findPreference(Constants.UNGROUP_OTP_CODE_KEY).setOnPreferenceChangeListener(this);
        findPreference(Constants.DISPLAY_ACCOUNT_GROUP_KEY).setOnPreferenceChangeListener(this);
        findPreference(Constants.MINIMIZE_APP_AFTER_COPY_TO_CLIPBOARD_KEY).setOnPreferenceChangeListener(this);
        findPreference(Constants.HAPTIC_FEEDBACK_KEY).setOnPreferenceChangeListener(this);
        if (! VibratorUtils.canVibrate(context)) {
            findPreference(Constants.HAPTIC_FEEDBACK_KEY).getParent().removePreference(findPreference(Constants.HAPTIC_FEEDBACK_KEY));
        }
        findPreference(Constants.DISABLE_SCREENSHOTS_KEY).setOnPreferenceChangeListener(this);
        findPreference(Constants.HIDE_OTP_AUTOMATICALLY_KEY).setOnPreferenceChangeListener(this);
        findPreference(Constants.SHOW_NEXT_OTP_CODE_KEY).setOnPreferenceChangeListener(this);
        findPreference(PIN_ACCESS_ENABLED_KEY).setOnPreferenceChangeListener(this);
        ((CheckBoxPreference) findPreference(PIN_ACCESS_ENABLED_KEY)).setChecked(preferences.contains(Constants.PIN_CODE_KEY));
        findPreference(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY).setOnPreferenceChangeListener(this);
        if (! AuthenticWithBiometrics.canUseBiometrics(context)) {
            findPreference(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY).getParent().removePreference(findPreference(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY));
        }
        findPreference(GITHUB_REPO_KEY).setOnPreferenceClickListener(this);
        findPreference(Constants.AUTO_UPDATE_APP_KEY).setOnPreferenceChangeListener(this);
        findPreference(Constants.AUTO_UPDATE_APP_ONLY_IN_WIFI_KEY).setOnPreferenceChangeListener(this);
        findPreference(OPEN_SOURCE_LICENSES_KEY).setOnPreferenceClickListener(this);
        final Resources resources = getResources();
        String app_version = getString(R.string.app_build_version_number, getString(R.string.app_version_name_value), resources.getInteger(R.integer.app_version_number_value));
        if (resources.getBoolean(R.bool.is_debug_version)) {
            app_version = getString(R.string.app_version_is_debug_release, app_version);
        }
        findPreference(APP_VERSION_KEY).setSummary(app_version);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle saved_instance_state) {
        super.onViewCreated(view, saved_instance_state);
        initializePreferencesListeners(view.getContext());
        setMutablePreferencesSummariesAndAvailability();
        setDivider(null);
    }

    private void onSettingValueChanged(@NotNull final String[] keys) {
        synchronized (mIntent) {
            boolean changed = false;
            for (String key : keys) {
                if (! mChanges.contains(key)) {
                    mChanges.add(key);
                    changed = true;
                }
            }
            if (changed) {
                mIntent.putStringArrayListExtra(EXTRA_CHANGED_SETTINGS, mChanges);
                getActivity().setResult(Activity.RESULT_OK, mIntent);
            }
        }
    }

    private void onSettingValueChanged(@NotNull final String key) {
        onSettingValueChanged(new String[] { key });
    }

    @Override
    public boolean onPreferenceClick(@NonNull final Preference preference) {
        final Context context = preference.getContext();
        if ((Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY.equals(preference.getKey())) || (Constants.TWO_FACTOR_AUTH_TOKEN_KEY.equals(preference.getKey()))) {
            if (Database.TwoFactorAccountOperations.exists(true)) {
                UiUtils.showConfirmDialog(getActivity(), R.string.not_synced_changes_will_be_lost, R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (isAdded()) {
                            ((CancellableEditTextPreference) preference).showDialog();
                        }
                    }
                });
                return true;
            }
        }
        else if (OPEN_SERVER_LOCATION_KEY.equals(preference.getKey())) {
            final String url = SharedPreferencesUtilities.getEncryptedString(context, Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY, null);
            if (url != null) {
                HtmlActivity.openInWebBrowser(getActivity(), Uri.parse(url));
            }
        }
        else if (SYNC_DETAILS_KEY.equals(preference.getKey())) {
            MainService.startService(context);
        }
        else if (RESET_ACCOUNTS_LAST_USE_KEY.equals(preference.getKey())) {
            onSettingValueChanged(Constants.SORT_ACCOUNTS_BY_LAST_USE_KEY);
            preference.setEnabled(false);
            Main.getInstance().getBackgroundTask(this, RESET_LAST_USE_TIMES_BACKGROUND_TASK).start();
        }
        else if (GITHUB_REPO_KEY.equals(preference.getKey())) {
            HtmlActivity.openInWebBrowser(getActivity(), Constants.GITHUB_REPO);
        }
        else if (OPEN_SOURCE_LICENSES_KEY.equals(preference.getKey())) {
            startActivity(new Intent(context, HtmlActivity.class).putExtra(HtmlActivity.EXTRA_FILE_PATHNAME, "file:///android_asset/open-source-licenses.html"));
            return true;
        }
        return false;
    }

    private void removeDownloadedData(final int message_id) {
        if (message_id == 0) {
            final Context context = getContext();
            if (context != null) {
                SharedPreferencesUtilities.getDefaultSharedPreferences(context).edit().remove(Constants.LAST_SYNC_TIME_KEY).remove(Constants.LAST_SYNC_ERROR_KEY).remove(Constants.LAST_SYNC_ERROR_TIME_KEY).apply();
                Main.getInstance().getBackgroundTask(this, DELETE_ALL_ACCOUNTS_BACKGROUND_TASK).start();
            }
        }
        else {
            UiUtils.showConfirmDialog(getActivity(), message_id, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    removeDownloadedData(0);
                }
            });
        }
    }

    private void onServerLocationChanged(@NotNull final Context context, @NotNull final String location) {
        final SharedPreferences preferences = SharedPreferencesUtilities.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor editor = preferences.edit();
        final int message_id = preferences.contains(Constants.TWO_FACTOR_AUTH_TOKEN_KEY) ? Database.TwoFactorAccountOperations.exists() ? R.string.server_location_has_changed_and_token_defined_and_downloaded_data_exists : R.string.server_location_has_changed_and_token_defined_and_no_downloaded_data_exists : Database.TwoFactorAccountOperations.exists() ? R.string.server_location_has_changed_and_token_not_defined_but_downloaded_data_exists : 0;
        if (location.isEmpty()) {
            editor.remove(Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY);
        }
        else if (! SharedPreferencesUtilities.putEncryptedString(context, editor, Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY, location)) {
            return;
        }
        editor.remove(Constants.TWO_FACTOR_AUTH_TOKEN_KEY);
        editor.apply();
        setMutablePreferencesSummariesAndAvailability();
        onSettingValueChanged(new String[] { Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY, Constants.TWO_FACTOR_AUTH_TOKEN_KEY });
        removeDownloadedData(message_id);
        if (message_id == 0) {
            UiUtils.showToast(context, R.string.server_location_has_changed_and_token_not_defined_and_no_downloaded_data_exists);
        }
    }

    private void onTokenChanged(@NotNull final Context context, @NotNull final String token) {
        final SharedPreferences.Editor editor = SharedPreferencesUtilities.getDefaultSharedPreferences(context).edit();
        final int message_id = Database.TwoFactorAccountOperations.exists() ? R.string.token_has_changed_and_downloaded_data_exists : 0;
        if (SharedPreferencesUtilities.putEncryptedString(context, editor, Constants.TWO_FACTOR_AUTH_TOKEN_KEY, token)) {
            editor.apply();
            onSettingValueChanged(Constants.TWO_FACTOR_AUTH_TOKEN_KEY);
            setMutablePreferencesSummariesAndAvailability();
            removeDownloadedData(message_id);
            if (message_id == 0) {
                UiUtils.showToast(context, R.string.token_has_changed_and_no_downloaded_data_exists);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(@NonNull final Preference preference, final Object new_value) {
        final Context context = preference.getContext();
        if (Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY.equals(preference.getKey())) {
            final String trimmed_new_value = new_value.toString().trim();
            if (! StringUtils.equals(trimmed_new_value, SharedPreferencesUtilities.getEncryptedString(context, Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY, ""), true)) {
                if (trimmed_new_value.toLowerCase().startsWith("http:")) {
                    UiUtils.showConfirmDialog(getActivity(), R.string.server_http_is_insecure_warning, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            onServerLocationChanged(context, trimmed_new_value);
                        }
                    });
                    return false;
                }
                onServerLocationChanged(context, trimmed_new_value);
                return true;
            }
        }
        else if (Constants.TWO_FACTOR_AUTH_TOKEN_KEY.equals(preference.getKey())) {
            final String trimmed_new_value = new_value.toString().trim();
            if (! trimmed_new_value.isEmpty()) {
                onTokenChanged(context, trimmed_new_value);
                return true;
            }
        }
        else if (Constants.SYNC_ON_STARTUP_KEY.equals(preference.getKey())) {
            onSettingValueChanged(preference.getKey());
            return true;
        }
        else if (Constants.SORT_ACCOUNTS_BY_LAST_USE_KEY.equals(preference.getKey())) {
            onSettingValueChanged(preference.getKey());
            return true;
        }
        else if (Constants.UNGROUP_OTP_CODE_KEY.equals(preference.getKey())) {
            onSettingValueChanged(preference.getKey());
            return true;
        }
        else if (Constants.DISPLAY_ACCOUNT_GROUP_KEY.equals(preference.getKey())) {
            onSettingValueChanged(preference.getKey());
            return true;
        }
        else if (Constants.MINIMIZE_APP_AFTER_COPY_TO_CLIPBOARD_KEY.equals(preference.getKey())) {
            onSettingValueChanged(preference.getKey());
            return true;
        }
        else if (Constants.HAPTIC_FEEDBACK_KEY.equals(preference.getKey())) {
            onSettingValueChanged(preference.getKey());
            return true;
        }
        else if (Constants.DISABLE_SCREENSHOTS_KEY.equals(preference.getKey())) {
            UiUtils.showMessageDialog(getActivity(), R.string.change_will_be_applied_next_time_you_start_the_app);
            onSettingValueChanged(preference.getKey());
            return true;
        }
        else if (Constants.HIDE_OTP_AUTOMATICALLY_KEY.equals(preference.getKey())) {
            onSettingValueChanged(preference.getKey());
            return true;
        }
        else if (Constants.SHOW_NEXT_OTP_CODE_KEY.equals(preference.getKey())) {
            onSettingValueChanged(preference.getKey());
            return true;
        }
        else if (PIN_ACCESS_ENABLED_KEY.equals(preference.getKey())) {
            if ((boolean) new_value) {
                AuthenticWithPin.request(getActivity(), this);
            }
            else {
                AuthenticWithPin.authenticate(getActivity(), this, SharedPreferencesUtilities.getEncryptedString(context, Constants.PIN_CODE_KEY, null));
            }
        }
        else if (Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY.equals(preference.getKey())) {
            if (! (boolean) new_value) {
                onSettingValueChanged(preference.getKey());
                return true;
            }
            AuthenticWithBiometrics.authenticate(getActivity(), this);
        }
        else if (Constants.AUTO_UPDATE_APP_KEY.equals(preference.getKey())) {
            onSettingValueChanged(preference.getKey());
            return true;
        }
        else if (Constants.AUTO_UPDATE_APP_ONLY_IN_WIFI_KEY.equals(preference.getKey())) {
            onSettingValueChanged(preference.getKey());
            return true;
        }
        return false;
    }

    @Override
    public void onPinAuthenticationSucceeded() {
        final Context context = getContext();
        if (context != null) {
            SharedPreferences.Editor editor = SharedPreferencesUtilities.getDefaultSharedPreferences(getContext()).edit();
            editor.remove(Constants.PIN_CODE_KEY);
            editor.remove(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY);
            editor.putBoolean(PIN_ACCESS_ENABLED_KEY, false);
            editor.apply();
            UiUtils.showToast(context, R.string.pin_has_been_removed);
            setMutablePreferencesSummariesAndAvailability();
            onSettingValueChanged(new String[] { Constants.PIN_CODE_KEY, Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY });
        }
    }

    @Override
    public void onPinAuthenticationError(final boolean cancelled) {
        if (! cancelled) {
            UiUtils.showToast(getContext(), R.string.pin_is_not_valid);
        }
    }

    @Override
    public void onPinRequestDone(final String value) {
        final Context context = getContext();
        if (context != null) {
            SharedPreferences.Editor editor = SharedPreferencesUtilities.getDefaultSharedPreferences(context).edit();
            if (SharedPreferencesUtilities.putEncryptedString(context, editor, Constants.PIN_CODE_KEY, value)) {
                editor.putBoolean(PIN_ACCESS_ENABLED_KEY, true);
                editor.apply();
                UiUtils.showToast(context, R.string.pin_has_been_set);
                onSettingValueChanged(Constants.PIN_CODE_KEY);
                setMutablePreferencesSummariesAndAvailability();
            }
        }
    }

    @Override
    public void onPinRequestCancelled() {}

    @Override
    public void onBiometricAuthenticationSucceeded() {
        final Context context = getContext();
        if ((context != null) && (AuthenticWithBiometrics.linkBiometrics())) {
            SharedPreferencesUtilities.getDefaultSharedPreferences(context).edit().putBoolean(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY, true).apply();
            onSettingValueChanged(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY);
            setMutablePreferencesSummariesAndAvailability();
        }
    }

    @Override
    public void onBiometricAuthenticationError(final int error_code) {}

    @Override
    public Object onBackgroundTaskStarted(@Nullable final Object data) {
        if (RESET_LAST_USE_TIMES_BACKGROUND_TASK.equals(data)) {
            Database.TwoFactorAccountAtomicOperations.resetLastUses();
        }
        else if (DELETE_ALL_ACCOUNTS_BACKGROUND_TASK.equals(data)) {
            Database.TwoFactorAccountAtomicOperations.deleteAccountsAndGroups();
        }
        return data;
    }

    @Override
    public void onBackgroundTaskFinished(@Nullable final Object data) {
        if (isAdded()) {
            if (RESET_LAST_USE_TIMES_BACKGROUND_TASK.equals(data)) {
                UiUtils.showToast(getContext(), R.string.data_usage_has_been_deleted);
                findPreference(RESET_ACCOUNTS_LAST_USE_KEY).setEnabled(true);
            }
            else if (DELETE_ALL_ACCOUNTS_BACKGROUND_TASK.equals(data)) {
                setSyncDetailsPreferenceState();
            }
        }
    }
}
