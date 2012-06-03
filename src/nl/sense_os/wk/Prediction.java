package nl.sense_os.wk;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import nl.sense_os.wk.content.Game;
import nl.sense_os.wk.content.Player;
import nl.sense_os.wk.content.Poule;
import nl.sense_os.wk.content.Round;
import nl.sense_os.wk.shared.Keys;
import nl.sense_os.wk.shared.Util;
import nl.sense_os.wk.sync.SyncAlarmReceiver;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
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
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

public class Prediction extends FragmentActivity {

	/**
	 * Adapter for list of games with scores
	 * 
	 * @author steven
	 * 
	 */
	private class MyListAdapter extends ArrayAdapter<Round> {
		private final Round round;

		public MyListAdapter(Context context, int resourceId, Round round) {
			super(context, resourceId);
			this.round = round;
		}

		@Override
		public int getCount() {
			return this.round.getGames().size();
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

			final Game game = this.round.getGames().get(position);

			// team strings
			final TextView homeTeam = (TextView) rowView.findViewById(R.id.HomeTeam);
			homeTeam.setText(game.getTeamHome());
			final TextView awayTeam = (TextView) rowView.findViewById(R.id.AwayTeam);
			awayTeam.setText(game.getTeamAway());

			// joker
			final View jokerView = rowView.findViewById(R.id.Joker);
			if (game.isJoker()) {
				jokerView.setVisibility(View.VISIBLE);
			} else {
				jokerView.setVisibility(View.INVISIBLE);
			}

			// predictions
			final TextView homePred = (TextView) rowView.findViewById(R.id.HomePrediction);
			homePred.setText(game.getPredHome());
			if (game.getPredPenaltyWinner() == 1) {
				homePred.setTypeface(null, Typeface.BOLD);
			} else {
				homePred.setTypeface(null, Typeface.NORMAL);
			}
			final TextView awayPred = (TextView) rowView.findViewById(R.id.AwayPrediction);
			awayPred.setText(game.getPredAway());
			if (game.getPredPenaltyWinner() == 2) {
				awayPred.setTypeface(null, Typeface.BOLD);
			} else {
				awayPred.setTypeface(null, Typeface.NORMAL);
			}

			// real results
			final TextView homeReal = (TextView) rowView.findViewById(R.id.HomeReal);
			homeReal.setText(game.getRealHome());
			if (game.getRealPenaltyWinner() == 1) {
				homeReal.setTypeface(null, Typeface.BOLD);
			} else {
				homeReal.setTypeface(null, Typeface.NORMAL);
			}
			final TextView awayReal = (TextView) rowView.findViewById(R.id.AwayReal);
			awayReal.setText(game.getRealAway());
			if (game.getRealPenaltyWinner() == 2) {
				awayReal.setTypeface(null, Typeface.BOLD);
			} else {
				awayReal.setTypeface(null, Typeface.NORMAL);
			}

			// format to show correct/incorrect prediction
			View predScoreView = rowView.findViewById(R.id.PredScore);
			if (null != predScoreView) {
				if (!(game.getRealHome() == null || game.getRealHome().equals(""))) {
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
				final Round round = isFinals ? player.getFinals().get(index) : player
						.getGroupStage().get(index);
				final Game game = round.getGames().get((int) id);

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
				editor.putString(Keys.PREF_SCORES, this.scores.toString());
				editor.putString(Keys.PREF_FIXTURES, this.fixtures.toString());
				editor.commit();

				// show updated scores on the UI
				player.setGroupStage(Util.getGroupStage(Prediction.this,
						Prediction.this.player.getUsername()));
				player.setFinals(Util.getFinals(Prediction.this,
						Prediction.this.player.getUsername()));
				player.setJokers(Util.getJokers(Prediction.this,
						Prediction.this.player.getUsername()));
				poule.players.put(name, player);

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

			setDialogSynchronize(false);

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
			setDialogSynchronize(true);
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

			setDialogWrite(false);

			if (true == success) {

				Prediction.this.poule.players.put(player.getUsername(), player);
				prepareResult();

				populateTabs();
			} else {
				Toast.makeText(Prediction.this, "Save failed. Cause: " + error, Toast.LENGTH_LONG)
						.show();
			}
		}

		@Override
		protected void onPreExecute() {
			setDialogWrite(true);
		}
	}

	private static final String TAG = "WK Prediction";
	private boolean isFinals;
	private Player player;
	private Poule poule;
	private int selectedTab;
	private TabHost tabs;
	private boolean editable;

	private Dialog createDialogEditScore(final Game game, final Round round, final long id,
			final int index) {

		// inflate view
		View view = getLayoutInflater().inflate(R.layout.edit_dialog, null, false);

		// team names
		((TextView) view.findViewById(R.id.TeamHome)).setText(game.getTeamHome());
		((TextView) view.findViewById(R.id.TeamAway)).setText(game.getTeamAway());

		// prediction
		final EditText scoreHome = (EditText) view.findViewById(R.id.ScoreHome);
		scoreHome.setText(game.getPredHome());
		final EditText scoreAway = (EditText) view.findViewById(R.id.ScoreAway);
		scoreAway.setText(game.getPredAway());
		final CheckBox joker = (CheckBox) view.findViewById(R.id.JokerCB);
		joker.setChecked(game.isJoker());
		joker.setEnabled(game.isJoker() || (this.player.getJokers() > 0));

		// show radio group for penalty winners in the finals
		RadioGroup penaltyRadios = (RadioGroup) view.findViewById(R.id.RadioGroup01);
		final RadioButton homeRadio = ((RadioButton) view.findViewById(R.id.HomeRadioButton));
		final RadioButton awayRadio = ((RadioButton) view.findViewById(R.id.AwayRadioButton));
		if (this.isFinals) {
			penaltyRadios.setVisibility(View.VISIBLE);
			if (game.getPredPenaltyWinner() == 1) {
				homeRadio.setChecked(true);
			} else if (game.getPredPenaltyWinner() == 2) {
				awayRadio.setChecked(true);
			}
		} else {
			view.findViewById(R.id.RadioGroup01).setVisibility(View.GONE);
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.edit_prediction);
		builder.setView(view);

		// set up save button
		if (game.getRealHome().equals("")) {
			builder.setPositiveButton(R.string.save, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					final String newHome = scoreHome.getText().toString();
					final String newAway = scoreAway.getText().toString();
					final boolean newJoker = joker.isChecked();

					// check for penalty winner
					int newPenaltyWinner = -1;
					if (Prediction.this.isFinals && (newHome.equals(newAway))) {
						if (homeRadio.isChecked()) {
							newPenaltyWinner = 1;
						} else if (awayRadio.isChecked()) {
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
					if (!game.getPredHome().equals(newHome) || !game.getPredAway().equals(newAway)
							|| !(newJoker == game.isJoker())
							|| !(newPenaltyWinner == game.getPredPenaltyWinner())) {
						game.setPredHome(newHome);
						game.setPredAway(newAway);
						game.setPredPenaltyWinner(newPenaltyWinner);

						round.getGames().set((int) id, game);
						if (Prediction.this.isFinals) {
							Prediction.this.player.getFinals().set(index, round);
						} else {
							Prediction.this.player.getGroupStage().set(index, round);
						}

						// store new value
						dialog.dismiss();
						final SharedPreferences prefs = PreferenceManager
								.getDefaultSharedPreferences(Prediction.this);
						new WriteScoreTask().execute(prefs.getString(Keys.PREF_LOGIN_NAME, ""),
								createWriteValue());
					} else {
						// ignore save request for unchanged values
						dialog.dismiss();
					}
				}
			});

		} else {
			// no save button
			scoreHome.setEnabled(false);
			scoreAway.setEnabled(false);
			joker.setEnabled(false);
			if (this.isFinals) {
				penaltyRadios.setEnabled(false);
				homeRadio.setEnabled(false);
				awayRadio.setEnabled(false);
			}
		}

		// cancel button
		builder.setNegativeButton(android.R.string.cancel, null);

		return builder.create();
	}

	private String createWriteValue() {
		String value = "";

		// first the group stage
		for (final Round round : this.player.getGroupStage()) {
			value += Util.gamesToCsv(round.getGames());
		}

		// finals
		for (final Round round : this.player.getFinals()) {
			value += "\n" + Util.gamesToCsv(round.getGames());
		}

		return value;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.selectedTab = 0;
		this.isFinals = false;

		Intent intent = getIntent();
		this.poule = (Poule) intent.getParcelableExtra(Keys.KEY_POULE);
		final String playerName = intent.getStringExtra(Keys.KEY_PLAYER);
		this.player = poule.players.get(playerName);

		String myName = PreferenceManager.getDefaultSharedPreferences(this).getString(
				Keys.PREF_LOGIN_NAME, "");
		this.editable = playerName.equals(myName) ? true : false;

		// set title
		setTitle(player.getFullName());
		setContentView(R.layout.prediction);

		populateTabs();
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
		final String name = prefs.getString(Keys.PREF_LOGIN_NAME, "");
		switch (item.getItemId()) {
		case R.id.menu_sync:
			final String pass = prefs.getString(Keys.PREF_LOGIN_PASS, "");
			new SyncTask().execute(name, pass);
			break;
		case R.id.menu_autosync_off:
			Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			editor.putBoolean(Keys.PREF_AUTOSYNC, false);
			editor.commit();

			// set new alarm
			SyncAlarmReceiver.stopSynchronizing(this);

			break;
		case R.id.menu_autosync_on:
			editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			editor.putBoolean(Keys.PREF_AUTOSYNC, true);
			editor.commit();

			// set new alarm
			SyncAlarmReceiver.startSynchronizing(this);

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
				Keys.PREF_AUTOSYNC, false);
		menu.findItem(R.id.menu_autosync_on).setVisible(!autosync);
		menu.findItem(R.id.menu_autosync_off).setVisible(autosync);
		menu.findItem(R.id.menu_groups).setVisible(isFinals);
		menu.findItem(R.id.menu_finals).setVisible(!isFinals);
		return true;
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

		final ArrayList<Round> rounds = this.isFinals ? this.player.getFinals() : this.player
				.getGroupStage();
		for (final Round round : rounds) {

			final TabSpec spec = this.tabs.newTabSpec("round_" + round.getId());
			spec.setContent(new TabHost.TabContentFactory() {

				@Override
				public View createTabContent(String tag) {
					final LinearLayout linLayout = new LinearLayout(Prediction.this);
					linLayout.setOrientation(LinearLayout.VERTICAL);

					final ListView gameList = new ListView(Prediction.this);
					final String[] games = new String[round.getGames().size()];
					for (int i = 0; i < games.length; i++) {
						final int homeId = round.getGames().get(i).getIdHome();
						final int awayId = round.getGames().get(i).getIdAway();
						games[i] = round.getTeams().get(homeId - 1) + " - "
								+ round.getTeams().get(awayId - 1);
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
			spec.setIndicator(round.getId());

			this.tabs.addTab(spec);
		}

		// select the right tab (if activity was already running)
		this.tabs.setCurrentTab(this.selectedTab);

		this.tabs.setVisibility(View.VISIBLE);
	}

	private void prepareResult() {
		final Intent data = new Intent();
		data.putExtra(Keys.KEY_POULE, this.poule);
		setResult(RESULT_OK, data);
	}

	private void showFinals() {
		Prediction.this.isFinals = true;
		Prediction.this.selectedTab = 0;
		Prediction.this.tabs.setCurrentTab(0);
		populateTabs();

		updateActionBar();
	}

	private void showGroups() {
		Prediction.this.isFinals = false;
		Prediction.this.selectedTab = 0;
		Prediction.this.tabs.setCurrentTab(0);
		populateTabs();

		updateActionBar();
	}

	private void setDialogSynchronize(boolean enable) {
		if (enable) {
			DialogFragment fragment = new DialogFragment() {

				@Override
				public Dialog onCreateDialog(Bundle savedInstanceState) {

					setCancelable(false);

					ProgressDialog dialog = new ProgressDialog(getActivity());
					dialog.setMessage(getString(R.string.sync_progress));

					return dialog;
				};
			};
			fragment.show(getSupportFragmentManager(), "sync");
		} else {
			Fragment fragment = getSupportFragmentManager().findFragmentByTag("sync");
			if (null != fragment) {
				((DialogFragment) fragment).dismiss();
			}
		}
	}

	private void setDialogWrite(boolean enable) {
		if (enable) {
			DialogFragment fragment = new DialogFragment() {

				@Override
				public Dialog onCreateDialog(Bundle savedInstanceState) {

					setCancelable(false);

					ProgressDialog dialog = new ProgressDialog(getActivity());
					dialog.setMessage(getString(R.string.write_progress));

					return dialog;
				};
			};
			fragment.show(getSupportFragmentManager(), "write");
		} else {
			Fragment fragment = getSupportFragmentManager().findFragmentByTag("write");
			if (null != fragment) {
				((DialogFragment) fragment).dismiss();
			}
		}
	}

	@TargetApi(11)
	private void updateActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			invalidateOptionsMenu();
		}
	}
}
