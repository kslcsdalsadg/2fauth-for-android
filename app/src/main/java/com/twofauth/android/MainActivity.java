package com.twofauth.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;

import android.content.res.ColorStateList;
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

import com.twofauth.android.main_activity.AccountsListIndexAdapter;
import com.twofauth.android.main_activity.AuthenticWithBiometrics;
import com.twofauth.android.main_activity.AuthenticWithPin;
import com.twofauth.android.main_activity.accounts_list.TwoFactorAccountViewHolder;
import com.twofauth.android.main_activity.tasks.CheckForAppUpdates;
import com.twofauth.android.main_activity.tasks.CheckForAppUpdates.AppVersionData;
import com.twofauth.android.main_activity.tasks.DataFilterer;
import com.twofauth.android.main_activity.tasks.DataLoader;
import com.twofauth.android.main_activity.tasks.DataLoader.LoadedAccountsData;
import com.twofauth.android.main_activity.GroupsListAdapter;
import com.twofauth.android.main_activity.tasks.SingleAccountDataSynchronizer;
import com.twofauth.android.main_service.StatusChangedBroadcastReceiver;
import com.twofauth.android.MainService.SyncResultType;
import com.twofauth.android.Database.TwoFactorAccount;

import com.twofauth.android.main_activity.AccountsListAdapter;
import com.twofauth.android.main_activity.FabButtonShowOrHide;
import com.twofauth.android.main_activity.FabButtonShowOrHide.DisplayState;
import com.twofauth.android.preferences_activity.MainPreferencesFragment;

import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class MainActivity extends BaseActivity implements StatusChangedBroadcastReceiver.OnMainServiceStatusChangedListener, AccountsListAdapter.OnOtpCodeVisibleStateChangedListener, AccountsListAdapter.OnAccountNeedsToBeSynchronizedListener, GroupsListAdapter.OnSelectedGroupChangesListener, DataLoader.OnDataLoadListener, DataFilterer.OnDataFilteredListener, CheckForAppUpdates.OnCheckForUpdatesListener, AuthenticWithBiometrics.OnBiometricAuthenticationFinishedListener, AuthenticWithPin.OnPinAuthenticationFinishedListener, ActivityResultCallback<ActivityResult>, View.OnClickListener, TextWatcher, ViewTreeObserver.OnGlobalLayoutListener {
    private static final String LAST_NOTIFIED_APP_UPDATED_VERSION_KEY = "last-notified-app-updated-version";
    private static final String LAST_NOTIFIED_APP_UPDATED_TIME_KEY = "last-notified-app-updated-time";
    private static final long NOTIFY_SAME_APP_VERSION_UPDATE_INTERVAL = DateUtils.DAY_IN_MILLIS;
    private static final long SYNC_BUTTON_ROTATION_DURATION = (long) (2.5f * DateUtils.SECOND_IN_MILLIS);
    private static final long OPEN_SETTINGS_VIBRATION_INTERVAL = 30;
    private static final long SYNC_ACCOUNTS_VIBRATION_INTERVAL = 30;
    private static final long COPY_TO_CLIPBOARD_VIBRATION_INTERVAL = 60;

    private final StatusChangedBroadcastReceiver mReceiver = new StatusChangedBroadcastReceiver(this);

    private final AccountsListIndexAdapter mAccountsListIndexAdapter = new AccountsListIndexAdapter();
    private final AccountsListAdapter mAccountsListAdapter = new AccountsListAdapter(this, this, mAccountsListIndexAdapter, false);;

    private final GroupsListAdapter mGroupsListAdapter = new GroupsListAdapter(this);

    private Thread mDataLoader = null;
    private Thread mDataFilterer = null;

    private LoadedAccountsData mLoadedAccountsData = null;
    private String mActiveGroup = null;

    private final ActivityResultLauncher<Intent> mActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this);
    private String mStartedActivityForResult;

    private boolean mUnlocked = false;

    private FabButtonShowOrHide mFabButtonShowOrHide;
    private final RotateAnimation mRotateAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);;
    private boolean mRotatingSyncingAccountsFab = false;

    private boolean mFirstAccess = true;
    private boolean mKeyboardVisible = false;

    @SuppressLint("CutPasteId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SharedPreferencesUtilities.getDefaultSharedPreferences(this).getBoolean(Constants.DISABLE_SCREENSHOTS_KEY, getResources().getBoolean(R.bool.disable_screenshots_default))) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        setContentView(R.layout.main_activity);
        final RecyclerView accounts_index_recycler_view = (RecyclerView) findViewById(R.id.accounts_list_index);
        accounts_index_recycler_view.setLayoutManager(new LinearLayoutManager(this));
        accounts_index_recycler_view.setAdapter(mAccountsListIndexAdapter);
        ((SimpleItemAnimator) accounts_index_recycler_view.getItemAnimator()).setSupportsChangeAnimations(false);
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
        ((FloatingActionButton) findViewById(R.id.copy_to_clipboard)).setOnClickListener(this);
        mFabButtonShowOrHide = new FabButtonShowOrHide((RecyclerView) findViewById(R.id.accounts_list), false, new FloatingActionButton[] { (FloatingActionButton) findViewById(R.id.sync_server_data), (FloatingActionButton) findViewById(R.id.open_app_settings) }, null);
        ((EditText) findViewById(R.id.filter_text)).addTextChangedListener(this);
        mRotateAnimation.setDuration(SYNC_BUTTON_ROTATION_DURATION);
        mRotateAnimation.setInterpolator(new LinearInterpolator());
        mRotateAnimation.setRepeatCount(Animation.INFINITE);
        findViewById(android.R.id.content).getViewTreeObserver().addOnGlobalLayoutListener(this);
        if (SharedPreferencesUtilities.getDefaultSharedPreferences(this).getBoolean(Constants.SYNC_ON_STARTUP_KEY, getResources().getBoolean(R.bool.sync_on_startup_default))) {
            MainService.startService(this);
        }
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
        if (! isFinishedOrFinishing()) {
            mAccountsListAdapter.onPause();
            findViewById(R.id.accounts_list_header).setVisibility(View.GONE);
            findViewById(R.id.accounts_list_index_container).setVisibility(View.GONE);
        }
    }

    private void setAccountsListIndexVisibility() {
        final View accounts_list_index_container = findViewById(R.id.accounts_list_index_container);
        final boolean accounts_list_index_container_will_be_visible = ((mLoadedAccountsData != null) && (mLoadedAccountsData.accounts != null) && (! mLoadedAccountsData.accounts.isEmpty()) && mLoadedAccountsData.alphaSorted && mUnlocked && (! mKeyboardVisible) && (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT));
        accounts_list_index_container.setVisibility((accounts_list_index_container_will_be_visible && (mFabButtonShowOrHide.getDisplayState() != DisplayState.HIDDEN)) ? View.VISIBLE : View.GONE);
        mFabButtonShowOrHide.setOtherViews(accounts_list_index_container_will_be_visible ? new View[] { findViewById(R.id.accounts_list_index_container) } : null); 
    }

    @SuppressLint("NotifyDataSetChanged")
    public void onConfigurationChanged(@NotNull final Configuration new_config) {
        super.onConfigurationChanged(new_config);
        final RecyclerView recycler_view = (RecyclerView) findViewById(R.id.accounts_list);
        ((GridLayoutManager) recycler_view.getLayoutManager()).setSpanCount(new_config.orientation == Configuration.ORIENTATION_PORTRAIT ? 1 : 2);
        recycler_view.getAdapter().notifyDataSetChanged();
        setAccountsListIndexVisibility();
    }

    private void onKeyboardVisibilityChanged(final boolean visible) {
        synchronized (mSynchronizationObject) {
            if (mKeyboardVisible != visible) {
                mKeyboardVisible = visible;
                setAccountsListIndexVisibility();
            }
        }
    }

    @Override
    public void onGlobalLayout() {
        final Rect rect = new Rect();
        final View root_view = findViewById(android.R.id.content);
        root_view.getWindowVisibleDisplayFrame(rect);
        final int screen_height = root_view.getRootView().getHeight(), keypad_height = screen_height - rect.bottom;
        onKeyboardVisibilityChanged(keypad_height > screen_height * 0.15);
    }

    private void onAuthenticationSucceeded() {
        final boolean first_access = mFirstAccess;
        mFirstAccess = false;
        mUnlocked = true;
        mAccountsListAdapter.onResume();
        findViewById(R.id.accounts_list_header).setVisibility(((mLoadedAccountsData == null) || (mLoadedAccountsData.accounts == null) || mLoadedAccountsData.accounts.isEmpty()) ? View.GONE : View.VISIBLE);
        setAccountsListIndexVisibility();
        final SharedPreferences preferences = SharedPreferencesUtilities.getDefaultSharedPreferences(this);
        if (first_access && ((SharedPreferencesUtilities.getEncryptedString(this, preferences, Constants.TWO_FACTOR_AUTH_SERVER_LOCATION_KEY, null) == null) || (SharedPreferencesUtilities.getEncryptedString(this, preferences, Constants.TWO_FACTOR_AUTH_TOKEN_KEY, null) == null))) {
            findViewById(R.id.open_app_settings).callOnClick();
        }
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
            SharedPreferencesUtilities.getDefaultSharedPreferences(this).edit().remove(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY).apply();
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
            final SharedPreferences preferences = SharedPreferencesUtilities.getDefaultSharedPreferences(this);
            if (preferences.contains(Constants.PIN_CODE_KEY)) {
                boolean authenticate_with_pin = true;
                if (preferences.getBoolean(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY, false)) {
                    final boolean can_use_biometrics = AuthenticWithBiometrics.canUseBiometrics(this);
                    authenticate_with_pin = ((! can_use_biometrics) || (! AuthenticWithBiometrics.areBiometricsLinked()));
                    if ((authenticate_with_pin) && (! can_use_biometrics)) {
                        UiUtils.showToast(this, R.string.fingerprint_validation_disabled_due_to_biometric_enrollment);
                        preferences.edit().remove(Constants.USE_FINGERPRINT_INSTEAD_OF_PIN_CODE_KEY).apply();
                    }
                    else if (! authenticate_with_pin) {
                        AuthenticWithBiometrics.authenticate(this, this);
                    }
                }
                if (authenticate_with_pin) {
                    AuthenticWithPin.authenticate(this, this, SharedPreferencesUtilities.getEncryptedString(this, preferences, Constants.PIN_CODE_KEY, null));
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
            VibratorUtils.vibrate(this, SYNC_ACCOUNTS_VIBRATION_INTERVAL);
            MainService.startService(this);
        }
        else if (id == R.id.open_app_settings) {
            VibratorUtils.vibrate(this, OPEN_SETTINGS_VIBRATION_INTERVAL);
            startActivityForResult(PreferencesActivity.class);
        }
        else if (id == R.id.copy_to_clipboard) {
            final boolean has_vibrated = VibratorUtils.vibrate(this, COPY_TO_CLIPBOARD_VIBRATION_INTERVAL), app_has_been_minimized = mAccountsListAdapter.copyActiveAccountOtpCodeToClipboard(this);
            if ((has_vibrated) && (app_has_been_minimized)) {
                ThreadUtils.sleep(COPY_TO_CLIPBOARD_VIBRATION_INTERVAL);
            }
        }
    }

    public void onAccountSynchronizationNeeded(@NotNull final TwoFactorAccount account) {
        SingleAccountDataSynchronizer.getBackgroundTask(this, mAccountsListAdapter, account, null).start();
    }

    public void onOtpCodeBecomesVisible(@NotNull final String otp_type) {
        findViewById(R.id.otp_time).setVisibility((TwoFactorAccount.OTP_TYPE_TOTP_VALUE.equals(otp_type) || TwoFactorAccount.OTP_TYPE_STEAM_VALUE.equals(otp_type)) ? View.VISIBLE : View.INVISIBLE);
        if (mFabButtonShowOrHide.getDisplayState() != FabButtonShowOrHide.DisplayState.HIDDEN) {
            ((FloatingActionButton) findViewById(R.id.copy_to_clipboard)).show();
        }
        mFabButtonShowOrHide.setFloatingActionButtons(new FloatingActionButton[] { (FloatingActionButton) findViewById(R.id.sync_server_data), (FloatingActionButton) findViewById(R.id.open_app_settings), (FloatingActionButton) findViewById(R.id.copy_to_clipboard) });
    }

    public void onTotpCodeShowAnimated(final long interval_until_current_otp_cycle_ends, final long cycle_time) {
        final ProgressBar otp_time = (ProgressBar) findViewById(R.id.otp_time);
        otp_time.setProgress(Math.max(0, (int) ((100 * interval_until_current_otp_cycle_ends) / cycle_time)));
        otp_time.setProgressTintList(ColorStateList.valueOf(getResources().getColor(interval_until_current_otp_cycle_ends < TwoFactorAccountViewHolder.OTP_IS_ABOUT_TO_EXPIRE_TIME ? R.color.otp_visible_last_seconds : interval_until_current_otp_cycle_ends < TwoFactorAccountViewHolder.OTP_IS_NEAR_TO_ABOUT_TO_EXPIRE_TIME ? R.color.otp_visible_near_of_last_seconds : R.color.otp_visible_normal, getTheme())));
    }

    public void onOtpCodeHidden() {
        findViewById(R.id.otp_time).setVisibility(View.INVISIBLE);
        ((FloatingActionButton) findViewById(R.id.copy_to_clipboard)).hide();
        mFabButtonShowOrHide.setFloatingActionButtons(new FloatingActionButton[] { (FloatingActionButton) findViewById(R.id.sync_server_data), (FloatingActionButton) findViewById(R.id.open_app_settings) });
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
                if ((syncing_or_loading_data) && (!mRotatingSyncingAccountsFab)) {
                    ((FloatingActionButton) findViewById(R.id.sync_server_data)).startAnimation(mRotateAnimation);
                    mRotatingSyncingAccountsFab = true;
                }
                else if ((! syncing_or_loading_data) && (mRotatingSyncingAccountsFab)) {
                    ((FloatingActionButton) findViewById(R.id.sync_server_data)).clearAnimation();
                    mRotatingSyncingAccountsFab = false;
                }
            }
        }
    }

    public void onServiceStarted() {
        setSyncDataButtonAvailability();
    }

    public void onServiceFinished(@Nullable final SyncResultType result_type) {
        UiUtils.showToast(this, result_type == SyncResultType.UPDATED ? R.string.sync_completed : result_type == SyncResultType.NO_CHANGES ? R.string.sync_no_changes : R.string.sync_error);
        setSyncDataButtonAvailability();
        if (result_type == SyncResultType.UPDATED) {
            loadData();
        }
    }

    public void onDataSyncedFromServer(@Nullable final SyncResultType result_type) {}

    private void loadData() {
        synchronized (mSynchronizationObject) {
            if (mDataLoader == null) {
                mDataLoader = DataLoader.getBackgroundTask(this, mAccountsListAdapter, mGroupsListAdapter, this);
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
                    findViewById(R.id.accounts_list_header).setVisibility(((mLoadedAccountsData == null) || (mLoadedAccountsData.accounts == null) || mLoadedAccountsData.accounts.isEmpty() || (! mUnlocked)) ? View.GONE : View.VISIBLE);
                    setAccountsListIndexVisibility();
                    ((EditText) findViewById(R.id.filter_text)).setText(null);
                }
                setSyncDataButtonAvailability();
                setAccountsListIndexVisibility();
            }
        }
    }

    @Override
    public void onDataLoadError() {
        onDataLoaded(false);
    }

    @Override
    public void onDataLoadSuccess(@Nullable LoadedAccountsData data) {
        synchronized (mSynchronizationObject) {
            mLoadedAccountsData = data;
            onDataLoaded(true);
        }
    }

    private void filterData() {
        synchronized (mSynchronizationObject) {
            if (mLoadedAccountsData != null) {
                ThreadUtils.interrupt(mDataFilterer);
                mDataFilterer = DataFilterer.getBackgroundTask(this, mAccountsListAdapter, mGroupsListAdapter, mLoadedAccountsData.accounts, mActiveGroup, ((EditText) findViewById(R.id.filter_text)).getText().toString(), this);
                mDataFilterer.start();
            }
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
        if (SharedPreferencesUtilities.getDefaultSharedPreferences(this).getBoolean(Constants.AUTO_UPDATE_APP_KEY, getResources().getBoolean(R.bool.auto_update_app_default))) {
            CheckForAppUpdates.getBackgroundTask(this, this).start();
        }
    }

    public void onCheckForUpdatesFinished(@NotNull final File downloaded_app_file, @NotNull final AppVersionData downloaded_app_version)
    {
        if (! isFinishedOrFinishing()) {
            final SharedPreferences preferences = SharedPreferencesUtilities.getDefaultSharedPreferences(this);
            boolean update_will_be_notified = true;
            if (downloaded_app_version.code == preferences.getInt(LAST_NOTIFIED_APP_UPDATED_VERSION_KEY, 0)) {
                update_will_be_notified = (preferences.getLong(LAST_NOTIFIED_APP_UPDATED_TIME_KEY, 0) + NOTIFY_SAME_APP_VERSION_UPDATE_INTERVAL < System.currentTimeMillis());
            }            
            if (update_will_be_notified) {
                preferences.edit().putInt(LAST_NOTIFIED_APP_UPDATED_VERSION_KEY, downloaded_app_version.code).putLong(LAST_NOTIFIED_APP_UPDATED_TIME_KEY, System.currentTimeMillis()).apply();
                UiUtils.showConfirmDialog(this, getString(R.string.there_is_an_update_version, getString(R.string.app_build_version_number, getString(R.string.app_version_name_value), getResources().getInteger(R.integer.app_version_number_value)), getString(R.string.app_build_version_number, downloaded_app_version.name, downloaded_app_version.code)), R.string.install_now, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(FileProvider.getUriForFile(getBaseContext(), getPackageName() + ".provider", downloaded_app_file), "application/vnd.android.package-archive");
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);
                        }
                        catch (Exception e) {
                            Log.e(Constants.LOG_TAG_NAME, "Exception while trying to install an app update", e);
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
                            if (! Database.TwoFactorAccountOperations.exists()) {
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
