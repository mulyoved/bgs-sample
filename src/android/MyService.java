package com.red_folder.phonegap.plugin.backgroundservice.sample;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;
import android.annotation.TargetApi;
import android.R;


import android.content.Context;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.util.Log;

import com.red_folder.phonegap.plugin.backgroundservice.BackgroundService;

import de.greenrobot.event.EventBus;

public class MyService extends BackgroundService {
    public Context context = this;
	private EventBus eventBus = EventBus.getDefault();

	private final static String TAG = MyService.class.getSimpleName();

    private int notif_id=100;
    private String notificationTitle = "App Service";
    private String notificationText = "Running";

	private String mHelloTo = "World";
	private boolean mReportRealTime = false;
	private boolean mReportStatusChange = false;
    private long mReportStatusChangeStartTime = 0;

	private SensorManager mSensorManager;
	private float acceleration = 0;
	private float mStddev;
	private float mAvg;
	private int mStatus = 0;
	private String activityDetection_status = "unknown";
	private int activityDetection_confidence = 0;
	private long activityDetection_time = 0;
	private float mAvgSampleRate = 0;
	private long mSampleCount = 0;
	private float mStepCounter = 0;
	private int mStep = 0;
    private int mStepWindow = 0;

	private String mActivityStat = "Unknown";
	private float mActivityConfidence = 0f;
	private long mActivityLastUpdate = 0;

	List<AccelomterReadingRaw> accelerometerReadingList = new ArrayList<AccelomterReadingRaw>();


	private static final int ACCELEROMETER_READ_MAXSIZE = 2400;
	private int accelerometerRawBackMs = 1000 * 60; // 1 min configurable
	private int accelerometerReadBackMs = 1000 * 12;
	private float walkMinStddev = 1.2f;
	private float walkMinAvg = 1.2f;
	private float prevx;
	private float prevy;
	private float prevz;
	private float lowPassFilter = 0.1f;
	private float highPassFilter = 7f;
	private boolean hasPrevValue = false;

    private int stepCountTrigger = 10;
    private int stepCountWindow = 30000;

	List<AccelomterReading> accelerometerData = new ArrayList<AccelomterReading>(ACCELEROMETER_READ_MAXSIZE);

	public class AccelomterReading {
		public float value;
		public long time;
	}


	public class AccelomterReadingRaw {
		float x;
		float y;
		float z;
		float g;
		long time;
		int stepCounter;
		int stepDetector;

		public JSONObject getJson() throws JSONException {
			JSONObject result = new JSONObject();

			result.put("x", x);
			result.put("y", y);
			result.put("z", z);
			result.put("g", g);
			result.put("time", time);
			result.put("stepCounter", stepCounter);
			result.put("stepDetector", stepDetector);

			return result;
		}
	}


	@Override
	protected JSONObject doWork() {
		JSONObject result = new JSONObject();

		try {
			SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			String now = df.format(new Date(System.currentTimeMillis()));

			result.put("acceleration", acceleration);
			result.put("stddev", mStddev);
			result.put("avg", mAvg);
			result.put("avgSampleRate", mAvgSampleRate);
			result.put("sampleCount", mSampleCount);
			result.put("stepCounter", mStepCounter);
			result.put("stepDetector", mStep);
            result.put("stepWindows", mStepWindow);

			result.put("activityDetection_status", activityDetection_status);
			result.put("activityDetection_confidence", activityDetection_confidence);
			result.put("activityDetection_time", activityDetection_time);

			String statusString = "drive";
			if (mStatus == 1) statusString = "walk";

			result.put("status", statusString);
			String msg = String.format("%s Std:%-7.4f Avg:%-7.4f Acc:%-7.4f SRate:%-7.2fms Period:%d Step:%-7.1f %-7d",
					statusString, mStddev, mAvg, acceleration, mAvgSampleRate, mSampleCount,
			mStepCounter,mStep);
			result.put("Message", msg);

			//Log.d(TAG, msg);
		} catch (JSONException e) {
		}

		return result;
	}

	@Override
	protected JSONObject internalData() {
		long timeBack = System.currentTimeMillis() - accelerometerRawBackMs;
		JSONObject result = new JSONObject();
		try {
			synchronized (accelerometerReadingList) {
				JSONArray arrayData = new JSONArray();
				for (AccelomterReadingRaw obj : accelerometerReadingList) {
					if (obj.time > timeBack) {
						arrayData.put(obj.getJson());
					}
				}

				result.put("accelerometerReading", arrayData);
				accelerometerReadingList.clear();
			}

			Log.d(TAG, "Return internalData");
		} catch (JSONException e) {
			e.printStackTrace();
			Log.d(TAG, "internalData Exception");
		}
		return result;
	}


	@Override
	protected JSONObject getConfig() {
		JSONObject result = new JSONObject();

		try {
			result.put("HelloTo", this.mHelloTo);
		} catch (JSONException e) {
		}

		return result;
	}

	@Override
	protected void setConfig(JSONObject config) {
		try {
			if (config.has("HelloTo"))
				this.mHelloTo = config.getString("HelloTo");

			if (config.has("report-realtime"))
				this.mReportRealTime = config.getBoolean("report-realtime");

			if (config.has("report-status-change")) {
                boolean reportStatusChange = config.getBoolean("report-status-change");
                if (reportStatusChange && !this.mReportStatusChange) {
                    long time = System.currentTimeMillis();
                    this.mReportStatusChangeStartTime = time;
                    Log.d(TAG, "Reset mReportStatusChangeStartTime");
                }
                this.mReportStatusChange = reportStatusChange;
            }

			if (config.has("accelerometerRawBackMs"))
				this.accelerometerRawBackMs = config.getInt("accelerometerRawBackMs");

			if (config.has("accelerometerReadBackMs"))
				this.accelerometerReadBackMs = config.getInt("accelerometerReadBackMs");

			if (config.has("walkMinStddev"))
				this.walkMinStddev = (float) config.getDouble("walkMinStddev");

			if (config.has("walkMinAvg"))
				this.walkMinAvg = (float) config.getDouble("walkMinAvg");

			if (config.has("lowPassFilter"))
				this.lowPassFilter = (float) config.getDouble("lowPassFilter");

			if (config.has("highPassFilter"))
				this.highPassFilter = (float) config.getDouble("highPassFilter");

            if (config.has("stepCountTrigger"))
                this.stepCountTrigger = (int) config.getDouble("stepCountTrigger");

            if (config.has("stepCountWindow"))
                this.stepCountWindow = (int) config.getDouble("stepCountWindow");

            if (config.has("notificationTitle")) {
                this.notificationTitle = config.getString("notificationTitle");
                Log.d(TAG, "Title: " + this.notificationTitle);
            }
            if (config.has("notificationText")) {
                this.notificationText = config.getString("notificationText");
                Log.d(TAG, "Text: " + this.notificationText);
            }
            updateNotification(this.notificationTitle, this.notificationText);

		} catch (JSONException e) {
		}
	}

    private Notification getActivityNotification(String title, String text){
        //Build a Notification required for running service in foreground.
        Intent main = getApplicationContext().getPackageManager().getLaunchIntentForPackage(getApplicationContext().getPackageName());
        main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1000, main,  PendingIntent.FLAG_UPDATE_CURRENT);

        int icon = R.drawable.star_big_on;
        int normalIcon = getResources().getIdentifier("icon", "drawable", getPackageName());
        int notificationIcon = getResources().getIdentifier("notificationicon", "drawable", getPackageName());
        if(notificationIcon != 0) {
            Log.d(TAG, "Found Custom Notification Icon!");
            icon = notificationIcon;
        }
        else if(normalIcon != 0) {
            Log.d(TAG, "Found normal Notification Icon!");
            icon = normalIcon;
        }

        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle(title);
        builder.setContentText(text);
        builder.setSmallIcon(icon);
        builder.setContentIntent(pendingIntent);
        Notification notification;
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            notification = buildForegroundNotification(builder);
        } else {
            notification = buildForegroundNotificationCompat(builder);
        }
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_NO_CLEAR;
        return notification;
    }

    private void updateNotification(String title, String text) {
        Notification notification = getActivityNotification(title, text);
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
        mNotificationManager.notify(notif_id, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        notif_id = startId;
        super.onStartCommand(intent, flags, startId);
        //handleOnStart(intent);
        Log.d(TAG, "intent" + intent);

        startForeground(notif_id, getActivityNotification(this.notificationTitle, this.notificationText));

        PowerManager mgr = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
        wakeLock.acquire();

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, filter);

        return START_NOT_STICKY;
    }


	public void onEvent(ActivityMessageEvent event) {
		Log.d(TAG, "*** ActivityMessageEvent: " + event.status);

		if (mReportRealTime ||
				(mReportStatusChange && activityDetection_status != event.status)) {
			activityDetection_status = event.status;
			activityDetection_confidence = event.confidence;
			activityDetection_time = event.time;
			runOnce();
		}
	}


    void showAlert() {
        PowerManager mgr = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
        wakeLock.acquire();

        //Intent resultIntent = new Intent(context, AlertActivity.class);
        //resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //context.startActivity(resultIntent);
    }

    public BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                mSensorManager.unregisterListener(sensorEventListener);
                mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), mSensorManager.SENSOR_DELAY_NORMAL);
                mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), mSensorManager.SENSOR_DELAY_NORMAL);
                mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR), mSensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    };


    @TargetApi(16)
    private Notification buildForegroundNotification(Notification.Builder builder) {
        return builder.build();
    }

    @SuppressWarnings("deprecation")
    @TargetApi(15)
    private Notification buildForegroundNotificationCompat(Notification.Builder builder) {
        return builder.getNotification();
    }

	@Override
	protected JSONObject initialiseLatestResult() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void onTimerEnabled() {
		Log.d(TAG, "*** onTimerEnabled");
		eventBus.register(this);

		// TODO Auto-generated method stub

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		List<Sensor> deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);

		mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), mSensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), mSensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR), mSensorManager.SENSOR_DELAY_NORMAL);

	}

	@Override
	protected void onTimerDisabled() {
		Log.d(TAG, "*** onTimerDisabled");
		eventBus.unregister(this);

		// TODO Auto-generated method stub
		if (mSensorManager != null) {
			mSensorManager.unregisterListener(sensorEventListener);
		}
	}

    private int calcStepCountWindow(int window, long now) {
        long timeBack = now - window;
        timeBack = Math.max(timeBack, mReportStatusChangeStartTime);
        int stepCount = 0;
        int i = accelerometerData.size() - 1;
        while (i >= 0) {
            AccelomterReading accelomterReading = accelerometerData.get(i);
            if (accelomterReading.time >= timeBack) {
                stepCount = (int) accelomterReading.value;
            } else {
                break;
            }
            i--;
        }

        return stepCount;
    }


	private float calcStdDev(long now) {
		long timeBack = now - accelerometerReadBackMs;

		float sum = 0;
		int i = accelerometerData.size() - 1;
		int count = 0;
		while (i >= 0) {
			AccelomterReading accelomterReading = accelerometerData.get(i);
			if (accelomterReading.time >= timeBack) {
				sum += accelomterReading.value;
				count++;
			} else {
				break;
			}
			i--;
		}

		float avg = sum / count;
		i = accelerometerData.size() - 1;
		sum = 0;
		while (i >= 0) {
			AccelomterReading accelomterReading = accelerometerData.get(i);
			if (accelomterReading.time >= timeBack) {
				sum += (accelomterReading.value - avg) * (accelomterReading.value - avg);
			} else {
				break;
			}
			i--;
		}

		mAvg = avg;
		mSampleCount = count;
		return sum / count;
	}


	private SensorEventListener sensorEventListener = new SensorEventListener() {
		@Override
		public void onSensorChanged(SensorEvent event) {

			Sensor sensor = event.sensor;
			/*if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");
				String now = df.format(new Date(System.currentTimeMillis()));


				float x = event.values[0];
				float y = event.values[1];
				float z = event.values[2];
				float gravity = SensorManager.STANDARD_GRAVITY;
				final float gUnfiltered = (float) Math.abs(Math.sqrt(x * x + y * y + z * z) - gravity);

				long time = System.currentTimeMillis();

				AccelomterReadingRaw accReading = new AccelomterReadingRaw();
				accReading.x = x;
				accReading.y = y;
				accReading.z = z;
				accReading.g = gravity;
				accReading.time = time;
				accReading.stepCounter = (int) mStepCounter;
				accReading.stepDetector = mStep;

				int itemCount = 0;
				collectSensorSample(accReading);

				//we take the value only if is reasonable for walking
				if (hasPrevValue) {
					prevx = x * lowPassFilter + prevx * (1 - lowPassFilter);
					prevy = y * lowPassFilter + prevy * (1 - lowPassFilter);
					prevz = z * lowPassFilter + prevz * (1 - lowPassFilter);
				} else {
					prevx = x;
					prevy = y;
					prevz = z;
					hasPrevValue = true;
				}
				final float g = (prevx * prevx + prevy * prevy + prevz * prevz);
				acceleration = (float) Math.abs(Math.sqrt(g) - gravity);


				collectReading(time, acceleration);

				mStddev = calcStdDev(time);

				int status = 0;
				if (mStddev > walkMinStddev && mAvg > walkMinAvg) {
					status = 1;
				}

				if (mReportRealTime ||
						(mReportStatusChange && status != mStatus)) {
					mStatus = status;
					runOnce();
				}
			} else if (sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
				mStepCounter = event.values[0];
				Log.d(TAG, "TYPE_STEP_COUNTER");
				if (mReportRealTime) {
					runOnce();
				}
			} else */ if (sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                Log.d(TAG, "TYPE_STEP_DETECTOR");
                long time = System.currentTimeMillis();
                AccelomterReadingRaw accReading = new AccelomterReadingRaw();
                accReading.x = event.values[0];
                accReading.stepDetector = mStep;
                accReading.time = time;
                collectSensorSample(accReading);

				if (event.values[0] == 1.0f) {
                    mStep++;
                    collectReading(time, mStep);

                    int status = 0;
                    int stepStart = calcStepCountWindow(stepCountWindow, time);
                    if (stepStart != 0) {
                        mStepWindow = mStep - stepStart;
						/*
                        if (mStepWindow > stepCountTrigger) {
                            status = 1;
                        }
                        */

                        if (mReportRealTime ||
                                (mReportStatusChange && status != mStatus)) {
                            mStatus = status;
                            runOnce();
                        }
                    }
				}
			}

		}

        private void collectReading(long time, float value) {
            AccelomterReading accelomterReading = new AccelomterReading();
            accelomterReading.value = value;
            accelomterReading.time = time;

            accelerometerData.add(accelomterReading);
            if (accelerometerData.size() >= ACCELEROMETER_READ_MAXSIZE) {
                accelerometerData.remove(0);
            }
        }

        private void collectSensorSample(AccelomterReadingRaw accReading) {
			synchronized (accelerometerReadingList) {
                accelerometerReadingList.add(accReading);
                if (accelerometerReadingList.size() > 20000) {
                    accelerometerReadingList.remove(0);
                }

                mAvgSampleRate = (accelerometerReadingList.get(accelerometerReadingList.size() - 1).time - accelerometerReadingList.get(0).time) / accelerometerReadingList.size();
            }
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			Log.d(TAG, "sensorEventListener.onAccuracyChanged");
		}
	};

	public void setStatus(String state, float confidence) {
		mActivityStat = state;
		mActivityConfidence = confidence;
		mActivityLastUpdate = System.currentTimeMillis();

		Log.d(TAG, "setStatus " + state);
		runOnce();
	}



}
