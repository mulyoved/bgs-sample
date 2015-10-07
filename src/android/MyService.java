package com.red_folder.phonegap.plugin.backgroundservice.sample;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.red_folder.phonegap.plugin.backgroundservice.BackgroundService;

public class MyService extends BackgroundService {

	private final static String TAG = MyService.class.getSimpleName();

	private String mHelloTo = "World";
	private boolean mReportRealTime = false;
	private boolean mReportStatusChange = false;

	private SensorManager mSensorManager;
	private float acceleration = 0;
	private float mStddev;
	private float mAvg;
	private int mStatus = 0;
	private float mAvgSampleRate = 0;
	private long mSampleCount = 0;
	private float mStepCounter = 0;
	private int mStep = 0;
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

			if (config.has("report-status-change"))
				this.mReportStatusChange = config.getBoolean("report-status-change");

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

		} catch (JSONException e) {
		}

	}

	@Override
	protected JSONObject initialiseLatestResult() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void onTimerEnabled() {
		// TODO Auto-generated method stub

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		List<Sensor> deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);

		mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), mSensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), mSensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR), mSensorManager.SENSOR_DELAY_NORMAL);

	}

	@Override
	protected void onTimerDisabled() {
		// TODO Auto-generated method stub
		if (mSensorManager != null) {
			mSensorManager.unregisterListener(sensorEventListener);
		}
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
			if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
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
				synchronized (accelerometerReadingList) {
					accelerometerReadingList.add(accReading);
					itemCount = accelerometerReadingList.size();
					if (accelerometerReadingList.size() > 20000) {
						accelerometerReadingList.remove(0);
					}

					mAvgSampleRate = (accelerometerReadingList.get(accelerometerReadingList.size() - 1).time - accelerometerReadingList.get(0).time) / accelerometerReadingList.size();
				}

				//we take the value only if is reasonable for walking
				if (gUnfiltered < highPassFilter) {
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


					AccelomterReading accelomterReading = new AccelomterReading();
					accelomterReading.value = acceleration;
					accelomterReading.time = time;

					accelerometerData.add(accelomterReading);
					if (accelerometerData.size() >= ACCELEROMETER_READ_MAXSIZE) {
						accelerometerData.remove(0);
					}

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
				}
			} else if (sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
				mStepCounter = event.values[0];
			} else if (sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
				if (event.values[0] == 1.0f) {
					mStep++;
				}
			}

		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			Log.d(TAG, "sensorEventListener.onAccuracyChanged");
		}
	};


}
