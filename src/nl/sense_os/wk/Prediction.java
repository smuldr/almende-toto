package nl.sense_os.wk;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

public class Prediction extends Activity {

	private class MyListAdapter extends ArrayAdapter<Round> {
		private final Round round;

		public MyListAdapter(Context context, int resourceId, Round round) {
			super(context, resourceId);
			this.round = round;
		}

		@Override
		public int getCount() {
			return this.round.games.size();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View rowView = null;

			if (convertView == null) {
				final LayoutInflater aInflater = LayoutInflater.from(getContext());
				rowView = aInflater.inflate(R.layout.round_row, parent, false);
			} else {
				rowView = convertView;
			}

			final Game game = this.round.games.get(position);

			// team strings
			final TextView homeTeam = (TextView) rowView.findViewById(R.id.HomeTeam);
			homeTeam.setText(game.teamHome);
			final TextView awayTeam = (TextView) rowView.findViewById(R.id.AwayTeam);
			awayTeam.setText(game.teamAway);

			// joker
			final View jokerView = rowView.findViewById(R.id.Joker);
			if (game.joker) {
				jokerView.setVisibility(View.VISIBLE);
			} else {
				jokerView.setVisibility(View.INVISIBLE);
			}

			// predictions
			final TextView homePred = (TextView) rowView.findViewById(R.id.HomePrediction);
			homePred.setText(game.predHome);
			if (game.predPenaltyWinner == 1) {
				homePred.setTypeface(null, Typeface.BOLD);
			} else {
				homePred.setTypeface(null, Typeface.NORMAL);
			}
			final TextView awayPred = (TextView) rowView.findViewById(R.id.AwayPrediction);
			awayPred.setText(game.predAway);
			if (game.predPenaltyWinner == 2) {
				awayPred.setTypeface(null, Typeface.BOLD);
			} else {
				awayPred.setTypeface(null, Typeface.NORMAL);
			}

			// real results
			final TextView homeReal = (TextView) rowView.findViewById(R.id.HomeReal);
			homeReal.setText(game.realHome);
			if (game.realPenaltyWinner == 1) {
				homeReal.setTypeface(null, Typeface.BOLD);
			} else {
				homeReal.setTypeface(null, Typeface.NORMAL);
			}
			final TextView awayReal = (TextView) rowView.findViewById(R.id.AwayReal);
			awayReal.setText(game.realAway);
			if (game.realPenaltyWinner == 2) {
				awayReal.setTypeface(null, Typeface.BOLD);
			} else {
				awayReal.setTypeface(null, Typeface.NORMAL);
			}

			// format to show correct/incorrect prediction
			View predScoreView = rowView.findViewById(R.id.PredScore);
			if (null != predScoreView) {
				if (!(game.realHome == null || game.realHome.equals(""))) {
					if (game.isTotoCorrect()) {
						predScoreView.setBackgroundColor(0xFF7FFF7F);
					} else {
						predScoreView.setBackgroundColor(0xFFFF3F3F);
					}
				} else {
					predScoreView.setBackgroundColor(0x00000000);
				}
			} else {
				Log.w(TAG, "Cannot find prediction score cell view!");
				Log.d(TAG, "Troublesome game: " + game.teamHome + " - " + game.teamAway);
			}

			return rowView;
		}
	}

	/**
	 * Listener for clicks on the list with possible answers.
	 */
	private class MyListListener implements AdapterView.OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, final long id) {

			if (Prediction.this.editable) {
				// get tab activity label from the view
				final int index = Prediction.this.tabs.getCurrentTab();
				final Round round = Prediction.this.isFinals ? Prediction.this.player.finals
						.get(index) : Prediction.this.player.groupStage.get(index);
				final Game game = round.games.get((int) id);

				// show edit dialog
				final Dialog editScores = createDialogEditScore(game, round, id, index);
				editScores.show();
			}
		}
	}

	private class SyncTask extends AsyncTask<String, Void, Boolean> {
		private static final String URL = "http://my.sense-os.nl/ek2012/server.php?q=";
		private String error;
		private JSONObject fixtures;
		private String name;
		private JSONObject scores;

		@Override
		protected Boolean doInBackground(String... params) {
			this.name = params[0];
			final String pass = params[1];

			final String changeUrl = jsonLoginUrl(this.name, pass);

			final HttpClient httpClient = new DefaultHttpClient();
			String response = "";
			try {
				final HttpGet request = new HttpGet(changeUrl);

				Log.d(TAG,
						"HTTP execute: " + URLDecoder.decode(request.getRequestLine().toString()));
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

				// save scores and fixtures in preferences
				final Editor editor = PreferenceManager
						.getDefaultSharedPreferences(Prediction.this).edit();
				editor.putString(Wk.PREF_SCORES, this.scores.toString());
				editor.putString(Wk.PREF_FIXTURES, this.fixtures.toString());
				editor.commit();

				// show updated scores on the UI
				Prediction.this.player.groupStage = Util.getGroupStage(Prediction.this,
						Prediction.this.player.username);
				Prediction.this.player.finals = Util.getFinals(Prediction.this,
						Prediction.this.player.username);
				Prediction.this.player.jokers = Util.getJokers(Prediction.this,
						Prediction.this.player.username);
				Prediction.this.poule.players.put(this.name, Prediction.this.player);

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

			removeDialog(DIALOG_SYNC_PROGRESS);

			if (true == success) {
				prepareResult();
				populateTabs();
			} else {
				Toast.makeText(Prediction.this, "Login failed. Cause: " + this.error,
						Toast.LENGTH_LONG).show();
			}
		}

		@Override
		protected void onPreExecute() {
			showDialog(DIALOG_SYNC_PROGRESS);
		}

		private boolean parseJson(String response) {
			boolean success = false;

			// create JSON object from response
			JSONObject responseObj = null;
			try {
				responseObj = new JSONObject(response);
			} catch (final JSONException e) {
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

	private class WriteScoreTask extends AsyncTask<String, Void, Boolean> {
		private static final String URL = "http://my.sense-os.nl/ek2012/server.php?q=";
		private String error;

		@Override
		protected Boolean doInBackground(String... params) {
			final String name = params[0];
			final String games = params[1];

			final String jsonUrl = jsonWriteUrl(name, games);

			final HttpClient httpClient = new DefaultHttpClient();
			String response = "";
			try {
				final HttpGet request = new HttpGet(jsonUrl);

				Log.d(TAG,
						"HTTP execute: " + URLDecoder.decode(request.getRequestLine().toString()));
				final ResponseHandler<String> responseHandler = new BasicResponseHandler();
				response = httpClient.execute(request, responseHandler);

			} catch (final IOException e) {
				Log.e(TAG, "IOException in JsonTask.", e);
				return false;
			} catch (final IllegalAccessError e) {
				Log.e(TAG, "IllegalAccessError in JsonTask.", e);
				return false;
			}

			if (response.equals("")) {
				return true;
			} else {
				return false;
			}
		}

		private String jsonWriteUrl(String name, String scores) {

			final JSONObject json = new JSONObject();
			try {
				json.put("type", "write_score");
				json.put("player", name);
				json.put("value", scores);
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

			removeDialog(DIALOG_WRITE_PROGRESS);

			if (true == success) {

				Prediction.this.poule.players.put(Prediction.this.player.username,
						Prediction.this.player);
				prepareResult();

				populateTabs();
			} else {
				Toast.makeText(Prediction.this, "Save failed. Cause: " + this.error,
						Toast.LENGTH_LONG).show();
			}
		}

		@Override
		protected void onPreExecute() {
			showDialog(DIALOG_WRITE_PROGRESS);
		}
	}

	private static final int DIALOG_INITIALIZING = 1;
	private static final int DIALOG_SYNC_PROGRESS = 2;
	private static final int DIALOG_WRITE_PROGRESS = 3;
	private static final String TAG = "WK Prediction";
	private boolean isFinals;
	private Player player;
	private Poule poule;
	private int selectedTab;
	private TabHost tabs;
	private boolean editable;

	private Dialog createDialogEditScore(final Game game, final Round round, final long id,
			final int index) {

		final Dialog dialog = new Dialog(Prediction.this);
		dialog.setTitle("Edit prediction");
		dialog.setContentView(R.layout.edit_dialog);

		// fill dialog with current scores
		((TextView) dialog.findViewById(R.id.TeamHome)).setText(game.teamHome);
		((TextView) dialog.findViewById(R.id.TeamAway)).setText(game.teamAway);

		final EditText scoreHome = (EditText) dialog.findViewById(R.id.ScoreHome);
		scoreHome.setText(game.predHome);
		final EditText scoreAway = (EditText) dialog.findViewById(R.id.ScoreAway);
		scoreAway.setText(game.predAway);
		final CheckBox joker = (CheckBox) dialog.findViewById(R.id.JokerCB);
		joker.setChecked(game.joker);
		joker.setEnabled(game.joker || (this.player.jokers > 0));

		// show radio group for penalty winners in the finals
		if (this.isFinals) {
			dialog.findViewById(R.id.RadioGroup01).setVisibility(View.VISIBLE);
			if (game.predPenaltyWinner == 1) {
				((RadioButton) dialog.findViewById(R.id.HomeRadioButton)).setChecked(true);
			} else if (game.predPenaltyWinner == 2) {
				((RadioButton) dialog.findViewById(R.id.AwayRadioButton)).setChecked(true);
			}
		} else {
			dialog.findViewById(R.id.RadioGroup01).setVisibility(View.GONE);
		}

		// enable option to save if the game is not finished yet
		if (game.realHome.equals("")) {
			final Button save = (Button) dialog.findViewById(R.id.SaveButton);
			save.setEnabled(true);
			save.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					final String newHome = scoreHome.getText().toString();
					final String newAway = scoreAway.getText().toString();
					final boolean newJoker = joker.isChecked();

					// check for penalty winner
					int newPenaltyWinner = -1;
					if (Prediction.this.isFinals && (newHome.equals(newAway))) {
						final RadioButton homeButton = (RadioButton) dialog
								.findViewById(R.id.HomeRadioButton);
						final RadioButton awayButton = (RadioButton) dialog
								.findViewById(R.id.AwayRadioButton);

						if (homeButton.isChecked()) {
							newPenaltyWinner = 1;
						} else if (awayButton.isChecked()) {
							newPenaltyWinner = 2;
						} else {
							Log.w(TAG, "No penalty winner selected.");
							newPenaltyWinner = -1;

							Toast.makeText(Prediction.this, "Select penalty winner", 1).show();
							// do not close dialog
							return;
						}
					}

					// write new scores
					if (!game.predHome.equals(newHome) || !game.predAway.equals(newAway)
							|| !(newJoker == game.joker)
							|| !(newPenaltyWinner == game.predPenaltyWinner)) {
						game.predHome = newHome;
						game.predAway = newAway;
						game.predPenaltyWinner = newPenaltyWinner;

						round.games.set((int) id, game);
						if (Prediction.this.isFinals) {
							Prediction.this.player.finals.set(index, round);
						} else {
							Prediction.this.player.groupStage.set(index, round);
						}

						// store new value
						dialog.dismiss();
						final SharedPreferences prefs = PreferenceManager
								.getDefaultSharedPreferences(Prediction.this);
						new WriteScoreTask().execute(prefs.getString(Wk.PREF_LOGIN_NAME, ""),
								createWriteValue());
					} else {
						// ignore save request for unchanged values
						dialog.dismiss();
					}
				}
			});
		} else {
			scoreHome.setEnabled(false);
			scoreAway.setEnabled(false);
			joker.setEnabled(false);
			final Button save = (Button) dialog.findViewById(R.id.SaveButton);
			save.setEnabled(false);
			if (this.isFinals) {
				dialog.findViewById(R.id.RadioGroup01).setEnabled(false);
				dialog.findViewById(R.id.HomeRadioButton).setEnabled(false);
				dialog.findViewById(R.id.AwayRadioButton).setEnabled(false);
			}
		}
		final Button back = (Button) dialog.findViewById(R.id.BackButton);
		back.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		return dialog;
	}

	private Dialog createDialogInitializing() {
		final ProgressDialog dialog = new ProgressDialog(this);
		dialog.setMessage("Initializing WK poule...");
		dialog.setCancelable(false);
		return dialog;
	}

	private Dialog createDialogLoginProgress() {
		final ProgressDialog dialog = new ProgressDialog(this);
		dialog.setMessage("Synchronizing data...");
		dialog.setCancelable(false);
		return dialog;
	}

	private Dialog createDialogWriteProgress() {
		final ProgressDialog dialog = new ProgressDialog(this);
		dialog.setMessage("Writing scores...");
		dialog.setCancelable(false);
		return dialog;
	}

	private String createWriteValue() {
		String value = "";

		// first the group stage
		for (final Round round : this.player.groupStage) {
			value += Util.gamesToCsv(round.games);
		}

		// finals
		for (final Round round : this.player.finals) {
			value += "\n" + Util.gamesToCsv(round.games);
		}

		return value;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Object retainedData = getLastNonConfigurationInstance();
		this.selectedTab = 0;
		this.isFinals = false;
		if (null != retainedData) {
			final Object[] data = (Object[]) retainedData;
			this.selectedTab = (Integer) data[0];
			this.isFinals = (Boolean) data[1];
			this.poule = (Poule) data[2];
			this.player = (Player) data[3];
			this.editable = (Boolean) data[4];
		} else {
			Intent intent = getIntent();
			this.poule = (Poule) intent.getParcelableExtra(Wk.KEY_POULE);
			final String playerName = intent.getStringExtra(Wk.KEY_PLAYER);
			this.player = poule.players.get(playerName);

			String myName = PreferenceManager.getDefaultSharedPreferences(this).getString(
					Wk.PREF_LOGIN_NAME, "");
			this.editable = playerName.equals(myName) ? true : false;
		}

		// requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setTitle(player.fullName);
		setContentView(R.layout.prediction);

		populateTabs();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
		case DIALOG_INITIALIZING:
			dialog = createDialogInitializing();
			break;
		case DIALOG_SYNC_PROGRESS:
			dialog = createDialogLoginProgress();
			break;
		case DIALOG_WRITE_PROGRESS:
			dialog = createDialogWriteProgress();
			break;
		default:
			Log.w(TAG, "Cannot create dialog! ID: " + id);
			break;
		}
		return dialog;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_prediction, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final String name = prefs.getString(Wk.PREF_LOGIN_NAME, "");
		switch (item.getItemId()) {
		case R.id.menu_sync:
			final String pass = prefs.getString(Wk.PREF_LOGIN_PASS, "");
			new SyncTask().execute(name, pass);
			break;
		case R.id.menu_autosync_off:
			Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			editor.putBoolean(Wk.PREF_AUTOSYNC, false);
			editor.commit();

			// set new alarm
			AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			PendingIntent operation = PendingIntent.getBroadcast(this, WkSyncer.REQID_SYNC,
					new Intent("nl.sense_os.wk.Sync"), 0);
			mgr.cancel(operation);
			break;
		case R.id.menu_autosync_on:
			editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			editor.putBoolean(Wk.PREF_AUTOSYNC, true);
			editor.commit();

			// set new alarm
			mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			operation = PendingIntent.getBroadcast(this, WkSyncer.REQID_SYNC, new Intent(
					"nl.sense_os.wk.Sync"), 0);
			mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), operation);
			break;
		case R.id.menu_groups:
			showGroups();
			break;
		case R.id.menu_finals:
			showFinals();
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
				Wk.PREF_AUTOSYNC, false);
		menu.findItem(R.id.menu_autosync_on).setVisible(!autosync);
		menu.findItem(R.id.menu_autosync_off).setVisible(autosync);
		menu.findItem(R.id.menu_groups).setVisible(isFinals);
		menu.findItem(R.id.menu_finals).setVisible(!isFinals);
		return true;
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		final Object[] saveMe = { this.tabs.getCurrentTab(), this.isFinals, this.poule,
				this.player, this.editable };
		return saveMe;
	}

	/**
	 * Adds tabs to the TabHost. The tab labels are automatically shown in the TabWidget, which are
	 * linked to specific content.
	 */
	private void populateTabs() {
		// Log.d(TAG,"populateTabs");
		this.tabs = (TabHost) findViewById(R.id.tabhost);
		// Log.d(TAG,"Found TabHost: " + (this.tabs != null));
		this.tabs.setup();
		// Log.d(TAG,"TabHost setup OK");
		this.selectedTab = this.tabs.getCurrentTab();
		// Log.d(TAG,"Selected tab: " + this.selectedTab);
		this.tabs.setCurrentTab(0);
		this.tabs.setVisibility(View.GONE);
		// Log.d(TAG,"setCurrentTab OK");
		this.tabs.clearAllTabs();
		// Log.d(TAG,"clearAllTabs OK");
		final OnItemClickListener listener = new MyListListener();

		if (null == this.player) {
			return;
		}

		Log.d(TAG, "Prediction for " + player.fullName + " (" + player.username + ")");

		final ArrayList<Round> rounds = this.isFinals ? this.player.finals : this.player.groupStage;
		for (final Round round : rounds) {

			final TabSpec spec = this.tabs.newTabSpec("round_" + round.id);
			spec.setContent(new TabHost.TabContentFactory() {

				@Override
				public View createTabContent(String tag) {
					final LinearLayout linLayout = new LinearLayout(Prediction.this);
					linLayout.setOrientation(LinearLayout.VERTICAL);

					final ListView gameList = new ListView(Prediction.this);
					final String[] games = new String[round.games.size()];
					for (int i = 0; i < games.length; i++) {
						final int homeId = round.games.get(i).idHome;
						final int awayId = round.games.get(i).idAway;
						games[i] = round.teams.get(homeId - 1) + " - "
								+ round.teams.get(awayId - 1);
					}
					final ArrayAdapter<Round> adapter = new MyListAdapter(Prediction.this,
							R.id.HomeTeam, round);
					gameList.setAdapter(adapter);
					gameList.setOnItemClickListener(listener);

					gameList.setLayoutParams(new LayoutParams(-1, 0, 1));
					linLayout.addView(gameList);

					return linLayout;
				}
			});
			spec.setIndicator(round.id);

			this.tabs.addTab(spec);
		}

		// select the right tab (if activity was already running)
		this.tabs.setCurrentTab(this.selectedTab);

		this.tabs.setVisibility(View.VISIBLE);
	}

	private void showGroups() {
		Prediction.this.isFinals = false;
		Prediction.this.selectedTab = 0;
		Prediction.this.tabs.setCurrentTab(0);
		populateTabs();

		invalidateOptionsMenu();
	}

	private void showFinals() {
		Prediction.this.isFinals = true;
		Prediction.this.selectedTab = 0;
		Prediction.this.tabs.setCurrentTab(0);
		populateTabs();

		invalidateOptionsMenu();
	}

	private void prepareResult() {
		final Intent data = new Intent();
		data.putExtra(Wk.KEY_POULE, this.poule);
		setResult(RESULT_OK, data);
	}
}
