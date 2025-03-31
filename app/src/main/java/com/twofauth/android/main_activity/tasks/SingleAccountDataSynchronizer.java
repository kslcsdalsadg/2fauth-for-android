package com.twofauth.android.main_activity.tasks;

import android.util.Log;

import com.twofauth.android.BaseActivity;
import com.twofauth.android.Constants;
import com.twofauth.android.Database.TwoFactorAccount;
import com.twofauth.android.Main;
import com.twofauth.android.main_activity.AccountsListAdapter;
import com.twofauth.android.main_service.ServerDataSynchronizer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SingleAccountDataSynchronizer {
    public interface OnSynchronizerFinishedListener {
        public abstract void onSynchronizedFinished(boolean success);
    }

    private static class SingleAccountDataSynchronizerImplementation implements BaseActivity.SynchronizedCallback, Main.OnBackgroundTaskExecutionListener {
        private final BaseActivity mActivity;

        private final AccountsListAdapter mAccountsListAdapter;

        private final TwoFactorAccount mAccount;

        private final OnSynchronizerFinishedListener mListener;

        private boolean mSuccess = false;

        SingleAccountDataSynchronizerImplementation(@NotNull final BaseActivity activity, @NotNull final AccountsListAdapter accounts_list_adapter, @NotNull final TwoFactorAccount account, @Nullable final OnSynchronizerFinishedListener listener) {
            mActivity = activity;
            mAccountsListAdapter = accounts_list_adapter;
            mAccount = account;
            mListener = listener;
        }

        @Override
        public Object onBackgroundTaskStarted(@Nullable final Object data) {
            try {
                mSuccess = ServerDataSynchronizer.synchronizeAccountData(mActivity, mAccount);
            }
            catch (Exception e) {
                Log.e(Constants.LOG_TAG_NAME, "Exception while trying to synchronize an element", e);
            }
            return null;
        }

        @Override
        public Object synchronizedCode(@Nullable final Object object) {
            mAccountsListAdapter.notifyItemChanged(mAccount);
            return null;
        }

        @Override
        public void onBackgroundTaskFinished(@Nullable final Object data) {
            if (! mActivity.isFinishedOrFinishing()) {
                try {
                    if (mSuccess) {
                        mActivity.executeSynchronized(this);
                    }
                }
                catch (Exception e) {
                    Log.e(Constants.LOG_TAG_NAME, "Exception while trying to display a synchronized element", e);
                    mSuccess = false;
                }
                finally {
                    if (mListener != null) {
                        mListener.onSynchronizedFinished(mSuccess);
                    }
                }
            }
        }
    }

    public static Thread getBackgroundTask(@NotNull final BaseActivity activity, @NotNull final AccountsListAdapter accounts_list_adapter, @NotNull final TwoFactorAccount account, @Nullable OnSynchronizerFinishedListener listener) {
        return Main.getInstance().getBackgroundTask(new SingleAccountDataSynchronizerImplementation(activity, accounts_list_adapter, account, listener));
    }
}
