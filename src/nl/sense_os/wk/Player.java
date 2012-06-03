package nl.sense_os.wk;

import java.util.ArrayList;

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
			player.company = playerJson.getString("company");
			player.fullName = playerJson.getString("fullname");
			player.username = playerJson.getString("name");

			player.groupStage = Util.getGroupStage(context, player.username);
			player.finals = Util.getFinals(context, player.username);
			player.jokers = Util.getJokers(context, player.username);
		} catch (final JSONException e) {
			Log.e(TAG, "JSONException parsing player object.", e);
			return null;
		}

		return player;
	}

	String company;
	ArrayList<Round> finals;
	String fullName;
	ArrayList<Round> groupStage;
	int jokers;
	String username;

	public Player() {
		this.finals = new ArrayList<Round>();
		this.groupStage = new ArrayList<Round>();
	}

	private Player(Parcel in) {
		this.company = in.readString();
		this.finals = new ArrayList<Round>();
		final int maxFinals = in.readInt();
		for (int i = 0; i < maxFinals; i++) {
			this.finals.add((Round) in.readParcelable(getClass()
					.getClassLoader()));
		}
		this.fullName = in.readString();
		this.groupStage = new ArrayList<Round>();
		final int maxGroupStage = in.readInt();
		for (int i = 0; i < maxGroupStage; i++) {
			this.groupStage.add((Round) in.readParcelable(getClass()
					.getClassLoader()));
		}
		this.jokers = in.readInt();
		this.username = in.readString();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(this.company);
		dest.writeInt(this.finals.size());
		for (final Round r : this.finals) {
			dest.writeParcelable(r, 0);
		}
		dest.writeString(this.fullName);
		dest.writeInt(this.groupStage.size());
		for (final Round r : this.groupStage) {
			dest.writeParcelable(r, 0);
		}
		dest.writeInt(this.jokers);
		dest.writeString(this.username);
	}
}
