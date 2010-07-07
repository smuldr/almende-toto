package nl.sense_os.wk;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class Wk extends Activity {

    private class InitPouleTask extends AsyncTask<Void, Void, Poule> {

        @Override
        protected Poule doInBackground(Void... params) {

            return Util.getPoule(Wk.this);
        }

        @Override
        protected void onPostExecute(Poule poule) {

            dismissDialog(DIALOG_INITIALIZING);
            Wk.this.initializing = false;

            // save players object
            Wk.this.poule = poule;

            onLogIn();
        }

        @Override
        protected void onPreExecute() {

            showDialog(DIALOG_INITIALIZING);

            Wk.this.initializing = true;
        }
    }

    private class LoginTask extends AsyncTask<String, Void, Boolean> {
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

            final String changeUrl = jsonLoginUrl(this.name, this.pass);

            final HttpClient httpClient = new DefaultHttpClient();
            String response = "";
            try {
                final HttpGet request = new HttpGet(changeUrl);

                Log.d(TAG, URLDecoder.decode(request.getRequestLine().toString()));
                final ResponseHandler<String> responseHandler = new BasicResponseHandler();
                response = httpClient.execute(request, responseHandler);

            } catch (final IOException e) {
                Log.e(TAG, "IOException in JsonTask.", e);
                this.error = e.getMessage();
                return false;
            } catch (final IllegalAccessError e) {
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
            } catch (final UnsupportedEncodingException e) {
                Log.e(TAG, "Error encoding JSON object for HTTP Get", e);
                return null;
            }
            return result;
        }

        @Override
        protected void onPostExecute(Boolean success) {

            removeDialog(DIALOG_PROGRESS);

            if (true == success) {
                // save scores and fixtures in preferences
                final SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(Wk.this);
                final Editor editor = prefs.edit();

                // start inittask if the scores are changed
                if (false == this.scores.equals(prefs.getString(Wk.PREF_SCORES, ""))) {
                    editor.putString(PREF_SCORES, this.scores.toString());
                    editor.putString(PREF_FIXTURES, this.fixtures.toString());

                    new InitPouleTask().execute();
                }

                editor.putString(PREF_LOGIN_NAME, this.name);
                editor.putString(PREF_LOGIN_PASS, this.pass);
                editor.commit();

                onLogIn();
            } else {
                Toast.makeText(Wk.this, "Login failed. Cause: " + this.error, Toast.LENGTH_LONG)
                        .show();

                showDialog(DIALOG_LOGIN);
            }
        }

        @Override
        protected void onPreExecute() {
            showDialog(DIALOG_PROGRESS);

            onLogOut();
        }

        private boolean parseJson(String response) {
            boolean success = false;

            // create JSON object from response
            JSONObject responseObj = null;
            try {
                responseObj = new JSONObject(response);
            } catch (final JSONException e) {
                Log.e(TAG, "JSONException parsing response:", e);
                this.error = e.getMessage();
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
            } catch (final JSONException e) {
                Log.e(TAG, "JSONException in main object:", e);
                this.error = "JSONException in main object";
                return false;
            }

            if (type.equals("get_player")) {
                // parse payload
                try {
                    for (int i = 0; i < payload.length(); i++) {
                        final JSONObject obj = payload.getJSONObject(i);
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
                } catch (final JSONException e) {
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

    private class StandingsListener implements AdapterView.OnItemClickListener {

        public void onItemClick(AdapterView<?> parent, View view, int position, final long id) {

            // player name from the label
            final String lbl = ((TextView) view.findViewById(R.id.StandingsRow)).getText()
                    .toString();
            int nameStart = lbl.indexOf(". ") + 2;
            int nameEnd = lbl.indexOf("(") - 1;
            String name = lbl.substring(nameStart, nameEnd);

            // start predictions activity
            final Intent prediction = new Intent("nl.sense_os.wk.Prediction");
            prediction.putExtra(KEY_PLAYER, name);
            prediction.putExtra(KEY_POULE, Wk.this.poule);
            startActivityForResult(prediction, REQID_PREDICTION);
        }
    }

    private static final int DIALOG_INITIALIZING = 1;
    private static final int DIALOG_LOGIN = 2;
    private static final int DIALOG_PROGRESS = 3;
    public static final String KEY_POULE = "nl.sense_os.wk.Poule";
    public static final String KEY_PLAYER = "nl.sense_os.wk.PlayerName";
    public static final int MENU_AUTOSYNC_OFF = 4;
    public static final int MENU_AUTOSYNC_ON = 3;
    public static final int MENU_LOGIN = 1;
    public static final int MENU_SYNC = 2;
    public static final String PREF_AUTOSYNC = "autosync";
    public static final String PREF_FIXTURES = "fixtures";
    public static final String PREF_LOGIN_NAME = "login_name";
    public static final String PREF_LOGIN_PASS = "login_pass";
    public static final String PREF_SCORES = "scores";
    public static final int REQID_PREDICTION = 32;
    private static final String TAG = "WK";

    private boolean initializing;
    private Poule poule;
    private ListView listView;

    private Dialog createDialogInitializing() {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Initializing...");
        dialog.setCancelable(false);
        return dialog;
    }

    /**
     * @return a login dialog.
     */
    private Dialog createDialogLogin() {

        // create View with input fields for dialog content
        final LinearLayout login = new LinearLayout(this);
        login.setOrientation(LinearLayout.VERTICAL);
        final EditText emailField = new EditText(this);
        emailField.setLayoutParams(new LayoutParams(-1, -2));
        emailField.setHint("Username");
        emailField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        login.addView(emailField);
        final EditText passField = new EditText(this);
        passField.setLayoutParams(new LayoutParams(-1, -2));
        passField.setHint("Password");
        passField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passField.setTransformationMethod(new PasswordTransformationMethod());
        passField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        login.addView(passField);

        // get current login email from preferences
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        emailField.setText(prefs.getString(PREF_LOGIN_NAME, ""));

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Log in");
        builder.setView(login);
        builder.setPositiveButton("Log in", new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                final String name = emailField.getText().toString();
                final String pass = passField.getText().toString();

                // put md5 string
                final String MD5Pass = md5(pass);

                final Editor editor = prefs.edit();
                editor.putString(PREF_LOGIN_NAME, name);
                editor.putString(PREF_LOGIN_PASS, MD5Pass);
                editor.commit();

                // initiate Login
                new LoginTask().execute(name, MD5Pass);
            }
        });
        builder.setNeutralButton("Close", new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.setCancelable(false);
        return builder.create();
    }

    private Dialog createDialogProgress() {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Logging in...");
        dialog.setCancelable(false);
        return dialog;
    }

    private String md5(String pass) {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e1) {
            return null;
        }
        final byte[] passBytes = pass.getBytes();

        sha.update(passBytes);
        final byte[] passwordSha1 = sha.digest();
        String passStringSha1 = "";
        for (final byte b : passwordSha1) {
            String s = Integer.toHexString(b & 0x0FF);
            if (s.length() < 2) {
                s = "0" + s;
            }
            passStringSha1 += s;
        }
        return passStringSha1;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQID_PREDICTION:
            if (resultCode == RESULT_OK) {
                this.poule = (Poule) data.getParcelableExtra(KEY_POULE);
            } else {
                // no changes were made in the predictions activity
            }
            break;
        default:
            Log.w(TAG, "Unexpected activity result");
            break;
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.Prediction:
            String name = PreferenceManager.getDefaultSharedPreferences(this).getString(
                    PREF_LOGIN_NAME, "");
            final Intent prediction = new Intent("nl.sense_os.wk.Prediction");
            prediction.putExtra(KEY_PLAYER, name);
            prediction.putExtra(KEY_POULE, this.poule);
            startActivityForResult(prediction, REQID_PREDICTION);
            break;
        case R.id.Standings:
            Toast.makeText(this, "Standings are coming soon...", Toast.LENGTH_LONG).show();
            // startActivity(new Intent("nl.sense_os.wk.Standings"));
            break;
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final Object data = getLastNonConfigurationInstance();
        if (null != data) {
            this.poule = (Poule) data;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        switch (id) {
        case DIALOG_INITIALIZING:
            dialog = createDialogInitializing();
            break;
        case DIALOG_LOGIN:
            dialog = createDialogLogin();
            break;
        case DIALOG_PROGRESS:
            dialog = createDialogProgress();
            break;
        default:
            Log.e(TAG, "Error in onCreateDialog: unexpected dialog id: " + id);
            break;
        }
        return dialog;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_LOGIN, Menu.NONE, "Change login").setIcon(
                android.R.drawable.ic_menu_info_details);
        menu.add(Menu.NONE, MENU_SYNC, Menu.NONE, "Sync").setIcon(R.drawable.ic_menu_refresh);
        return true;
    }

    private void onLogIn() {
        findViewById(R.id.Prediction).setEnabled(true);
        findViewById(R.id.Standings).setEnabled(true);

        // get current player
        if (null == this.poule) {
            if (false == this.initializing) {
                new InitPouleTask().execute();
            }
        } else {
            // get my username
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Wk.this);
            final String username = prefs.getString(PREF_LOGIN_NAME, "");

            // get my player
            final Player me = this.poule.players.get(username);

            // show welcome text
            ((TextView) findViewById(R.id.Hello)).setText("Hello, " + me.fullName + "!");

            // show standings
            if (null == listView) {
                listView = (ListView) findViewById(R.id.StandingsList);
                TextView header = new TextView(this);
                header.setPadding(8, 2, 2, 2);
                header.setText("Standings");
                header.setTypeface(null, Typeface.BOLD);
                header.setBackgroundColor(0xFF808080);
                header.setTextColor(0xFFFFFFFF);
                listView.addHeaderView(header);
            }

            ArrayList<Player> standings = Util.getStandings(this.poule);
            String[] standingsArray = new String[standings.size()];
            int rank = 0;
            int rankStep = 1;
            int oldScore = -1;
            for (int i = 0; i < standings.size(); i++) {
                Player p = standings.get(i);
                int pScore = Util.getTotalScore(p.groupStage, p.finals);

                if (pScore != oldScore) {
                    oldScore = pScore;
                    rank += rankStep;
                    rankStep = 1;
                } else {
                    rankStep++;
                }

                standingsArray[i] = rank + ". " + p.username + " (" + pScore + ")";
            }
            listView.setAdapter(new ArrayAdapter<String>(this, R.layout.standings_row,
                    R.id.StandingsRow, standingsArray));
            listView.setOnItemClickListener(new StandingsListener());

            // start syncing
            if (prefs.getBoolean(PREF_AUTOSYNC, false)) {
                AlarmManager mgr = (AlarmManager) getSystemService(ALARM_SERVICE);
                PendingIntent operation = PendingIntent.getBroadcast(this, WkSyncer.REQID_SYNC,
                        new Intent("nl.sense_os.wk.Sync"), 0);
                mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), operation);
            }
        }
    }

    private void onLogOut() {
        findViewById(R.id.Prediction).setEnabled(false);
        findViewById(R.id.Standings).setEnabled(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_LOGIN:
            showDialog(DIALOG_LOGIN);
            break;
        case MENU_SYNC:
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            final String name = prefs.getString(PREF_LOGIN_NAME, "");
            final String pass = prefs.getString(PREF_LOGIN_PASS, "");
            new LoginTask().execute(name, pass);
            break;
        case MENU_AUTOSYNC_OFF:
            Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putBoolean(PREF_AUTOSYNC, false);
            editor.commit();

            // set new alarm
            AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            PendingIntent operation = PendingIntent.getBroadcast(this, WkSyncer.REQID_SYNC,
                    new Intent("nl.sense_os.wk.Sync"), 0);
            mgr.cancel(operation);
            break;
        case MENU_AUTOSYNC_ON:
            editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putBoolean(PREF_AUTOSYNC, true);
            editor.commit();

            // set new alarm
            mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            operation = PendingIntent.getBroadcast(this, WkSyncer.REQID_SYNC, new Intent(
                    "nl.sense_os.wk.Sync"), 0);
            mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), operation);
            break;
        default:
            Log.w(TAG, "Unexpected option selected");
            return false;
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final boolean autosync = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                PREF_AUTOSYNC, false);
        if (autosync) {
            menu.removeItem(MENU_AUTOSYNC_ON);
            menu.add(Menu.NONE, MENU_AUTOSYNC_OFF, Menu.NONE, "Turn auto sync OFF");
        } else {
            menu.removeItem(MENU_AUTOSYNC_OFF);
            menu.add(Menu.NONE, MENU_AUTOSYNC_ON, Menu.NONE, "Turn auto sync ON");
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // check login
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String pass = prefs.getString(PREF_LOGIN_PASS, "");
        if (pass.equals("")) {
            showDialog(DIALOG_LOGIN);
        } else {
            onLogIn();
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return this.poule;
    }
}
