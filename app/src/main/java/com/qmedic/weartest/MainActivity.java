package com.qmedic.weartest;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

import com.estimote.sdk.Region;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

public class MainActivity extends Activity implements
        DataApi.DataListener, ConnectionCallbacks, OnConnectionFailedListener {

    private static final String TAG = "WEAR TEST";

    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";
    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);

    private TextView mTextView;
    private GoogleApiClient mGoogleApiClient;
    MyReceiver myReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {

        super.onStart();

        //Register BroadcastReceiver
        //to receive event from our service
        myReceiver = new MyReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WearListCallListenerService.SERVICE_ACTION);
        registerReceiver(myReceiver, intentFilter);

        //Start our own service
        Intent intent = new Intent(MainActivity.this, com.qmedic.weartest.WearListCallListenerService.class);
        startService(intent);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(myReceiver);
        super.onStop();
    }


    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    public static String SERVICE_CALLED_WEAR = "WearService";
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            final Uri uri = event.getDataItem().getUri();
            if ("/txt".equals(uri.getPath())) {
                final DataMap map = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                // read your values from map:
                if (map.containsKey(SERVICE_CALLED_WEAR)) {
                    Asset asset = map.getAsset(SERVICE_CALLED_WEAR);
                    String msg = new String(asset.getData());
                    Log.d("QMEDIC", msg);
                }
            }
        }
    }
    /*
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        mTextView.setText("Data has changed at " + new Date().toString());

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().equals("/txt")) {
                // Get the Asset object
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                Asset asset = dataMapItem.getDataMap().getAsset("com.example.company.key.TXT");

                ConnectionResult result =
                        mGoogleApiClient.blockingConnect(10000, TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {
                    return;
                }

                // Convert asset into a file descriptor and block until it's ready
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        mGoogleApiClient, asset).await().getInputStream();

                if (assetInputStream == null) {
                    return;
                }
            }
        }

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED)
                    //&& event.getDataItem().getUri().getPath().equals("/gz"))
            {
                // Get the Asset object
                DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                Asset asset = dataMapItem.getDataMap().getAsset("qmedic");

                ConnectionResult result =  mGoogleApiClient.blockingConnect(10000, TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {return;}


                // Convert asset into a file descriptor and block until it's ready
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        mGoogleApiClient, asset).await().getInputStream();
                mGoogleApiClient.disconnect();
                if (assetInputStream == null) { return; }

                // Get folder for output
                File sdcard = Environment.getExternalStorageDirectory();
                File dir = new File(sdcard.getAbsolutePath() + "/spades/");
                if (!dir.exists()) { dir.mkdirs(); } // Create folder if needed

                // Read data from the Asset and write it to a file on external storage
                final File file = new File(dir, "stuff.gz");
                try {
                    FileOutputStream fOut = new FileOutputStream(file);
                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = assetInputStream.read(data, 0, data.length)) != -1) {
                        fOut.write(data, 0, nRead);
                    }

                    fOut.flush();
                    fOut.close();
                }
                catch (Exception e)
                {
                    Log.e(TAG + "-APP", "Failed to do stuff: " + e.toString());
                }

                // Rescan folder to make it appear
                try {
                    String[] paths = new String[1];
                    paths[0] = file.getAbsolutePath();
                    MediaScannerConnection.scanFile(this, paths, null, null);
                } catch (Exception e) {
                    Log.e(TAG + "-APP", "Failed to do stuff: " + e.toString());
                }
            }
        }

    }*/

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            // TODO Auto-generated method stub

            String txt = arg1.getStringExtra("DATAPASSED");
            if (txt == null) return;

            mTextView.setText(txt);
        }
    }
}
