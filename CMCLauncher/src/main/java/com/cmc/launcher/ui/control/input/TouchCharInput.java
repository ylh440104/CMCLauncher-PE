package com.cmc.launcher.ui.control.input;

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cmc.launcher.game.sdl.SdlBridge;

import org.libsdl.app.SDLActivity;

public class TouchCharInput extends androidx.appcompat.widget.AppCompatEditText {
    public static final String TEXT_FILLER = "                              ";
    public TouchCharInput(@NonNull Context context) {
        this(context, null);
    }
    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.editTextStyle);
    }
    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    private static TouchCharInput sActiveInput;
    private boolean mIsDoingInternalChanges = false;
    private InputListener mListener;

    public void enableKeyboard() {

        if (SdlBridge.getSdlEnabled() && SdlBridge.isSdlRenderActive()) {
            SDLActivity.enableSDLEditKeyboard();
            return;
        }
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
        enable();
        sActiveInput = this;
        SdlBridge.requestComposeFocus();
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        clear();
    }

    public static void disableActiveInput() {
        if (sActiveInput != null) {
            sActiveInput.disableKeyboard();
        }
    }

    public static boolean isActive() {
        return sActiveInput != null;
    }

    public void disableKeyboard() {

        if (SdlBridge.getSdlEnabled() && SdlBridge.isSdlRenderActive()) {
            SDLActivity.disableSDLEditKeyboard();
            if (sActiveInput == this) {
                sActiveInput = null;
            }
            return;
        }
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
        clearFocus();
        setVisibility(GONE);
        setEnabled(false);
        if (sActiveInput == this) {
            sActiveInput = null;
        }
        SdlBridge.requestComposeFocus();
    }

    public void clear() {
        mIsDoingInternalChanges = true;
        setText(TEXT_FILLER);
        setSelection(TEXT_FILLER.length());
        mIsDoingInternalChanges = false;
    }

    private void enable(){
        setEnabled(true);
        setFocusable(true);
        setVisibility(VISIBLE);
        requestFocus();
    }

    public void setListener(InputListener listener){
        mListener = listener;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            disableKeyboard();
        }
        return super.onKeyPreIme(keyCode, event);
    }

    private void setup() {
        setOnEditorActionListener((textView, i, keyEvent) -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
            if (mListener != null) {
                mListener.onEnter();
            }
            clear();
            return false;
        });
        clear();
        disableKeyboard();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (mIsDoingInternalChanges)
            return;
        if (mListener != null) {
            for (int i = 0; i < lengthBefore; ++i) {
                mListener.onBackspace();
            }

            for (int i = start, count = 0; count < lengthAfter; ++i) {
                mListener.onSend(text.charAt(i));
                ++count;
            }
        }

        if (text.length() < 1)
            clear();
    }
}
