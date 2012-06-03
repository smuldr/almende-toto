package com.almende.footballpool.content;

import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class Game implements Parcelable {
	public static final Parcelable.Creator<Game> CREATOR = new Parcelable.Creator<Game>() {
		public Game createFromParcel(Parcel in) {
			return new Game(in);
		}

		public Game[] newArray(int size) {
			return new Game[size];
		}
	};

	private static final String TAG = "WK Game";

	public static Game parseGame(JSONObject object, ArrayList<String> teams) {
		final Game game = new Game();

		try {
			game.idHome = object.getInt("home");
			game.idAway = object.getInt("away");
			game.teamHome = teams.get(game.idHome - 1);
			game.teamAway = teams.get(game.idAway - 1);
			game.time = object.getString("time");
			game.location = object.getString("location");

			game.realHome = "";
			game.realAway = "";
			game.predHome = "";
			game.predAway = "";
			game.joker = false;

		} catch (final JSONException e) {
			Log.e(TAG, "JSONException parsing Game.", e);
		}

		return game;
	}

	private int idAway;
	private int idHome;
	private boolean joker;
	private String location;
	private String predAway;
	private String predHome;
	private int predPenaltyWinner;
	private String realAway;
	private String realHome;
	private int realPenaltyWinner;
	private String teamAway;
	private String teamHome;
	private String time;

	public Game() {
		// empty constructor
	}

	private Game(Parcel in) {
		this.idAway = in.readInt();
		this.idHome = in.readInt();
		this.joker = in.readInt() > 0;
		this.location = in.readString();
		this.predAway = in.readString();
		this.predHome = in.readString();
		this.predPenaltyWinner = in.readInt();
		this.realAway = in.readString();
		this.realHome = in.readString();
		this.realPenaltyWinner = in.readInt();
		this.teamAway = in.readString();
		this.teamHome = in.readString();
		this.time = in.readString();
	}

	public int describeContents() {
		return 0;
	}

	public int getMyScore(int round) {

		if (this.realHome == null || this.predHome == null || this.realAway == null
				|| this.predAway == null || this.realHome.equals("") || this.predHome.equals("")
				|| this.realAway.equals("") || this.predAway.equals("")) {
			// cannot calculate score
			return 0;
		}

		int score = 0;

		if (this.predHome.equals(this.realHome)) {
			switch (round) {
			case 1:
				score += 2;
				break;
			case 2:
				score += 2;
				break;
			case 3:
				score += 3;
				break;
			case 4:
				score += 4;
				break;
			case 5:
				score += 5;
				break;
			}
		}
		if (this.predAway.equals(this.realAway)) {
			switch (round) {
			case 1:
				score += 2;
				break;
			case 2:
				score += 2;
				break;
			case 3:
				score += 3;
				break;
			case 4:
				score += 4;
				break;
			case 5:
				score += 5;
				break;
			}
		}

		// calculate toto score
		if (isTotoCorrect()) {

			switch (round) {
			case 1:
				score += 4;
				break;
			case 2:
				score += 5;
				break;
			case 3:
				score += 6;
				break;
			case 4:
				score += 8;
				break;
			case 5:
				score += 10;
				break;
			}
		}

		// Log.d(TAG, s + score);

		return score;
	}

	/**
	 * @return the idAway
	 */
	public int getIdAway() {
		return idAway;
	}

	/**
	 * @param idAway
	 *            the idAway to set
	 */
	public void setIdAway(int idAway) {
		this.idAway = idAway;
	}

	/**
	 * @return the idHome
	 */
	public int getIdHome() {
		return idHome;
	}

	/**
	 * @param idHome
	 *            the idHome to set
	 */
	public void setIdHome(int idHome) {
		this.idHome = idHome;
	}

	/**
	 * @return the joker
	 */
	public boolean isJoker() {
		return joker;
	}

	/**
	 * @param joker
	 *            the joker to set
	 */
	public void setJoker(boolean joker) {
		this.joker = joker;
	}

	/**
	 * @return the location
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * @param location
	 *            the location to set
	 */
	public void setLocation(String location) {
		this.location = location;
	}

	/**
	 * @return the predAway
	 */
	public String getPredAway() {
		return predAway;
	}

	/**
	 * @param predAway
	 *            the predAway to set
	 */
	public void setPredAway(String predAway) {
		this.predAway = predAway;
	}

	/**
	 * @return the predHome
	 */
	public String getPredHome() {
		return predHome;
	}

	/**
	 * @param predHome
	 *            the predHome to set
	 */
	public void setPredHome(String predHome) {
		this.predHome = predHome;
	}

	/**
	 * @return the predPenaltyWinner
	 */
	public int getPredPenaltyWinner() {
		return predPenaltyWinner;
	}

	/**
	 * @param predPenaltyWinner
	 *            the predPenaltyWinner to set
	 */
	public void setPredPenaltyWinner(int predPenaltyWinner) {
		this.predPenaltyWinner = predPenaltyWinner;
	}

	/**
	 * @return the realAway
	 */
	public String getRealAway() {
		return realAway;
	}

	/**
	 * @param realAway
	 *            the realAway to set
	 */
	public void setRealAway(String realAway) {
		this.realAway = realAway;
	}

	/**
	 * @return the realHome
	 */
	public String getRealHome() {
		return realHome;
	}

	/**
	 * @param realHome
	 *            the realHome to set
	 */
	public void setRealHome(String realHome) {
		this.realHome = realHome;
	}

	/**
	 * @return the realPenaltyWinner
	 */
	public int getRealPenaltyWinner() {
		return realPenaltyWinner;
	}

	/**
	 * @param realPenaltyWinner
	 *            the realPenaltyWinner to set
	 */
	public void setRealPenaltyWinner(int realPenaltyWinner) {
		this.realPenaltyWinner = realPenaltyWinner;
	}

	/**
	 * @return the teamAway
	 */
	public String getTeamAway() {
		return teamAway;
	}

	/**
	 * @param teamAway
	 *            the teamAway to set
	 */
	public void setTeamAway(String teamAway) {
		this.teamAway = teamAway;
	}

	/**
	 * @return the teamHome
	 */
	public String getTeamHome() {
		return teamHome;
	}

	/**
	 * @param teamHome
	 *            the teamHome to set
	 */
	public void setTeamHome(String teamHome) {
		this.teamHome = teamHome;
	}

	/**
	 * @return the time
	 */
	public String getTime() {
		return time;
	}

	/**
	 * @param time
	 *            the time to set
	 */
	public void setTime(String time) {
		this.time = time;
	}

	public boolean isTotoCorrect() {

		if (this.realAway.equals("x") || this.realHome.equals("x") || this.realAway.equals("")
				|| this.realHome.equals("")) {
			return false;
		}

		int intPredHome = -1;
		int intPredAway = -1;
		int intRealHome = -1;
		int intRealAway = -1;
		try {
			intPredHome = Integer.parseInt(this.predHome);
			intPredAway = Integer.parseInt(this.predAway);
			intRealHome = Integer.parseInt(this.realHome);
			intRealAway = Integer.parseInt(this.realAway);
		} catch (final NumberFormatException e) {
			Log.w(TAG, "NumberFormatException calculating scores.", e);
			return false;
		}

		if (this.predPenaltyWinner > 0) {
			if (this.predPenaltyWinner == this.realPenaltyWinner) {
				// draw
				return true;
			} else if (this.predPenaltyWinner == 1) {
				if (intRealHome > intRealAway) {
					return true;
				} else {
					return false;
				}
			} else if (this.predPenaltyWinner == 2) {
				if (intRealAway > intRealHome) {
					return true;
				} else {
					return false;
				}
			}
		} else if (this.realPenaltyWinner > 0) {
			if (this.realPenaltyWinner == 1) {
				if (intPredHome > intPredAway) {
					return true;
				} else {
					return false;
				}
			} else if (this.realPenaltyWinner == 2) {
				if (intPredAway > intPredHome) {
					return true;
				} else {
					return false;
				}
			}
		} else if ((intPredHome > intPredAway) && (intRealHome > intRealAway)) {
			// correctly predicted home win
			return true;
		} else if ((intPredHome < intPredAway) && (intRealHome < intRealAway)) {
			// correctly predicted away win
			return true;
		} else if ((intPredHome == intPredAway) && (intRealHome == intRealAway)
				&& (this.realPenaltyWinner <= 0)) {
			// correctly predicted draw without penalty winner
			return true;
		}

		return false;
	}

	@Override
	public String toString() {
		return this.teamHome + " - " + this.teamAway + ": " + this.predHome + " - " + this.predAway;
	}

	public void writeToParcel(Parcel dest, int flags) {

		dest.writeInt(this.idAway);
		dest.writeInt(this.idHome);
		dest.writeInt(this.joker ? 1 : 0);
		dest.writeString(this.location);
		dest.writeString(this.predAway);
		dest.writeString(this.predHome);
		dest.writeInt(this.predPenaltyWinner);
		dest.writeString(this.realAway);
		dest.writeString(this.realHome);
		dest.writeInt(this.realPenaltyWinner);
		dest.writeString(this.teamAway);
		dest.writeString(this.teamHome);
		dest.writeString(this.time);

	}
}
