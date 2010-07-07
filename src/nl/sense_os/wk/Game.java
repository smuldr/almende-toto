package nl.sense_os.wk;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

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

    int idAway;
    int idHome;
    boolean joker;
    String location;
    String predAway;
    String predHome;
    int predPenaltyWinner;
    String realAway;
    String realHome;
    int realPenaltyWinner;
    String teamAway;

    String teamHome;

    String time;

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
        // TODO Auto-generated method stub
        return 0;
    }

    public int getMyScore(int round) {
        
//        String s = "Round " + round + ", " + this.predHome + "-" + this.predAway + " <<>> " + this.realHome + "-" + this.realAway + ". Points: ";
        
        if (this.realHome.equals("") || this.predHome.equals("") || this.realAway.equals("")
                || this.predAway.equals("")) {
            
//            Log.d(TAG, s + "0");
            
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
        
//        Log.d(TAG, s + score);

        return score;
    }

    public boolean isTotoCorrect() {

        if (this.realAway.equals("x") || this.realHome.equals("x") || this.realAway.equals("") || this.realHome.equals("")) {
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
        } else if ((intPredHome == intPredAway) && (intRealHome == intRealAway) && (this.realPenaltyWinner <= 0)) {
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
