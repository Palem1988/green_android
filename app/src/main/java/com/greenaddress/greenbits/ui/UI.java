package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.google.common.collect.Lists;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.greenaddress.greenbits.GaService;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class UI {
    private static final String TAG = UI.class.getSimpleName();

    public static final int INVALID_RESOURCE_ID = 0;
    public static final ArrayList<String> UNITS = Lists.newArrayList("BTC", "mBTC", "\u00B5BTC", "bits");
    public enum FEE_TARGET {
        HIGH(3),
        NORMAL(6),
        LOW(12),
        ECONOMY(24),
        CUSTOM(-1);
        private final int mBlock;
        FEE_TARGET(int block) { mBlock = block; }
        public int getBlock() { return mBlock; }
    }
    public static final FEE_TARGET[] FEE_TARGET_VALUES = FEE_TARGET.values();

    private static final String MICRO_BTC = "\u00B5BTC";
    private static final MonetaryFormat BTC = new MonetaryFormat().shift(0).minDecimals(8).noCode();
    private static final MonetaryFormat MBTC = new MonetaryFormat().shift(3).minDecimals(5).noCode();
    private static final MonetaryFormat UBTC = new MonetaryFormat().shift(6).minDecimals(2).noCode();
    private static final DecimalFormat mDecimalFmt = new DecimalFormat("#,###.########", DecimalFormatSymbols.getInstance(
                                                                           Locale.US));

    // Class to unify cancel and dismiss handling */
    private static class DialogCloseHandler implements DialogInterface.OnCancelListener,
                                                       DialogInterface.OnDismissListener {
        private final Runnable mCallback;
        private final boolean mCancelOnly;

        public DialogCloseHandler(final Runnable callback, final boolean cancelOnly) {
            mCallback = callback;
            mCancelOnly = cancelOnly;
        }
        @Override
        public void onCancel(final DialogInterface d) { mCallback.run(); }
        @Override
        public void onDismiss(final DialogInterface d) { if (!mCancelOnly) mCallback.run(); }
    }

    public static void setDialogCloseHandler(final Dialog d, final Runnable callback, final boolean cancelOnly) {
        final DialogCloseHandler handler = new DialogCloseHandler(callback, cancelOnly);
        d.setOnCancelListener(handler);
        d.setOnDismissListener(handler);
    }

    public static void setDialogCloseHandler(final Dialog d, final Runnable callback) {
        setDialogCloseHandler(d, callback, false);
    }

    public static MaterialDialog dismiss(final Activity a, final Dialog d) {
        if (d != null)
            if (a == null)
                d.dismiss();
            else
                a.runOnUiThread(new Runnable() { public void run() { d.dismiss(); } });
        return null;
    }

    public static View inflateDialog(final Fragment f, final int id) {
        return inflateDialog(f.getActivity(), id);
    }

    public static View inflateDialog(final Activity a, final int id) {
        return a.getLayoutInflater().inflate(id, null, false);
    }

    private static boolean isEnterKeyDown(final KeyEvent e) {
        return e != null && e.getAction() == KeyEvent.ACTION_DOWN &&
               e.getKeyCode() == KeyEvent.KEYCODE_ENTER;
    }

    public static TextView.OnEditorActionListener getListenerRunOnEnter(final Runnable r) {
        return new EditText.OnEditorActionListener() {
                   @Override
                   public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                       if (actionId == EditorInfo.IME_ACTION_DONE ||
                           actionId == EditorInfo.IME_ACTION_SEARCH ||
                           actionId == EditorInfo.IME_ACTION_SEND ||
                           isEnterKeyDown(event)) {
                           if (event == null || !event.isShiftPressed()) {
                               r.run(); // The user is done typing.
                               return true; // Consume.
                           }
                       }
                       return false; // Pass on to other listeners.
                   }
        };
    }

    public static MaterialDialog.Builder popup(final Activity a, final String title, final int pos, final int neg) {
        final MaterialDialog.Builder b;
        b = new MaterialDialog.Builder(a)
            .title(title)
            .titleColorRes(R.color.white)
            .positiveColorRes(R.color.accent)
            .negativeColorRes(R.color.accent)
            .contentColorRes(R.color.white)
            .theme(Theme.DARK);
        if (pos != INVALID_RESOURCE_ID)
            b.positiveText(pos);
        if (neg != INVALID_RESOURCE_ID)
            return b.negativeText(neg);
        return b;
    }

    public static MaterialDialog.Builder popup(final Activity a, final int title, final int pos, final int neg) {
        return popup(a, a.getString(title), pos, neg);
    }

    public static MaterialDialog.Builder popup(final Activity a, final int title, final int pos) {
        return popup(a, title, pos, INVALID_RESOURCE_ID);
    }

    public static MaterialDialog.Builder popup(final Activity a, final String title) {
        return popup(a, title, android.R.string.ok, android.R.string.cancel);
    }

    public static MaterialDialog.Builder popup(final Activity a, final int title) {
        return popup(a, title, android.R.string.ok, android.R.string.cancel);
    }

    public static Map<String, String> getTwoFactorLookup(final Resources res) {
        final List<String> localized = Arrays.asList(res.getStringArray(R.array.twoFactorChoices));
        final List<String> methods = Arrays.asList(res.getStringArray(R.array.twoFactorMethods));
        final Map<String, String> map = new HashMap<>();
        for (int i = 0; i < localized.size(); i++)
            map.put(methods.get(i), localized.get(i));
        return map;
    }

    public static MaterialDialog popupWait(final Activity a, final int title) {
        final int id = INVALID_RESOURCE_ID;
        final MaterialDialog dialog = popup(a, title, id).progress(true, 0).build();
        dialog.show();
        return dialog;
    }

    public static void toast(final Activity activity, final String msg, final Button reenable, final int len) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                if (reenable != null)
                    reenable.setEnabled(true);
                Log.d(TAG, "Toast: " + msg);
                final Resources res = activity.getResources();
                final String translated = i18n(res, msg);
                Toast t = Toast.makeText(activity, translated, len);
                View v = t.getView();
                v.setBackgroundColor(0xaf000000);
                ((TextView) v.findViewById(android.R.id.message)).setTextColor(res.getColor(R.color.accentLight));
                t.show();
            }
        });
    }

    public static void toast(final Activity activity, final int id, final Button reenable) {
        toast(activity, activity.getString(id), reenable);
    }

    public static void toast(final Activity activity, final String msg, final Button reenable) {
        toast(activity, msg, reenable, Toast.LENGTH_LONG);
    }

    public static void toast(final Activity activity, final Throwable t, final Button reenable) {
        t.printStackTrace();
        toast(activity, t.getMessage(), reenable, Toast.LENGTH_LONG);
    }

    public static void toast(final Activity activity, final int id, final int len) {
        toast(activity, activity.getString(id), null, len);
    }

    public static void toast(final Activity activity, final String s, final int len) {
        toast(activity, s, null, len);
    }

    // Dummy TextWatcher for simple overrides
    public static class TextWatcher implements android.text.TextWatcher {
        TextWatcher() { super(); }
        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) { }
        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) { }
        @Override
        public void afterTextChanged(final Editable s) { }
    }

    public static < T extends View > T mapClick(final Activity activity, final int id, final View.OnClickListener fn) {
        final T v = find(activity, id);
        v.setOnClickListener(fn);
        return v;
    }

    public static < T extends View > T mapClick(final Activity activity, final int id, final Intent activityIntent) {
        return mapClick(activity, id, new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                activity.startActivity(activityIntent);
            }
        });
    }

    public static void unmapClick(final View v) {
        if (v != null)
            v.setOnClickListener(null);
    }

    public static void mapEnterToPositive(final Dialog dialog, final int editId) {
        final TextView edit = UI.find(dialog, editId);
        edit.setOnEditorActionListener(getListenerRunOnEnter(new Runnable() {
            public void run() {
                final MaterialDialog md = (MaterialDialog) dialog;
                md.onClick(md.getActionButton(DialogAction.POSITIVE));
            }
        }));
    }

    private final static Set<Integer> idsToNotReplace = new HashSet<>();
    static {
        idsToNotReplace.add(R.id.layoutCode);
        idsToNotReplace.add(R.id.textCode);
        idsToNotReplace.add(R.id.copyButton);
    }

    // Keyboard hiding taken from https://stackoverflow.com/a/11656129
    public static void attachHideKeyboardListener(final Activity activity, final View view) {
        // Set up touch listener for non-text box views to hide keyboard.
        if (!(view instanceof EditText) && !(view instanceof Button) && !(idsToNotReplace.contains(view.getId()))) {
            view.setOnClickListener(v -> hideSoftKeyboard(activity));
        }

        //If a layout container, iterate over children and seed recursion.
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View innerView = ((ViewGroup) view).getChildAt(i);
                attachHideKeyboardListener(activity, innerView);
            }
        }
    }

    private static void hideSoftKeyboard(Activity activity) {
        if (activity == null)
            return;
        final InputMethodManager inputMethodManager =
            (InputMethodManager) activity.getSystemService(
                Activity.INPUT_METHOD_SERVICE);
        if (inputMethodManager == null || activity.getCurrentFocus() == null)
            return;
        inputMethodManager.hideSoftInputFromWindow(
            activity.getCurrentFocus().getWindowToken(), 0);
    }

    // Show/Hide controls
    public static void showIf(final boolean condition, final View ... views) {
        for (final View v: views)
            if (v != null)
                v.setVisibility(condition ? View.VISIBLE : View.GONE);
    }

    public static void show(final View ... views) { showIf(true, views); }

    public static void hideIf(final boolean condition, final View ... views) {
        showIf(!condition, views);
    }

    public static void hide(final View ... views) { showIf(false, views); }

    // Enable/Disable controls
    public static void enableIf(final boolean condition, final View ... views) {
        for (final View v: views)
            v.setEnabled(condition);
    }

    public static void enable(final View ... views) { enableIf(true, views); }

    public static void disableIf(final boolean condition, final View ... views) {
        enableIf(!condition, views);
    }

    public static void disable(final View ... views) { enableIf(false, views); }

    public static void setText(final Activity activity, final int id, final int msgId) {
        final TextView t = find(activity, id);
        t.setText(msgId);
    }

    public static String getText(final View v, final int id) {
        return getText(find(v, id));
    }

    public static String getText(final TextView text) {
        return text.getText().toString();
    }

    public static void clear(final TextView ... views) {
        for (final TextView v: views)
            v.setText(R.string.empty);
    }

    public static < T extends View > T find(final Activity activity, final int id) {
        return (T) activity.findViewById(id);
    }

    public static < T extends View > T find(final View v, final int id) {
        return (T) v.findViewById(id);
    }

    public static < T extends View > T find(final Dialog dialog, final int id) {
        return (T) dialog.findViewById(id);
    }

    public static void preventScreenshots(final Activity activity) {
        if (!BuildConfig.DEBUG) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    public static LinearLayout.LayoutParams getScreenLayout(final Activity activity,
                                                            final double scale) {
        final DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        final int min = (int) (Math.min(dm.heightPixels, dm.widthPixels) * scale);
        return new LinearLayout.LayoutParams(min, min);
    }

    public static void showDialog(final Dialog dialog) {
        // (FIXME not sure if there's any smaller subset of these 3 calls below which works too)
        dialog.getWindow().clearFlags(LayoutParams.FLAG_NOT_FOCUSABLE |
                                      LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        dialog.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private static MonetaryFormat getUnitFormat(final GaService service) {
        final String unit = service.getBitcoinUnit();
        if (MonetaryFormat.CODE_BTC.equals(unit))
            return BTC;
        if (MonetaryFormat.CODE_MBTC.equals(unit))
            return MBTC;
        return UBTC;
    }

    public static String formatCoinValue(final GaService service, final Coin value) {
        return getUnitFormat(service).format(value).toString();
    }

    public static String formatCoinValueWithUnit(final GaService service, final Coin value) {
        return getUnitFormat(service).format(value).toString() + " " + service.getBitcoinUnit();
    }

    public static Coin parseCoinValue(final GaService service, final String value) {
        return getUnitFormat(service).parse(value);
    }

    private static final int SCALE = 4;
    public static Bitmap getQRCode(final String data) {
        final ByteMatrix matrix;
        try {
            matrix = Encoder.encode(data, ErrorCorrectionLevel.M).getMatrix();
        } catch (final WriterException e) {
            throw new RuntimeException(e);
        }

        final int height = matrix.getHeight() * SCALE;
        final int width = matrix.getWidth() * SCALE;
        final int min = height < width ? height : width;

        final Bitmap mQRCode = Bitmap.createBitmap(min, min, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < min; ++x)
            for (int y = 0; y < min; ++y)
                mQRCode.setPixel(x, y, matrix.get(x / SCALE, y / SCALE) == 1 ? Color.BLACK : Color.TRANSPARENT);
        return mQRCode;
    }

    public static BitmapDrawable getQrBitmapDrawable(final Context context, final String address) {
        final BitmapDrawable bd = new BitmapDrawable(context.getResources(), getQRCode(address));
        bd.setFilterBitmap(false);
        return bd;
    }

    // Return the translated string represented by the identifier given
    public static String i18n(final Resources res, final String textOrIdentifier) {
        if (TextUtils.isEmpty(textOrIdentifier))
            return "";
        if (!textOrIdentifier.startsWith("id_"))
            return textOrIdentifier; // Not a string id
        try {
            int resId = res.getIdentifier(textOrIdentifier, "string", "com.greenaddress.greenbits_android_wallet");
            return res.getString(resId);
        } catch (final Exception e) {
            return textOrIdentifier; // Unknown id
        }
    }
}
