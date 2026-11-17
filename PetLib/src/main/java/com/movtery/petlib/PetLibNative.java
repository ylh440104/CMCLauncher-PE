package com.movtery.petlib;

import android.graphics.Bitmap;

final class PetLibNative {
    static {
        System.loadLibrary("petlib");
    }

    private PetLibNative() {
    }

    static native long nativeCreate(byte[] skin, int arm);

    static native void nativeDestroy(long ptr);

    static native String nativeSetSkin(long ptr, byte[] skin, int arm);

    static native String nativeSetCape(long ptr, byte[] cape);

    static native void nativeSetLighting(long ptr, float azimuth, float elevation, float brightness, float softness);

    static native void nativeSetSuperSample(long ptr, float value);

    static native void nativeSetOuterLayer(long ptr, boolean enabled);

    static native void nativeSetTau(long ptr, float gaze, float yaw, float anim, float recenter);

    static native void nativeSetHeadRange(long ptr, float x, float y);

    static native void nativeSetSquashDepth(long ptr, float depth);

    static native void nativeSetGaze(long ptr, float x, float y, boolean hasTarget);

    static native void nativeSetYaw(long ptr, float yaw);

    static native float nativeGetYaw(long ptr);

    static native void nativePlayAnimation(long ptr, int id, float speed, boolean headFollow);

    static native void nativeStopAnimation(long ptr);

    static native void nativeSquashTap(long ptr, float now);

    static native void nativePlaySwing(long ptr, float now);

    static native void nativePlayInteract(long ptr);

    static native void nativeRender(long ptr, int width, int height, float now, float dt, Bitmap bitmap);

    static native boolean nativeHitTest(long ptr, float x, float y);
}
