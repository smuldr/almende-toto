package com.almende.footballpool;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.almende.footballpool.content.Player;
import com.almende.footballpool.content.Poule;
import com.almende.footballpool.shared.Keys;
import com.almende.footballpool.shared.Util;
import com.almende.footballpool.sync.SyncAlarmReceiver;

public class Standings extends FragmentActivity {

	private class InitPouleTask extends AsyncTask<Void, Void, Poule> {

		@Override
		protected Poule doInBackground(Void... params) {

			return Util.getPoule(Standings.this);
		}

		@Override
		protected void onPostExecute(Poule poule) {

			setDialogInitialize(false);

			// save players object
			Standings.this.poule = poule;

			onLogIn();
		}

		@Override
		protected void onPreExecute() {
			setDialogInitialize(true);
		}
	}

	private class LoginDialog extends DialogFragment {

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

		public Dialog onCreateDialog(Bundle savedInstanceState) {

			LayoutInflater inflater = getActivity().getLayoutInflater();
			View view = inflater.inflate(R.layout.login_dialog, null, false);

			final EditText usernameField = (EditText) view.findViewById(R.id.username);
			final EditText passField = (EditText) view.findViewById(R.id.password);

			// get current login email from preferences
			final SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(getActivity());
			usernameField.setText(prefs.getString(Keys.PREF_LOGIN_NAME, ""));

			final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(R.string.login_title);
			builder.setView(view);
			builder.setPositiveButton(android.R.string.ok, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					final String name = usernameField.getText().toString();
					final String pass = passField.getText().toString();

					// put md5 string
					final String MD5Pass = md5(pass);

					final Editor editor = prefs.edit();
					editor.putString(Keys.PREF_LOGIN_NAME, name);
					editor.putString(Keys.PREF_LOGIN_PASS, MD5Pass);
					editor.commit();

					// initiate Login
					new LoginTask().execute(name, MD5Pass);
				}
			});
			builder.setNegativeButton(android.R.string.cancel, null);

			return builder.create();
		}
	}

	private class LoginTask extends AsyncTask<String, Void, Boolean> {
		private static final String URL = "http://my.sense-os.nl/ek2012/server.php?q=";
		private String error;
		private JSONObject fixtures;
		private String name;
		private String pass;
		private JSONObject scores;

		private String createLoginUrl(String username, String password) {

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
		protected Boolean doInBackground(String... params) {
			this.name = params[0];
			this.pass = params[1];

			final String changeUrl = createLoginUrl(this.name, this.pass);

			final HttpClient httpClient = new DefaultHttpClient();
			String response = "";
			try {
				final HttpGet request = new HttpGet(changeUrl);
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

		@Override
		protected void onPostExecute(Boolean success) {

			setDialogLoginProgress(false);

			if (true == success) {
				// save scores and fixtures in preferences
				final SharedPreferences prefs = PreferenceManager
						.getDefaultSharedPreferences(Standings.this);
				final Editor editor = prefs.edit();

				// start inittask if the scores are changed
				if (false == this.scores.equals(prefs.getString(Keys.PREF_SCORES, ""))) {
					editor.putString(Keys.PREF_SCORES, this.scores.toString());
					editor.putString(Keys.PREF_FIXTURES, this.fixtures.toString());

					new InitPouleTask().execute();
				}

				editor.putString(Keys.PREF_LOGIN_NAME, this.name);
				editor.putString(Keys.PREF_LOGIN_PASS, this.pass);
				editor.commit();

				onLogIn();
			} else {
				Toast.makeText(Standings.this, "Login failed. Cause: " + this.error,
						Toast.LENGTH_LONG).show();

				setDialogLogin(true);
			}
		}

		@Override
		protected void onPreExecute() {
			setDialogLoginProgress(true);

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

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, final long id) {

			// player name from the label
			final String lbl = ((TextView) view.findViewById(android.R.id.text1)).getText()
					.toString();
			int nameStart = lbl.indexOf(". ") + 2;
			int nameEnd = lbl.indexOf("(") - 1;
			String fullName = lbl.substring(nameStart, nameEnd);
			String username = fullName;
			for (Player player : poule.players.values()) {
				if (fullName.equals(player.getFullName())) {
					username = player.getUsername();
					break;
				}
			}

			// start predictions activity
			showPrediction(username);
		}
	}

	public static final int REQID_PREDICTION = 32;
	private static final String TAG = "WK";

	private boolean initializing;
	private Poule poule;
	private ListView listView;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQID_PREDICTION:
			if (resultCode == RESULT_OK) {
				this.poule = (Poule) data.getParcelableExtra(Keys.KEY_POULE);
			} else {
				// no changes were made in the predictions activity
			}
			break;
		default:
			Log.w(TAG, "Unexpected activity result");
			break;
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.standings_label);
		setContentView(R.layout.standings);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_standings, menu);
		return true;
	}

	private void onLogIn() {

		// get current player
		if (null == poule) {
			if (false == this.initializing) {
				new InitPouleTask().execute();
			}
		} else {
			// get my username
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

			// show standings
			listView = (ListView) findViewById(R.id.StandingsList);

			ArrayList<Player> standings = Util.getStandings(this.poule);
			String[] standingsArray = new String[standings.size()];
			int rank = 0;
			int rankStep = 1;
			int oldScore = -1;
			for (int i = 0; i < standings.size(); i++) {
				Player p = standings.get(i);
				int pScore = Util.getTotalScore(p.getGroupStage(), p.getFinals());

				if (pScore != oldScore) {
					oldScore = pScore;
					rank += rankStep;
					rankStep = 1;
				} else {
					rankStep++;
				}

				standingsArray[i] = rank + ". " + p.getFullName() + " (" + pScore + ")";
			}
			listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
					android.R.id.text1, standingsArray));
			listView.setOnItemClickListener(new StandingsListener());

			// start syncing
			if (prefs.getBoolean(Keys.PREF_AUTOSYNC, true)) {
				SyncAlarmReceiver.startSynchronizing(this);
			}
		}
	}

	private void onLogOut() {
		// nothing to do
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_login:
			setDialogLogin(true);
			break;
		case R.id.menu_refresh:
			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			final String name = prefs.getString(Keys.PREF_LOGIN_NAME, "");
			final String pass = prefs.getString(Keys.PREF_LOGIN_PASS, "");
			new LoginTask().execute(name, pass);
			break;
		case R.id.menu_autosync_off:
			Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			editor.putBoolean(Keys.PREF_AUTOSYNC, false);
			editor.commit();

			SyncAlarmReceiver.stopSynchronizing(this);

			break;
		case R.id.menu_autosync_on:
			editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			editor.putBoolean(Keys.PREF_AUTOSYNC, true);
			editor.commit();

			// set new alarm
			SyncAlarmReceiver.startSynchronizing(this);

			break;
		case R.id.menu_prediction:
			String username = PreferenceManager.getDefaultSharedPreferences(this).getString(
					Keys.PREF_LOGIN_NAME, "");
			showPrediction(username);
			break;
		default:
			Log.w(TAG, "Unexpected option selected");
			return false;
		}
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean autosync = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				Keys.PREF_AUTOSYNC, true);
		menu.findItem(R.id.menu_autosync_on).setVisible(!autosync);
		menu.findItem(R.id.menu_autosync_off).setVisible(autosync);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();

		// check login
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final String pass = prefs.getString(Keys.PREF_LOGIN_PASS, "");
		if (pass.equals("")) {
			setDialogLogin(true);
		} else {
			onLogIn();
		}
	}

	private void setDialogInitialize(boolean enable) {
		if (enable) {
			DialogFragment fragment = new DialogFragment() {

				@Override
				public Dialog onCreateDialog(Bundle savedInstanceState) {

					setCancelable(false);

					ProgressDialog dialog = new ProgressDialog(getActivity());
					dialog.setMessage(getString(R.string.init_progress));

					return dialog;
				};
			};
			fragment.show(getSupportFragmentManager(), "init");
		} else {
			Fragment fragment = getSupportFragmentManager().findFragmentByTag("init");
			if (null != fragment) {
				((DialogFragment) fragment).dismiss();
			}
		}
	}

	private void setDialogLogin(boolean enable) {
		if (enable) {
			LoginDialog dialog = new LoginDialog();
			dialog.show(getSupportFragmentManager(), "login");
		} else {
			Fragment fragment = getSupportFragmentManager().findFragmentByTag("login");
			if (null != fragment) {
				((LoginDialog) fragment).dismiss();
			}
		}
	}

	private void setDialogLoginProgress(boolean enable) {
		if (enable) {
			DialogFragment fragment = new DialogFragment() {

				@Override
				public Dialog onCreateDialog(Bundle savedInstanceState) {

					setCancelable(false);

					ProgressDialog dialog = new ProgressDialog(getActivity());
					dialog.setMessage(getString(R.string.login_progress));

					return dialog;
				};
			};
			fragment.show(getSupportFragmentManager(), "loginProgress");
		} else {
			Fragment fragment = getSupportFragmentManager().findFragmentByTag("loginProgress");
			if (null != fragment) {
				((DialogFragment) fragment).dismiss();
			}
		}
	}

	private void showPrediction(String username) {
		final Intent prediction = new Intent(this, Prediction.class);
		prediction.putExtra(Keys.KEY_PLAYER, username);
		prediction.putExtra(Keys.KEY_POULE, this.poule);
		startActivityForResult(prediction, REQID_PREDICTION);
	}
}
