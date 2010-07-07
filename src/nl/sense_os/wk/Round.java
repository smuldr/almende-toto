package nl.sense_os.wk;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class Round implements Parcelable {
    public static final Parcelable.Creator<Round> CREATOR = new Parcelable.Creator<Round>() {
        public Round createFromParcel(Parcel in) {
            return new Round(in);
        }

        public Round[] newArray(int size) {
            return new Round[size];
        }
    };

    private static final String TAG = "WK Round";

    public static Round parseRound(JSONObject object) {

        final Round round = new Round();

        // get id of this round
        try {
            round.id = object.getString("id");
        } catch (final JSONException e) {
            Log.e(TAG, "JSONException parsing Round ID.", e);
        }

        // try to get username of this round
        try {
            round.name = object.getString("name");
        } catch (final JSONException e) {
            round.name = "poule " + round.id;
        }

        // get teams for this round
        try {
            final JSONArray teamArray = object.getJSONArray("team");
            for (int i = 0; i < teamArray.length(); i++) {
                final JSONObject team = teamArray.getJSONObject(i);
                round.teams.add(team.getString("name"));
            }
        } catch (final JSONException e) {
            Log.e(TAG, "JSONException parsing Round teams.", e);
        }

        // get games for this round
        try {
            final JSONArray gameArray = object.getJSONArray("game");
            for (int i = 0; i < gameArray.length(); i++) {
                final JSONObject game = gameArray.getJSONObject(i);
                round.games.add(Game.parseGame(game, round.teams));
            }
        } catch (final JSONException e) {
            try {
                final JSONObject game = object.getJSONObject("game");
                round.games.add(Game.parseGame(game, round.teams));
            } catch (final JSONException ex) {
                Log.e(TAG, "JSONException parsing Round games.", ex);
            }
        }

        return round;
    }

    ArrayList<Game> games;
    String id;
    String name;
    ArrayList<String> teams;

    public Round() {
        this.games = new ArrayList<Game>();
        this.teams = new ArrayList<String>();
    }

    private Round(Parcel in) {

        this.games = new ArrayList<Game>();
        final int maxGames = in.readInt();
        for (int i = 0; i < maxGames; i++) {
            this.games.add((Game) in.readParcelable(getClass().getClassLoader()));
        }
        this.id = in.readString();
        this.name = in.readString();
        this.teams = new ArrayList<String>();
        final int maxTeams = in.readInt();
        for (int i = 0; i < maxTeams; i++) {
            this.teams.add(in.readString());
        }

    }

    public int describeContents() {

        return 0;
    }

    @Override
    public String toString() {
        String string = this.name;
        for (final Game game : this.games) {
            string += "\n  " + this.teams.get(game.idHome - 1) + " vs "
                    + this.teams.get(game.idAway - 1);
            string += ", " + game.time + ", " + game.location;
        }

        return string;
    }

    public void writeToParcel(Parcel dest, int flags) {

        dest.writeInt(this.games.size());
        for (final Game game : this.games) {
            dest.writeParcelable(game, 0);
        }
        dest.writeString(this.id);
        dest.writeString(this.name);
        dest.writeInt(this.teams.size());
        for (final String s : this.teams) {
            dest.writeString(s);
        }
    }
}
