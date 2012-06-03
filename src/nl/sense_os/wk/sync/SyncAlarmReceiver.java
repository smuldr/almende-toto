package nl.sense_os.wk.sync;

import nl.sense_os.wk.shared.Keys;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class SyncAlarmReceiver extends BroadcastReceiver {

	private static final String TAG = "WK Sync";
	private static final String ACTION = "nl.sense_os.wk.SYNCHRONIZE";
	private static final int REQ_CODE_AWAKE = 241;
	private static final int REQ_CODE_WAKEUP = 0x0221a2;
	private static final long INTERVAL_AWAKE = AlarmManager.INTERVAL_HALF_HOUR;
	private static final long INTERVAL_WAKEUP = AlarmManager.INTERVAL_HALF_DAY;

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive");

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean(Keys.PREF_AUTOSYNC, true)) {
			if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
				startSynchronizing(context);
			} else {
				context.startService(new Intent(context, SyncService.class));
			}
		} else {
			// sync is disabled
		}
	}

	public static void startSynchronizing(Context context) {
		Log.d(TAG, "start synchronizing");

		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(ACTION);

		// set alarm that goes off only when the phone is awake
		PendingIntent operation = PendingIntent.getBroadcast(context, REQ_CODE_AWAKE, intent, 0);
		am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + INTERVAL_AWAKE,
				INTERVAL_AWAKE, operation);

		// set alarm that goes off when the phone sleeps
		operation = PendingIntent.getBroadcast(context, REQ_CODE_WAKEUP, intent, 0);
		am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
				System.currentTimeMillis() + INTERVAL_AWAKE, INTERVAL_WAKEUP, operation);
	}

	public static void stopSynchronizing(Context context) {
		Log.d(TAG, "stop synchronizing");

		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(ACTION);
		am.cancel(PendingIntent.getBroadcast(context, REQ_CODE_AWAKE, intent, 0));
		am.cancel(PendingIntent.getBroadcast(context, REQ_CODE_WAKEUP, intent, 0));
	}
}