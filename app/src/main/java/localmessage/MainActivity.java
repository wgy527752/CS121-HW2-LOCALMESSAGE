package localmessage;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import localmessage.R;
import com.google.gson.Gson;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class MainActivity extends ActionBarActivity {
    Location lastLocation;
    private double lastAccuracy = (double) 1e10;
    private long lastAccuracyTime = 0;
    double latitude;
    double longitude;
    double time;
    private static final String LOG_TAG = "lclicker";
    private static final float GOOD_ACCURACY_METERS = 100;
    // This is an id for my app, to keep the key space separate from other apps.
    private static final String MY_APP_ID = "luca_bboard";
    private static final String SERVER_URL_PREFIX = "https://luca-teaching.appspot.com/store/default/";
    // To remember the favorite account.
    public static final String PREF_ACCOUNT = "pref_account";
    // To remember the post we received.
    public static final String PREF_POSTS = "pref_posts";
    // Uploader.
    private ServerCall uploader;
    // Remember whether we have already successfully checked in.
    private boolean checkinSuccessful = false;
    private ArrayList<String> accountList;

    private class ListElement {
        ListElement() {};
        public String textLabel;
        public String buttonLabel;
    }

    private ArrayList<ListElement> aList;

    private class MyAdapter extends ArrayAdapter<ListElement> {
        int resource;
        Context context;

        public MyAdapter(Context _context, int _resource, List<ListElement> items) {
            super(_context, _resource, items);
            resource = _resource;
            context = _context;
            this.context = _context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout newView;
            ListElement w = getItem(position);
            // Inflate a new view if necessary.
            if (convertView == null) {
                newView = new LinearLayout(getContext());
                String inflater = Context.LAYOUT_INFLATER_SERVICE;
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(inflater);
                vi.inflate(resource,  newView, true);
            } else {
                newView = (LinearLayout) convertView;
            }
            // Fills in the view.
            TextView tv = (TextView) newView.findViewById(R.id.itemText);
            tv.setText(w.textLabel);
            // Set a listener for the whole list item.
            newView.setTag(w.textLabel);
            newView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String s = v.getTag().toString();
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(context, s, duration);
                    toast.show();
                }
            });
            return newView;
        }
    }

    private MyAdapter aa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        aList = new ArrayList<ListElement>();
        aa = new MyAdapter(this, R.layout.list_element, aList);
        ListView myListView = (ListView) findViewById(R.id.listView);
        myListView.setAdapter(aa);
        aa.notifyDataSetChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // First super, then do stuff.
        // Let us display the previous posts, if any.
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String result = settings.getString(PREF_POSTS, null);
        if (result != null) {
            displayResult(result);
        }
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    @Override
    protected void onPause() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(locationListener);
        // Stops the upload if any.
        if (uploader != null) {
            uploader.cancel(true);
            uploader = null;
        }
        super.onPause();
    }

    // Listens to the location, and gets the most recent location.

    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            longitude = location.getLongitude();
            latitude = location.getLatitude();
            lastLocation = location;
            time = location.getTime();
            TextView tv = (TextView) findViewById(R.id.accuracyView);
            String s = String.format("%f m\n%f m", latitude, longitude);
            tv.setText(s);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}
    };


    String randomString(final int length) {
        char[] chars = "abcdefghijklmnopqrstuvwxyz1234567890".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        return sb.toString();
    }

    public void refresh(View v) {
        PostMessageSpec myCallSpec = new PostMessageSpec();
        myCallSpec.url = SERVER_URL_PREFIX + "get_local";
        myCallSpec.context = MainActivity.this;
        // Let's add the parameters.
        HashMap<String,String> m = new HashMap<String,String>();
        m.put("lat", Double.toString(latitude));
        m.put("lng", Double.toString(longitude));

        myCallSpec.setParams(m);
        // Actual server call.
        if (uploader != null) {
            // There was already an upload in progress.
            uploader.cancel(true);
        }
        uploader = new ServerCall();
        uploader.execute(myCallSpec);
    }

    public void clickButton(View v) {
        // Get the text we want to send.
        EditText et = (EditText) findViewById(R.id.editText);
        String msg = et.getText().toString();
        String msgid = randomString(8);
        // start the call.
        PostMessageSpec myCallSpec = new PostMessageSpec();
        myCallSpec.url = SERVER_URL_PREFIX + "put_local";
        myCallSpec.context = MainActivity.this;

        HashMap<String,String> m = new HashMap<String,String>();
        m.put("lat", Double.toString(latitude));
        m.put("lng", Double.toString(longitude));
        m.put("msgid", randomString(8));
        m.put("msg", msg);
        m.put("ts", Double.toString(time));

        myCallSpec.setParams(m);
        // Actual server call.
        if (uploader != null) {
            uploader.cancel(true);
        }
        uploader = new ServerCall();
        uploader.execute(myCallSpec);

        InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);
        et.setText("");
        et.clearFocus();
    }

    private String reallyComputeHash(String s) {
        // Computes the crypto hash of string s, in a web-safe format.
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(s.getBytes());
            digest.update("My secret key".getBytes());
            byte[] md = digest.digest();
            // Now we need to make it web safe.
            String safeDigest = Base64.encodeToString(md, Base64.URL_SAFE);
            return safeDigest;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }
    /**
     * This class is used to do the HTTP call, and it specifies how to use the result.
     */
    class PostMessageSpec extends ServerCallSpec {
        @Override
        public void useResult(Context context, String result) {
            if (result == null) {
                // Do something here, e.g. tell the user that the server cannot be contacted.
                Log.i(LOG_TAG, "The server call failed.");
            } else {
                // Translates the string result, decoding the Json.
                Log.i(LOG_TAG, "Received string: " + result);
                displayResult(result);
                // Stores in the settings the last messages received.
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PREF_POSTS, result);
                editor.commit();
            }
        }
    }

    private void displayResult(String result) {
        Gson gson = new Gson();
        MessageList ml = gson.fromJson(result, MessageList.class);
        // Fills aList, so we can fill the listView.
        aList.clear();
        for (int i = 0; i < ml.messages.length; i++) {
            ListElement ael = new ListElement();
            /* adds the message body to the text label in the app view
            -- pulls this from the message class by indexing into the message
            -- list and obtaining the type */
            ael.textLabel = ml.messages[i].msg+"\n"+ml.messages[i].ts;
            aList.add(ael);
        }
        aa = new MyAdapter(this, R.layout.list_element, aList);
        ListView myListView = (ListView) findViewById(R.id.listView);
        myListView.setAdapter(aa);
        aa.notifyDataSetChanged();
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
