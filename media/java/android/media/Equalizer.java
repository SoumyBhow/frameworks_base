/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import android.media.AudioEffect;

/**
 * An Equalizer is used to alter the frequency response of a particular music source or of the main
 * output mix.
 * <p>An application creates an Equalizer object to instantiate and control an Equalizer engine
 * in the audio framework. The application can either simply use predefined presets or have a more
 * precise control of the gain in each frequency band controlled by the equalizer.
 * <p>The methods, parameter types and units exposed by the Equalizer implementation are directly
 * mapping those defined by the OpenSL ES 1.0.1 Specification (http://www.khronos.org/opensles/)
 * for the SLEqualizerItf interface. Please refer to this specification for more details.
 * <p>To attach the Equalizer to a particular AudioTrack or MediaPlayer, specify the audio session
 * ID of this AudioTrack or MediaPlayer when constructing the Equalizer. If the audio session ID 0
 * is specified, the Equalizer applies to the main audio output mix.
 // TODO when AudioEffect is unhidden
 // <p> See {_at_link android.media.AudioEffect} class for more details on controlling audio effects.
 *
 * {@hide Pending API council review}
 */

public class Equalizer extends AudioEffect {

    private final static String TAG = "Equalizer";

    // These constants must be synchronized with those in
    // frameworks/base/include/media/EffectEqualizerApi.h
    /**
     * Number of bands. Parameter ID for {@link android.media.Equalizer.OnParameterChangeListener}
     */
    public static final int PARAM_NUM_BANDS = 0;
    /**
     * Band level range. Parameter ID for OnParameterChangeListener
     */
    public static final int PARAM_LEVEL_RANGE = 1;
    /**
     * Band level. Parameter ID for OnParameterChangeListener
     */
    public static final int PARAM_BAND_LEVEL = 2;
    /**
     * Band center frequency. Parameter ID for OnParameterChangeListener
     */
    public static final int PARAM_CENTER_FREQ = 3;
    /**
     * Band frequency range. Parameter ID for OnParameterChangeListener
     */
    public static final int PARAM_BAND_FREQ_RANGE = 4;
    /**
     * Band for a given frequency. Parameter ID for OnParameterChangeListener
     */
    public static final int PARAM_GET_BAND = 5;
    /**
     * Current preset. Parameter ID for OnParameterChangeListener
     */
    public static final int PARAM_CURRENT_PRESET = 6;
    /**
     * Request number of presets. Parameter ID for OnParameterChangeListener
     */
    public static final int PARAM_GET_NUM_OF_PRESETS = 7;
    /**
     * Request preset name. Parameter ID for OnParameterChangeListener
     */
    public static final int PARAM_GET_PRESET_NAME = 8;
    /**
     * maximum size for perset name
     */
    public static final int PARAM_STRING_SIZE_MAX = 32;

    /**
     * Number of presets implemented by Equalizer engine
     */
    private int mNumPresets;
    /**
     * Names of presets implemented by Equalizer engine
     */
    private String[] mPresetNames;

    /**
     * Registered listener for parameter changes.
     */
    private OnParameterChangeListener mParamListener = null;

    /**
     * Listener used internally to to receive raw parameter change event from AudioEffect super class
     */
    private BaseParameterListener mBaseParamListener = null;

    /**
     * Lock for access to mParamListener
     */
    private final Object mParamListenerLock = new Object();

    /**
     * Class constructor.
     * @param priority the priority level requested by the application for controlling the Equalizer
     * engine. As the same engine can be shared by several applications, this parameter indicates
     * how much the requesting application needs control of effect parameters. The normal priority
     * is 0, above normal is a positive number, below normal a negative number.
     * @param audioSession  System wide unique audio session identifier. If audioSession
     *  is not 0, the Equalizer will be attached to the MediaPlayer or AudioTrack in the
     *  same audio session. Otherwise, the Equalizer will apply to the output mix.
     *
     * @throws java.lang.IllegalStateException
     * @throws java.lang.IllegalArgumentException
     * @throws java.lang.UnsupportedOperationException
     * @throws java.lang.RuntimeException
     */
    public Equalizer(int priority, int audioSession)
    throws IllegalStateException, IllegalArgumentException,
           UnsupportedOperationException, RuntimeException {
        super(EFFECT_TYPE_EQUALIZER, EFFECT_TYPE_NULL, priority, audioSession);

        mNumPresets = (int)getNumberOfPresets();

        if (mNumPresets != 0) {
            mPresetNames = new String[mNumPresets];
            byte[] value = new byte[PARAM_STRING_SIZE_MAX];
            int[] param = new int[2];
            param[0] = PARAM_GET_PRESET_NAME;
            for (int i = 0; i < mNumPresets; i++) {
                param[1] = i;
                checkStatus(getParameter(param, value));
                int length = 0;
                while (value[length] != 0) length++;
                try {
                    mPresetNames[i] = new String(value, 0, length, "ISO-8859-1");
                    Log.e(TAG, "preset #: "+i+" name: "+mPresetNames[i]+" length: "+length);
                } catch (java.io.UnsupportedEncodingException e) {
                    Log.e(TAG, "preset name decode error");
                }
            }
        }
    }

    /**
     * Gets the number of frequency bands supported by the Equalizer engine.
     * @return the number of bands
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public short getNumberOfBands()
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] param = new int[1];
        param[0] = PARAM_NUM_BANDS;
        short[] value = new short[1];
        checkStatus(getParameter(param, value));
        return value[0];
    }

    /**
     * Gets the level range for use by {@link #setBandLevel(int,short)}. The level is expressed in
     * milliBel.
     * @return the band level range in an array of short integers. The first element is the lower
     * limit of the range, the second element the upper limit.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public short[] getBandLevelRange()
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] param = new int[1];
        int[] value = new int[2];
        param[0] = PARAM_LEVEL_RANGE;
        checkStatus(getParameter(param, value));

        short[] result = new short[2];

        result[0] = (short)value[0];
        result[1] = (short)value[1];

        return result;
    }

    /**
     * Sets the given equalizer band to the given gain value.
     * @param band Frequency band that will have the new gain. The numbering of the bands starts
     * from 0 and ends at (number of bands - 1). See @see #getNumberOfBands().
     * @param level New gain in millibels that will be set to the given band. getBandLevelRange()
     * will define the maximum and minimum values.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public void setBandLevel(int band, short level)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] param = new int[2];
        int[] value = new int[1];

        param[0] = PARAM_BAND_LEVEL;
        param[1] = band;
        value[0] = (int)level;
        checkStatus(setParameter(param, value));
    }

    /**
     * Gets the gain set for the given equalizer band.
     * @param band Frequency band whose gain is requested. The numbering of the bands starts
     * from 0 and ends at (number of bands - 1).
     * @return Gain in millibels of the given band.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public short getBandLevel(int band)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] param = new int[2];
        int[] result = new int[1];

        param[0] = PARAM_BAND_LEVEL;
        param[1] = band;
        checkStatus(getParameter(param, result));

        return (short)result[0];
    }


    /**
     * Gets the center frequency of the given band.
     * @param band Frequency band whose center frequency is requested. The numbering of the bands
     * starts from 0 and ends at (number of bands - 1).
     * @return The center frequency in milliHertz
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public int getCenterFreq(int band)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] param = new int[2];
        int[] result = new int[1];

        param[0] = PARAM_CENTER_FREQ;
        param[1] = band;
        checkStatus(getParameter(param, result));

        return result[0];
    }

    /**
     * Gets the frequency range of the given frequency band.
     * @param band Frequency band whose frequency range is requested. The numbering of the bands
     * starts from 0 and ends at (number of bands - 1).
     * @return The frequency range in millHertz in an array of integers. The first element is the
     * lower limit of the range, the second element the upper limit.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public int[] getBandFreqRange(int band)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] param = new int[2];
        int[] result = new int[2];
        param[0] = PARAM_BAND_FREQ_RANGE;
        param[1] = band;
        checkStatus(getParameter(param, result));

        return result;
    }

    /**
     * Gets the band that has the most effect on the given frequency.
     * @param frequency Frequency in milliHertz which is to be equalized via the returned band.
     * @return Frequency band that has most effect on the given frequency.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public int getBand(int frequency)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] param = new int[2];
        int[] result = new int[1];

        param[0] = PARAM_GET_BAND;
        param[1] = frequency;
        checkStatus(getParameter(param, result));

        return result[0];
    }

    /**
     * Gets current preset.
     * @return Preset that is set at the moment.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public short getCurrentPreset()
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] param = new int[1];
        param[0] = PARAM_CURRENT_PRESET;
        short[] value = new short[1];
        checkStatus(getParameter(param, value));
        return value[0];
    }

    /**
     * Sets the equalizer according to the given preset.
     * @param preset New preset that will be taken into use. The valid range is [0,
     * number of presets-1]. See {@see #getNumberOfPresets()}.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public void usePreset(short preset)
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkStatus(setParameter(PARAM_CURRENT_PRESET, preset));
    }

    /**
     * Gets the total number of presets the equalizer supports. The presets will have indices
     * [0, number of presets-1].
     * @return The number of presets the equalizer supports.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public short getNumberOfPresets()
    throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        int[] param = new int[1];
        param[0] = PARAM_GET_NUM_OF_PRESETS;
        short[] value = new short[1];
        checkStatus(getParameter(param, value));
        return value[0];
    }

    /**
     * Gets the preset name based on the index.
     * @param preset Index of the preset. The valid range is [0, number of presets-1].
     * @return A string containing the name of the given preset.
     * @throws IllegalStateException
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    public String getPresetName(short preset)
    {
        if (preset >= 0 && preset < mNumPresets) {
            return mPresetNames[preset];
        } else {
            return "";
        }
    }

    /**
     * The OnParameterChangeListener interface defines a method called by the Equalizer when a
     * parameter value has changed.
     */
    public interface OnParameterChangeListener  {
        /**
         * Method called when a parameter value has changed. The method is called only if the
         * parameter was changed by another application having the control of the same
         * Equalizer engine.
         * @param effect the Equalizer on which the interface is registered.
         * @param status status of the set parameter operation.
         // TODO when AudioEffect is unhidden
         // See {_at_link android.media.AudioEffect#setParameter(byte[], byte[])}.
         * @param param1 ID of the modified parameter. See {@link #PARAM_BAND_LEVEL} ...
         * @param param2 additional parameter qualifier (e.g the band for band level parameter).
         * @param value the new parameter value.
         */
        void onParameterChange(Equalizer effect, int status, int param1, int param2, int value);
    }

    /**
     * Listener used internally to receive unformatted parameter change events from AudioEffect
     * super class.
     */
    private class BaseParameterListener implements AudioEffect.OnParameterChangeListener {
        private BaseParameterListener() {

        }
        public void onParameterChange(AudioEffect effect, int status, byte[] param, byte[] value) {
            OnParameterChangeListener l = null;

            synchronized (mParamListenerLock) {
                if (mParamListener != null) {
                    l = mParamListener;
                }
            }
            if (l != null) {
                int p1 = -1;
                int p2 = -1;
                int v = -1;

                if (param.length >= 4) {
                    p1 = byteArrayToInt(param, 0);
                    if (param.length >= 8) {
                        p2 = byteArrayToInt(param, 4);
                    }
                }
                if (value.length == 2) {
                    v = (int)byteArrayToShort(value, 0);;
                } else if (value.length == 4) {
                    v = byteArrayToInt(value, 0);
                }

                if (p1 != -1 && v != -1) {
                    l.onParameterChange(Equalizer.this, status, p1, p2, v);
                }
            }
        }
    }

    /**
     * Registers an OnParameterChangeListener interface.
     * @param listener OnParameterChangeListener interface registered
     */
    public void setParameterListener(OnParameterChangeListener listener) {
        synchronized (mParamListenerLock) {
            if (mParamListener == null) {
                mParamListener = listener;
                mBaseParamListener = new BaseParameterListener();
                super.setParameterListener(mBaseParamListener);
            }
        }
    }

}
