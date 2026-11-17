package org.libsdl.app;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class SDLDummyEdit extends View implements View.OnKeyListener
{
    InputConnection ic;
    int input_type;

    SDLDummyEdit(Context context) {
        super(context);
        setFocusableInTouchMode(true);
        setFocusable(true);
        setOnKeyListener(this);
    }

    void setInputType(int input_type) {
        this.input_type = input_type;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (!SdlImeController.isInputAccepted()) {

            SdlImeController.requestHide(SdlImeController.Source.BACK);
            return true;
        }
        return SDLActivity.handleKeyEvent(v, keyCode, event, ic);
    }

    @Override
    public boolean onKeyPreIme (int keyCode, KeyEvent event) {

        if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
            SdlImeController.requestHide(SdlImeController.Source.BACK);
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !SdlImeController.isInputAccepted()) {
            SdlImeController.requestHide(SdlImeController.Source.BACK);
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        ic = new SDLInputConnection(this, true);

        outAttrs.inputType = input_type;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                              EditorInfo.IME_FLAG_NO_FULLSCREEN ;

        return ic;
    }
}
