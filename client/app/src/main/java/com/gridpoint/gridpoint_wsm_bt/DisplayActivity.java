package com.gridpoint.gridpoint_wsm_bt;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.digi.xbee.api.android.XBeeBLEDevice;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.listeners.relay.IMicroPythonDataReceiveListener;

import java.util.Date;
import java.text.SimpleDateFormat;

public class DisplayActivity extends AppCompatActivity implements IMicroPythonDataReceiveListener {

    // Constants.
    //private static final String SEPARATOR = "@@@";

    private static final String MSG_ACK = "OK";

    private static final int ACK_TIMEOUT = 5000;

    // Variables.
    private XBeeBLEDevice device;

    private TableLayout tableLayout;
    private TextView messageText;
    private TextView timeStamp;
    private ToggleButton startButton;

    private boolean ackReceived = false;

    private final Object lock = new Object();

    private MyBroadcastReceiver myBroadcastReceiver = new MyBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);

        // Get the selected XBee device.
        device = MainActivity.getXBeeDevice();

        // Initialize layout components.
        tableLayout = findViewById(R.id.tableLayout);
        messageText = findViewById(R.id.messageText);
        timeStamp = findViewById(R.id.timeStamp);

        startButton = findViewById(R.id.startButton);
        startButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, final boolean checked) {
                // Send a message to the MicroPython interface with the action and refresh time.
                final String data = checked ? "ON" : "OFF";
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            boolean ackReceived = sendDataAndWaitResponse(data.getBytes());
                            if (ackReceived) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        tableLayout.setAlpha(checked ? 1 : (float) 0.2);
                                    }
                                });
                            }
                        } catch (XBeeException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register a MicroPython data listener.
        device.addMicroPythonDataListener(this);

        // Register a receiver to be notified when the Bluetooth connection is lost.
        registerReceiver(myBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister a MicroPython data listener.
        device.removeMicroPythonDataListener(this);

        // Unregister the the Bluetooth connection lost receiver.
        unregisterReceiver(myBroadcastReceiver);
    }

    @Override
    public void onBackPressed() {
        // Ask the user if wants to close the connection.
        new AlertDialog.Builder(this).setTitle(getResources().getString(R.string.disconnect_device_title))
                .setMessage(getResources().getString(R.string.disconnect_device_description))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        closeAndBack();
                    }
                }).setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void dataReceived(byte[] data) {
        // If the response is "OK", notify the lock to continue the process.
        if (new String(data).equals(MSG_ACK)) {
            ackReceived = true;
            synchronized (lock) {
                lock.notify();
            }
        } else {
            // If the process is stopped, do nothing.
            if (!startButton.isChecked())
                return;

            // Get the temperature and humidity from the received data.
            //String[] dataString = new String(data).split(SEPARATOR);
            //if (dataString.length != 2)
                //return;

            final String message = new String(data);
            final ColorStateList oldColors = messageText.getTextColors();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Set timestamp
                    String timeStampString = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                    timeStamp.setText(timeStampString);
                    // Update the message.
                    messageText.setText(message);
                    // Make the texts blink for a short time.
                    messageText.setTextColor(getResources().getColor(R.color.colorAccent));
                }
            });

            try {
                Thread.sleep(200);
            } catch (InterruptedException ignore) {
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    messageText.setTextColor(oldColors);
                }
            });
        }
    }

    /**
     * Sends the given data and waits for an ACK response during the configured
     * timeout.
     *
     * @param data Data to send.
     *
     * @return {@code true} if the ACK was received, {@code false} otherwise.
     *
     * @throws XBeeException if there is any problem sending the data.
     */
    private boolean sendDataAndWaitResponse(byte[] data) throws XBeeException {
        ackReceived = false;
        // Send the data.
        device.sendMicroPythonData(data);
        // Wait until the ACK is received to send the next block.
        try {
            synchronized (lock) {
                lock.wait(ACK_TIMEOUT);
            }
        } catch (InterruptedException ignore) {}
        // If the ACK was not received, show an error.
        if (!ackReceived) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(DisplayActivity.this).setTitle(getResources().getString(R.string.error_waiting_response_title))
                            .setMessage(getResources().getString(R.string.error_waiting_response_description))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            });
            return false;
        }
        return true;
    }

    /**
     * Closes the connection with the device and goes to the previous activity.
     */
    private void closeAndBack() {
        device.close();
        super.onBackPressed();
    }

    /**
     * Class to handle the Bluetooth connection lost.
     */
    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                // Show a dialog to the user.
                new AlertDialog.Builder(DisplayActivity.this).setTitle(getResources().getString(R.string.connection_lost_title))
                        .setMessage(getResources().getString(R.string.connection_lost_description))
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                closeAndBack();
                            }
                        }).show();
            }
        }
    }
}