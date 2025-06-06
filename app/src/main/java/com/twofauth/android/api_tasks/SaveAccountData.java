package com.twofauth.android.api_tasks;

import android.content.Context;
import android.util.Log;

import com.twofauth.android.API;
import com.twofauth.android.Constants;
import com.twofauth.android.Main;
import com.twofauth.android.R;
import com.twofauth.android.database.TwoFactorAccount;
import com.twofauth.android.utils.Preferences;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SaveAccountData {
    public interface OnDataSavedListener {
        public abstract void onDataSaved(TwoFactorAccount account, boolean success, boolean synced);
    }

    private static class SaveAccountDataImplementation implements Main.OnBackgroundTaskExecutionListener {
        private final Context mContext;

        private final TwoFactorAccount mAccount;
        private final boolean mAllowRemoteSynchronization;

        private final OnDataSavedListener mListener;

        private boolean mSuccess = false;
        private boolean mSynced = false;

        SaveAccountDataImplementation(@NotNull final Context context, @NotNull final TwoFactorAccount account, final boolean allow_remote_synchronization, @Nullable final OnDataSavedListener listener) {
            mContext = context;
            mAccount = account;
            mAllowRemoteSynchronization = allow_remote_synchronization;
            mListener = listener;
        }

        private boolean synchronizeAccount(@NotNull final SQLiteDatabase database, @NotNull final TwoFactorAccount account) throws Exception {
            return (mAllowRemoteSynchronization && account.getServerIdentity().isSyncingImmediately() && API.synchronizeAccount(database, mContext, account, true, true));
        }

        @Override
        public @Nullable Object onBackgroundTaskStarted(@Nullable final Object data) {
            try {
                final SQLiteDatabase database = Main.getInstance().getDatabaseHelper().open(true);
                if (database != null) {
                    try {
                        if (Main.getInstance().getDatabaseHelper().beginTransaction(database)) {
                            try {
                                final TwoFactorAccount stored_account = mAccount.inDatabase() ? Main.getInstance().getDatabaseHelper().getTwoFactorAccountsHelper().get(mAccount.getRowId()) : null;
                                if ((stored_account != null) && stored_account.isRemote() && (mAccount.getServerIdentity().getRowId() != stored_account.getServerIdentity().getRowId())) {
                                    stored_account.setStatus(TwoFactorAccount.STATUS_DELETED);
                                    stored_account.save(database, mContext);
                                    mAccount.setRowId(-1);
                                    mAccount.setRemoteId(0);
                                    mAccount.setStatus(TwoFactorAccount.STATUS_NOT_SYNCED);
                                }
                                mAccount.save(database, mContext);
                                mSuccess = true;
                                boolean synced = true;
                                if ((stored_account != null) && (stored_account.getRowId() != mAccount.getRowId())) { synced &= synchronizeAccount(database, stored_account); }
                                synced &= synchronizeAccount(database, mAccount);
                                mSynced = synced;
                            }
                            finally {
                                Main.getInstance().getDatabaseHelper().endTransaction(database, mSuccess);
                            }
                        }
                    }
                    finally {
                        Main.getInstance().getDatabaseHelper().close(database);
                    }
                }
            }
            catch (Exception e) {
                Log.e(Main.LOG_TAG_NAME, "Exception while trying to store/synchronize an account", e);
            }
            return null;
        }

        @Override
        public void onBackgroundTaskFinished(@Nullable final Object data) {
            if (mListener != null) { mListener.onDataSaved(mAccount, mSuccess, mSynced); }
        }
    }

    public static @NotNull Thread getBackgroundTask(@NotNull final Context context, @NotNull final TwoFactorAccount account, final boolean allow_remote_synchronization, @Nullable OnDataSavedListener listener) {
        return Main.getInstance().getBackgroundTask(new SaveAccountDataImplementation(context, account, allow_remote_synchronization, listener));
    }

    public static @NotNull Thread getBackgroundTask(@NotNull final Context context, @NotNull final TwoFactorAccount account, @Nullable OnDataSavedListener listener) {
        return getBackgroundTask(context, account, true, listener);
    }
}
