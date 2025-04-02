package com.twofauth.android;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.twofauth.android.preferences_activity.MainPreferencesFragment;

public class PreferencesActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable final Bundle saved_instance_state) {
        super.onCreate(saved_instance_state);
        setResult(Activity.RESULT_CANCELED);
        setContentView(R.layout.preferences_activity);
        setTitle(R.string.settings);
        if (saved_instance_state == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.preferences_content, new MainPreferencesFragment()).commit();
        }
    }

    @Override
    protected void processOnBackPressed() {
        finish();
    }
}
