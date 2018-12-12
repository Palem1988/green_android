package com.greenaddress.greenbits.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StrikethroughSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.blockstream.libwally.Wally;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.greenaddress.greenapi.CryptoHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.schildbach.wallet.ui.ScanActivity;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

public class MnemonicActivity extends LoginActivity implements View.OnClickListener {

    private static final String TAG = MnemonicActivity.class.getSimpleName();

    private static final int PINSAVE = 1337;
    private static final int QRSCANNER = 1338;
    private static final int CAMERA_PERMISSION = 150;
    private static final int WORDS_PER_LINE = 3;
    private static final int MNEMONIC_LENGTH = 24;

    private RecyclerView mRecyclerView;
    private MnemonicViewAdapter mMnemonicViewAdapter;
    private Button mOkButton;

    final private MultiAutoCompleteTextView.Tokenizer mTokenizer = new MultiAutoCompleteTextView.Tokenizer() {
        private boolean isspace(final CharSequence t, final int pos) {
            return Character.isWhitespace(t.charAt(pos));
        }

        public int findTokenStart(final CharSequence t, int cursor) {
            final int end = cursor;
            while (cursor > 0 && !isspace(t, cursor - 1))
                --cursor;
            while (cursor < end && isspace(t, cursor))
                ++cursor;
            return cursor;
        }

        public int findTokenEnd(final CharSequence t, int cursor) {
            final int end = t.length();
            while (cursor < end && !isspace(t, cursor))
                ++cursor;
            return cursor;
        }

        public CharSequence terminateToken(final CharSequence t) {
            int cursor = t.length();

            while (cursor > 0 && isspace(t, cursor - 1))
                cursor--;
            if (cursor > 0 && isspace(t, cursor - 1))
                return t;
            if (t instanceof Spanned) {
                final SpannableString sp = new SpannableString(t + " ");
                TextUtils.copySpansFrom((Spanned) t, 0, t.length(), Object.class, sp, 0);
                return sp;
            }
            return t;
        }
    };

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {
        Log.i(TAG, getIntent().getType() + ' ' + getIntent());
        setTitleBackTransparent();
        setTitleWithNetwork(R.string.id_restore);

        mOkButton = UI.find(this,R.id.mnemonicOkButton);
        mOkButton.setOnClickListener(this);

        final List<String> data = new ArrayList<>(MNEMONIC_LENGTH);
        for (int i=0; i < MNEMONIC_LENGTH; i++) data.add("");
        mMnemonicViewAdapter = new MnemonicViewAdapter(this, data);

        mRecyclerView = UI.find(this, R.id.mnemonicRecyclerView);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, WORDS_PER_LINE));
        mRecyclerView.setAdapter(mMnemonicViewAdapter);

        NFCIntentMnemonicLogin();
        mOkButton.setEnabled(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UI.unmapClick(mOkButton);
    }

    private boolean markInvalidWord(final Editable e) {
        for (final StrikethroughSpan s : e.getSpans(0, e.length(), StrikethroughSpan.class))
            e.removeSpan(s);

        final String word = e.toString();
        final int end = word.length();
        if (!MnemonicHelper.isPrefix(word))
            e.setSpan(new StrikethroughSpan(), 0, end, 0);
        return !MnemonicHelper.mWords.contains(word);
    }

    protected int getMainViewId() { return R.layout.activity_mnemonic; }

    private String getMnemonic() {
        StringBuilder sb = new StringBuilder();
        for (String s : mMnemonicViewAdapter.getItems())
            sb.append(s).append(" ");
        return sb.toString().trim();
    }

    private void setMnemonic(final String mnemonic) {
        final List<String> strings = mMnemonicViewAdapter.getItems();
        strings.clear();
        strings.addAll(Arrays.asList(mnemonic.split(" ")));
        mMnemonicViewAdapter.notifyDataSetChanged();
    }

    private boolean isValid(final String mnemonic) {
        final String words[] = mnemonic.split(" ");
        if (words.length != 24 && words.length != 27)
            return false;
        try {
            Wally.bip39_mnemonic_validate(Wally.bip39_get_wordlist("en"), mnemonic);
        } catch (final IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    private void enableLogin() {
        stopLoading();
        mOkButton.setEnabled(isValid(getMnemonic()));
    }

    private void doLogin() {
        final String mnemonic = getMnemonic();
        setMnemonic(mnemonic); // Trim mnemonic when OK pressed

        if (isLoading())
            return;

        if (mService.getConnectionManager().isPostLogin()) {
            toast(R.string.id_you_must_first_log_out_before);
            return;
        }

        if (!mService.getConnectionManager().isConnected()) {
            toast(R.string.id_unable_to_contact_the_green);
            return;
        }

        final String words[] = mnemonic.split(" ");
        if (words.length != 24 && words.length != 27) {
            toast(R.string.id_invalid_mnemonic_must_be_24_or);
            return;
        }

        try {
            Wally.bip39_mnemonic_validate(Wally.bip39_get_wordlist("en"), mnemonic);
        } catch (final IllegalArgumentException e) {
            toast(R.string.id_invalid_mnemonic_must_be_24_or); // FIXME: Use different error message
            return;
        }

        mService.getExecutor().execute(() -> mService.getConnectionManager().loginWithMnemonic(mnemonic,""));

        startLoading();
        mOkButton.setEnabled(false);

    }

    private ListenableFuture<String> askForPassphrase() {
        final SettableFuture<String> fn = SettableFuture.create();
        runOnUiThread(new Runnable() {
            public void run() {
                final View v = UI.inflateDialog(MnemonicActivity.this, R.layout.dialog_passphrase);
                final EditText passphraseValue = UI.find(v, R.id.passphraseValue);
                passphraseValue.requestFocus();
                final MaterialDialog dialog = UI.popup(MnemonicActivity.this, "Encryption passphrase")
                                              .customView(v, true)
                                              .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(final MaterialDialog dlg, final DialogAction which) {
                        fn.set(UI.getText(passphraseValue));
                    }
                })
                                              .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(final MaterialDialog dlg, final DialogAction which) {
                        enableLogin();
                    }
                }).build();
                UI.showDialog(dialog);
            }
        });
        return fn;
    }

    @Override
    public void onClick(final View v) {
        if (v == mOkButton)
            doLogin();
    }

    private void onScanClicked() {
        final String[] perms = { "android.permission.CAMERA" };
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1 &&
            checkSelfPermission(perms[0]) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(perms, CAMERA_PERMISSION);
        else {
            final Intent scanner = new Intent(MnemonicActivity.this, ScanActivity.class);
            startActivityForResult(scanner, QRSCANNER);
        }
    }

    private void loginOnUiThread() {
        runOnUiThread(new Runnable() { public void run() { doLogin(); } });
    }

    private static byte[] getNFCPayload(final Intent intent) {
        final Parcelable[] extra = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        return ((NdefMessage) extra[0]).getRecords()[0].getPayload();
    }

    private void NFCIntentMnemonicLogin() {
        final Intent intent = getIntent();

        if (intent == null || !NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()))
            return;

        if (intent.getType().equals("x-gait/mnc")) {
            // Unencrypted NFC
            setMnemonic(CryptoHelper.mnemonic_from_bytes(getNFCPayload(intent)));
            loginOnUiThread();

        } else if (intent.getType().equals("x-ga/en"))
            // Encrypted NFC
            CB.after(askForPassphrase(), new CB.Op<String>() {
                @Override
                public void onSuccess(final String passphrase) {
                    setMnemonic(CryptoHelper.decrypt_mnemonic(getNFCPayload(intent),
                                                              passphrase));
                    loginOnUiThread();
                }
            });
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
        case PINSAVE:
            onLoginSuccess();
            break;
        case QRSCANNER:
            if (data != null && data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT) != null) {
                setMnemonic(data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT));
                enableLogin();
            }
            break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.mnemonic, menu);
        menu.findItem(R.id.action_scan).setIcon(R.drawable.qr);
        return true;
    }


    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
                                    final ContextMenu.ContextMenuInfo menuInfo) {
        // Handle custom paste
        menu.add(0, v.getId(), 0, "Paste");
    }

    @Override
    public boolean onContextItemSelected(final MenuItem menuItem) {
        // place your TextView's text in clipboard
        final ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_PLAIN)) {
            final ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            setMnemonic(item.getText().toString());
        }
        return super.onContextItemSelected(menuItem);
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final boolean haveCamera = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        setMenuItemVisible(menu, R.id.action_add, haveCamera);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            onBackPressed();
            return true;
        case R.id.action_scan:
            onScanClicked();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] granted) {
        if (requestCode == CAMERA_PERMISSION &&
            isPermissionGranted(granted, R.string.id_please_enable_camera))
            startActivityForResult(new Intent(this, ScanActivity.class), QRSCANNER);
    }

    @Override
    protected void onLoginSuccess() {
        super.onLoginSuccess();
        if (getCallingActivity() == null) {
            final Intent savePin = PinSaveActivity.createIntent(MnemonicActivity.this, mService.getMnemonic());
            startActivityForResult(savePin, PINSAVE);
        } else {
            setResult(RESULT_OK);
            finishOnUiThread();
        }
    }

    @Override
    protected void onLoginFailure() {
        super.onLoginFailure();
        final String message = "Login failed";
        MnemonicActivity.this.toast(message);
        enableLogin();
    }

    class MnemonicViewAdapter extends RecyclerView.Adapter<MnemonicViewAdapter.EditTextViewHolder> implements
        View.OnKeyListener, TextView.OnEditorActionListener {

        private List<String> mData;
        private LayoutInflater mInflater;
        private ArrayAdapter<String> adapter;

        MnemonicViewAdapter(final Context context, final List<String> data) {
            mInflater = LayoutInflater.from(context);
            mData = data;
            adapter = new ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line,
                                               MnemonicHelper.mWordsArray);
        }

        @Override
        public MnemonicViewAdapter.EditTextViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            final View view = mInflater.inflate(R.layout.list_element_mnemonic, parent, false);
            final MultiAutoCompleteTextView mnemonicText = UI.find(view, R.id.mnemonicText);
            mnemonicText.setAdapter(adapter);
            mnemonicText.setThreshold(3);
            mnemonicText.setTokenizer(mTokenizer);
            mnemonicText.setOnEditorActionListener(this);
            mnemonicText.setOnKeyListener(this);
            mnemonicText.addTextChangedListener(new UI.TextWatcher() {
                @Override
                public void afterTextChanged(final Editable s) {
                    super.afterTextChanged(s);
                    final boolean isInvalid = markInvalidWord(s);
                    if (!isInvalid && s.length() > 3) {
                        nextFocus();
                        enableLogin();
                    }
                }
            });
            registerForContextMenu(mnemonicText);
            return new MnemonicViewAdapter.EditTextViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final MnemonicViewAdapter.EditTextViewHolder holder, final int position) {
            if (position > mData.size())
                return;
            holder.numericText.setText(String.valueOf(position + 1));
            holder.mnemonicText.setText(mData.get(position));
        }

        private void nextFocus() {
            final View view = MnemonicActivity.this.getCurrentFocus();
            if (!(view instanceof TextView))
                return;
            final View next = view.focusSearch(View.FOCUS_FORWARD);
            if (next != null)
                next.requestFocus();
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        public List<String> getItems() {
            return mData;
        }

        @Override
        public boolean onKey(final View view, final int keyCode, final KeyEvent keyEvent) {
            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_SPACE) {
                nextFocus();
                return true;
            }
            return false;
        }

        @Override
        public boolean onEditorAction(final TextView textView, final int actionId, final KeyEvent keyEvent) {
            if (keyEvent != null) {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER ||
                    keyEvent.getKeyCode() == KeyEvent.KEYCODE_SPACE) {
                    nextFocus();
                    return true;
                }
            }
            return false;
        }

        public class EditTextViewHolder extends RecyclerView.ViewHolder {
            public MultiAutoCompleteTextView mnemonicText;
            public TextView numericText;

            EditTextViewHolder(final View itemView) {
                super(itemView);
                mnemonicText = UI.find(itemView, R.id.mnemonicText);
                numericText = UI.find(itemView, R.id.numericText);

                mnemonicText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        mData.set(getAdapterPosition(), s.toString());
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
            }
        }
    }

}
