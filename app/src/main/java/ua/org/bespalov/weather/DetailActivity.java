package ua.org.bespalov.weather;


import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class DetailActivity extends ActionBarActivity {
    public static final String DATE_KEY = "date";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        if (savedInstanceState == null) {
            String dateString = getIntent().getStringExtra(DATE_KEY);

            Bundle args = new Bundle();
            args.putString(DATE_KEY, dateString);

            DetailFragment detailFragment = new DetailFragment();
            detailFragment.setArguments(args);

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.weather_detail_container, detailFragment)
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent settingIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingIntent);
        }

        return super.onOptionsItemSelected(item);
    }
}
