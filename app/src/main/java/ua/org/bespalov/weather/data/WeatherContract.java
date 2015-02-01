package ua.org.bespalov.weather.data;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.BaseColumns;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WeatherContract {
    public static final String CONTENT_AUTHORITY = "ua.org.bespalov.weather";
    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);
    public static final String PATH_WEATHER = "weather";
    public static final String PATH_LOCATION = "location";
    public static final String DATE_FORMAT = "yyyyMMdd";

    public static String getDbDateString (Date date){
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        return sdf.format(date);
    }

    public static final class WeatherEntry implements BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath(PATH_WEATHER).build();
        public static final String CONTENT_TYPE = "vdn.android.cursor.dir/" + CONTENT_AUTHORITY + "/" + PATH_WEATHER;
        public static final String CONTENT_ITEM_TYPE = "vdn.android.cursor.item/" + CONTENT_AUTHORITY + "/" +PATH_WEATHER;

        public static final String TABLE_NAME = "weather";
        // Column with the foreign key into the location table.
        public static final String COLUMN_LOC_KEY = "location_id";
        // Date, stored as Text with format yyyy-MM-dd
        public static final String COLUMN_DATETEXT = "date";
        // Weather id as returned by API, to identify the icon to be used
        public static final String COLUMN_WEATHER_ID = "weather_id";
        // Short description and long description of the weather, as provided by API.
        // e.g "clear" vs "sky is clear".
        public static final String COLUMN_SHORT_DESC = "short_desc";
        // Min and max temperatures for the day (stored as floats)
        public static final String COLUMN_MIN_TEMP = "min";
        public static final String COLUMN_MAX_TEMP = "max";
        // Humidity is stored as a float representing percentage
        public static final String COLUMN_HUMIDITY = "humidity";
        // Humidity is stored as a float representing percentage
        public static final String COLUMN_PRESSURE = "pressure";
        // Windspeed is stored as a float representing windspeed mph
        public static final String COLUMN_WIND_SPEED = "wind";
        // Degrees are meteorological degrees (e.g, 0 is north, 180 is south). Stored as floats.
        public static final String COLUMN_DEGREES = "degrees";

        public static Uri buildWeatherUri(long id){
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }
        public static Uri buildWeatherLocation(String locationSettings){
            return CONTENT_URI.buildUpon().appendPath(locationSettings).build();
        }
        public static Uri buildWeatherLocationWithStartDate(String locationSettings, String startDate){
            return CONTENT_URI.buildUpon().appendPath(locationSettings)
                    .appendQueryParameter(COLUMN_DATETEXT, startDate).build();
        }
        public static Uri buildWeatherLocationWithDate(String locationSettings, String date){
            return CONTENT_URI.buildUpon().appendPath(locationSettings).appendPath(date).build();
        }
        public static String getLocationSettingFromUri(Uri uri){
            return uri.getPathSegments().get(1);
        }
        public static String getDateFromUri(Uri uri){
            return uri.getPathSegments().get(2);
        }
        public static String getStartDateFromUri(Uri uri){
            return uri.getQueryParameter(COLUMN_DATETEXT);
        }
    }
    public static final class LocationEntry implements BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath(PATH_LOCATION).build();
        public static final String CONTENT_TYPE = "vdn.android.cursor.dir/" + CONTENT_AUTHORITY + "/" + PATH_LOCATION;
        public static final String CONTENT_ITEM_TYPE = "vdn.android.cursor.item/" + CONTENT_AUTHORITY + "/" +PATH_LOCATION;

        public static final String TABLE_NAME = "location";
        // Location settings
        public static final String COLUMN_LOC_SETTING = "location_setting";
        // Location name as text
        public static final String COLUMN_LOC_NAME = "location_name";
        // Location latitude and longitude (stored as floats)
        public static final String COLUMN_LOC_LAT = "lat";
        public static final String COLUMN_LOC_LONG = "long";

        public static Uri buildLocationUri(long id){
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }
    }
}
