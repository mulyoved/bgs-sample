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

	private SensorManager mSensorManager;
	private static final int TIME_DELAY = 5;
	private static final float SENSIBILITY_UP = 3.5f, SENSIBILITY_DOWN = 1.2f;
	private static final String STOP = "State : Stop", WALK = "State : Walking", RUN = "State : Running";
	private int stop_cnt = 0, walk_cnt = 0, run_cnt = 0;
	private String text_speed, text_state;
	private String text_x, text_y, text_z;
	private boolean detect_flag = false;
	private double vib_step = 0;
	List<AccelomterReading> accelerometerReadingList = new ArrayList<AccelomterReading>();

	public class AccelomterReading {
		float x;
		float y;
		float z;
		float g;
		long time;

		public JSONObject getJson() throws JSONException {
			JSONObject result = new JSONObject();

			result.put("x", x);
			result.put("y", y);
			result.put("z", z);
			result.put("g", g);
			result.put("time", time);

			return result;
		}
	}

	@Override
	protected JSONObject doWork() {
		JSONObject result = new JSONObject();
		
		try {
			SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss"); 
			String now = df.format(new Date(System.currentTimeMillis())); 

			String msg = "Hello " + this.mHelloTo + " - its currently " + now;
			result.put("Message", msg);

			Log.d(TAG, msg);
		} catch (JSONException e) {
		}
		
		return result;	
	}

	@Override
	protected JSONObject internalData() {
		JSONObject result = new JSONObject();
		try {
			synchronized (accelerometerReadingList) {
				JSONArray arrayData = new JSONArray();
				for (AccelomterReading obj : accelerometerReadingList) {
					arrayData.put(obj.getJson());
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
		mSensorManager.registerListener(sensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), mSensorManager.SENSOR_DELAY_NORMAL);

	}

	@Override
	protected void onTimerDisabled() {
		// TODO Auto-generated method stub
		if (mSensorManager != null) {
			mSensorManager.unregisterListener(sensorEventListener);
		}
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
				final float g = (x * x + y * y + z * z);
				float gravity = SensorManager.STANDARD_GRAVITY;
				vib_step = Math.abs(Math.sqrt(g) - gravity);
				text_speed = "Accelerator : " + String.valueOf(vib_step);

				if (vib_step <= SENSIBILITY_DOWN) {
					stop_cnt ++;walk_cnt = run_cnt = 0;
					if (stop_cnt > TIME_DELAY) {
						stop_cnt = 0;
						text_state = STOP;
						detect_flag = true;
					}
				} else if ((vib_step > SENSIBILITY_DOWN) && (vib_step <= SENSIBILITY_UP)) {
					walk_cnt ++;stop_cnt = run_cnt = 0;
					if (walk_cnt > TIME_DELAY) {
						walk_cnt = 0;
						text_state = WALK;
						detect_flag = true;
					}
				} else if (vib_step > SENSIBILITY_UP) {
					run_cnt ++;stop_cnt = walk_cnt = 0;
					if (run_cnt > TIME_DELAY) {
						run_cnt = 0;
						text_state = RUN;
						detect_flag = true;
					}
				}

				text_x = "X : " + String.valueOf(x);
				text_y = "Y : " + String.valueOf(y);
				text_z = "Z : " + String.valueOf(z);

				AccelomterReading accReading = new AccelomterReading();
				accReading.x = x;
				accReading.y = y;
				accReading.z = z;
				accReading.g = gravity;
				accReading.time = System.currentTimeMillis();
				synchronized (accelerometerReadingList) {
					accelerometerReadingList.add(accReading);
					if (accelerometerReadingList.size() > 20000) {
						accelerometerReadingList.remove(0);
					}
				}

				//Log.d(TAG, "sensorEventListener.onSensorChanged " + now );
			}

		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			Log.d(TAG, "sensorEventListener.onAccuracyChanged");
		}
	};


}
