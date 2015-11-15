package hackprincetonf2015.wheretherebepeople;

import android.content.IntentSender;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;
import android.graphics.Color;
import android.os.Handler;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.Polyline;

import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.table.TableOperationCallback;

import java.net.MalformedURLException;
import java.util.Date;

import com.microsoft.windowsazure.mobileservices.table.query.QueryOrder;
import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.TwitterSession;

public class MapsActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    public static final String TAG = MapsActivity.class.getSimpleName();

    private MobileServiceClient mClient;
    private MobileServiceTable<Coordinates> mCoordinateTable;
    private MobileServiceTable<Index> mIndexTable;

    private long thisUser = 777777;

    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    private final static int DB_FETCH_DELAY = 10000; // time interval between db fetches

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

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
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds

        TwitterSession session = Twitter.getInstance().core.getSessionManager().getActiveSession();
        String msg = "@" + session.getUserName() + " logged in! (#" + session.getUserId() + ")";
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();

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

        LatLng latLng = new LatLng(currentLatitude, currentLongitude);

        MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .title("I am here!");
        mMap.addMarker(options);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        CameraUpdate zoom = CameraUpdateFactory.zoomTo(15);
        mMap.animateCamera(zoom);
    }

    private void drawPath(MobileServiceList<Coordinates> points) {
//        Coordinates last_point = points.get(points.size() - 1);
//        LatLng lastPoint = new LatLng(last_point.latitude, last_point.longitude);
//        MarkerOptions options = new MarkerOptions()
//                .position(lastPoint)
//                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
//                .title("Final position");
//        mMap.addMarker(options);

        Color heat = new Color();

        for (int i = 0; i < points.size() - 1; i++) {
            Log.d(TAG, ""+points.get(i).userId+": "+points.get(i).latitude+", " +points.get(i).longitude);

            if (points.get(i).userId == points.get(i+1).userId) {
                LatLng point1 = new LatLng(points.get(i).latitude, points.get(i).longitude);
                LatLng point2 = new LatLng(points.get(i + 1).latitude, points.get(i + 1).longitude);
                mMap.addPolyline(new PolylineOptions()
                        .add(point1, point2)
                        .width(16)
                        .color(heat.argb(50, 160, 0, 0)));
            }
            else {
                LatLng lastPoint = new LatLng(points.get(i).latitude, points.get(i).longitude);
                MarkerOptions options = new MarkerOptions()
                .position(lastPoint)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .title("Current location");
                mMap.addMarker(options);
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (location == null) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        } else {
            handleNewLocation(location);
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
                    //MobileServiceList<Index> users = null;
                    try {
                        //mCoordinateTable.where().field("userId").eq(thisUser).execute().get();
                        coordinates = mCoordinateTable.
                                orderBy("time", QueryOrder.Ascending).
//                                orderBy("userId", QueryOrder.Ascending).
                                execute().get();

                        Log.e(TAG, "coordinates: "+coordinates.size());
                        //users = mIndexTable.select("userId").execute().get();
                    } catch (Exception e) {
                        Log.e(TAG, "ERROR NULLPOINTEREXCEPTION");
                    }
                    return coordinates;
                }

                protected void onPostExecute(MobileServiceList<Coordinates> coordinates) {
                    Log.e(TAG, "ERROR EXECUTED");
                    drawPath(coordinates);
                }
            }.execute();
            //fetchHandler.postDelayed(this, DB_FETCH_DELAY);
        }
    };

    private void insertDB(double lat, double lon) {
        Coordinates coordinate = new Coordinates();
        coordinate.latitude = lat;
        coordinate.longitude = lon;
        coordinate.userId = thisUser;
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_maps, menu);
        return true;
    }
}