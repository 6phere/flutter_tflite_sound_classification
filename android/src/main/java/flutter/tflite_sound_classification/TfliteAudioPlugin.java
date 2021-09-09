package flutter.tflite_sound_classification;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.AssetFileDescriptor;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Looper;
import android.os.Handler;

import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;

import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.task.audio.classifier.Classifications;
import org.tensorflow.lite.support.label.Category;

import java.util.concurrent.CompletableFuture; //required to get value from thread
import java.util.concurrent.CountDownLatch;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Date;
import java.util.stream.Collectors;
import java.lang.Long;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry; //required for onRequestPermissionsResult
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;


public class TfliteAudioPlugin implements MethodCallHandler, StreamHandler, PluginRegistry.RequestPermissionsResultListener {

    //ui elements
    private static final String LOG_TAG = "tflite_sound_classification";
    private static final int REQUEST_RECORD_AUDIO = 13;
    private static final float MINIMUM_DISPLAY_THRESHOLD = 0.3f;
    private Handler handler = new Handler(Looper.getMainLooper());

    //working recording variables
    AudioRecord record;
    boolean shouldContinue = true;
    private Thread recordingThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();
    private long classificationInterval;
    //working label variables
    private List<String> labels;

    //working recognition variables
    private AudioClassifier audioClassifier = null;
    private TensorAudio audioTensor = null;

    //flutter
    private final Registrar registrar;
    private HashMap arguments;
    private Result result;
    private EventSink events;


    //initialises register variable with a constructor
    private TfliteAudioPlugin(Registrar registrar) {
        this.registrar = registrar;
    }

    public static void registerWith(Registrar registrar) {
        TfliteAudioPlugin tfliteAudioPlugin = new TfliteAudioPlugin(registrar);

        final MethodChannel channel = new MethodChannel(registrar.messenger(), "tflite_sound_classification");
        channel.setMethodCallHandler(tfliteAudioPlugin);

        final EventChannel eventChannel = new EventChannel(registrar.messenger(), "startAudioRecognition");
        eventChannel.setStreamHandler(tfliteAudioPlugin);

        registrar.addRequestPermissionsResultListener(tfliteAudioPlugin);
    }


    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        this.arguments = (HashMap) call.arguments;
        this.result = result;
        switch (call.method) {
            case "loadModel":
                Log.d(LOG_TAG, "loadModel");
                try {
                    loadModel();
                } catch (Exception e) {
                    result.error("failed to load model", e.getMessage(), e);
                }
                break;
            case "stopAudioRecognition":
                forceStopRecogniton();
                break;
            default:
                result.notImplemented();
                break;
        }
    }


    @Override
    public void onListen(Object arguments, EventSink events) {
        this.events = events;
        this.arguments = (HashMap) arguments;
        checkPermissions();
    }

    @Override
    public void onCancel(Object arguments) {
        this.events = null;
    }


    private void loadModel() throws IOException {
        String model = arguments.get("model").toString();
        Log.d(LOG_TAG, "model name is: " + model);
        MappedByteBuffer buffer = null;
        String key = registrar.lookupKeyForAsset(model);
        AssetManager assetManager = registrar.context().getAssets();
        AssetFileDescriptor fileDescriptor = assetManager.openFd(key);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

        audioClassifier = AudioClassifier.createFromBuffer(buffer);
        audioTensor = audioClassifier.createInputTensorAudio();

        //load labels
        String labels = arguments.get("label").toString();
        Log.d(LOG_TAG, "label name is: " + labels);

        // classification interval
        String classificationIntervalStr = arguments.get("classificationInterval").toString();
        if(classificationIntervalStr=="") classificationIntervalStr = "500";
        classificationInterval = Long.parseLong(classificationIntervalStr);

        if (labels.length() > 0) {
            key = registrar.lookupKeyForAsset(labels);
            loadLabels(assetManager, key);
        }
    }

    private void loadLabels(AssetManager assetManager, String path) {
        BufferedReader br;
        try {
            if (assetManager != null) {
                br = new BufferedReader(new InputStreamReader(assetManager.open(path)));
            } else {
                br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path))));
            }
            String line;
            labels = new ArrayList<>(); //resets label input
            while ((line = br.readLine()) != null) {
                labels.add(line);
            }
            Log.d(LOG_TAG, "Labels: " + labels.toString());
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read label file", e);
        }

    }


    private void checkPermissions() {
        //int hasStoragePerm = pm.checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, context.getPackageName());
        //        boolean hasPermissions = hasStoragePerm == PackageManager.PERMISSION_GRANTED
//                && hasRecordPerm == PackageManager.PERMISSION_GRANTED;
        Log.d(LOG_TAG, "Check for permissions");
        Context context = registrar.context();
        PackageManager pm = context.getPackageManager();
        int hasRecordPerm = pm.checkPermission(Manifest.permission.RECORD_AUDIO, context.getPackageName());
        boolean hasPermissions = hasRecordPerm == PackageManager.PERMISSION_GRANTED;
        if (hasPermissions) {
            startRecording();
            Log.d(LOG_TAG, "Permission already granted. start recording");
        } else {
            requestMicrophonePermission();
        }
    }

    private void requestMicrophonePermission() {
        Log.d(LOG_TAG, "Permission requested.");
        Activity activity = registrar.activity();
        ActivityCompat.requestPermissions(activity,
                new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    }

    @Override
    public boolean onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        //if request is cancelled, result arrays will be empty
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
            Log.d(LOG_TAG, "Permission granted. Start recording...");
        } else {
            showRationaleDialog(
                    "Microphone Permissions",
                    "Permission has been declined. Please accept permissions in your settings"
            );
            //return false for hasPermission
            Map<String, Object> finalResults = new HashMap();
            finalResults.put("recognitionResult", null);
            finalResults.put("hasPermission", false);
            if (events != null) {
                events.success(finalResults);
                events.endOfStream();
            }
        }
        return true;
    }


    public void showRationaleDialog(String title, String message) {

        runOnUIThread(() -> {
            Activity activity = registrar.activity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(title);
            builder.setMessage(message);
            builder.setPositiveButton(
                    "Settings",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + activity.getPackageName()));
                            intent.addCategory(Intent.CATEGORY_DEFAULT);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(intent);
                        }
                    });
            builder.setNegativeButton(
                    "Cancel",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        });

    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
    }

    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        record = audioClassifier.createAudioRecord();

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            return;
        }

        record.startRecording();

        Log.v(LOG_TAG, "Recording started");


        while (shouldContinue) {
            //Reads audio data and records it into redcordFrame
            audioTensor.load(record);

            recordingBufferLock.lock();
            try {
                Classifications output = audioClassifier.classify(audioTensor).get(0);
                List<Map<String,Object>> categoryList = output.getCategories().stream().filter(category -> category.getScore() > MINIMUM_DISPLAY_THRESHOLD).map(
                        category -> new HashMap<String, Object>() {{
                            put("label", category.getLabel());
                            put("score", category.getScore());
                        }}
                ).collect(Collectors.toList());
                //Map score and inference time
                Map<String, Object> finalResults = new HashMap();
                finalResults.put("recognitionResult", categoryList);
                finalResults.put("hasPermission", true);

                getResult(finalResults);

                Thread.sleep(classificationInterval);

            }catch(InterruptedException ex){
                Log.e(LOG_TAG, "InterruptedException: " + ex.toString());
            } finally {
                recordingBufferLock.unlock();

            }
        }
    }

    //passes map to from platform to flutter.
    public void getResult(Map<String, Object> recognitionResult) {

        //passing data from platform to flutter requires ui thread
        runOnUIThread(() -> {
            if (events != null) {
                Log.v(LOG_TAG, "result: " + recognitionResult.toString());
                events.success(recognitionResult);
            }
        });
    }

    public void stopRecording() {

        if (recordingThread == null || shouldContinue == false) {
            Log.d(LOG_TAG, "Recording has already stopped. Breaking stopRecording()");
            return;
        }

        shouldContinue = false;

        record.stop();
        record.release();

        recordingThread = null;//closes recording
        Log.d(LOG_TAG, "Recording stopped.");
    }

    public void forceStopRecogniton() {

        stopRecording();

        //passing data from platform to flutter requires ui thread
        runOnUIThread(() -> {
            if (events != null) {
                Log.d(LOG_TAG, "Recognition Stream stopped");
                events.endOfStream();
            }
        });
    }


    private void runOnUIThread(Runnable runnable) {
        if (Looper.getMainLooper() == Looper.myLooper())
            runnable.run();
        else
            handler.post(runnable);
    }

}


