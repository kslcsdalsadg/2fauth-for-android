package com.twofauth.android;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.twofauth.android.main_activity.AuthenticWithBiometrics;
import com.twofauth.android.main_activity.AuthenticWithPin;
import com.twofauth.android.main_activity.CheckForAppUpdates;
import com.twofauth.android.main_activity.DataFilterer;
import com.twofauth.android.main_activity.DataLoader;
import com.twofauth.android.main_activity.GroupsListAdapter;
import com.twofauth.android.ListUtils;
import com.twofauth.android.main_activity.MainServiceStatusChangedBroadcastReceiver;

import com.twofauth.android.main_activity.AccountsListAdapter;
import com.twofauth.android.main_activity.FabButtonShowOrHide;
import com.twofauth.android.preferences_activity.MainPreferencesFragment;

import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.EditText;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity implements MainServiceStatusChangedBroadcastReceiver.OnMainServiceStatusChanged, GroupsListAdapter.onSelectedGroupChanges, DataLoader.OnDataLoadListener, DataFilterer.OnDataFilteredListener, CheckForAppUpdates.OnCheckForUpdatesListener, AuthenticWithBiometrics.OnBiometricAuthenticationFinished, AuthenticWithPin.OnPinAuthenticationFinished, ActivityResultCallback<ActivityResult>, View.OnClickListener, TextWatcher {
    private static final String LAST_NOTIFIED_APP_UPDATED_VERSION_KEY = "last-notified-app-updated-version";
    private static final String LAST_NOTIFIED_APP_UPDATED_TIME_KEY = "last-notified-app-updated-time";
    private static final long NOTIFY_SAME_APP_VERSION_UPDATE_INTERVAL = DateUtils.DAY_IN_MILLIS;
    private static final long SYNC_BUTTON_ROTATION_DURATION = (long) (2.5f * DateUtils.SECOND_IN_MILLIS);

    private static class ThreadUtils {
        public static void interrupt(@Nullable final Thread thread) {
            if (thread != null) {
                thread.interrupt();
            }
        }
    }
    private final MainServiceStatusChangedBroadcastReceiver mReceiver = new MainServiceStatusChangedBroadcastReceiver(this);
    private final AccountsListAdapter mAccountsListAdapter = new AccountsListAdapter(false);;

    private final GroupsListAdapter mGroupsListAdapter = new GroupsListAdapter(this);
    private Thread mDataLoader = null;

    private Thread mDataFilterer = null;
    private final List<JSONObject> mItems = new ArrayList<JSONObject>();
    private String mActiveGroup = null;
    private final ActivityResultLauncher<Intent> mActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this);
    private String mStartedActivityForResult;
    private boolean mUnlocked = false;
    private final RotateAnimation mRotateAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);;
    private boolean mRotatingFab = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Constants.getDefaultSharedPreferences(this).getBoolean(Constants.DISABLE_SCREENSHOTS_KEY, getResources().getBoolean(R.bool.disable_screenshots_default))) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        setContentView(R.layout.main_activity);
        final RecyclerView accounts_recycler_view = (RecyclerView) findViewById(R.id.accounts_list);
        accounts_recycler_view.setLayoutManager(new GridLayoutManager(this, getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ? 1 : 2));
        accounts_recycler_view.setAdapter(mAccountsListAdapter);
        ((SimpleItemAnimator) accounts_recycler_view.getItemAnimator()).setSupportsChangeAnimations(false);
        final RecyclerView groups_recycler_view = (RecyclerView) findViewById(R.id.groups_list);
        groups_recycler_view.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        groups_recycler_view.setAdapter(mGroupsListAdapter);
        ((SimpleItemAnimator) groups_recycler_view.getItemAnimator()).setSupportsChangeAnimations(false);
        groups_recycler_view.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull final Rect out_rect, @NonNull final View view, @NonNull final RecyclerView parent, @NonNull final RecyclerView.State state) {
                super.getItemOffsets(out_rect, view, parent, state);
                out_rect.right = (parent.getChildAdapterPosition(view) == parent.getAdapter().getItemCount() - 1) ? 0 : UiUtils.getPixelsFromDp(getBaseContext(), 10);
            }
        });
        ((FloatingActionButton) findViewById(R.id.sync_server_data)).setOnClickListener(this);
        ((FloatingActionButton) findViewById(R.id.open_app_settings)).setOnClickListener(this);
        new FabButtonShowOrHide((RecyclerView) findViewById(R.id.accounts_list), new FloatingActionButton[] { (FloatingActionButton) findViewById(R.id.sync_server_data), (FloatingActionButton) findViewById(R.id.open_app_settings) });
        ((EditText) findViewById(R.id.filter_text)).addTextChangedListener(this);
        mRotateAnimation.setDuration(SYNC_BUTTON_ROTATION_DURATION);
        mRotateAnimation.setInterpolator(new LinearInterpolator());
        mRotateAnimation.setRepeatCount(Animation.INFINITE);
        checkForAppUpdates();
    }

    @Override
    public void onDestroy() {
        synchronized (mSynchronizationObject) {
            ThreadUtils.interrupt(mDataLoader);
            mDataLoader = null;
            ThreadUtils.interrupt(mDataFilterer);
            mDataFilterer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mReceiver.enable(this);
        setSyncDataButtonAvailability();
        loadData();
        unlock();
    }

    @Override
    public void onPause() {
        super.onPause();
        mReceiver.disable(this);
        mUnlocked = false;
        mAccountsListAdapter.onPause();
        findViewById(R.id.filters).setVisibility(View.GONE);
    }

    public void onConfigurationChanged(Configuration new_config) {
        super.onConfigurationChanged(new_config);
        final RecyclerView recycler_view = (RecyclerView) findViewById(R.id.accounts_list);
        ((GridLayoutManager) recycler_view.getLayoutManager()).setSpanCount(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ? 1 : 2);
        recycler_view.getAdapter().notifyDataSetChanged();
    }

    private void onAuthenticationSucceeded() {
        mAccountsListAdapter.onResume();
        findViewById(R.id.filters).setVisibility(mItems.isEmpty() ? View.GONE : View.VISIBLE);
        mUnlocked = true;
    }

    public void onBiometricAuthenticationSucceeded() {
        onAuthenticationSucceeded();
    }

    public void onPinAuthenticationSucceeded() {
        onAuthenticationSucceeded();
    }

    public void onAuthenticationError() {
        finish();
    }

    public void onBiometricAuthenticationError(final int error_code) {
        if (error_code == BiometricPrompt.ERROR_LOCKOUT) {
            Constants.getDefaultSharedPreferences(this).edit().remove(Constants.FINGERPRINT_ACCESS_KEY).apply();
        }
        onAuthenticationError();
    }

    public void onPinAuthenticationError(final boolean cancelled) {
        onAuthenticationError();
    }

    private void unlock() {
        if (mUnlocked) {
            onAuthenticationSucceeded();
        }
        else {
            final SharedPreferences preferences = Constants.getDefaultSharedPreferences(this);
            if (preferences.contains(Constants.PIN_ACCESS_KEY)) {
                if ((preferences.getBoolean(Constants.FINGERPRINT_ACCESS_KEY, false)) && (AuthenticWithBiometrics.canUseBiometrics(this))) {
                    AuthenticWithBiometrics.authenticate(this, this);
                }
                else {
                    AuthenticWithPin.authenticate(this, this, Constants.getDefaultSharedPreferences(this).getString(Constants.PIN_ACCESS_KEY, null));
                }
            }
            else {
                onAuthenticationSucceeded();
            }
        }
    }

    private void startActivityForResult(@NotNull final Class<?> activity_class) {
        Main.getInstance().startObservingIfAppBackgrounded();
        mStartedActivityForResult = activity_class.getName();
        mActivityResultLauncher.launch(new Intent(this, activity_class));
    }

    @Override
    public void onClick(@NotNull final View view) {
        final int id = view.getId();
        if (id == R.id.sync_server_data) {
            MainService.startService(this);
        }
        else if (id == R.id.open_app_settings) {
            startActivityForResult(PreferencesActivity.class);
        }
    }

    @Override
    public void beforeTextChanged(@NotNull final CharSequence string, final int start, final int count, final int after) {}

    @Override
    public void onTextChanged(@NotNull final CharSequence string, final int start, final int before, final int count) {}

    @Override
    public void afterTextChanged(@NotNull final Editable editable) {
        filterData();
    }

    private void setSyncDataButtonAvailability() {
        if (! isFinishedOrFinishing()) {
            synchronized (mSynchronizationObject) {
                final boolean syncing_or_loading_data = ((mDataLoader != null) || (MainService.isRunning(this)));
                ((FloatingActionButton) findViewById(R.id.sync_server_data)).setEnabled(MainService.canSyncServerData(this) && (! syncing_or_loading_data));
                if ((syncing_or_loading_data) && (! mRotatingFab)) {
                    ((FloatingActionButton) findViewById(R.id.sync_server_data)).startAnimation(mRotateAnimation);
                    mRotatingFab = true;
                }
                else if ((! syncing_or_loading_data) && (mRotatingFab)) {
                    ((FloatingActionButton) findViewById(R.id.sync_server_data)).clearAnimation();
                    mRotatingFab = false;
                }
            }
        }
    }

    public void onServiceStarted() {
        setSyncDataButtonAvailability();
    }
    public void onServiceFinished() {
        setSyncDataButtonAvailability();
        loadData();
    }
    public void onDataSyncedFromServer() {}

    private void loadData() {
        synchronized (mSynchronizationObject) {
            if (mDataLoader == null) {
                mDataLoader = new DataLoader(this, mAccountsListAdapter, mGroupsListAdapter, this);
                mDataLoader.start();
            }
        }
    }

    public void onDataLoaded(final boolean success) {
        if (! isFinishedOrFinishing()) {
            synchronized (mSynchronizationObject) {
                mDataLoader = null;
                if (success) {
                    mAccountsListAdapter.setViews(findViewById(R.id.accounts_list), findViewById(R.id.empty_view));
                    mActiveGroup = null;
                    findViewById(R.id.filters).setVisibility((mItems.isEmpty() || (! mUnlocked)) ? View.GONE : View.VISIBLE);
                    ((EditText) findViewById(R.id.filter_text)).setText(null);
                }
                setSyncDataButtonAvailability();
            }
        }
    }

    @Override
    public void onDataLoadError() {
        onDataLoaded(false);
    }

    @Override
    public void onDataLoadSuccess(@Nullable List<JSONObject> items) {
        synchronized (mSynchronizationObject) {
            ListUtils.setItems(mItems, items);
            onDataLoaded(true);
        }
    }

    private void filterData() {
        synchronized (mSynchronizationObject) {
            ThreadUtils.interrupt(mDataFilterer);
            mDataFilterer = new DataFilterer(this, mAccountsListAdapter, mGroupsListAdapter, mItems, mActiveGroup, ((EditText) findViewById(R.id.filter_text)).getText().toString(), this);
            mDataFilterer.start();
        }
    }

    @Override
    public void onDataFilterSuccess(final boolean any_filter_applied) {
        synchronized (mSynchronizationObject) {
            mAccountsListAdapter.setViews(findViewById(R.id.accounts_list), findViewById(any_filter_applied ? R.id.accounts_list : R.id.empty_view ));
            mDataFilterer = null;
        }
    }
    @Override
    public void onDataFilterError() {}

    public void onSelectedGroupChanges(@Nullable final String active_group, @Nullable final String previous_active_group) {
        synchronized (mSynchronizationObject) {
            if (! StringUtils.equals(mActiveGroup, active_group)) {
                mActiveGroup = active_group;
                filterData();
            }
        }
    }

    private void checkForAppUpdates() {
        if (Constants.getDefaultSharedPreferences(this).getBoolean(Constants.AUTO_UPDATES_APP_KEY, getResources().getBoolean(R.bool.auto_updates_app_default))) {
            new CheckForAppUpdates(this, this).start();
        }
    }
    public void onCheckForUpdatesFinished(@Nullable final File apk_local_file, @Nullable final String apk_local_file_version)
    {
        if ((apk_local_file != null) && (! isFinishedOrFinishing())) {
            final SharedPreferences preferences = Constants.getDefaultSharedPreferences(this);
            boolean update_will_be_notified = true;
            if (apk_local_file_version.equals(preferences.getString(LAST_NOTIFIED_APP_UPDATED_VERSION_KEY, null))) {
                update_will_be_notified = (preferences.getLong(LAST_NOTIFIED_APP_UPDATED_TIME_KEY, 0) + NOTIFY_SAME_APP_VERSION_UPDATE_INTERVAL < System.currentTimeMillis());
            }            
            if (update_will_be_notified) {
                preferences.edit().putString(LAST_NOTIFIED_APP_UPDATED_VERSION_KEY, apk_local_file_version).putLong(LAST_NOTIFIED_APP_UPDATED_TIME_KEY, System.currentTimeMillis()).apply();
                UiUtils.showConfirmDialog(this, getString(R.string.there_is_an_update_version, getString(R.string.app_version_name_value), apk_local_file_version), R.string.install_now, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(FileProvider.getUriForFile(getBaseContext(), getPackageName() + ".provider", apk_local_file), "application/vnd.android.package-archive");
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);
                        }
                        catch (Exception e) {
                            Log.d(Constants.LOG_TAG_NAME, "Exception while trying to install an app update", e);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onActivityResult(@NotNull final ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            if ((PreferencesActivity.class.getName().equals(mStartedActivityForResult)) && (! isFinishedOrFinishing())) {
                final Intent intent = result.getData();
                if (intent != null) {
                    final List<String> changed_settings = intent.getStringArrayListExtra(MainPreferencesFragment.EXTRA_CHANGED_SETTINGS);
                    if (changed_settings != null) {
                        if ((changed_settings.contains(Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY)) || (changed_settings.contains(Constants.TWO_FACTOR_AUTH_TOKEN_KEY))) {
                            if (! Constants.getDefaultSharedPreferences(this).contains(Constants.TWO_FACTOR_AUTH_ACCOUNTS_DATA_KEY)) {
                                onDataLoadSuccess(null);
                                if ((MainService.canSyncServerData(this)) && (! MainService.isRunning(this))) {
                                    MainService.startService(this);
                                }
                            }
                            else {
                                loadData();
                            }
                        }
                        else if (changed_settings.contains(Constants.SORT_ACCOUNTS_BY_LAST_USE_KEY)) {
                            loadData();
                        }
                        mAccountsListAdapter.onOptionsChanged();
                    }
                }
            }
        }
        mUnlocked = ! Main.getInstance().stopObservingIfAppBackgrounded();
    }

    @Override
    protected void processOnBackPressed() {
        boolean do_filter_data_instead_of_finish = false;
        synchronized (mSynchronizationObject) {
            if ((mActiveGroup != null) || (! ((EditText) findViewById(R.id.filter_text)).getText().toString().isEmpty())) {
                ((EditText) findViewById(R.id.filter_text)).setText(null);
                mActiveGroup = null;
                do_filter_data_instead_of_finish = true;
            }
        }
        if (do_filter_data_instead_of_finish) {
            filterData();
        }
        else {
            finish();
        }
    }
}
