package com.vanderbie.bumpy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import nl.sense_os.service.SenseServiceStub;
import nl.sense_os.service.constants.SenseDataTypes;
import nl.sense_os.service.constants.SensePrefs;
import nl.sense_os.service.constants.SensePrefs.Main.Ambience;
import nl.sense_os.service.constants.SensePrefs.Main.Motion;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.jjoe64.graphview.BarGraphView;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;
import com.jjoe64.graphview.LineGraphView;
import com.vanderbie.bumpy.location.LocationUtils;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import android.app.Activity;
import android.os.Bundle;

public class BumpyMainFragmentActivity extends FragmentActivity implements
		SensorEventListener, LocationListener,
		GooglePlayServicesClient.ConnectionCallbacks,
		GooglePlayServicesClient.OnConnectionFailedListener {

	private GraphView graphView;
	private GraphViewSeries xLine;
	private GraphViewSeries yLine;
	private GraphViewSeries zLine;
	private GraphViewSeries rideLine;
	private double graph2LastXValue = 0d;

	private SensorManager sensorManager;
	private ArrayList<AccelData> sensorData;
	private boolean started = true;
	private boolean resumeSensing = false;
	private long lastAdd;

	// A request to connect to Location Services
	private LocationRequest mLocationRequest;

	// Stores the current instantiation of the location client in this object
	private LocationClient mLocationClient;
	// Handle to SharedPreferences for this app
	SharedPreferences mPrefs;

	// Handle to a SharedPreferences editor
	SharedPreferences.Editor mEditor;

	/*
	 * Note if updates have been turned on. Starts out as "false"; is set to
	 * "true" in the method handleRequestSuccess of LocationUpdateReceiver.
	 */
	boolean mUpdatesRequested = false;

	// Handles to UI widgets
	private TextView mLatLng;
	private TextView mAddress;
	private ProgressBar mActivityIndicator;
	private TextView mConnectionState;
	private TextView mConnectionStatus;
	
	//Sense
    private static final String TAG = "Bumpy";
    private static final String DEMO_SENSOR_NAME = "bumpy";
    private BumpyApplication mApplication;
    
    //int ride/transport
    private int currentTransport = 999;
    // 0 = car
    // 1 = bus
    // 2 = tram
    // 3 = train
    // 4 = metro
    // 5 = bike

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bumbview);

//		mApplication = (BumpyApplication) this.;
//        setPreferences();
        
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		sensorData = new ArrayList<AccelData>();
		lastAdd = System.currentTimeMillis();

		xLine = new GraphViewSeries("x-as", new GraphViewSeriesStyle(
				Color.GREEN, 2), new GraphViewData[] {});
		yLine = new GraphViewSeries("y-as", new GraphViewSeriesStyle(Color.RED,
				2), new GraphViewData[] {});
		zLine = new GraphViewSeries("z-as", new GraphViewSeriesStyle(
				Color.BLUE, 2), new GraphViewData[] {});
		// graph with dynamically genereated horizontal and vertical labels
//		if (getIntent().getStringExtra("type").equals("bar")) {
//			graphView = new BarGraphView(this // context
//					, "GraphViewDemo" // heading
//			);
//		} else {
			graphView = new LineGraphView(this // context
					, "GraphViewDemo" // heading
			);
//		}

//		// graph with custom labels and drawBackground
//		if (getIntent().getStringExtra("type").equals("bar")) {
//			graphView = new BarGraphView(this, "Accelerometer Demo");
//		} else {
			graphView = new LineGraphView(this, "Accelerometer Demo");
			// ((LineGraphView) graphView).setDrawBackground(true);
//		}
		graphView.addSeries(xLine);
		graphView.addSeries(yLine); // data
		graphView.addSeries(zLine); // data
		graphView.setViewPort(-20, 20);
		graphView.setScalable(true);
		graphView.setScrollable(true);
		graphView.setShowLegend(true);

		LinearLayout layout = (LinearLayout) findViewById(R.id.graph2);
		layout.addView(graphView);

		// Get handles to the UI view objects
		mLatLng = (TextView) findViewById(R.id.lat_lng);
		mAddress = (TextView) findViewById(R.id.address);
		mActivityIndicator = (ProgressBar) findViewById(R.id.address_progress);
		mConnectionState = (TextView) findViewById(R.id.text_connection_state);
		mConnectionStatus = (TextView) findViewById(R.id.text_connection_status);

		// Create a new global location parameters object
		mLocationRequest = LocationRequest.create();

		/*
		 * Set the update interval
		 */
		mLocationRequest
				.setInterval(LocationUtils.UPDATE_INTERVAL_IN_MILLISECONDS);

		// Use high accuracy
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

		// Set the interval ceiling to one minute
		mLocationRequest
				.setFastestInterval(LocationUtils.FAST_INTERVAL_CEILING_IN_MILLISECONDS);

		// Note that location updates are off until the user turns them on
		mUpdatesRequested = false;

		// Open Shared Preferences
		mPrefs = getSharedPreferences(LocationUtils.SHARED_PREFERENCES,
				Context.MODE_PRIVATE);

		// Get an editor
		mEditor = mPrefs.edit();

		/*
		 * Create a new location client, using the enclosing class to handle
		 * callbacks.
		 */
		mLocationClient = new LocationClient(this, this, this);
		
		
		//GMaps
		// Get a handle to the Map Fragment
//        GoogleMap map = ((MapFragment) getFragmentManager()
//                .findFragmentById(R.id.map)).getMap();
        FragmentManager myFM = this.getSupportFragmentManager();

        final SupportMapFragment myMAPF = (SupportMapFragment) myFM
                        .findFragmentById(R.id.map);
        GoogleMap map = myMAPF.getMap();
        
        LatLng amsterdam = new LatLng(52.372088,4.898701);
        
        map.setMyLocationEnabled(true);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(amsterdam, 13));
	}

	/*
	 * Called when the Activity is no longer visible at all. Stop updates and
	 * disconnect.
	 */
	@Override
	public void onStop() {

		// If the client is connected
		if (mLocationClient.isConnected()) {
			stopPeriodicUpdates();
		}

		// After disconnect() is called, the client is considered "dead".
		mLocationClient.disconnect();

		super.onStop();
	}

	@Override
	protected void onPause() {
		if (started) {
			stopDisplaySensing();
			resumeSensing = true;
		}

		// Save the current setting for updates
		mEditor.putBoolean(LocationUtils.KEY_UPDATES_REQUESTED,
				mUpdatesRequested);
		mEditor.commit();

		super.onPause();
	}

	/*
	 * Called when the Activity is restarted, even before it becomes visible.
	 */
	@Override
	public void onStart() {

		super.onStart();

		/*
		 * Connect the client. Don't re-start any requests here; instead, wait
		 * for onResume()
		 */
		mLocationClient.connect();

	}

	@Override
	protected void onResume() {
		super.onResume();
		if (resumeSensing) {
			startDisplaySensing();
		}

		// If the app already has a setting for getting location updates, get it
		if (mPrefs.contains(LocationUtils.KEY_UPDATES_REQUESTED)) {
			mUpdatesRequested = mPrefs.getBoolean(
					LocationUtils.KEY_UPDATES_REQUESTED, false);

			// Otherwise, turn off location updates until requested
		} else {
			mEditor.putBoolean(LocationUtils.KEY_UPDATES_REQUESTED, false);
			mEditor.commit();
		}
	}

	private void startDisplaySensing() {
		started = true;
		Sensor accel = sensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(this, accel,
				SensorManager.SENSOR_DELAY_FASTEST);

		// If Google Play Services is available
		if (servicesConnected()) {

			// Get the current location
			Location currentLocation = mLocationClient.getLastLocation();

			// Display the current location in the UI
			mLatLng.setText(LocationUtils.getLatLng(this, currentLocation));
		}

		((ToggleButton) this.findViewById(R.id.toggleBumpy))
				.setChecked(started);
	}

	private void stopDisplaySensing() {
		if (started == true) {
			sensorManager.unregisterListener(this);
			started = false;

			((ToggleButton) this.findViewById(R.id.toggleBumpy))
					.setChecked(started);
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (started) {
			double x = event.values[0];
			double y = event.values[1];
			double z = event.values[2];
			long timestamp = System.currentTimeMillis();
			AccelData data = new AccelData(timestamp, x, y, z);
			sensorData.add(data);
			if (timestamp - lastAdd >= 200) {
				graph2LastXValue += 1d;

				xLine.appendData(new GraphViewData(graph2LastXValue,
						event.values[0]), true, 20);
				yLine.appendData(new GraphViewData(graph2LastXValue,
						event.values[1]), true, 20);
				zLine.appendData(new GraphViewData(graph2LastXValue,
						event.values[2]), true, 20);

				lastAdd = timestamp;
			}

		}

	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.toggleBumpy:
			if (((ToggleButton) view).isChecked()) {
				// start service
				startDisplaySensing();
				//startSense();
			} else {
				// stop service
				stopDisplaySensing();
				//stopSense();
			}
			break;
		case R.id.transportation:
			int rbId;
			if ((rbId = ((RadioGroup) view).getCheckedRadioButtonId()) > -1) {
				RadioButton rb = (RadioButton) this.findViewById(rbId);
				// push switch from transportation mode to the server
				
				if(currentTransport != 999){
					//stop previous transport
					//insertData(currentTransport, false);
				}
				
				switch(rbId){
					case R.id.transportation_car:
						currentTransport = 0;
						break;
					case R.id.transportation_bus:
						currentTransport = 1;
						break;
//					case R.id.transportation_tram:
//						currentTransport = 2;
//						break;
					case R.id.transportation_train:
						currentTransport = 3;
						break;
					case R.id.transportation_metro:
						currentTransport = 4;
						break;
					case R.id.transportation_bike:
						currentTransport = 5;
						break;
				}
				
				currentTransport = rbId;
				//start current transport
				//insertData(currentTransport, true);
			}
			break;
		}
	}

	/*
	 * Handle results returned to this Activity by other Activities started with
	 * startActivityForResult(). In particular, the method onConnectionFailed()
	 * in LocationUpdateRemover and LocationUpdateRequester may call
	 * startResolutionForResult() to start an Activity that handles Google Play
	 * services problems. The result of this call returns here, to
	 * onActivityResult.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {

		// Choose what to do based on the request code
		switch (requestCode) {

		// If the request code matches the code sent in onConnectionFailed
		case LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST:

			switch (resultCode) {
			// If Google Play services resolved the problem
			case Activity.RESULT_OK:

				// Log the result
				Log.d(LocationUtils.APPTAG, getString(R.string.resolved));

				// Display the result
				mConnectionState.setText(R.string.connected);
				mConnectionStatus.setText(R.string.resolved);
				break;

			// If any other result was returned by Google Play services
			default:
				// Log the result
				Log.d(LocationUtils.APPTAG, getString(R.string.no_resolution));

				// Display the result
				mConnectionState.setText(R.string.disconnected);
				mConnectionStatus.setText(R.string.no_resolution);

				break;
			}

			// If any other request code was received
		default:
			// Report that this Activity received an unknown requestCode
			Log.d(LocationUtils.APPTAG,
					getString(R.string.unknown_activity_request_code,
							requestCode));

			break;
		}
	}

	/**
	 * Verify that Google Play services is available before making a request.
	 * 
	 * @return true if Google Play services is available, otherwise false
	 */
	private boolean servicesConnected() {

		// Check that Google Play services is available
		int resultCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(this);

		// If Google Play services is available
		if (ConnectionResult.SUCCESS == resultCode) {
			// In debug mode, log the status
			Log.d(LocationUtils.APPTAG,
					getString(R.string.play_services_available));

			// Continue
			return true;
			// Google Play services was not available for some reason
		} else {
			// Display an error dialog
			Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode,
					this, 0);
			if (dialog != null) {
				ErrorDialogFragment errorFragment = new ErrorDialogFragment();
				errorFragment.setDialog(dialog);
				errorFragment.show(getSupportFragmentManager(),
						LocationUtils.APPTAG);
			}
			return false;
		}
	}

	/**
	 * Invoked by the "Get Location" button.
	 * 
	 * Calls getLastLocation() to get the current location
	 * 
	 * @param v
	 *            The view object associated with this method, in this case a
	 *            Button.
	 */
	public void getLocation(View v) {

		// If Google Play Services is available
		if (servicesConnected()) {

			// Get the current location
			Location currentLocation = mLocationClient.getLastLocation();

			// Display the current location in the UI
			mLatLng.setText(LocationUtils.getLatLng(this, currentLocation));
		}
	}

	/**
	 * Invoked by the "Get Address" button. Get the address of the current
	 * location, using reverse geocoding. This only works if a geocoding service
	 * is available.
	 * 
	 * @param v
	 *            The view object associated with this method, in this case a
	 *            Button.
	 */
	// For Eclipse with ADT, suppress warnings about Geocoder.isPresent()
	@SuppressLint("NewApi")
	public void getAddress(View v) {

		// In Gingerbread and later, use Geocoder.isPresent() to see if a
		// geocoder is available.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD
				&& !Geocoder.isPresent()) {
			// No geocoder is present. Issue an error message
			Toast.makeText(this, R.string.no_geocoder_available,
					Toast.LENGTH_LONG).show();
			return;
		}

		if (servicesConnected()) {

			// Get the current location
			Location currentLocation = mLocationClient.getLastLocation();

			// Turn the indefinite activity indicator on
			mActivityIndicator.setVisibility(View.VISIBLE);

			// Start the background task
			(new BumpyMainFragmentActivity.GetAddressTask(this)).execute(currentLocation);
		}
	}

	/**
	 * Invoked by the "Start Updates" button Sends a request to start location
	 * updates
	 * 
	 * @param v
	 *            The view object associated with this method, in this case a
	 *            Button.
	 */
	public void startUpdates(View v) {
		mUpdatesRequested = true;

		if (servicesConnected()) {
			startPeriodicUpdates();
		}
	}

	/**
	 * Invoked by the "Stop Updates" button Sends a request to remove location
	 * updates request them.
	 * 
	 * @param v
	 *            The view object associated with this method, in this case a
	 *            Button.
	 */
	public void stopUpdates(View v) {
		mUpdatesRequested = false;

		if (servicesConnected()) {
			stopPeriodicUpdates();
		}
	}

	/*
	 * Called by Location Services when the request to connect the client
	 * finishes successfully. At this point, you can request the current
	 * location or start periodic updates
	 */
	@Override
	public void onConnected(Bundle bundle) {
		mConnectionStatus.setText(R.string.connected);

		if (mUpdatesRequested) {
			startPeriodicUpdates();
		}
	}

	/*
	 * Called by Location Services if the connection to the location client
	 * drops because of an error.
	 */
	@Override
	public void onDisconnected() {
		mConnectionStatus.setText(R.string.disconnected);
	}

	/*
	 * Called by Location Services if the attempt to Location Services fails.
	 */
	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {

		/*
		 * Google Play services can resolve some errors it detects. If the error
		 * has a resolution, try sending an Intent to start a Google Play
		 * services activity that can resolve error.
		 */
		if (connectionResult.hasResolution()) {
			try {

				// Start an Activity that tries to resolve the error
				connectionResult.startResolutionForResult(this,
						LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

				/*
				 * Thrown if Google Play services canceled the original
				 * PendingIntent
				 */

			} catch (IntentSender.SendIntentException e) {

				// Log the error
				e.printStackTrace();
			}
		} else {

			// If no resolution is available, display a dialog to the user with
			// the error.
			showErrorDialog(connectionResult.getErrorCode());
		}
	}

	/**
	 * Report location updates to the UI.
	 * 
	 * @param location
	 *            The updated location.
	 */
	@Override
	public void onLocationChanged(Location location) {

		// Report to the UI that the location was updated
		mConnectionStatus.setText(R.string.location_updated);

		// In the UI, set the latitude and longitude to the value received
		mLatLng.setText(LocationUtils.getLatLng(this, location));
	}

	/**
	 * In response to a request to start updates, send a request to Location
	 * Services
	 */
	private void startPeriodicUpdates() {

		mLocationClient.requestLocationUpdates(mLocationRequest, this);
		mConnectionState.setText(R.string.location_requested);
	}

	/**
	 * In response to a request to stop updates, send a request to Location
	 * Services
	 */
	private void stopPeriodicUpdates() {
		mLocationClient.removeLocationUpdates(this);
		mConnectionState.setText(R.string.location_updates_stopped);
	}

	/**
	 * Show a dialog returned by Google Play services for the connection error
	 * code
	 * 
	 * @param errorCode
	 *            An error code returned from onConnectionFailed
	 */
	private void showErrorDialog(int errorCode) {

		// Get the error dialog from Google Play services
		Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(errorCode,
				this, LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

		// If Google Play services can provide an error dialog
		if (errorDialog != null) {

			// Create a new DialogFragment in which to show the error dialog
			ErrorDialogFragment errorFragment = new ErrorDialogFragment();

			// Set the dialog in the DialogFragment
			errorFragment.setDialog(errorDialog);

			// Show the error dialog in the DialogFragment
			errorFragment.show(getSupportFragmentManager(),
					LocationUtils.APPTAG);
		}
	}

	/**
	 * Define a DialogFragment to display the error dialog generated in
	 * showErrorDialog.
	 */
	public static class ErrorDialogFragment extends DialogFragment {

		// Global field to contain the error dialog
		private Dialog mDialog;

		/**
		 * Default constructor. Sets the dialog field to null
		 */
		public ErrorDialogFragment() {
			super();
			mDialog = null;
		}

		/**
		 * Set the dialog to display
		 * 
		 * @param dialog
		 *            An error dialog
		 */
		public void setDialog(Dialog dialog) {
			mDialog = dialog;
		}

		/*
		 * This method must return a Dialog to the DialogFragment.
		 */
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return mDialog;
		}
	}

	/**
	 * An AsyncTask that calls getFromLocation() in the background. The class
	 * uses the following generic types: Location - A
	 * {@link android.location.Location} object containing the current location,
	 * passed as the input parameter to doInBackground() Void - indicates that
	 * progress units are not used by this subclass String - An address passed
	 * to onPostExecute()
	 */
	protected class GetAddressTask extends AsyncTask<Location, Void, String> {

		// Store the context passed to the AsyncTask when the system
		// instantiates it.
		Context localContext;

		// Constructor called by the system to instantiate the task
		public GetAddressTask(Context context) {

			// Required by the semantics of AsyncTask
			super();

			// Set a Context for the background task
			localContext = context;
		}

		/**
		 * Get a geocoding service instance, pass latitude and longitude to it,
		 * format the returned address, and return the address to the UI thread.
		 */
		@Override
		protected String doInBackground(Location... params) {
			/*
			 * Get a new geocoding service instance, set for localized
			 * addresses. This example uses android.location.Geocoder, but other
			 * geocoders that conform to address standards can also be used.
			 */
			Geocoder geocoder = new Geocoder(localContext, Locale.getDefault());

			// Get the current location from the input parameter list
			Location location = params[0];

			// Create a list to contain the result address
			List<Address> addresses = null;

			// Try to get an address for the current location. Catch IO or
			// network problems.
			try {

				/*
				 * Call the synchronous getFromLocation() method with the
				 * latitude and longitude of the current location. Return at
				 * most 1 address.
				 */
				addresses = geocoder.getFromLocation(location.getLatitude(),
						location.getLongitude(), 1);

				// Catch network or other I/O problems.
			} catch (IOException exception1) {

				// Log an error and return an error message
				Log.e(LocationUtils.APPTAG,
						getString(R.string.IO_Exception_getFromLocation));

				// print the stack trace
				exception1.printStackTrace();

				// Return an error message
				return (getString(R.string.IO_Exception_getFromLocation));

				// Catch incorrect latitude or longitude values
			} catch (IllegalArgumentException exception2) {

				// Construct a message containing the invalid arguments
				String errorString = getString(
						R.string.illegal_argument_exception,
						location.getLatitude(), location.getLongitude());
				// Log the error and print the stack trace
				Log.e(LocationUtils.APPTAG, errorString);
				exception2.printStackTrace();

				//
				return errorString;
			}
			// If the reverse geocode returned an address
			if (addresses != null && addresses.size() > 0) {

				// Get the first address
				Address address = addresses.get(0);

				// Format the first line of address
				String addressText = getString(
						R.string.address_output_string,

						// If there's a street address, add it
						address.getMaxAddressLineIndex() > 0 ? address
								.getAddressLine(0) : "",

						// Locality is usually a city
						address.getLocality(),

						// The country of the address
						address.getCountryName());

				// Return the text
				return addressText;

				// If there aren't any addresses, post a message
			} else {
				return getString(R.string.no_address_found);
			}
		}

		/**
		 * A method that's called once doInBackground() completes. Set the text
		 * of the UI element that displays the address. This method runs on the
		 * UI thread.
		 */
		@Override
		protected void onPostExecute(String address) {

			// Turn off the progress bar
			mActivityIndicator.setVisibility(View.GONE);

			// Set the address in the UI
			mAddress.setText(address);
		}
	}
	
	  private void insertData(int ride, boolean start) {
	        Log.v(TAG, "Insert data point");

	        // Description of the sensor

	        final long timestamp = System.currentTimeMillis();
	        final String name = DEMO_SENSOR_NAME;
	        final String displayName = "bumpy_ride";
	        final String dataType = SenseDataTypes.JSON;
	        final String description = name;
	        // the value to be sent, in json format
	        final String value = "{\"Ride\":\""+ride+"\",\"Start\":\""+(start ? "start" : "stop")+"\"}";

	        // start new Thread to prevent NetworkOnMainThreadException
	        new Thread() {

	            @Override
	            public void run() {
	                mApplication.getSensePlatform().addDataPoint(name, displayName, description,
	                        dataType, value, timestamp);
	            }
	        }.start();

	        // show message
	  }

	private void startSense() {
		Log.v(TAG, "Start logging Bumpy via Sense");

		SenseServiceStub senseService = mApplication.getSenseService();

		// enable some specific sensor modules
		senseService.toggleMotion(true);
		senseService.toggleLocation(true);

		// enable main state
		senseService.toggleMain(true);
	}

	private void stopSense() {
		Log.v(TAG, "Stop logging Bumpy via Sense");
		mApplication.getSenseService().toggleMain(false);
	}
	
	/**
     * Sets up the Sense service preferences
     */
    private void setPreferences() {
        Log.v(TAG, "Set preferences");

        SenseServiceStub senseService = mApplication.getSenseService();

        // turn off some specific sensors
        senseService.setPrefBool(Ambience.LIGHT, false);
        senseService.setPrefBool(Ambience.CAMERA_LIGHT, false);
        senseService.setPrefBool(Ambience.PRESSURE, false);

        // turn on specific sensors
        senseService.setPrefBool(Ambience.MIC, false);
        // NOTE: spectrum might be too heavy for the phone or consume too much energy
        senseService.setPrefBool(Ambience.AUDIO_SPECTRUM, false);
        
        senseService.setPrefBool(Motion.ACCELEROMETER, true);
        senseService.setPrefBool(Motion.GYROSCOPE, true);
        senseService.setPrefBool(Motion.MOTION_ENERGY, true);
        senseService.setPrefBool(Motion.ORIENTATION, true);
        senseService.setPrefBool(nl.sense_os.service.constants.SensePrefs.Main.Location.AUTO_GPS, true);
        
        // set how often to sample
        // 1 := rarely (~every 15 min)
        // 0 := normal (~every 5 min)
        // -1 := often (~every 10 sec)
        // -2 := real time (this setting affects power consumption considerably!)
        senseService.setPrefString(SensePrefs.Main.SAMPLE_RATE, "-2");

        // set how often to upload
        // 1 := eco mode (buffer data for 30 minutes before bulk uploading)
        // 0 := normal (buffer 5 min)
        // -1 := often (buffer 1 min)
        // -2 := real time (every new data point is uploaded immediately)
        senseService.setPrefString(SensePrefs.Main.SYNC_RATE, "0");

        // show message
        showToast(R.string.msg_prefs_set);
    }
    
    private void showToast(final int resId, final Object... formatArgs) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                CharSequence msg = getString(resId, formatArgs);
                Toast.makeText(BumpyMainFragmentActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
