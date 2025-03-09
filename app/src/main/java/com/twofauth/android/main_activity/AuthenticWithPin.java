package com.twofauth.android.main_activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.twofauth.android.R;
import com.twofauth.android.StringUtils;
import com.twofauth.android.UiUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AuthenticWithPin {
    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 3;
    public interface OnPinAuthenticationFinished {
        public abstract void onPinAuthenticationSucceeded();
        public abstract void onPinAuthenticationError(boolean cancelled);
    }

    public interface OnPinRequestFinished {
        public abstract void onPinRequestDone(String value);
        public abstract void onPinRequestCancelled();
    }

    private static class PinData {
        public int pin = 0;

        public int digits = 0;
        public boolean accepted = false;

        PinData() {}
    }

    public static void authenticate(@NotNull final FragmentActivity activity, @NotNull final OnPinAuthenticationFinished callback, final String current_pin) {
        final PinData pin_data = new PinData();
        final AlertDialog.Builder dialog_builder = new AlertDialog.Builder(activity);
        final LayoutInflater inflater = activity.getLayoutInflater();
        final View dialog_view = inflater.inflate(R.layout.pin_dialog, null), button_delete = dialog_view.findViewById(R.id.button_delete);
        final TextView pin_textview = (TextView) dialog_view.findViewById(R.id.pin);
        final List<View> buttons = new ArrayList<View>(Arrays.asList(new View[] { dialog_view.findViewById(R.id.button_0), dialog_view.findViewById(R.id.button_1), dialog_view.findViewById(R.id.button_2), dialog_view.findViewById(R.id.button_3), dialog_view.findViewById(R.id.button_4), dialog_view.findViewById(R.id.button_5), dialog_view.findViewById(R.id.button_6), dialog_view.findViewById(R.id.button_7), dialog_view.findViewById(R.id.button_8), dialog_view.findViewById(R.id.button_9), button_delete }));
        dialog_builder.setView(dialog_view);
        dialog_builder.setTitle(R.string.pin_access_dialog_title);
        ((TextView) dialog_view.findViewById(R.id.message)).setText(R.string.pin_access_enter_current_pin);
        dialog_builder.setPositiveButton(activity.getString(R.string.accept), (DialogInterface.OnClickListener) null);
        dialog_builder.setNegativeButton(activity.getString(R.string.cancel), (DialogInterface.OnClickListener) null);
        dialog_builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(@NotNull final DialogInterface dialog) {
                Object tag = pin_textview.getTag();
                if ((tag instanceof Boolean) && ((boolean) tag)) {
                    callback.onPinAuthenticationSucceeded();
                }
                else {
                    callback.onPinAuthenticationError(tag == null);
                }
            }
        });
        final AlertDialog dialog = dialog_builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                final Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                button.setTag(1);
                button.setEnabled(false);
                button.setOnClickListener(new View.OnClickListener() {
                    private void cleanPinData() {
                        pin_data.pin = 0;
                        pin_data.digits = 0;
                        pin_textview.setText(null);
                        button_delete.setEnabled(false);
                        ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                    }
                    @Override
                    public void onClick(@NotNull final View view) {
                        if (StringUtils.equals(String.valueOf(pin_data.pin), current_pin)) {
                            pin_textview.setTag(true);
                            dialog.dismiss();
                        }
                        else {
                            int attempts = (int) view.getTag();
                            if (attempts < MAX_ATTEMPTS) {
                                cleanPinData();
                                view.setTag(attempts + 1);
                            }
                            else {
                                pin_textview.setTag(false);
                                dialog.dismiss();
                            }
                            UiUtils.showToast(activity, R.string.pin_is_not_valid);
                        }
                    }
                });
            }
        });
        for (View button : buttons) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(@NotNull final View view) {
                    if (view.getId() == R.id.button_delete) {
                        pin_data.pin /= 10;
                        pin_data.digits --;
                    }
                    else {
                        pin_data.pin = pin_data.pin * 10 + buttons.indexOf(view);
                        pin_data.digits ++;
                    }
                    pin_textview.setText(StringUtils.toHiddenString(pin_data.digits));
                    button_delete.setEnabled(pin_data.digits > 0);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(pin_data.digits >= MIN_PIN_LENGTH);
                }
            });
        }
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        button_delete.setEnabled(false);
    }

    public static void request(@NotNull final FragmentActivity activity, @NotNull final OnPinRequestFinished callback) {
        final PinData[] pins_data = new PinData[] { new PinData(), new PinData() };
        final AlertDialog.Builder dialog_builder = new AlertDialog.Builder(activity);
        final LayoutInflater inflater = activity.getLayoutInflater();
        final View dialog_view = inflater.inflate(R.layout.pin_dialog, null), button_delete = dialog_view.findViewById(R.id.button_delete);
        final TextView pin_textview = (TextView) dialog_view.findViewById(R.id.pin), message_textview = (TextView) dialog_view.findViewById(R.id.message);
        final List<View> buttons = new ArrayList<View>(Arrays.asList(new View[] { dialog_view.findViewById(R.id.button_0), dialog_view.findViewById(R.id.button_1), dialog_view.findViewById(R.id.button_2), dialog_view.findViewById(R.id.button_3), dialog_view.findViewById(R.id.button_4), dialog_view.findViewById(R.id.button_5), dialog_view.findViewById(R.id.button_6), dialog_view.findViewById(R.id.button_7), dialog_view.findViewById(R.id.button_8), dialog_view.findViewById(R.id.button_9), button_delete }));
        dialog_builder.setView(dialog_view);
        dialog_builder.setTitle(R.string.pin_access_dialog_title);
        message_textview.setText(R.string.pin_access_enter_new_pin);
        dialog_builder.setPositiveButton(activity.getString(R.string.accept), (DialogInterface.OnClickListener) null);
        dialog_builder.setNegativeButton(activity.getString(R.string.cancel), (DialogInterface.OnClickListener) null);
        dialog_builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(@NotNull final DialogInterface dialog) {
                Object tag = pin_textview.getTag();
                if ((tag instanceof Boolean) && ((boolean) tag)) {
                    callback.onPinRequestDone(String.valueOf(pins_data[0].pin));
                }
                else {
                    callback.onPinRequestCancelled();
                }
            }
        });
        final AlertDialog dialog = dialog_builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                final Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                button.setTag(0);
                button.setEnabled(false);
                button.setOnClickListener(new View.OnClickListener() {
                    private void cleanPinData(final int index) {
                        pins_data[index].pin = 0;
                        pins_data[index].digits = 0;
                        pin_textview.setText(null);
                        for (View button : buttons) {
                            button.setEnabled(true);
                        }
                        button_delete.setEnabled(false);
                        ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                    }

                    @Override
                    public void onClick(@NotNull final View view) {
                        final int attempt = (int) view.getTag();
                        if (attempt == 0) {
                            message_textview.setText(R.string.pin_access_enter_new_pin_again);
                            cleanPinData(1);
                            view.setTag(1);
                        }
                        else {
                            final boolean success = ((pins_data[0].pin == pins_data[1].pin) && (pins_data[0].digits == pins_data[1].digits));
                            if (! success) {
                                UiUtils.showToast(activity, R.string.pin_and_its_repeat_does_not_match);
                            }
                            pin_textview.setTag(success);
                            dialog.dismiss();
                        }
                    }
                });
            }
        });
        for (View button : buttons) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(@NotNull final View view) {
                    final PinData pin_data = pins_data[(int) dialog.getButton(DialogInterface.BUTTON_POSITIVE).getTag()];
                    if (view.getId() == R.id.button_delete) {
                        pin_data.pin /= 10;
                        pin_data.digits --;
                    }
                    else {
                        pin_data.pin = pin_data.pin * 10 + buttons.indexOf(view);
                        pin_data.digits ++;
                    }
                    for (View button : buttons) {
                        button.setEnabled(pin_data.digits < MAX_PIN_LENGTH);
                    }
                    pin_textview.setText(StringUtils.toHiddenString(pin_data.digits));
                    button_delete.setEnabled(pin_data.digits > 0);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(pin_data.digits >= MIN_PIN_LENGTH);
                }
            });
        }
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        button_delete.setEnabled(false);
    }
}
