package ua.org.bespalov.weather;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends ActionBarActivity implements ForecastFragment.OnListItemClickedListener{

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    boolean mTwoPane;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (findViewById(R.id.weather_detail_container) != null){
            mTwoPane = true;
            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.weather_detail_container, new DetailFragment())
                        .commit();
            }
        } else {
            mTwoPane = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings){
            Intent settingIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingIntent);
        }
        if (id == R.id.action_location_map) {
            openPreferredLocationInMap();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openPreferredLocationInMap (){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String location = sharedPreferences.getString(getString(R.string.pref_location_key),getString(R.string.pref_location_default));
        Uri geoLocation = new Uri.Builder().scheme("geo")
                .appendQueryParameter("q", location)
                .build();
        Intent locationIntent = new Intent(Intent.ACTION_VIEW);
        locationIntent.setData(geoLocation);
        if (locationIntent.resolveActivity(getPackageManager()) != null){
            startActivity(locationIntent);
        } else {
            Log.d(LOG_TAG, "Could not call " + geoLocation + ", no intent");
        }
    }

    @Override
    public void onListItemClicked(String dateString) {
        if (mTwoPane) {
            Bundle args = new Bundle();
            args.putString(DetailActivity.DATE_KEY, dateString);

            DetailFragment detailFragment = new DetailFragment();
            detailFragment.setArguments(args);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.weather_detail_container, detailFragment).commit();
        } else {
            Intent intent = new Intent(this, DetailActivity.class)
                    .putExtra(DetailActivity.DATE_KEY, dateString);
            startActivity(intent);
        }
    }
}