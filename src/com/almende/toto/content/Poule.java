package com.almende.toto.content;

import java.util.HashMap;
import java.util.Map.Entry;

import android.os.Parcel;
import android.os.Parcelable;

public class Poule implements Parcelable {

	public static final Parcelable.Creator<Poule> CREATOR = new Parcelable.Creator<Poule>() {
		@Override
		public Poule createFromParcel(Parcel in) {
			return new Poule(in);
		}

		@Override
		public Poule[] newArray(int size) {
			return new Poule[size];
		}
	};

	public HashMap<String, Player> players;

	public Poule() {
		this.players = new HashMap<String, Player>();
	}

	private Poule(Parcel in) {
		this.players = new HashMap<String, Player>();
		final int maxPlayers = in.readInt();
		for (int i = 0; i < maxPlayers; i++) {
			this.players.put(in.readString(),
					(Player) in.readParcelable(getClass().getClassLoader()));
		}
	}

	@Override
	public int describeContents() {
		return 0;
	}

	/**
	 * @return the players
	 */
	public HashMap<String, Player> getPlayers() {
		return players;
	}

	/**
	 * @param players
	 *            the players to set
	 */
	public void setPlayers(HashMap<String, Player> players) {
		this.players = players;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(this.players.size());
		for (final Entry<String, Player> entry : this.players.entrySet()) {
			dest.writeString(entry.getKey());
			dest.writeParcelable(entry.getValue(), 0);
		}
	}
}
