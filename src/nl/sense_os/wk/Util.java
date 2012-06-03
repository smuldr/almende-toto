package nl.sense_os.wk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class Util {
	private static final String TAG = "WK Util";

	public static ArrayList<Game> csvToGames(String csvPred, String csvReal) {

		if (csvPred.length() == 0) {
			Log.w(TAG, "Prediction is empty!");
			csvPred = csvReal;
		}

		ArrayList<Game> games = new ArrayList<Game>();

		String homeToken = "";
		String awayToken = "";
		String jokerToken = "";
		String currToken = "";
		int commasFound = 0;
		for (int i = 0; i < csvPred.length(); i++) {
			char c = csvPred.charAt(i);
			currToken += c;
			if (c == ',') {
				switch (commasFound) {
				case 0:
					homeToken = currToken.substring(0, currToken.length() - 1);
					currToken = "";
					break;
				case 1:
					awayToken = currToken.substring(0, currToken.length() - 1);
					currToken = "";
					break;
				case 2:
					jokerToken = currToken.substring(0, currToken.length() - 1);
					break;
				}
				commasFound++;
			}

			if (commasFound == 3) {
				final Game game = new Game();

				// find penalty winner prediction
				if (homeToken.contains(":w")) {
					game.predPenaltyWinner = 1;
					game.predHome = homeToken.substring(0, homeToken.indexOf(":")).trim();
					game.predAway = awayToken.trim();
				} else if (awayToken.contains(":w")) {
					game.predPenaltyWinner = 2;
					game.predAway = awayToken.substring(0, awayToken.indexOf(":")).trim();
					game.predHome = homeToken.trim();
				} else {
					game.predHome = homeToken.trim();
					game.predAway = awayToken.trim();
					game.predPenaltyWinner = -1;
				}
				game.joker = jokerToken.equals("j");

				games.add(game);
				commasFound = 0;
				currToken = "";
			}
		}

		currToken = "";
		commasFound = 0;
		int gameIndex = 0;
		for (int i = 0; i < csvReal.length(); i++) {
			char c = csvReal.charAt(i);

			// sometimes there are more commas than games?
			if (gameIndex >= games.size()) {
				break;
			}

			currToken += c;
			if (c == ',') {
				switch (commasFound) {
				case 0:
					homeToken = currToken.substring(0, currToken.length() - 1);
					currToken = "";
					break;
				case 1:
					awayToken = currToken.substring(0, currToken.length() - 1);
					currToken = "";
					break;
				case 2:
					jokerToken = currToken.substring(0, currToken.length() - 1);
					break;
				}
				commasFound++;
			}

			if (commasFound == 3) {
				Game game = games.get(gameIndex);

				// find penalty winner result
				if (homeToken.contains(":w")) {
					game.realPenaltyWinner = 1;
					game.realHome = homeToken.substring(0, homeToken.indexOf(":")).trim();
					game.realAway = awayToken.trim();
				} else if (awayToken.contains(":w")) {
					game.realPenaltyWinner = 2;
					game.realAway = awayToken.substring(0, awayToken.indexOf(":")).trim();
					game.realHome = homeToken.trim();
				} else {
					game.realHome = homeToken.trim();
					game.realAway = awayToken.trim();
					game.realPenaltyWinner = -1;
				}

				games.set(gameIndex, game);
				commasFound = 0;
				currToken = "";
				gameIndex++;
			}
		}
		return games;
	}

	public static String gamesToCsv(ArrayList<Game> games) {
		String csv = "";

		for (Game game : games) {
			String homeToken = game.predHome + (game.predPenaltyWinner == 1 ? ":w" : "");
			String awayToken = game.predAway + (game.predPenaltyWinner == 2 ? ":w" : "");
			csv += homeToken + "," + awayToken + "," + (game.joker ? "j" : "") + ",";
		}

		return csv;
	}

	public static int getJokers(Context context, String username) {
		Log.d(TAG, "Get jokers for " + username);

		// get important data from preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String scoreString = prefs.getString(Wk.PREF_SCORES, "");

		int availableJokers = -1;

		// get available jokers and the predictions (including any jokers
		// already used)
		String poulesPred = "", finals4Pred = "", finals2Pred = "", finals1Pred = "";
		String poulesReal = "", finals4Real = "", finals2Real = "", finals1Real = "";
		try {
			JSONObject scores = new JSONObject(scoreString);
			JSONObject scoresList = scores.getJSONObject("list");

			// my own predictions
			JSONObject predScores = scoresList.getJSONObject(username);
			poulesPred = predScores.getString("poules");
			finals4Pred = predScores.getString("finals4");
			finals2Pred = predScores.getString("finals2");
			finals1Pred = predScores.getString("finals1");
			availableJokers = Integer.parseInt(predScores.getString("jokers"));

			JSONObject realScores = scores.getJSONObject("real");
			poulesReal = realScores.getString("poules");
			finals4Real = realScores.getString("finals4");
			finals2Real = realScores.getString("finals2");
			finals1Real = realScores.getString("finals1");
		} catch (JSONException e) {
			Log.e(TAG, "JSONException getting scores from JSON", e);
		}

		// put scores in the games
		Log.d(TAG, "Parse group stage jokers");
		final ArrayList<Game> groupStage = Util.csvToGames(poulesPred, poulesReal);
		for (Game game : groupStage) {
			if (game.joker) {
				availableJokers--;
			}
		}

		Log.d(TAG, "Parse quarter finals jokers");
		final ArrayList<Game> finals4 = Util.csvToGames(finals4Pred, finals4Real);
		for (Game game : finals4) {
			if (game.joker) {
				availableJokers--;
			}
		}

		Log.d(TAG, "Parse semi finals jokers");
		final ArrayList<Game> finals2 = Util.csvToGames(finals2Pred, finals2Real);
		for (Game game : finals2) {
			if (game.joker) {
				availableJokers--;
			}
		}

		Log.d(TAG, "Parse final jokers");
		final ArrayList<Game> finals1 = Util.csvToGames(finals1Pred, finals1Real);
		for (Game game : finals1) {
			if (game.joker) {
				availableJokers--;
			}
		}

		return availableJokers;
	}

	public static ArrayList<Round> getGroupStage(Context context, String username) {
		Log.d(TAG, "Get group stage for " + username);

		// get important data from preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String jsonScores = prefs.getString(Wk.PREF_SCORES, "");
		String jsonFixtures = prefs.getString(Wk.PREF_FIXTURES, "");

		ArrayList<Round> groupStage = new ArrayList<Round>();
		try {
			JSONObject fixtures = new JSONObject(jsonFixtures);

			JSONArray finalsArray = fixtures.getJSONArray("poule");
			for (int i = 0; i < finalsArray.length(); i++) {
				groupStage.add(Round.parseRound(finalsArray.getJSONObject(i)));
			}
		} catch (JSONException e) {
			Log.e(TAG, "JSONException getting group stage games from JSON", e);
			return null;
		}

		// get the predictions and real scores
		String poulesPred = "";
		String poulesReal = "";
		try {
			JSONObject scores = new JSONObject(jsonScores);
			JSONObject scoresList = scores.getJSONObject("list");

			// my own predictions
			JSONObject predScores = scoresList.getJSONObject(username);
			poulesPred = predScores.getString("poules");

			// real scores
			JSONObject realScores = scores.getJSONObject("real");
			poulesReal = realScores.getString("poules");
		} catch (JSONException e) {
			Log.e(TAG, "JSONException getting scores from JSON", e);
			return null;
		}

		// put scores in the games
		final ArrayList<Game> groupStageGames = Util.csvToGames(poulesPred, poulesReal);
		int roundIndex = 0;
		int gameIndex = 0;
		for (Game game : groupStageGames) {
			Round round = groupStage.get(roundIndex);

			Game noScores = round.games.get(gameIndex);
			noScores.predHome = game.predHome;
			noScores.predAway = game.predAway;
			noScores.predPenaltyWinner = game.predPenaltyWinner;
			noScores.realHome = game.realHome;
			noScores.realAway = game.realAway;
			noScores.realPenaltyWinner = game.realPenaltyWinner;
			noScores.joker = game.joker;
			round.games.set(gameIndex, noScores);
			groupStage.set(roundIndex, round);
			gameIndex++;

			if (gameIndex >= round.games.size()) {
				roundIndex++;
				gameIndex = 0;
			}

			if (roundIndex >= groupStage.size()) {
				break;
			}
		}

		return groupStage;
	}

	public static ArrayList<Round> getFinals(Context context, String username) {
		Log.d(TAG, "Get finals for " + username);

		// get important data from preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String jsonScores = prefs.getString(Wk.PREF_SCORES, "");
		String jsonFixtures = prefs.getString(Wk.PREF_FIXTURES, "");

		ArrayList<Round> finals = new ArrayList<Round>();
		try {
			JSONObject fixtures = new JSONObject(jsonFixtures);

			JSONArray pouleArray = fixtures.getJSONArray("finals");
			for (int i = 0; i < pouleArray.length(); i++) {
				finals.add(Round.parseRound(pouleArray.getJSONObject(i)));
			}
		} catch (JSONException e) {
			Log.e(TAG, "JSONException getting finals games from JSON", e);
			return null;
		}

		// get the predictions and real scores
		String finals4Pred = "", finals2Pred = "", finals1Pred = "";
		String finals4Real = "", finals2Real = "", finals1Real = "";
		try {
			JSONObject scores = new JSONObject(jsonScores);
			JSONObject scoresList = scores.getJSONObject("list");

			// my own predictions
			JSONObject predScores = scoresList.getJSONObject(username);
			finals4Pred = predScores.getString("finals4");
			finals2Pred = predScores.getString("finals2");
			finals1Pred = predScores.getString("finals1");

			// real scores
			JSONObject realScores = scores.getJSONObject("real");
			finals4Real = realScores.getString("finals4");
			finals2Real = realScores.getString("finals2");
			finals1Real = realScores.getString("finals1");
		} catch (JSONException e) {
			Log.e(TAG, "JSONException getting scores from JSON", e);
			return null;
		}

		// put scores in the games

		final ArrayList<Game> finals4Games = Util.csvToGames(finals4Pred, finals4Real);
		final Round finals4NoScores = finals.get(0);
		for (int i = 0; i < finals4NoScores.games.size(); i++) {
			Game noTeams = finals4Games.get(i);
			Game noScores = finals4NoScores.games.get(i);
			noScores.predHome = noTeams.predHome;
			noScores.predAway = noTeams.predAway;
			noScores.predPenaltyWinner = noTeams.predPenaltyWinner;
			noScores.realHome = noTeams.realHome;
			noScores.realAway = noTeams.realAway;
			noScores.realPenaltyWinner = noTeams.realPenaltyWinner;
			finals4NoScores.games.set(i, noScores);
		}
		finals.set(0, finals4NoScores);

		final ArrayList<Game> finals2Games = Util.csvToGames(finals2Pred, finals2Real);
		final Round finals2NoScores = finals.get(1);
		for (int i = 0; i < finals2NoScores.games.size(); i++) {
			Game noTeams = finals2Games.get(i);
			Game noScores = finals2NoScores.games.get(i);
			noScores.predHome = noTeams.predHome;
			noScores.predAway = noTeams.predAway;
			noScores.predPenaltyWinner = noTeams.predPenaltyWinner;
			noScores.realHome = noTeams.realHome;
			noScores.realAway = noTeams.realAway;
			noScores.realPenaltyWinner = noTeams.realPenaltyWinner;
			finals2NoScores.games.set(i, noScores);
		}
		finals.set(1, finals2NoScores);

		final ArrayList<Game> finals1Games = Util.csvToGames(finals1Pred, finals1Real);
		final Round finals1NoScores = finals.get(2);
		for (int i = 0; i < finals1NoScores.games.size(); i++) {
			Game noTeams = finals1Games.get(i);
			Game noScores = finals1NoScores.games.get(i);
			noScores.predHome = noTeams.predHome;
			noScores.predAway = noTeams.predAway;
			noScores.predPenaltyWinner = noTeams.predPenaltyWinner;
			noScores.realHome = noTeams.realHome;
			noScores.realAway = noTeams.realAway;
			noScores.realPenaltyWinner = noTeams.realPenaltyWinner;
			finals1NoScores.games.set(i, noScores);
		}
		finals.set(2, finals1NoScores);

		return finals;
	}

	@SuppressWarnings("unchecked")
	public static Poule getPoule(Context context) {
		Log.d(TAG, "getPoule");

		// get important data from preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String jsonScores = prefs.getString(Wk.PREF_SCORES, "");

		Poule poule = new Poule();

		try {
			JSONObject scores = new JSONObject(jsonScores);
			JSONObject list = scores.getJSONObject("list");

			Iterator<String> names = list.keys();
			while (names.hasNext()) {
				String name = names.next();
				Player player = Player.parsePlayer(context, list.getJSONObject(name));
				poule.players.put(player.username, player);
			}
		} catch (JSONException e) {
			Log.e(TAG, "JSONException parsing players.", e);
		}

		return poule;
	}

	public static int getTotalScore(ArrayList<Round> groupStage, ArrayList<Round> finals) {

		int score = 0;

		for (Round round : groupStage) {
			for (Game game : round.games) {
				score += game.getMyScore(1);
			}
		}

		int roundNr = 2;
		for (Round round : finals) {
			for (Game game : round.games) {
				score += game.getMyScore(roundNr);
			}
			roundNr++;
		}

		return score;
	}

	public static ArrayList<Player> getStandings(Poule poule) {

		ArrayList<Player> standings = new ArrayList<Player>(poule.players.size());
		for (Entry<String, Player> entry : poule.players.entrySet()) {
			standings.add(entry.getValue());
		}

		// sort
		Collections.sort(standings, new Comparator<Player>() {

			public int compare(Player object1, Player object2) {
				int score1 = Util.getTotalScore(object1.groupStage, object1.finals);
				int score2 = Util.getTotalScore(object2.groupStage, object2.finals);

				return score2 - score1;
			}

		});

		return standings;
	}
}
