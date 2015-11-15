package hackprincetonf2015.wheretherebepeople;

import android.content.IntentSender;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.table.TableOperationCallback;
import com.microsoft.windowsazure.mobileservices.table.query.QueryOrder;
import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.models.Tweet;
import com.twitter.sdk.android.core.models.User;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MapsActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    public static final String TAG = MapsActivity.class.getSimpleName();

    private TwitterSession session;
    private MobileServiceClient mClient;
    private MobileServiceTable<Coordinates> mCoordinateTable;
    private MobileServiceTable<Index> mIndexTable;

    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    private final static int DB_FETCH_DELAY = 1000; // time interval between db fetches

    private double score = -1.0; // tweet score of user

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private RequestQueue queue;

    private Handler fetchHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        try {
            mClient = new MobileServiceClient(
                    "https://wtbp.azure-mobile.net/",
                    "GfBmrdzCMouqmEAzIFPOCdNWVGFWxE56",
                    this
            );
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        mCoordinateTable = mClient.getTable(Coordinates.class);
        mIndexTable = mClient.getTable(Index.class);

        fetchHandler = new Handler();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("Where There Be People");
        setSupportActionBar(toolbar);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(10 * 1000); // 1 second, in milliseconds

        queue = Volley.newRequestQueue(getApplicationContext());

        session = Twitter.getInstance().core.getSessionManager().getActiveSession();
        Twitter.getApiClient(session).getAccountService()
                .verifyCredentials(true, false, new Callback<User>() {
                    @Override
                    public void success(Result<User> userResult) {
                        User user = userResult.data;
                        Tweet tweet = user.status;
                        Log.d(TAG, tweet.text + "");

                        String tweetText = tweet.text.replace(" ", "+");

                        StringRequest request = new StringRequest(
                                Request.Method.GET,
                                "https://api.datamarket.azure.com/data.ashx/amla/text-analytics/v1/GetSentiment?Text=" + tweetText,
                                new Response.Listener<String>() {
                                    @Override
                                    public void onResponse(String response) {
                                        try {
                                            JSONObject jsonObject = new JSONObject(response);
                                            score = jsonObject.optDouble("Score");
                                            Log.d("Score", score + "");

                                        } catch (JSONException e) {
                                            Log.e(TAG, e.toString());
                                        }
                                    }
                                },
                                new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError error) {
                                        if (error.networkResponse != null && error.networkResponse.data != null) {
                                            Log.e(TAG, new VolleyError(new String(error.networkResponse.data)).toString());
                                        }
                                    }
                                }) {
                            @Override
                            public Map<String, String> getHeaders() throws AuthFailureError {
                                HashMap<String, String> params = new HashMap<>();
                                params.put("Authorization", "Basic QWNjb3VudEtleTo1OGg1NFNLYkdSVXc0QWdvK2lmRG5WbjdMZUZmTzY2b21WQitWNUQ3ZGxR");
                                params.put("Accept", "application/json");

                                return params;
                            }
                        };
                        request.setRetryPolicy(new DefaultRetryPolicy(
                                10000,
                                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                        queue.add(request);
                    }

                    @Override
                    public void failure(TwitterException e) {
                    }

                });

//        TwitterSession session = Twitter.getInstance().core.getSessionManager().getActiveSession();
//        String msg = "@" + session.getUserName() + " logged in! (#" + session.getUserId() + ")";
//        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();

        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.getUiSettings().setZoomControlsEnabled(true);
        fetchDB.run();
    }

    private void handleNewLocation(Location location) {
        Log.d(TAG, location.toString());

        double currentLatitude = location.getLatitude();
        double currentLongitude = location.getLongitude();

        insertDB(currentLatitude, currentLongitude);
    }

    private void drawPath(MobileServiceList<Coordinates> points) {
        mMap.clear();

        Color heat = new Color();

        MarkerOptions options;
        PolylineOptions currentline = new PolylineOptions();

        for (int i = 0; i < points.size() - 1; i++) {
            Log.d(TAG, ""+points.get(i).userid+": "+points.get(i).latitude+", " +points.get(i).longitude);

            if (points.get(i).userid == points.get(i+1).userid) {
                LatLng point1 = new LatLng(points.get(i).latitude, points.get(i).longitude);
                LatLng point2 = new LatLng(points.get(i + 1).latitude, points.get(i + 1).longitude);
                currentline.add(point1).width(16).color(heat.argb(50, 160, 0, 0));
            }
            else {
                LatLng lastPoint = new LatLng(points.get(i).latitude, points.get(i).longitude);
                if (points.get(i).userid == session.getUserId()) {
                    options = new MarkerOptions()
                            .position(lastPoint)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                            .title("Your location");
                }
                else {
                    float[] hsv = new float[3];
                    double user_score = points.get(i).score;
                    Color.RGBToHSV((int)(user_score * 255), 0, (int)((1 - user_score) * 255), hsv);
                    options = new MarkerOptions()
                            .position(lastPoint)
                            .icon(BitmapDescriptorFactory.defaultMarker(hsv[0]));
                }
                mMap.addMarker(options);
                mMap.addPolyline(currentline.add(lastPoint));
                currentline = new PolylineOptions();
            }
        }

        LatLng lastPoint = new LatLng(points.get(points.size()-1).latitude, points.get(points.size()-1).longitude);
        if (points.get(points.size()-1).userid == session.getUserId()) {
            options = new MarkerOptions()
                    .position(lastPoint)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .title("Your location");
        }
        else {
            float[] hsv = new float[3];
            double user_score = points.get(points.size() - 1).score;
            Color.RGBToHSV((int)(user_score * 255), 0, (int)(255 - (user_score * 255)), hsv);
            options = new MarkerOptions()
                    .position(lastPoint)
                    .icon(BitmapDescriptorFactory.defaultMarker(hsv[0]));
        }
        mMap.addMarker(options);
        mMap.addPolyline(currentline.add(lastPoint));
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (location == null) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        } else {
            handleNewLocation(location);
            double currentLatitude = location.getLatitude();
            double currentLongitude = location.getLongitude();

            insertDB(currentLatitude, currentLongitude);

            LatLng latLng = new LatLng(currentLatitude, currentLongitude);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0f));
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        handleNewLocation(location);
    }

    Runnable fetchDB = new Runnable() {
        @Override
        public void run() {
            new AsyncTask<Void, Void, MobileServiceList<Coordinates>>() {
                protected MobileServiceList<Coordinates> doInBackground(Void... no) {
                    MobileServiceList<Coordinates> coordinates = null;
                    try {
                        coordinates = mCoordinateTable.
                                orderBy("userid", QueryOrder.Ascending).
                                orderBy("time", QueryOrder.Ascending).
                                execute().get();

                        for (Coordinates c : coordinates) {
                            Log.d(TAG, "userid: " + c.userid + " , time: " + c.time);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "ERROR NULLPOINTEREXCEPTION");
                    }
                    return coordinates;
                }

                protected void onPostExecute(MobileServiceList<Coordinates> coordinates) {
                    if (coordinates.size() > 0) {
                        drawPath(coordinates);
                    }
                }
            }.execute();
            fetchHandler.postDelayed(this, DB_FETCH_DELAY);
        }
    };

    private void insertDB(double lat, double lon) {
        if (score >= 0) {
            Coordinates coordinate = new Coordinates();
            coordinate.latitude = lat;
            coordinate.longitude = lon;
            coordinate.userid = session.getUserId();
            coordinate.score = score;
            coordinate.time = new Date().getTime();
            mCoordinateTable.insert(coordinate, new TableOperationCallback<Coordinates>() {
                public void onCompleted(Coordinates entity, Exception exception, ServiceFilterResponse response) {
                    if (exception == null) {
                        Log.d(TAG, "Insert succeeded");
                    } else {
                        Log.d(TAG, "Insert failed");
                    }
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_maps, menu);
        return true;
    }
}