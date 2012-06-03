package com.almende.footballpool.sync;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;


import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.almende.footballpool.shared.Keys;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

public class SyncService extends IntentService {

	private static final String URL = "http://my.sense-os.nl/ek2012/server.php?q=";
	private static final String TAG = "EK SyncService";
	private String error;
	private JSONObject fixtures;
	private JSONObject scores;

	public SyncService() {
		super("SyncService");
	}

	private String getJsonLoginUrl(String username, String password) {

		final JSONObject json = new JSONObject();
		try {
			json.put("type", "login_request");
			json.put("player", username);
			json.put("pass", password);
		} catch (final JSONException e) {
			Log.e(TAG, "JSONException in top object");
			return null;
		}

		String result = "";
		try {
			result = URL + URLEncoder.encode(json.toString(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, "Error encoding JSON object for HTTP Get", e);
			return null;
		}
		return result;
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		ConnectivityManager conn = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = conn.getActiveNetworkInfo();
		if ((info != null) && (info.isConnected())) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			String name = prefs.getString(Keys.PREF_LOGIN_NAME, "");
			String pass = prefs.getString(Keys.PREF_LOGIN_PASS, "");
			boolean result = sync(name, pass);
			if (result) {
				onSyncSuccess();
			} else {
				Log.w(TAG, "Synchronization failed: " + error);
			}
		} else {
			// no network
		}
	}

	private void onSyncSuccess() {
		// save scores and fixtures in preferences
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString(Keys.PREF_SCORES, scores.toString());
		editor.putString(Keys.PREF_FIXTURES, fixtures.toString());
		editor.commit();
	}

	private boolean parseJson(String response) {
		boolean success = false;

		// create JSON object from response
		JSONObject responseObj = null;
		try {
			responseObj = new JSONObject(response);
		} catch (JSONException e) {
			Log.e(TAG, "JSONException parsing response:", e);
			return false;
		}

		// parse main response object
		String type = null;
		JSONArray payload = null;
		String error = null;
		try {
			type = responseObj.getString("type");
			payload = responseObj.getJSONArray("payload");
			error = responseObj.getString("error");
		} catch (JSONException e) {
			Log.e(TAG, "JSONException in main object:", e);
			this.error = "JSONException in main object";
			return false;
		}

		if (type.equals("get_player")) {
			// parse payload
			try {
				for (int i = 0; i < payload.length(); i++) {
					JSONObject obj = payload.getJSONObject(i);
					if (obj.has("type")) {
						this.fixtures = obj;
					} else if (obj.has("real")) {
						this.scores = obj;
					} else {
						Log.e(TAG, "Unexpected payload object");
						this.error = "Unexpected payload object";
						return false;
					}
				}
			} catch (JSONException e) {
				Log.e(TAG, "JSONException getting payload objects:", e);
				this.error = "JSONException getting payload objects";
				return false;
			}

			// check if payload is ok
			if ((this.scores != null) && (this.fixtures != null)) {
				success = true;
			} else {
				this.error = "unknown";
				return false;
			}

		} else if (type.equals("error")) {
			this.error = error;
			success = false;
		} else {
			this.error = "unexpected response: " + type;
			success = false;
		}
		return success;
	}

	private boolean sync(String name, String pass) {

		String changeUrl = getJsonLoginUrl(name, pass);

		HttpClient httpClient = new DefaultHttpClient();
		String response = "";
		try {
			HttpGet request = new HttpGet(changeUrl);
			ResponseHandler<String> responseHandler = new BasicResponseHandler();
			response = httpClient.execute(request, responseHandler);

		} catch (IOException e) {
			Log.e(TAG, "IOException in JsonTask.", e);
			return false;
		} catch (IllegalAccessError e) {
			Log.e(TAG, "IllegalAccessError in JsonTask.", e);
			return false;
		}

		if (parseJson(response)) {
			return true;
		} else {
			return false;
		}
	}
}
