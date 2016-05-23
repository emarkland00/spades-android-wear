package com.qmedic.weartest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Region;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

public class MainActivity extends Activity implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "QMEDIC_WEAR";
    private static final String SERVICE_CALLED_WEAR = "QMEDIC_DATA_MESSAGE";
    private static final String HEADER_LINE = "HEADER_TIMESTAMP,X,Y,Z\n";
    private static final int BUFFER_SIZE = 4096;
    private static final int EXPIRATION_IN_MS = 30000;

    private GoogleApiClient mGoogleApiClient;
    private SensorManager mSensorMgr;
    private Sensor mAccel;
    private TextView mTextView;
    private StringBuilder buffer = new StringBuilder();
    private String lastFileName = null;
    private String fileToTransfer = null;
    private OutputStreamWriter currentWriter = null;
    private Date tempFileExpirationDateTime = null;

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

        mSensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(mAccel != null) {
            mSensorMgr.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                @Override
                public void onConnected(Bundle connectionHint) {
                    Log.d(TAG, "onConnected: " + connectionHint);
                }

                @Override
                public void onConnectionSuspended(int cause) {
                    Log.d(TAG, "onConnectionSuspended: " + cause);
                }
            })
            .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(ConnectionResult result) {
                Log.d(TAG, "onConnectionFailed: " + result);
                }
            })
            .addApi(Wearable.API)
            .build();

        // keep screen on (debug only)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(mAccel != null) {
            mSensorMgr.unregisterListener(this, mAccel);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mGoogleApiClient == null) return;
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mGoogleApiClient == null) return;
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        String text = null;
        Date date = null;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                date = getDateFromEvent(event);
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                DecimalFormat df = new DecimalFormat("###.##");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
                text = String.format(
                    "%s, %s, %s, %s\n",
                    sdf.format(date),
                    df.format(x),
                    df.format(y),
                    df.format(z));

                if (mTextView != null) {
                    mTextView.setText(text);
                }

                //broadcastMessage(text.getBytes(), "txt");
                writeMessage(date, text);

            case Sensor.TYPE_HEART_RATE:
                // TODO: do some heart rate stuff

            default:
                // ignore
        }

        if (date != null && text != null) {
            writeMessage(date, text);
        }
    }

    /**
     * Gets the datetime from the event timestamp. (Credit: http://stackoverflow.com/a/9333605)
     * @param sensorEvent - The sensor event containing the timestamp
     * @return {Date} - The datetime of the event
     */
    private Date getDateFromEvent(SensorEvent sensorEvent) {
        long timeInMillis = (new Date()).getTime()
                + (sensorEvent.timestamp - System.nanoTime()) / 1000000L;
        return new Date(timeInMillis);
    }

    private void writeMessage(final Date date, final String msg) {
        boolean sendFile = shouldSendFile(date);

        buffer.append(msg);
        if (buffer.length() >= BUFFER_SIZE || sendFile) {
            try {
                OutputStreamWriter writer = getFileToWrite(date);
                if (writer != null) {
                    writer.write(msg);
                }
            } catch (IOException ex) {
                Log.w(TAG, ex.getMessage());
            }
        }

        // check if we should send file contents to device
        if (sendFile) {
            setExpiration(date);
            closeCurrentWriter();
            queueFileTransfer();
        }
    }

    private String getTempFileName(final Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss");
        return "temp-" + sdf.format(date) + ".csv";
    }

    private void setExpiration(final Date date) {
        tempFileExpirationDateTime = date;
        tempFileExpirationDateTime.setTime(tempFileExpirationDateTime.getTime() + EXPIRATION_IN_MS);
    }

    private boolean shouldSendFile(final Date date) {
        if (tempFileExpirationDateTime == null) setExpiration(date);
        return date.compareTo(tempFileExpirationDateTime) > 0;
    }

    private OutputStreamWriter getFileToWrite(final Date date) {
        String tempFileName = getTempFileName(date);
        if (lastFileName != tempFileName || currentWriter == null) {
            fileToTransfer = lastFileName;
            lastFileName = tempFileName;

            closeCurrentWriter();
        }

        try {
            if (currentWriter == null) {
                FileOutputStream outStream = openFileOutput(lastFileName, MODE_APPEND);

                OutputStreamWriter writer = new OutputStreamWriter(outStream);
                writer.append(HEADER_LINE);
                currentWriter = writer;
            }
        } catch (IOException ex) {
            Log.e(TAG, ex.getMessage());
        }

        return currentWriter;
    }

    private void closeCurrentWriter() {
        if (currentWriter == null) return;

        try {
            currentWriter.close();
        } catch (IOException ex) {
            Log.i(TAG, "Closing current writer. Got: " + ex.getMessage());
        } finally {
            currentWriter = null;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onConnected(Bundle bundle) {
        //TODO: maybe some management stats here?
    }

    @Override
    public void onConnectionSuspended(int i) {
        //TODO: maybe some management stats here?
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        //TODO: maybe some management stats here?
    }

    /**
     * Broadcasts the message (in bytes) to those connect to the local google client
     *
     * @param messageInBytes - The message to be broadcasted in bytes
     */
    private void broadcastMessage(byte[] messageInBytes, String extension) {
        Asset asset = Asset.createFromBytes(messageInBytes);
        if (!extension.startsWith("/")) {
            extension = "/" + extension;
        }

        PutDataMapRequest dataMap = PutDataMapRequest.create(extension);
        dataMap.getDataMap().putAsset(SERVICE_CALLED_WEAR, asset);
        PutDataRequest request = dataMap.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request);

        // add method to delete on successful transfer
    }

    private void queueFileTransfer() {
        if (fileToTransfer == null) return;

        String gzipFileName = fileToTransfer + ".gz";
        byte[] buffer = new byte[BUFFER_SIZE];
        int len;

        // Add file compression logic
        try {
            FileInputStream inStream = openFileInput(fileToTransfer);
            GZIPOutputStream outputStream = new GZIPOutputStream(openFileOutput(gzipFileName, MODE_APPEND));
            while ((len = inStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }

            inStream.close();
            outputStream.finish();
            outputStream.close();
        } catch (IOException ex) {
            Log.w(TAG, "Failed to compress file " + fileToTransfer + ": " + ex.getMessage());
            return;
        }

        // Add file transfer logic
        ByteArrayOutputStream out = null;
        try {
            FileInputStream in = openFileInput(gzipFileName);
            out = new ByteArrayOutputStream();
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }

            in.close();
        } catch (Exception ex) {
            Log.w(TAG, "Failed to get file output stream for " + fileToTransfer + ": " + ex.getMessage());
            return;
        }

        if (out == null) return;
        try {
            out.close();

            broadcastMessage(out.toByteArray(), "gz");
        } catch (Exception ex) {
            Log.w(TAG, "Failed to transfer file " + fileToTransfer + ": " + ex.getMessage());
            return;
        }

        fileToTransfer = null;
    }
}
