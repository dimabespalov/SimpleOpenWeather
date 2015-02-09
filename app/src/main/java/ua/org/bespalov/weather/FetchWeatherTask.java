package ua.org.bespalov.weather;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ArrayAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import ua.org.bespalov.weather.data.WeatherContract;
import ua.org.bespalov.weather.data.WeatherContract.WeatherEntry;
import ua.org.bespalov.weather.data.WeatherContract.LocationEntry;

public class FetchWeatherTask extends AsyncTask<String, Void, Void> {
    private static final String LOG_TAG = FetchWeatherTask.class.getSimpleName();
    private final Context mContext;

    public FetchWeatherTask(Context context){
        mContext = context;
    }
    @Override
    protected Void doInBackground(String... params) {
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        String forecastJsonStr = null;

        String modeType = "json";
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String unitsType = sharedPreferences.getString(mContext.getString(R.string.pref_units_key), mContext.getString(R.string.pref_units_metric));
        int numDays = 14;
        String locationQuery = params[0];

        String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
        String QUERY_PARAM = "q";
        String MODE_PARAM = "mode";
        String UNITS_PARAM = "units";
        String DAYS_PARAM = "cnt";
        try {
            Uri uri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(QUERY_PARAM, params[0])
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
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                return null;
            }
            forecastJsonStr = buffer.toString();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error: ", e);
            return null;
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
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     *
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
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
            weatherValues.put(WeatherEntry.COLUMN_DATETEXT, WeatherContract.getDbDateString(new Date(dateTime*1000L)));
            weatherValues.put(WeatherEntry.COLUMN_HUMIDITY, humidity);
            weatherValues.put(WeatherEntry.COLUMN_PRESSURE, pressure);
            weatherValues.put(WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
            weatherValues.put(WeatherEntry.COLUMN_DEGREES, windDirection);
            weatherValues.put(WeatherEntry.COLUMN_MAX_TEMP, high);
            weatherValues.put(WeatherEntry.COLUMN_MIN_TEMP, low);
            weatherValues.put(WeatherEntry.COLUMN_SHORT_DESC, description);
            weatherValues.put(WeatherEntry.COLUMN_WEATHER_ID, weather_id);

            cVVector.add(weatherValues);

            if (cVVector.size() > 0) {
                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                int rowsInserted = mContext.getContentResolver()
                        .bulkInsert(WeatherEntry.CONTENT_URI, cvArray);
                Log.v(LOG_TAG, "inserted " + rowsInserted + " rows of weather data");
            }
        }
    }

    private long addLocation (String locationSetting, String locName, double lat, double lon){
        Log.v(LOG_TAG, "inserting " + locName + ", with coord" + lat + ", " + lon);
        Cursor cursor = mContext.getContentResolver().query(
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
            Uri locationInsertUri = mContext.getContentResolver().insert(LocationEntry.CONTENT_URI, locationValues);
            return ContentUris.parseId(locationInsertUri);
        }
    }
}
