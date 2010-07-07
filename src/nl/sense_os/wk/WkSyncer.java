package nl.sense_os.wk;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

@SuppressWarnings("unused")
public class WkSyncer extends BroadcastReceiver {

    private class SyncTask extends AsyncTask<String, Void, Boolean> {
        private static final String URL = "http://wk.almende.com/server.php?q=";
        private String error;
        private JSONObject fixtures;
        private String name;
        private String pass;
        private JSONObject scores;

        @Override
        protected Boolean doInBackground(String... params) {
            this.name = params[0];
            this.pass = params[1];

            String changeUrl = jsonLoginUrl(name, pass);

            HttpClient httpClient = new DefaultHttpClient();
            String response = "";
            try {
                HttpGet request = new HttpGet(changeUrl);

                Log.d(TAG, "HTTP execute: "
                        + URLDecoder.decode(request.getRequestLine().toString()));
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

        private String jsonLoginUrl(String username, String password) {

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
        protected void onPostExecute(Boolean success) {

            if (true == success) {
                // save scores and fixtures in preferences
                Editor editor = PreferenceManager
                        .getDefaultSharedPreferences(WkSyncer.this.context).edit();
                editor.putString(Wk.PREF_SCORES, scores.toString());
                editor.putString(Wk.PREF_FIXTURES, fixtures.toString());
                editor.commit();
            } else {
                Log.w(TAG, "Login failed. Cause: " + this.error);
            }
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
    }

    public static final int REQID_SYNC = 1;
    public static final String TAG = "WK Sync";
    private static final String URL = "http://wk.almende.com/server.php?q=";
    private String error;
    private JSONObject fixtures;
    private JSONObject scores;
    private Context context;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        this.context = context;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean autosync = prefs.getBoolean(Wk.PREF_AUTOSYNC, false);

        // set new alarm
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent operation = PendingIntent.getBroadcast(context, REQID_SYNC, new Intent(
                "nl.sense_os.wk.Sync"), 0);
        mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3 * 60 * 1000, operation);

        if (autosync) {
            ConnectivityManager conn = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = conn.getActiveNetworkInfo();
            if ((info != null) && (info.isConnected())) {
                String name = prefs.getString(Wk.PREF_LOGIN_NAME, "");
                String pass = prefs.getString(Wk.PREF_LOGIN_PASS, "");
                new SyncTask().execute(name, pass);
            } else {
                Log.d(TAG, "Not syncing... NetworkInfo=" + (info==null));
            }
        }
    }

    private Boolean doSync(String name, String pass) {

        String changeUrl = jsonLoginUrl(name, pass);

        HttpClient httpClient = new DefaultHttpClient();
        String response = "";
        try {
            HttpGet request = new HttpGet(changeUrl);

            Log.d(TAG, "HTTP execute: " + URLDecoder.decode(request.getRequestLine().toString()));
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            response = httpClient.execute(request, responseHandler);

        } catch (IOException e) {
            Log.e(TAG, "IOException in JsonTask.", e);
            this.error = e.getMessage();
            return false;
        } catch (IllegalAccessError e) {
            Log.e(TAG, "IllegalAccessError in JsonTask.", e);
            this.error = e.getMessage();
            return false;
        }

        if (parseJson(response)) {
            return true;
        } else {
            return false;
        }
    }

    private String jsonLoginUrl(String username, String password) {

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

    protected void onPostExecute(Boolean success) {

        if (true == success) {
            // save scores and fixtures in preferences
            Editor editor = PreferenceManager.getDefaultSharedPreferences(this.context).edit();
            editor.putString(Wk.PREF_SCORES, scores.toString());
            editor.putString(Wk.PREF_FIXTURES, fixtures.toString());
            editor.commit();
        } else {
            Log.w(TAG, "Login failed. Cause: " + this.error);
        }
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
}
