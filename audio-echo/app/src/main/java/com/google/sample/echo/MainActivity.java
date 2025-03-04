/*
 * Copyright 2018 The Android Open Source Project
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

package com.google.sample.echo;

import static android.media.AudioManager.GET_DEVICES_INPUTS;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.input.InputManager;
import android.media.AudioDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioProfile;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaRouter;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Audio.Media;
import android.util.Log;
import android.view.InputDevice;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.media.AudioDeviceInfo;

public class MainActivity extends Activity
        implements ActivityCompat.OnRequestPermissionsResultCallback {
    public static final String TAG = "bugbug";


    private static final int AUDIO_ECHO_REQUEST = 0;

    private Button   controlButton;
    private TextView statusView;
    private String  nativeSampleRate;
    private String  nativeSampleBufSize;

    private SeekBar delaySeekBar;
    private TextView curDelayTV;
    private int echoDelayProgress;

    private SeekBar decaySeekBar;
    private TextView curDecayTV;
    private float echoDecayProgress;

    private boolean supportRecording;
    private Boolean isPlaying = false;

    private boolean ssl_created = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      setVolumeControlStream(AudioManager.STREAM_MUSIC);

      super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        controlButton = (Button)findViewById((R.id.capture_control_button));
        statusView = (TextView)findViewById(R.id.statusView);
        queryNativeAudioParameters();

        delaySeekBar = (SeekBar)findViewById(R.id.delaySeekBar);
        curDelayTV = (TextView)findViewById(R.id.curDelay);
        echoDelayProgress = delaySeekBar.getProgress() * 1000 / delaySeekBar.getMax();
        delaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float curVal = (float)progress / delaySeekBar.getMax();
                curDelayTV.setText(String.format("%s", curVal));
                setSeekBarPromptPosition(delaySeekBar, curDelayTV);
                if (!fromUser) return;

                echoDelayProgress = progress * 1000 / delaySeekBar.getMax();
                configureEcho(echoDelayProgress, echoDecayProgress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        delaySeekBar.post(new Runnable() {
            @Override
            public void run() {
                setSeekBarPromptPosition(delaySeekBar, curDelayTV);
            }
        });

        decaySeekBar = (SeekBar)findViewById(R.id.decaySeekBar);
        curDecayTV = (TextView)findViewById(R.id.curDecay);
        echoDecayProgress = (float)decaySeekBar.getProgress() / decaySeekBar.getMax();
        decaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float curVal = (float)progress / seekBar.getMax();
                curDecayTV.setText(String.format("%s", curVal));
                setSeekBarPromptPosition(decaySeekBar, curDecayTV);
                if (!fromUser)
                    return;

                echoDecayProgress = curVal;
                configureEcho(echoDelayProgress, echoDecayProgress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        decaySeekBar.post(new Runnable() {
            @Override
            public void run() {
                setSeekBarPromptPosition(decaySeekBar, curDecayTV);
            }
        });

        // initialize native audio system
        updateNativeAudioUI();

        if (supportRecording) {
           //maybecreatessl();
        }
    }

    private void maybecreatessl() {
      if(!ssl_created) {
        createSLEngine(
            16000, ///Integer.parseInt(nativeSampleRate),
            128, //Integer.parseInt(nativeSampleBufSize),
            echoDelayProgress,
            echoDecayProgress);
        ssl_created = true;
      }
    }
    private void maybedestroyssl() {
      if(ssl_created) {
        deleteSLEngine();
        ssl_created = false;
      }
    }

    private void setSeekBarPromptPosition(SeekBar seekBar, TextView label) {
        float thumbX = (float)seekBar.getProgress()/ seekBar.getMax() *
                              seekBar.getWidth() + seekBar.getX();
        label.setX(thumbX - label.getWidth()/2.0f);
    }

    @Override
    protected void onDestroy() {
        if (supportRecording) {
            if (isPlaying) {
                stopPlay();
            }
            maybedestroyssl();
            isPlaying = false;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setMicInputSource() {
      AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
      //audioManager.setParameters("input_source=6");
      Log.i(TAG, "params: " + audioManager.getParameters("input_source;routing"));
      Log.i(TAG, "---- > SETT MIC INPUT");
    }
    private void startEcho() {
        if(!supportRecording){
            return;
        }
      isMicrophoneDisconnected();

      setMicInputSource();
        //ensureMicRouting();
      maybecreatessl();
        if (!isPlaying) {
            if(!createSLBufferQueueAudioPlayer()) {
                statusView.setText(getString(R.string.player_error_msg));
                return;
            }
            if(!createAudioRecorder()) {
                deleteSLBufferQueueAudioPlayer();
                statusView.setText(getString(R.string.recorder_error_msg));
                return;
            }
            startPlay();   // startPlay() triggers startRecording()
            statusView.setText(getString(R.string.echoing_status_msg));
        } else {
            stopPlay();  // stopPlay() triggers stopRecording()
            updateNativeAudioUI();
            deleteAudioRecorder();
            deleteSLBufferQueueAudioPlayer();
        }
        isPlaying = !isPlaying;
        controlButton.setText(getString(isPlaying ?
                R.string.cmd_stop_echo: R.string.cmd_start_echo));
    }
    public void onEchoClick(View view) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                                               PackageManager.PERMISSION_GRANTED) {
            statusView.setText(getString(R.string.request_permission_status_msg));
            ActivityCompat.requestPermissions(
                    this,
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    AUDIO_ECHO_REQUEST);
            return;
        }
        startEcho();
    }

    public void getLowLatencyParameters(View view) {
        updateNativeAudioUI();
    }

  public boolean isMicrophoneDisconnected() {
    // A check specifically for microphones is not available before API 28, so it is assumed that a
    // connected input audio device is a microphone.
    AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    AudioDeviceInfo[] devices = audioManager.getDevices(GET_DEVICES_INPUTS);
    if (devices.length > 0) {
      //return false;
    }

    // fallback to check for BT voice capable RCU
    InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
    final int[] inputDeviceIds = inputManager.getInputDeviceIds();
    for (int inputDeviceId : inputDeviceIds) {
      final InputDevice inputDevice = inputManager.getInputDevice(inputDeviceId);
      final boolean hasMicrophone = inputDevice.hasMicrophone();
      if (hasMicrophone) {
        return false;
      }
    }
    return true;
  }



    private void ensureMicRouting() {
        MediaRouter router = (MediaRouter ) getSystemService(Context.MEDIA_ROUTER_SERVICE);
        Log.i(TAG," Num routes: " + router.getRouteCount());

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setBluetoothScoOn(false);
        audioManager.stopBluetoothSco();
        audioManager.setMicrophoneMute(false);
        //audioManager.setAllowedCapturePolicy();
        //audioManager.setRouting();
      AudioDeviceInfo[] devices = null;
      if (VERSION.SDK_INT >= VERSION_CODES.M) {
        devices = audioManager.getDevices(GET_DEVICES_INPUTS);
          for (AudioDeviceInfo device : devices) {
              Log.i(TAG, " input: " + device.getProductName());
              Log.i(TAG, " type: " + device.getType());
              Log.i(TAG, " id: " + device.getId());
                    if (
                         device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                         device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    // Found a Bluetooth device (BLE or classic)
                    Log.i("AudioEcho", "Selecting Bluetooth input: " + device.getProductName());
                    //audioManager.setPreferredDeviceForStrategy(device); // API 23+
                    break;
                }
                    if(device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                        Log.i("AudioEcho", "Selecting TYPE_BUILTIN_MIC input: " + device.getProductName());

                        AudioRecord recorder = new AudioRecord.Builder()
                            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                            .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(8000)
                                .build())
                            .setBufferSizeInBytes(2*128)
                            .build();
                        recorder.setPreferredDevice(device);
                        recorder.startRecording();
                        recorder.stop();
                        Log.i(TAG,"---------------- DONE record start");
                    }
            }
      }
    }

    private void queryNativeAudioParameters() {
        Log.i(TAG,"queryNativeAudioParameters");
        supportRecording = true;
        AudioManager myAudioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if(myAudioMgr == null) {
            Log.i(TAG,"Recording not supported 1");
            supportRecording = false;
            return;
        }
        boolean mute = myAudioMgr.isMicrophoneMute();
        Log.i(TAG, "Mic mute ? " + mute);

        nativeSampleRate  =  myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        //nativeSampleRate = "8000";
        nativeSampleRate = "16000";
        nativeSampleBufSize =myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        //nativeSampleBufSize = "128";


        // hardcoded channel to mono: both sides -- C++ and Java sides
        int recBufSize = AudioRecord.getMinBufferSize(
                Integer.parseInt(nativeSampleRate),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (recBufSize == AudioRecord.ERROR ||
                recBufSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.i(TAG,"Recording not supported 2");
            supportRecording = false;
        }

        // Hacky code
      if (VERSION.SDK_INT >= VERSION_CODES.M) {
          AudioDeviceInfo[] devices = myAudioMgr.getDevices(GET_DEVICES_INPUTS);
          if (devices.length > 0) {
              Log.i(TAG, "More than one GET_DEVICES_INPUTS");
              for (AudioDeviceInfo dev: devices) {
                  Log.i(TAG,"Device: " + dev.toString());
                  Log.i(TAG, "[prodname]: " + dev.getProductName());
                    if (VERSION.SDK_INT >= VERSION_CODES.P) {
                      Log.i(TAG, "address:" + dev.getAddress() );
                    }
                    Log.i(TAG, "ID:" + dev.getId());
                if (VERSION.SDK_INT >= VERSION_CODES.S) {
                  for(AudioProfile prof: dev.getAudioProfiles()) {
                      Log.i(TAG, "prof:" + prof.toString());
                  }
                }
                if (VERSION.SDK_INT >= VERSION_CODES.S) {
                  for(AudioDescriptor desc: dev.getAudioDescriptors()) {
                      Log.i(TAG, "desc:" + desc.toString());
                  }
                }
              }
          }
      }


    }
    private void updateNativeAudioUI() {
        if (!supportRecording) {
            statusView.setText(getString(R.string.mic_error_msg));
            controlButton.setEnabled(false);
            return;
        }

        statusView.setText(getString(R.string.fast_audio_info_msg,
                nativeSampleRate, nativeSampleBufSize));
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        /*
         * if any permission failed, the sample could not play
         */
        if (AUDIO_ECHO_REQUEST != requestCode) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 1  ||
            grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            /*
             * When user denied permission, throw a Toast to prompt that RECORD_AUDIO
             * is necessary; also display the status on UI
             * Then application goes back to the original state: it behaves as if the button
             * was not clicked. The assumption is that user will re-click the "start" button
             * (to retry), or shutdown the app in normal way.
             */
            statusView.setText(getString(R.string.permission_error_msg));
            Toast.makeText(getApplicationContext(),
                    getString(R.string.permission_prompt_msg),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        /*
         * When permissions are granted, we prompt the user the status. User would
         * re-try the "start" button to perform the normal operation. This saves us the extra
         * logic in code for async processing of the button listener.
         */
        statusView.setText(getString(R.string.permission_granted_msg,getString(R.string.cmd_start_echo)));


        // The callback runs on app's thread, so we are safe to resume the action
        startEcho();
    }

    /*
     * Loading our lib
     */
    static {
        System.loadLibrary("echo");
    }

    /*
     * jni function declarations
     */
    static native void createSLEngine(int rate, int framesPerBuf,
                                      long delayInMs, float decay);
    static native void deleteSLEngine();
    static native boolean configureEcho(int delayInMs, float decay);
    static native boolean createSLBufferQueueAudioPlayer();
    static native void deleteSLBufferQueueAudioPlayer();

    static native boolean createAudioRecorder();
    static native void deleteAudioRecorder();
    static native void startPlay();
    static native void stopPlay();
}
