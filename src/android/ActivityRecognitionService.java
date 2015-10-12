package com.red_folder.phonegap.plugin.backgroundservice.sample;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import android.os.HandlerThread;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import de.greenrobot.event.EventBus;


/**
 * Created by jsnavely on 1/2/15.
 */
public class ActivityRecognitionService extends IntentService {
    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */

    private Motion lastMotion = Motion.UNKNOWN;

    private EventBus eventBus = EventBus.getDefault();
    private String name;
    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    public ActivityRecognitionService(String name) {
        super(name);
        this.name = name;
        Log.d("where", "ActivityRecognitionService constructed");
    }

    public ActivityRecognitionService() {
        super("ActivityRecognitionService");
        Log.d("where", "ActivityRecognitionService constructed");
    }


    @Override
    public void onCreate() {
        super.onCreate();

        HandlerThread thread = new HandlerThread("IntentService[" + name + "]");
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    protected void onHandleIntent(Intent intent) {

        // If the incoming intent contains an update
        if (ActivityRecognitionResult.hasResult(intent)) {

            // Get the update
            ActivityRecognitionResult result =
                    ActivityRecognitionResult.extractResult(intent);
            // Get the most probable activity
            DetectedActivity mostProbableActivity =
                    result.getMostProbableActivity();
            /*
             * Get the probability that this activity is the
             * the user's actual activity
             */
            int confidence = mostProbableActivity.getConfidence();
            /*
             * Get an integer describing the type of activity
             */
            int activityType = mostProbableActivity.getType();
            Motion motion = getNameFromType(activityType);

            if (!lastMotion.equals(motion)) {
                eventBus.post(new ActivityMessageEvent(motion.toString(), confidence, System.currentTimeMillis()));

                reportMotion(motion);
                lastMotion = motion;
                Log.d("where", "intent has a recognition result: " + motion.toString());
                Log.d("where", "confidence: " + confidence);
            }


        }
    }

    private Motion getNameFromType(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return Motion.VEHICLE;
            case DetectedActivity.ON_BICYCLE:
                return Motion.BICYCLE;
            case DetectedActivity.ON_FOOT:
                return Motion.WALKING;
            case DetectedActivity.STILL:
                return Motion.STILL;
            case DetectedActivity.UNKNOWN:
                return Motion.UNKNOWN;
            case DetectedActivity.TILTING:
                return Motion.TILTING;
            default:
                return Motion.UNKNOWN;
        }
    }

    private void reportMotion(Motion motion) {
        Log.d("where", "confidence: " + motion);




        /*
        final String url = "http://192.168.1.110:5000/report";
        final JSONObject body = new JSONObject();
        try {
            body.put("motion", motion.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JsonObjectRequest req = new JsonObjectRequest(url, body,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            VolleyLog.v("Response:%n %s", response.toString(4));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                VolleyLog.e("Error: ", error.getMessage());
            }
        });
        volleyQueue.add(req);
        */
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent) msg.obj);
        }
    }


}
