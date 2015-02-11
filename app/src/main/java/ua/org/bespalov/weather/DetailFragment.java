package ua.org.bespalov.weather;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import ua.org.bespalov.weather.data.WeatherContract.WeatherEntry;
import ua.org.bespalov.weather.data.WeatherContract.LocationEntry;

public class DetailFragment extends android.support.v4.app.Fragment implements LoaderManager.LoaderCallbacks<Cursor>{
    private static final int FORECAST_LOADER = 0;
    private static final String[] FORECAST_COLUMNS = {
            WeatherEntry.TABLE_NAME + "." + WeatherEntry._ID,
            WeatherEntry.COLUMN_DATETEXT,
            WeatherEntry.COLUMN_SHORT_DESC,
            WeatherEntry.COLUMN_MAX_TEMP,
            WeatherEntry.COLUMN_MIN_TEMP,
            WeatherEntry.COLUMN_HUMIDITY,
            WeatherEntry.COLUMN_WIND_SPEED,
            WeatherEntry.COLUMN_DEGREES,
            WeatherEntry.COLUMN_PRESSURE,
            WeatherEntry.COLUMN_WEATHER_ID,
            LocationEntry.COLUMN_LOC_SETTING
    };

    private static String LOG_TAG = DetailFragment.class.getSimpleName();
    private static String FORECAST_SHARE_HASHTAG = "#Weather";
    private static final int DETAIL_LOADER = 0;
    private static final String LOCATION_KEY = "location";

    private Context mContext;
    private ShareActionProvider mShareActionProvider;
    private String mLocation;
    private String mForecast;

    private TextView mFriendlyDateView;
    private TextView mDateView;
    private TextView mHighTempView;
    private TextView mLowTempView;
    private TextView mHumidityView;
    private TextView mWindView;
    private TextView mPressureView;
    private TextView mDescriptionView;
    private ImageView mIconView;

    public DetailFragment() {
        setHasOptionsMenu(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(LOCATION_KEY, mLocation);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mLocation != null && !mLocation.equals(Utility.getPreferredLocation(getActivity()))){
            getLoaderManager().restartLoader(DETAIL_LOADER, null, this);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getLoaderManager().initLoader(DETAIL_LOADER, null, this);
        mContext = getActivity();
        if (savedInstanceState != null) {
            mLocation = savedInstanceState.getString(LOCATION_KEY);
        }
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_detail, container, false);
        mFriendlyDateView = (TextView) rootView.findViewById(R.id.detail_dayname_textview);
        mDateView = (TextView) rootView.findViewById(R.id.detail_date_textview);
        mHighTempView = (TextView) rootView.findViewById(R.id.detail_high_textview);
        mLowTempView = (TextView) rootView.findViewById(R.id.detail_low_textview);
        mHumidityView = (TextView) rootView.findViewById(R.id.detail_humidity_textview);
        mWindView = (TextView) rootView.findViewById(R.id.detail_wind_textview);
        mPressureView = (TextView) rootView.findViewById(R.id.detail_pressure_textview);
        mDescriptionView = (TextView) rootView.findViewById(R.id.detail_forecast_textview);
        mIconView = (ImageView) rootView.findViewById(R.id.item_icon);
        return rootView;
    }

    private Intent createShareForecastIntent(){
        Intent shareIntent = new Intent(Intent.ACTION_SEND)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, mForecast + FORECAST_SHARE_HASHTAG);
        return shareIntent;
    }
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.detailfragment, menu);
        MenuItem shareItem = menu.findItem(R.id.action_share);
        ShareActionProvider mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);
        if (mShareActionProvider != null) {
            mShareActionProvider.setShareIntent(createShareForecastIntent());
        } else {
            Log.d(LOG_TAG, "Share Action Provider is null?");
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String date = getActivity().getIntent().getStringExtra(Intent.EXTRA_TEXT);
        Uri weatherForLocationAndDateUri = WeatherEntry.buildWeatherLocationWithDate(
                Utility.getPreferredLocation(getActivity()), date
        );
        Log.d(LOG_TAG, weatherForLocationAndDateUri.toString());
        return new CursorLoader(
                getActivity(),
                weatherForLocationAndDateUri,
                FORECAST_COLUMNS,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()){return;}

        int weatherId = data.getInt(data.getColumnIndex(WeatherEntry.COLUMN_WEATHER_ID));
        mIconView.setImageResource(Utility.getArtResourceForWeatherCondition(weatherId));

        String dateString = data.getString(data.getColumnIndex(WeatherEntry.COLUMN_DATETEXT));
        mFriendlyDateView.setText(Utility.getDayName(mContext, dateString));
        mDateView.setText(Utility.getFormattedMonthDay(mContext, dateString));

        String weatherDescription = data.getString(data.getColumnIndex(WeatherEntry.COLUMN_SHORT_DESC));
        mDescriptionView.setText(weatherDescription);


        boolean isMetric = Utility.isMetric(mContext);
        String high = Utility.formatTemperature(mContext, data.getDouble(data.getColumnIndex(WeatherEntry.COLUMN_MAX_TEMP)), isMetric);
        String low = Utility.formatTemperature(mContext, data.getDouble(data.getColumnIndex(WeatherEntry.COLUMN_MIN_TEMP)), isMetric);
        mHighTempView.setText(high);
        mLowTempView.setText(low);

        String humidity = Utility.formatHumidity(mContext, data.getDouble(data.getColumnIndex(WeatherEntry.COLUMN_HUMIDITY)));
        mHumidityView.setText(humidity);

        String windSpeedAndDirection = Utility.formatWind(mContext,
                data.getDouble(data.getColumnIndex(WeatherEntry.COLUMN_WIND_SPEED)),
                data.getDouble(data.getColumnIndex(WeatherEntry.COLUMN_DEGREES)));
        mWindView.setText(windSpeedAndDirection);

        String pressure = Utility.formatPressure(mContext, data.getDouble(data.getColumnIndex(WeatherEntry.COLUMN_PRESSURE)));
        mPressureView.setText(pressure);

        mForecast = String.format("%s - %s - %s/%s", dateString, weatherDescription, high, low);
        if (mShareActionProvider != null) {
            mShareActionProvider.setShareIntent(createShareForecastIntent());
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }
}