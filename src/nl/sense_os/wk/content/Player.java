package nl.sense_os.wk.content;

import java.util.ArrayList;

import nl.sense_os.wk.shared.Util;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class Player implements Parcelable {

	public static final Parcelable.Creator<Player> CREATOR = new Parcelable.Creator<Player>() {

		@Override
		public Player createFromParcel(Parcel in) {
			return new Player(in);
		}

		@Override
		public Player[] newArray(int size) {
			return new Player[size];
		}
	};
	private static final String TAG = "WK Player";

	public static Player parsePlayer(Context context, JSONObject playerJson) {

		final Player player = new Player();

		try {
			player.setCompany(playerJson.getString("company"));
			player.setFullName(playerJson.getString("fullname"));
			player.setUsername(playerJson.getString("name"));

			player.setGroupStage(Util.getGroupStage(context, player.username));
			player.setFinals(Util.getFinals(context, player.username));
			player.setJokers(Util.getJokers(context, player.username));
		} catch (final JSONException e) {
			Log.e(TAG, "JSONException parsing player object", e);
			return null;
		}

		return player;
	}

	private String company;
	private ArrayList<Round> finals;
	private String fullName;
	private ArrayList<Round> groupStage;
	private int jokers;
	private String username;

	public Player() {
		setFinals(new ArrayList<Round>());
		setGroupStage(new ArrayList<Round>());
	}

	private Player(Parcel in) {
		setCompany(in.readString());
		setFinals(new ArrayList<Round>());
		final int maxFinals = in.readInt();
		for (int i = 0; i < maxFinals; i++) {
			getFinals().add((Round) in.readParcelable(getClass().getClassLoader()));
		}
		setFullName(in.readString());
		setGroupStage(new ArrayList<Round>());
		final int maxGroupStage = in.readInt();
		for (int i = 0; i < maxGroupStage; i++) {
			getGroupStage().add((Round) in.readParcelable(getClass().getClassLoader()));
		}
		setJokers(in.readInt());
		setUsername(in.readString());
	}

	@Override
	public int describeContents() {
		return 0;
	}

	/**
	 * @return the company
	 */
	public String getCompany() {
		return company;
	}

	/**
	 * @return the finals
	 */
	public ArrayList<Round> getFinals() {
		return finals;
	}

	/**
	 * @return the fullName
	 */
	public String getFullName() {
		return fullName;
	}

	/**
	 * @return the groupStage
	 */
	public ArrayList<Round> getGroupStage() {
		return groupStage;
	}

	/**
	 * @return the jokers
	 */
	public int getJokers() {
		return jokers;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * @param company
	 *            the company to set
	 */
	public void setCompany(String company) {
		this.company = company;
	}

	/**
	 * @param finals
	 *            the finals to set
	 */
	public void setFinals(ArrayList<Round> finals) {
		this.finals = finals;
	}

	/**
	 * @param fullName
	 *            the fullName to set
	 */
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	/**
	 * @param groupStage
	 *            the groupStage to set
	 */
	public void setGroupStage(ArrayList<Round> groupStage) {
		this.groupStage = groupStage;
	}

	/**
	 * @param jokers
	 *            the jokers to set
	 */
	public void setJokers(int jokers) {
		this.jokers = jokers;
	}

	/**
	 * @param username
	 *            the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(getCompany());
		dest.writeInt(getFinals().size());
		for (final Round r : getFinals()) {
			dest.writeParcelable(r, 0);
		}
		dest.writeString(getFullName());
		dest.writeInt(getGroupStage().size());
		for (final Round r : getGroupStage()) {
			dest.writeParcelable(r, 0);
		}
		dest.writeInt(getJokers());
		dest.writeString(getUsername());
	}
}
