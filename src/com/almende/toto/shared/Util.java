package com.almende.toto.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.almende.toto.content.Game;
import com.almende.toto.content.Player;
import com.almende.toto.content.Poule;
import com.almende.toto.content.Round;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class Util {
	private static final String TAG = "EK Util";

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
					game.setPredPenaltyWinner(1);
					game.setPredHome(homeToken.substring(0, homeToken.indexOf(":")).trim());
					game.setPredAway(awayToken.trim());
				} else if (awayToken.contains(":w")) {
					game.setPredPenaltyWinner(2);
					game.setPredAway(awayToken.substring(0, awayToken.indexOf(":")).trim());
					game.setPredHome(homeToken.trim());
				} else {
					game.setPredHome(homeToken.trim());
					game.setPredAway(awayToken.trim());
					game.setPredPenaltyWinner(-1);
				}
				game.setJoker(jokerToken.equals("j"));

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
					game.setRealPenaltyWinner(1);
					game.setRealHome(homeToken.substring(0, homeToken.indexOf(":")).trim());
					game.setRealAway(awayToken.trim());
				} else if (awayToken.contains(":w")) {
					game.setRealPenaltyWinner(2);
					game.setRealAway(awayToken.substring(0, awayToken.indexOf(":")).trim());
					game.setRealHome(homeToken.trim());
				} else {
					game.setRealHome(homeToken.trim());
					game.setRealAway(awayToken.trim());
					game.setRealPenaltyWinner(-1);
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
			String homeToken = game.getPredHome() + (game.getPredPenaltyWinner() == 1 ? ":w" : "");
			String awayToken = game.getPredAway() + (game.getPredPenaltyWinner() == 2 ? ":w" : "");
			csv += homeToken + "," + awayToken + "," + (game.isJoker() ? "j" : "") + ",";
		}

		return csv;
	}

	public static int getJokers(Context context, String username) {

		// get important data from preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String scoreString = prefs.getString(Keys.PREF_SCORES, "");

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
		// Log.d(TAG, "Parse group stage jokers");
		final ArrayList<Game> groupStage = Util.csvToGames(poulesPred, poulesReal);
		for (Game game : groupStage) {
			if (game.isJoker()) {
				availableJokers--;
			}
		}

		// Log.d(TAG, "Parse quarter finals jokers");
		final ArrayList<Game> finals4 = Util.csvToGames(finals4Pred, finals4Real);
		for (Game game : finals4) {
			if (game.isJoker()) {
				availableJokers--;
			}
		}

		// Log.d(TAG, "Parse semi finals jokers");
		final ArrayList<Game> finals2 = Util.csvToGames(finals2Pred, finals2Real);
		for (Game game : finals2) {
			if (game.isJoker()) {
				availableJokers--;
			}
		}

		// Log.d(TAG, "Parse final jokers");
		final ArrayList<Game> finals1 = Util.csvToGames(finals1Pred, finals1Real);
		for (Game game : finals1) {
			if (game.isJoker()) {
				availableJokers--;
			}
		}

		return availableJokers;
	}

	public static ArrayList<Round> getGroupStage(Context context, String username) {

		// get important data from preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String jsonScores = prefs.getString(Keys.PREF_SCORES, "");
		String jsonFixtures = prefs.getString(Keys.PREF_FIXTURES, "");

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

			Game noScores = round.getGames().get(gameIndex);
			noScores.setPredHome(game.getPredHome());
			noScores.setPredAway(game.getPredAway());
			noScores.setPredPenaltyWinner(game.getPredPenaltyWinner());
			noScores.setRealHome(game.getRealHome());
			noScores.setRealAway(game.getRealAway());
			noScores.setRealPenaltyWinner(game.getRealPenaltyWinner());
			noScores.setJoker(game.isJoker());
			round.getGames().set(gameIndex, noScores);
			groupStage.set(roundIndex, round);
			gameIndex++;

			if (gameIndex >= round.getGames().size()) {
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

		// get important data from preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String jsonScores = prefs.getString(Keys.PREF_SCORES, "");
		String jsonFixtures = prefs.getString(Keys.PREF_FIXTURES, "");

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
		for (int i = 0; i < finals4NoScores.getGames().size(); i++) {
			Game noTeams = finals4Games.get(i);
			Game noScores = finals4NoScores.getGames().get(i);
			noScores.setPredHome(noTeams.getPredHome());
			noScores.setPredAway(noTeams.getPredAway());
			noScores.setPredPenaltyWinner(noTeams.getPredPenaltyWinner());
			noScores.setRealHome(noTeams.getRealHome());
			noScores.setRealAway(noTeams.getRealAway());
			noScores.setRealPenaltyWinner(noTeams.getRealPenaltyWinner());
			finals4NoScores.getGames().set(i, noScores);
		}
		finals.set(0, finals4NoScores);

		final ArrayList<Game> finals2Games = Util.csvToGames(finals2Pred, finals2Real);
		final Round finals2NoScores = finals.get(1);
		for (int i = 0; i < finals2NoScores.getGames().size(); i++) {
			Game noTeams = finals2Games.get(i);
			Game noScores = finals2NoScores.getGames().get(i);
			noScores.setPredHome(noTeams.getPredHome());
			noScores.setPredAway(noTeams.getPredAway());
			noScores.setPredPenaltyWinner(noTeams.getPredPenaltyWinner());
			noScores.setRealHome(noTeams.getRealHome());
			noScores.setRealAway(noTeams.getRealAway());
			noScores.setRealPenaltyWinner(noTeams.getRealPenaltyWinner());
			finals2NoScores.getGames().set(i, noScores);
		}
		finals.set(1, finals2NoScores);

		final ArrayList<Game> finals1Games = Util.csvToGames(finals1Pred, finals1Real);
		final Round finals1NoScores = finals.get(2);
		for (int i = 0; i < finals1NoScores.getGames().size(); i++) {
			Game noTeams = finals1Games.get(i);
			Game noScores = finals1NoScores.getGames().get(i);
			noScores.setPredHome(noTeams.getPredHome());
			noScores.setPredAway(noTeams.getPredAway());
			noScores.setPredPenaltyWinner(noTeams.getPredPenaltyWinner());
			noScores.setRealHome(noTeams.getRealHome());
			noScores.setRealAway(noTeams.getRealAway());
			noScores.setRealPenaltyWinner(noTeams.getRealPenaltyWinner());
			finals1NoScores.getGames().set(i, noScores);
		}
		finals.set(2, finals1NoScores);

		return finals;
	}

	public static Poule getPoule(Context context) {

		// get important data from preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String jsonScores = prefs.getString(Keys.PREF_SCORES, "");

		Poule poule = new Poule();

		try {
			JSONObject scores = new JSONObject(jsonScores);
			JSONObject list = scores.getJSONObject("list");

			@SuppressWarnings("unchecked")
			Iterator<String> names = list.keys();
			while (names.hasNext()) {
				String name = names.next();
				Player player = Player.parsePlayer(context, list.getJSONObject(name));
				poule.players.put(player.getUsername(), player);
			}
		} catch (JSONException e) {
			Log.e(TAG, "JSONException parsing players.", e);
		}

		return poule;
	}

	public static int getTotalScore(ArrayList<Round> groupStage, ArrayList<Round> finals) {

		int score = 0;

		for (Round round : groupStage) {
			for (Game game : round.getGames()) {
				score += game.getMyScore(1);
			}
		}

		int roundNr = 2;
		for (Round round : finals) {
			for (Game game : round.getGames()) {
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
				int score1 = Util.getTotalScore(object1.getGroupStage(), object1.getFinals());
				int score2 = Util.getTotalScore(object2.getGroupStage(), object2.getFinals());

				return score2 - score1;
			}

		});

		return standings;
	}
}
