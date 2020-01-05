package com.gruppo4.RingApplication;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationManagerCompat;

import com.eis.smslibrary.SMSManager;
import com.eis.smslibrary.SMSPeer;
import com.eis.smslibrary.exceptions.InvalidTelephoneNumberException;
import com.eis.smslibrary.listeners.SMSSentListener;
import com.gruppo4.RingApplication.structure.*;
import com.gruppo4.RingApplication.structure.dialog.*;
import com.gruppo4.RingApplication.structure.exceptions.IllegalCommandException;

/**
 * @author Gruppo4
 */
public class MainActivity extends AppCompatActivity implements PasswordDialogListener {

    private static final int CHANGE_PASS_COMMAND = 0;
    private static final int SET_PASS_COMMAND = 1;
    private EditText phoneNumberField, passwordField;
    private Button ringButton;
    private PasswordManager passwordManager;
    private static final String IDENTIFIER = RingCommandHandler.SPLIT_CHARACTER;
    private static final int WAIT_TIME_PERMISSION = 1500;
    private static final int WAIT_TIME_RING_BTN_ENABLED = 5 * 1000;
    private static final String DIALOG_TAG = "Device Password";
    public static final String CHANNEL_NAME = "TestChannelName";
    public static final String CHANNEL_ID = "123";
    public static final String BAR_TITLE = "ringUp";
    private static final String NOTIFICATION_CHANNEL_DESCRIPTION = "Stop Ringtone Notification";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Setting up the action bar
        Toolbar toolbar = findViewById(R.id.actionBar);
        toolbar.setTitle(BAR_TITLE);
        toolbar.setTitleTextColor(Color.WHITE);
        setSupportActionBar(toolbar);

        //Checking the if permissions are granted
        requestPermissions();

        passwordManager = new PasswordManager(getApplicationContext());
        //If the device password is not already set, a dialog will be open
        if (!passwordManager.isPassSaved())
            openDialog(SET_PASS_COMMAND);

        createNotificationChannel();

        //Only if the activity is started by a service
        startFromService();

        //Setting up the custom listener in order to receive messages
        SMSManager.getInstance().setReceivedListener(ReceivedMessageListener.class, getApplicationContext());
        phoneNumberField = findViewById(R.id.phone_number_field);
        passwordField = findViewById(R.id.password_field);
        ringButton = findViewById(R.id.ring_button);
        ringButton.setOnClickListener(v -> sendRingCommand());
    }


    /**
     * Method used to show up the {@link menu/app_menu.xml}
     *
     * @author Alberto Ursino
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Called when the user selects an item from the {@link menu/app_menu.xml}
     *
     * @author Alberto Ursino
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.change_password_menu_item:
                openDialog(CHANGE_PASS_COMMAND);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Creates the dialog used to insert a non empty password or exit/abort
     *
     * @param command Specified type of dialog that should be opened, represented by an int value
     * @throws IllegalCommandException usually thrown when the dialog command passed is not valid
     * @author Alberto Ursino
     */
    void openDialog(int command) throws IllegalCommandException {
        PasswordDialog passwordDialog;
        switch (command) {
            case SET_PASS_COMMAND:
                passwordDialog = new PasswordDialog(SET_PASS_COMMAND);
                passwordDialog.show(getSupportFragmentManager(), DIALOG_TAG);
                break;
            case CHANGE_PASS_COMMAND:
                passwordDialog = new PasswordDialog(CHANGE_PASS_COMMAND);
                passwordDialog.show(getSupportFragmentManager(), DIALOG_TAG);
                break;
            default:
                throw new IllegalCommandException();
        }
    }

    /**
     * Creates the NotificationChannel, but only on API 26+ because
     * the NotificationChannel class is new and not in the support library
     * <p>
     * Register the channel with the system; you can't change the importance
     * or other notification behaviors after this
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //IMPORTANCE_HIGH makes pop-up the notification
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(NOTIFICATION_CHANNEL_DESCRIPTION);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            } else {
                Log.d("Notification Manager", "getSystemService(NotificationManager.class) returns a null object");
            }
        }
    }

    /**
     * Updates intent obtained from a service's call
     *
     * @param intent to handle
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        startFromService();
    }

    /**
     * Manages action from intent
     */
    private void startFromService() {
        Log.d("MainActivity", "startFromService called");
        Intent intent = getIntent();
        if (intent != null) {
            switch (intent.getAction()) {
                case AppManager.ALERT_ACTION: {
                    createStopRingDialog();
                    Log.d("MainActivity", "Creating StopRingDialog...");
                    break;
                }
                default:
                    break;
            }
        } else {
            Log.d("startFromService intent", "getIntent returns a null intent");
        }
    }

    /**
     * Creates and shows AlertDialog with one option:
     * [stop] --> stop the ringtone and cancel the notification
     */
    private void createStopRingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(getString(R.string.text_stop_ring_dialog));
        builder.setCancelable(true);
        Log.d("MainActivity", "StopRingDialog created");

        builder.setPositiveButton(
                getString(R.string.text_notification_button), (dialogInterface, i) -> {
                    AppManager.getInstance().stopRingtone();
                    AppManager.notificationFlag = 0;
                    Log.d("MainActivity", "Stopping ringtone");
                    //cancel the right notification by id
                    int id = getIntent().getIntExtra(AppManager.NOTIFICATION_ID, -1);
                    NotificationManagerCompat.from(getApplicationContext()).cancel(id);
                    Log.d("MainActivity", "Notification " + id + " cancelled");
                    dialogInterface.dismiss();
                }
        );

        AlertDialog alert = builder.create();
        alert.show();
        Log.d("MainActivity", "Showing StopRingDialog...");
    }

    /**
     * Method used to send the ring command through the {@link AppManager#sendCommand(Context, RingCommand, SMSSentListener)} method
     *
     * @author Alberto Ursino
     * @author Luca Crema
     */
    public void sendRingCommand() {
        String phoneNumber = phoneNumberField.getText().toString();
        String password = passwordField.getText().toString();

        if (password.isEmpty() && phoneNumber.isEmpty()) {
            Toast.makeText(getApplicationContext(), getString(R.string.toast_pass_phone_number_absent), Toast.LENGTH_SHORT).show();
        } else if (phoneNumber.isEmpty()) {
            Toast.makeText(getApplicationContext(), getString(R.string.toast_phone_number_absent), Toast.LENGTH_SHORT).show();
        } else if (password.isEmpty()) {
            Toast.makeText(getApplicationContext(), getString(R.string.toast_password_absent), Toast.LENGTH_SHORT).show();
        } else {
            try {
                //Creation of the ring command
                final RingCommand ringCommand = new RingCommand(new SMSPeer(phoneNumber), IDENTIFIER + password);

                AppManager.getInstance().sendCommand(getApplicationContext(), ringCommand, (message, sentState) -> {
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_message_sent_listener) + " " + phoneNumber, Toast.LENGTH_SHORT).show();
                    ringButton.setEnabled(true);
                });
                ringButton.setEnabled(false);
            } catch (InvalidTelephoneNumberException e) {
                Toast.makeText(getApplicationContext(), getString(R.string.toast_invalid_phone_number), Toast.LENGTH_SHORT).show();
            }

            //Sets the button enabled after a while
            final Handler handler = new Handler();
            handler.postDelayed(() -> {
                if (!ringButton.isEnabled())
                    ringButton.setEnabled(true);
            }, WAIT_TIME_RING_BTN_ENABLED);
        }
    }

    /**
     * @return true if the app has both RECEIVE_SMS and SEND_SMS permissions, false otherwise
     * @author Alberto Ursino
     */
    public boolean checkPermissions() {
        Context context = getApplicationContext();
        return (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) &&
                (context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Checks if permissions are granted, if not then requests them to the user
     *
     * @author Alberto Ursino
     */
    public void requestPermissions() {
        if (!checkPermissions())
            requestPermissions(new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS}, 0);
    }

    /**
     * Callback for the permissions request
     *
     * @author Alberto Ursino
     * @author Luca Crema
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!(grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(getApplicationContext(), getString(R.string.toast_app_needs_permissions), Toast.LENGTH_SHORT).show();
            //Let's wait the toast ends
            Handler handler = new Handler();
            handler.postDelayed(() -> requestPermissions(), WAIT_TIME_PERMISSION);
        }
    }

    /**
     * Overridden method used to capture the set password in the dialog
     */
    @Override
    public void onPasswordSet(String password, Context context) {
        passwordManager.setPassword(password);
    }

}
