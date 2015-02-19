package ua.org.bespalov.weather.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;

import ua.org.bespalov.weather.MainActivity;
import ua.org.bespalov.weather.R;
import ua.org.bespalov.weather.Utility;
import ua.org.bespalov.weather.data.WeatherContract;
import ua.org.bespalov.weather.data.WeatherContract.WeatherEntry;
import ua.org.bespalov.weather.data.WeatherContract.LocationEntry;

public class WeatherSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String LOG_TAG = WeatherSyncAdapter.class.getSimpleName();
    private static int SYNC_INTERVAL = 60*180;
    private static int SYNC_FLEXTIME = SYNC_INTERVAL/3;
    private static int DAY_IN_MILLIS = 1000*60*60*24;
    private static int WEATHER_NOTIFICATION_ID = 3004;

    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[] {
            WeatherEntry.COLUMN_WEATHER_ID,
            WeatherEntry.COLUMN_MAX_TEMP,
            WeatherEntry.COLUMN_MIN_TEMP,
            WeatherEntry.COLUMN_SHORT_DESC
    };

    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private static final int INDEX_SHORT_DESC = 3;

    public WeatherSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    private void notifyWeather(){
        Context context = getContext();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String lastNotificationKey = context.getString(R.string.pref_last_notification);
        long lastSync = sharedPreferences.getLong(lastNotificationKey, 0);

        if (System.currentTimeMillis() - lastSync >= DAY_IN_MILLIS){
            String locationQuery = Utility.getPreferredLocation(context);
            Uri weatherUri = WeatherEntry.buildWeatherLocationWithDate(locationQuery, WeatherContract.getDbDateString(new Date()));

            Cursor cursor = context.getContentResolver().query(
                    weatherUri,
                    NOTIFY_WEATHER_PROJECTION,
                    null,
                    null,
                    null
            );
            if (cursor.moveToFirst()){
                int weatherId = cursor.getInt(INDEX_WEATHER_ID);
                double high = cursor.getDouble(INDEX_MAX_TEMP);
                double low = cursor.getDouble(INDEX_MIN_TEMP);
                String desc = cursor.getString(INDEX_SHORT_DESC);

                int iconId = Utility.getIconResourceForWeatherCondition(weatherId);
                String title = context.getString(R.string.app_name);
                boolean isMetric = Utility.isMetric(context);

                String contentText = String.format(context.getString(R.string.format_notification),
                        desc,
                        Utility.formatTemperature(context, high, isMetric),
                        Utility.formatTemperature(context, low, isMetric)
                );

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putLong(lastNotificationKey, System.currentTimeMillis());
                editor.commit();

                Intent intent = new Intent(context, MainActivity.class);
                TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);
                taskStackBuilder.addNextIntent(intent);
                PendingIntent pendingIntent = taskStackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                Notification notification = new NotificationCompat.Builder(context)
                        .setSmallIcon(iconId)
                        .setContentTitle(title)
                        .setContentText(contentText)
                        .addAction(iconId, title, pendingIntent).build();
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(WEATHER_NOTIFICATION_ID, notification);
            }

        }
    }

    public static void syncImmediately(Context context) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(getSyncAccount(context),
                context.getString(R.string.content_authority), bundle);
    }

    private static Account getSyncAccount(Context context) {
        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        Account newAccount = new Account(context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        if (null == accountManager.getPassword(newAccount)){
            if (!accountManager.addAccountExplicitly(newAccount, "", null)){
                return null;
            }
            onAccountCreated(newAccount, context);
        }
        return newAccount;
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Log.d("WeatherSyncAdapter", "onPerformSync");
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        String forecastJsonStr = null;

        String modeType = "json";
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        String unitsType = sharedPreferences.getString(getContext().getString(R.string.pref_units_key), getContext().getString(R.string.pref_units_metric));
        int numDays = 14;
        String locationQuery = Utility.getPreferredLocation(getContext());

        String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
        String QUERY_PARAM = "q";
        String MODE_PARAM = "mode";
        String UNITS_PARAM = "units";
        String DAYS_PARAM = "cnt";
        try {
            Uri uri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(QUERY_PARAM, locationQuery)
                    .appendQueryParameter(MODE_PARAM, modeType)
                    .appendQueryParameter(UNITS_PARAM, unitsType)
                    .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                    .build();
            URL url = new URL(uri.toString());
            Log.i(LOG_TAG, url.toString());
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                return;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append("\n");
            }

            if (buffer.length() == 0) {
                return;
            }
            forecastJsonStr = buffer.toString();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error: ", e);
            return;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }
        try {
            getWeatherDataFromJson(forecastJsonStr, numDays, locationQuery);
            if (sharedPreferences.getBoolean(getContext().getString(R.string.pref_notifications_key), true)){
                notifyWeather();
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private void getWeatherDataFromJson(String forecastJsonStr, int numDays, String locationString)
            throws JSONException {
        final String OWM_LOC = "city";
        final String OWM_LOC_NAME = "name";
        final String OWM_LOC_COORD = "coord";
        final String OWM_LOC_LAT = "lat";
        final String OWM_LOC_LON = "lon";

        // These are the names of the JSON objects that need to be extracted.
        final String OWM_LIST = "list";

        final String OWM_DATETIME = "dt";
        final String OWM_PRESSURE = "pressure";
        final String OWM_HUMIDITY = "humidity";
        final String OWM_WINDSPEED = "speed";
        final String OWM_WIND_DIRECTION = "deg";

        final String OWM_TEMPERATURE = "temp";
        final String OWM_MAX = "max";
        final String OWM_MIN = "min";

        final String OWM_WEATHER = "weather";
        final String OWM_DESCRIPTION = "main";
        final String OWM_WEATHER_ID = "id";

        JSONObject forecastJson = new JSONObject(forecastJsonStr);
        JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

        JSONObject locJson = forecastJson.getJSONObject(OWM_LOC);
        String locName = locJson.getString(OWM_LOC_NAME);
        JSONObject coordJson = locJson.getJSONObject(OWM_LOC_COORD);
        double locLatitude = coordJson.getDouble(OWM_LOC_LAT);
        double locLongitude = coordJson.getDouble(OWM_LOC_LON);

        Log.v(LOG_TAG, locName + ", with coord: " + locLatitude + " " + locLongitude);
        long locationID = addLocation(locationString, locName, locLatitude, locLongitude);

        Vector<ContentValues> cVVector = new Vector<ContentValues>(weatherArray.length());

        for(int i = 0; i < weatherArray.length(); i++) {
            // For now, using the format "Day, description, hi/low"
            long dateTime;
            double pressure;
            int humidity;
            double windSpeed;
            double windDirection;

            double high;
            double low;

            String description;
            int weather_id;

            // Get the JSON object representing the day
            JSONObject dayForecast = weatherArray.getJSONObject(i);

            // The date/time is returned as a long.  We need to convert that
            // into something human-readable, since most people won't read "1400356800" as
            // "this saturday".
            dateTime = dayForecast.getLong(OWM_DATETIME);
            pressure = dayForecast.getDouble(OWM_PRESSURE);
            humidity = dayForecast.getInt(OWM_HUMIDITY);
            windSpeed = dayForecast.getDouble(OWM_WINDSPEED);
            windDirection = dayForecast.getDouble(OWM_WIND_DIRECTION);

            // description is in a child array called "weather", which is 1 element long.
            JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
            description = weatherObject.getString(OWM_DESCRIPTION);
            weather_id = weatherObject.getInt(OWM_WEATHER_ID);

            // Temperatures are in a child object called "temp".  Try not to name variables
            // "temp" when working with temperature.  It confuses everybody.
            JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
            high = temperatureObject.getDouble(OWM_MAX);
            low = temperatureObject.getDouble(OWM_MIN);

            ContentValues weatherValues = new ContentValues();

            weatherValues.put(WeatherEntry.COLUMN_LOC_KEY, locationID);
            weatherValues.put(WeatherEntry.COLUMN_DATETEXT, WeatherContract.getDbDateString(new Date(dateTime * 1000L)));
            weatherValues.put(WeatherEntry.COLUMN_HUMIDITY, humidity);
            weatherValues.put(WeatherEntry.COLUMN_PRESSURE, pressure);
            weatherValues.put(WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
            weatherValues.put(WeatherEntry.COLUMN_DEGREES, windDirection);
            weatherValues.put(WeatherEntry.COLUMN_MAX_TEMP, high);
            weatherValues.put(WeatherEntry.COLUMN_MIN_TEMP, low);
            weatherValues.put(WeatherEntry.COLUMN_SHORT_DESC, description);
            weatherValues.put(WeatherEntry.COLUMN_WEATHER_ID, weather_id);

            cVVector.add(weatherValues);

        }
        
        if (cVVector.size() > 0) {
            ContentValues[] cvArray = new ContentValues[cVVector.size()];
            cVVector.toArray(cvArray);
            int rowsInserted = getContext().getContentResolver()
                    .bulkInsert(WeatherEntry.CONTENT_URI, cvArray);
            Log.v(LOG_TAG, "inserted " + rowsInserted + " rows of weather data");
        }
        
        int rowsDeleted = deleteOldData();
        Log.v(LOG_TAG, "deleted " + rowsDeleted + " rows of weather data");
    }

    private int deleteOldData() {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.roll(Calendar.DATE, -1);
        String dateString = WeatherContract.getDbDateString(calendar.getTime());
        Log.v(LOG_TAG, "THIS DATE IS "+ dateString);
        return getContext().getContentResolver().delete(
                WeatherEntry.CONTENT_URI,
                WeatherEntry.COLUMN_DATETEXT + " <= ?",
                new String[]{dateString}
        );
    }

    private long addLocation (String locationSetting, String locName, double lat, double lon){
        Log.v(LOG_TAG, "inserting " + locName + ", with coord" + lat + ", " + lon);
        Cursor cursor = getContext().getContentResolver().query(
                LocationEntry.CONTENT_URI,
                new String[]{LocationEntry._ID},
                LocationEntry.COLUMN_LOC_SETTING + " = ?",
                new String[]{locationSetting},
                null
        );

        if (cursor.moveToFirst()){
            Log.v(LOG_TAG, "Found it in database!");
            int locationIndex = cursor.getColumnIndex(LocationEntry._ID);
            return cursor.getLong(locationIndex);
        } else {
            Log.v(LOG_TAG, "Didn't find it in database, inserting now!");
            ContentValues locationValues = new ContentValues();
            locationValues.put(LocationEntry.COLUMN_LOC_SETTING, locationSetting);
            locationValues.put(LocationEntry.COLUMN_LOC_NAME, locName);
            locationValues.put(LocationEntry.COLUMN_LOC_LAT, lat);
            locationValues.put(LocationEntry.COLUMN_LOC_LONG, lon);
            Uri locationInsertUri = getContext().getContentResolver().insert(LocationEntry.CONTENT_URI, locationValues);
            return ContentUris.parseId(locationInsertUri);
        }
    }

    private static void configurePeriodicSync (Context context, int syncInterval, int flexTime){
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            SyncRequest request = new SyncRequest.Builder().syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(account, authority).setExtras(new Bundle()).build();
            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account, authority, new Bundle(), syncInterval);
        }
    }

    private static void onAccountCreated(Account newAccount, Context context) {
        WeatherSyncAdapter.configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);
        ContentResolver.setSyncAutomatically(newAccount, context.getString(R.string.content_authority), true);
        syncImmediately(context);
    }

    public static void initializeSyncAdapter(Context context) {
        getSyncAccount(context);
    }
}
