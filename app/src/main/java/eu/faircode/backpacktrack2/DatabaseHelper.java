package eu.faircode.backpacktrack2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "BPT2.Database";

    private static final String DB_NAME = "BACKPACKTRACKII";
    private static final int DB_VERSION = 3;

    private static final long MS_DAY = 24 * 60 * 60 * 1000L;

    private static List<LocationChangedListener> mLocationChangedListeners = new ArrayList<LocationChangedListener>();
    private static List<ActivityChangedListener> mActivityChangedListeners = new ArrayList<ActivityChangedListener>();
    private static List<StepCountChangedListener> mStepCountChangedListeners = new ArrayList<StepCountChangedListener>();

    private Context mContext;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.w(TAG, "Creating database");
        createTableLocation(db);
        createTableActivity(db);
        createTableStep(db);
    }

    private void createTableLocation(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE location (" +
                " ID INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", time INTEGER NOT NULL" +
                ", provider INTEGER NOT NULL" +
                ", latitude REAL NOT NULL" +
                ", longitude REAL NOT NULL" +
                ", altitude REAL NULL" +
                ", speed REAL NULL" +
                ", bearing REAL NULL" +
                ", accuracy REAL NULL" +
                ", name TEXT" + ");");
        db.execSQL("CREATE INDEX idx_location_time ON location(time)");
        db.execSQL("CREATE INDEX idx_location_name ON location(name)");
    }

    private void createTableActivity(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE activity (" +
                " ID INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", time INTEGER NOT NULL" +
                ", activity INTEGER NOT NULL" +
                ", confidence INTEGER NOT NULL" + ");");
        db.execSQL("CREATE INDEX idx_activity_time ON activity(time)");
    }

    private void createTableStep(SQLiteDatabase db) {
        Log.w(TAG, "Adding table step");
        db.execSQL("CREATE TABLE step (" +
                " ID INTEGER PRIMARY KEY AUTOINCREMENT" +
                ", time INTEGER NOT NULL" +
                ", count INTEGER NOT NULL" + ");");
        db.execSQL("CREATE INDEX idx_step_time ON step(time)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading from version " + oldVersion + " to " + newVersion);

        if (oldVersion < 2)
            createTableActivity(db);

        if (oldVersion < 3)
            createTableStep(db);
    }

    // Location

    public DatabaseHelper insertLocation(Location location, String name) {
        synchronized (mContext.getApplicationContext()) {
            SQLiteDatabase db = this.getWritableDatabase();

            ContentValues cv = new ContentValues();
            cv.put("time", location.getTime());
            cv.put("provider", location.getProvider());
            cv.put("latitude", location.getLatitude());
            cv.put("longitude", location.getLongitude());

            if (location.hasAltitude())
                cv.put("altitude", location.getAltitude());
            else
                cv.putNull("altitude");

            if (location.hasSpeed())
                cv.put("speed", location.getSpeed());
            else
                cv.putNull("speed");

            if (location.hasBearing())
                cv.put("bearing", location.getBearing());
            else
                cv.putNull("bearing");

            if (location.hasAccuracy())
                cv.put("accuracy", location.getAccuracy());
            else
                cv.putNull("accuracy");

            if (name == null)
                cv.putNull("name");
            else
                cv.put("name", name);

            db.insert("location", null, cv);

            for (LocationChangedListener listener : mLocationChangedListeners)
                listener.onLocationAdded(location);

            return this;
        }
    }

    public DatabaseHelper updateLocationName(long id, String name) {
        synchronized (mContext.getApplicationContext()) {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("name", name);
            db.update("location", cv, "ID = ?", new String[]{Long.toString(id)});

            for (LocationChangedListener listener : mLocationChangedListeners)
                listener.onLocationUpdated();

            return this;
        }
    }

    public DatabaseHelper updateLocationAltitude(long id, double altitude) {
        synchronized (mContext.getApplicationContext()) {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("altitude", altitude);
            db.update("location", cv, "ID = ?", new String[]{Long.toString(id)});

            for (LocationChangedListener listener : mLocationChangedListeners)
                listener.onLocationUpdated();

            return this;
        }
    }

    public DatabaseHelper deleteLocation(long id) {
        synchronized (mContext.getApplicationContext()) {
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete("location", "ID = ?", new String[]{Long.toString(id)});

            for (LocationChangedListener listener : mLocationChangedListeners)
                listener.onLocationDeleted();

            return this;
        }
    }

    public DatabaseHelper deleteLocations(long from, long to) {
        synchronized (mContext.getApplicationContext()) {
            Log.w(TAG, "Delete from=" + from + " to=" + to);
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete("location", "time >= ? AND time <= ?", new String[]{Long.toString(from), Long.toString(to)});

            for (LocationChangedListener listener : mLocationChangedListeners)
                listener.onLocationDeleted();

            return this;
        }
    }

    public Cursor getLocations(long from, long to, boolean trackpoints, boolean waypoints, boolean asc) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT *, ID AS _id FROM location";
        query += " WHERE time >= ? AND time <= ?";
        if (trackpoints && !waypoints)
            query += " AND name IS NULL";
        if (!trackpoints && waypoints)
            query += " AND NOT name IS NULL";
        query += " ORDER BY time";
        if (!asc)
            query += " DESC";
        return db.rawQuery(query, new String[]{Long.toString(from), Long.toString(to)});
    }

    // Activity

    public DatabaseHelper insertActivity(long time, int activity, int confidence) {
        synchronized (mContext.getApplicationContext()) {
            SQLiteDatabase db = this.getWritableDatabase();

            ContentValues cv = new ContentValues();
            cv.put("time", time);
            cv.put("activity", activity);
            cv.put("confidence", confidence);

            db.insert("activity", null, cv);

            for (ActivityChangedListener listener : mActivityChangedListeners)
                listener.onActivityAdded(time, activity, confidence);

            return this;
        }
    }

    public DatabaseHelper deleteActivities() {
        synchronized (mContext.getApplicationContext()) {
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete("activity", null, new String[]{});

            for (ActivityChangedListener listener : mActivityChangedListeners)
                listener.onActivityDeleted();

            return this;
        }
    }

    public Cursor getActivities(long from, long to) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT *, ID AS _id FROM activity";
        query += " WHERE time >= ? AND time <= ?";
        query += " ORDER BY time DESC";
        return db.rawQuery(query, new String[]{Long.toString(from), Long.toString(to)});
    }

    // Steps

    public DatabaseHelper updateSteps(long time, int delta) {
        synchronized (mContext.getApplicationContext()) {
            SQLiteDatabase db = this.getWritableDatabase();
            long day = time / MS_DAY * MS_DAY;

            int count = -1;
            Cursor c = null;
            try {
                c = db.query("step", new String[]{"count"}, "time = ?", new String[]{Long.toString(day)}, null, null, null, null);
                if (c.moveToFirst())
                    count = c.getInt(c.getColumnIndex("count"));
            } finally {
                if (c != null)
                    c.close();
            }

            if (count < 0) {
                Log.w(TAG, "Creating new day time=" + day);
                ContentValues cv = new ContentValues();
                cv.put("time", day);
                cv.put("count", delta);
                db.insert("step", null, cv);
            } else {
                ContentValues cv = new ContentValues();
                cv.put("count", count + delta);
                db.update("step", cv, "time = ?", new String[]{Long.toString(day)});
            }

            for (StepCountChangedListener listener : mStepCountChangedListeners)
                if (count < 0)
                    listener.onStepCountAdded(day, delta);
                else
                    listener.onStepCountUpdated(day, count + delta);

            return this;
        }
    }

    public Cursor getSteps(boolean asc) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT *, ID AS _id FROM step";
        query += " ORDER BY time";
        if (!asc)
            query += " DESC";
        return db.rawQuery(query, new String[]{});
    }

    public int getSteps(long time) {
        long day = time / MS_DAY * MS_DAY;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("step", new String[]{"count"}, "time = ?", new String[]{Long.toString(day)}, null, null, "time DESC", null);
            if (c.moveToFirst())
                return c.getInt(c.getColumnIndex("count"));
            else
                return 0;
        } finally {
            if (c != null)
                c.close();
        }
    }

    // Changes

    public static void addLocationChangedListener(LocationChangedListener listener) {
        mLocationChangedListeners.add(listener);
    }

    public static void removeLocationChangedListener(LocationChangedListener listener) {
        mLocationChangedListeners.remove(listener);
    }

    public static void addActivityChangedListener(ActivityChangedListener listener) {
        mActivityChangedListeners.add(listener);
    }

    public static void removeActivityChangedListener(ActivityChangedListener listener) {
        mActivityChangedListeners.remove(listener);
    }

    public static void addStepCountChangedListener(StepCountChangedListener listener) {
        mStepCountChangedListeners.add(listener);
    }

    public static void removeStepCountChangedListener(StepCountChangedListener listener) {
        mStepCountChangedListeners.remove(listener);
    }

    public interface LocationChangedListener {
        void onLocationAdded(Location location);

        void onLocationUpdated();

        void onLocationDeleted();
    }

    public interface ActivityChangedListener {
        void onActivityAdded(long time, int activity, int confidence);

        void onActivityDeleted();
    }

    public interface StepCountChangedListener {
        void onStepCountAdded(long time, int count);

        void onStepCountUpdated(long time, int count);
    }
}