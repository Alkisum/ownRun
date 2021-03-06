package com.alkisum.android.cloudrun.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alkisum.android.cloudrun.R;
import com.alkisum.android.cloudrun.dialogs.ActivateRoutesDialog;
import com.alkisum.android.cloudrun.dialogs.ErrorDialog;
import com.alkisum.android.cloudrun.events.CoordinateEvent;
import com.alkisum.android.cloudrun.events.DistanceEvent;
import com.alkisum.android.cloudrun.events.GpsStatusEvent;
import com.alkisum.android.cloudrun.events.PaceEvent;
import com.alkisum.android.cloudrun.events.SessionActionEvent;
import com.alkisum.android.cloudrun.events.SpeedEvent;
import com.alkisum.android.cloudrun.location.Coordinate;
import com.alkisum.android.cloudrun.location.LocationHelper;
import com.alkisum.android.cloudrun.location.LocationUtils;
import com.alkisum.android.cloudrun.tasks.SessionRecorder;
import com.alkisum.android.cloudrun.ui.GpsStatus;
import com.alkisum.android.cloudrun.ui.Tile;
import com.alkisum.android.cloudrun.utils.Format;
import com.alkisum.android.cloudrun.utils.Pref;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Main activity showing location values.
 *
 * @author Alkisum
 * @version 4.1
 * @since 1.0
 */
public class MonitorActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        Tile.TileListener, SessionRecorder.RecorderListener {

    /**
     * Request code for denied permissions.
     */
    private static final int REQUEST_CODE = 683;

    /**
     * Delay before the screen get locked.
     */
    private static final long LOCK_DELAY = 5000;

    /**
     * Delay between each stopwatch blink when the session is paused.
     */
    private static final long BLINK_DELAY = 500;

    /**
     * Action constant for session start.
     */
    private static final int ACTION_START = 1;

    /**
     * Action constant for session resume.
     */
    private static final int ACTION_RESUME = 2;

    /**
     * Action constant for session pause.
     */
    private static final int ACTION_PAUSE = 3;

    /**
     * Action constant for session stop.
     */
    private static final int ACTION_STOP = 4;

    /**
     * Action constant for session lock.
     */
    private static final int ACTION_LOCK = 5;

    /**
     * Action constant for session unlock.
     */
    private static final int ACTION_UNLOCK = 6;

    /**
     * SharedPreferences instance.
     */
    private SharedPreferences sharedPref;

    /**
     * EventBus instance.
     */
    private EventBus eventBus;

    /**
     * LocationHelper instance.
     */
    private LocationHelper locationHelper;

    /**
     * SessionRecorder instance to start and stop recording data.
     */
    private SessionRecorder recorder;

    /**
     * Flag set to true if the session is currently running, false otherwise.
     */
    private boolean sessionRunning;

    /**
     * Flag set to true if the session is currently paused, false otherwise.
     */
    private boolean sessionPaused;

    /**
     * Flag set to true if the session is locked, false otherwise.
     */
    private boolean locked;

    /**
     * Handler for the task to make the stopwatch blink when the session is
     * paused.
     */
    private final Handler stopwatchBlinkHandler = new Handler();

    /**
     * Handler for the lock timeout.
     */
    private final Handler lockTimeoutHandler = new Handler();

    /**
     * Flag set to true when the lock timeout task delay is on, false otherwise.
     */
    private boolean lockTimeoutOn;

    /**
     * List of layout containing GPS data.
     */
    private List<Tile> tiles;

    /**
     * Array containing the last values for each data type. The index is based
     * on the data type constants.
     */
    private String[] currentValues;

    /**
     * Flag set to true if the GPS accuracy must be shown, false otherwise.
     */
    private boolean showGpsAccuracy;

    /**
     * Last coordinate received, used to initialize the map {@link MapActivity}.
     */
    private Coordinate lastCoordinate;

    /**
     * Id for current GPS status icon.
     */
    private int gpsStatusIconId = R.drawable.ic_gps_not_fixed_white_24dp;

    /**
     * GpsStatus instance.
     */
    private GpsStatus gpsStatus;

    /**
     * TextView showing the GPS accuracy by displaying the distance in meter
     * between 2 coordinates.
     */
    @BindView(R.id.monitor_txt_gps_accuracy)
    TextView textGpsAccuracy;

    /**
     * TextView at the top of the layout.
     */
    @BindView(R.id.monitor_txt_top)
    TextView textTop;

    /**
     * Drawer layout.
     */
    @BindView(R.id.monitor_drawer_layout)
    DrawerLayout drawerLayout;

    /**
     * ActionBarDrawerToggle instance.
     */
    private ActionBarDrawerToggle drawerToggle;

    /**
     * Layout containing the start button.
     */
    @BindView(R.id.monitor_layout_start)
    RelativeLayout layoutStart;

    /**
     * Layout containing the lock button.
     */
    @BindView(R.id.monitor_layout_lock)
    RelativeLayout layoutLock;

    /**
     * Layout containing the pause button.
     */
    @BindView(R.id.monitor_layout_pause)
    RelativeLayout layoutPause;

    /**
     * Layout containing the stop button.
     */
    @BindView(R.id.monitor_layout_stop)
    RelativeLayout layoutStop;

    @Override
    protected final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String[] deniedPermissions = getDeniedPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }
        );
        if (deniedPermissions.length > 0
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(deniedPermissions, REQUEST_CODE);
        } else {
            init();
        }

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        sharedPref.registerOnSharedPreferenceChangeListener(this);

        eventBus = EventBus.getDefault();
        eventBus.register(this);

        gpsStatus = new GpsStatus(getApplicationContext());
        gpsStatus.start();

        initCurrentValues();

        setContentView(R.layout.activity_monitor);
        ButterKnife.bind(this);

        setKeepScreenOn();

        setGui();
    }

    @Override
    protected final void onStart() {
        super.onStart();
        if (locationHelper != null && locationHelper.isRunningInForeground()
                && sessionRunning) {
            locationHelper.stopForeground();
        }
    }

    @Override
    protected final void onStop() {
        super.onStop();
        if (locationHelper != null && sessionRunning) {
            locationHelper.startForeground();
        }
    }

    @Override
    protected final void onDestroy() {
        super.onDestroy();
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
        eventBus.unregister(this);
        gpsStatus.stop();
        locationHelper.onDestroy();
    }

    /**
     * Get denied permissions from given list.
     *
     * @param permissions List of permissions
     * @return Array of denied permissions
     */
    private String[] getDeniedPermissions(final String[] permissions) {
        List<String> deniedPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    == PackageManager.PERMISSION_DENIED) {
                deniedPermissions.add(permission);
            }
        }
        return deniedPermissions.toArray(new String[0]);
    }

    @Override
    public final void onRequestPermissionsResult(
            final int requestCode, @NonNull final String[] permissions,
            @NonNull final int[] grantResults) {
        if (requestCode != REQUEST_CODE) {
            return;
        }
        boolean permissionsGranted = true;
        StringBuilder errorMessage = new StringBuilder();
        for (int i = 0; i < permissions.length; i++) {
            switch (permissions[i]) {
                case Manifest.permission.ACCESS_FINE_LOCATION:
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        if (!permissionsGranted) {
                            errorMessage.append("\n\n");
                        }
                        permissionsGranted = false;
                        errorMessage.append(getString(
                                R.string.permission_location));
                    }
                    break;
                case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        if (!permissionsGranted) {
                            errorMessage.append("\n\n");
                        }
                        permissionsGranted = false;
                        errorMessage.append(getString(
                                R.string.permission_storage));
                    }
                    break;
                default:
                    break;
            }
        }
        if (permissionsGranted) {
            init();
        } else {
            ErrorDialog.show(this, getString(R.string.permission_title),
                    errorMessage.toString(), (dialog, which) -> finish());
        }
    }

    @Override
    public final boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_monitor, menu);
        return true;
    }

    @Override
    public final boolean onPrepareOptionsMenu(final Menu menu) {
        // GPS status icon
        MenuItem gps = menu.findItem(R.id.action_gps);
        gps.setIcon(gpsStatusIconId);
        // Map icon
        MenuItem map = menu.findItem(R.id.action_map);
        map.setVisible(lastCoordinate != null);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public final boolean onOptionsItemSelected(final MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_gps) {
            showGpsAccuracy = !showGpsAccuracy;
            textGpsAccuracy.setText("");
        } else if (id == R.id.action_map) {
            Intent intent = new Intent(this, MapActivity.class);
            if (recorder != null && recorder.getSession() != null
                    && recorder.getSession().getEnd() == null) {
                intent.putExtra(MapActivity.ARG_SESSION_ID,
                        recorder.getSession().getId());
            }
            intent.putExtra(MapActivity.ARG_COORDINATE, lastCoordinate);
            intent.putExtra(MapActivity.ARG_GPS_STATUS, gpsStatusIconId);
            startActivity(intent);
        } else if (id == R.id.action_routes) {
            ActivateRoutesDialog.show(this);
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Initialise activity.
     */
    private void init() {
        locationHelper = new LocationHelper(this);
    }

    /**
     * Set the GUI.
     */
    private void setGui() {
        Toolbar toolbar = findViewById(R.id.monitor_toolbar);
        setSupportActionBar(toolbar);

        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        NavigationView navigationView = findViewById(
                R.id.monitor_navigation_view);
        View header = getLayoutInflater().inflate(
                R.layout.nav_header_monitor, navigationView, false);
        navigationView.addHeaderView(header);
        navigationView.setNavigationItemSelectedListener(this);

        setTiles();
    }

    /**
     * Create and add tiles to the Tile List.
     */
    private void setTiles() {

        int leftData = sharedPref.getInt(Pref.TILE_LEFT, Tile.DISTANCE);
        int topRightData = sharedPref.getInt(Pref.TILE_RIGHT_TOP, Tile.SPEED);
        int bottomRightData = sharedPref.getInt(
                Pref.TILE_RIGHT_BOTTOM, Tile.PACE);

        tiles = new ArrayList<>();
        tiles.add(new Tile(this, this, Pref.TILE_LEFT, leftData,
                findViewById(R.id.monitor_layout_left),
                findViewById(R.id.monitor_txt_left_value),
                findViewById(R.id.monitor_txt_left_unit)));
        tiles.add(new Tile(this, this, Pref.TILE_RIGHT_TOP, topRightData,
                findViewById(R.id.monitor_layout_right_top),
                findViewById(R.id.monitor_txt_right_top_value),
                findViewById(R.id.monitor_txt_right_top_unit)));
        tiles.add(new Tile(this, this, Pref.TILE_RIGHT_BOTTOM, bottomRightData,
                findViewById(R.id.monitor_layout_right_bottom),
                findViewById(R.id.monitor_txt_right_bottom_value),
                findViewById(R.id.monitor_txt_right_bottom_unit)));
    }

    @Override
    public final void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            if (locked) {
                Snackbar.make(drawerLayout, R.string.monitor_session_locked,
                        Snackbar.LENGTH_LONG).show();
            } else {
                if (sessionRunning) {
                    stopSession();
                }
                super.onBackPressed();
            }
        }
    }

    @Override
    public final boolean onNavigationItemSelected(
            @NonNull final MenuItem item) {
        drawerLayout.closeDrawer(GravityCompat.START);
        // Start activity a bit later to avoid the lag when closing the drawer
        drawerLayout.postDelayed(() -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                Intent intent = new Intent(MonitorActivity.this,
                        HistoryActivity.class);
                if (sessionRunning) {
                    intent.putExtra(HistoryActivity.ARG_IGNORE_SESSION_ID,
                            recorder.getSession().getId());
                }
                startActivity(intent);
            } else if (id == R.id.nav_routes) {
                Intent intent = new Intent(MonitorActivity.this,
                        RouteListActivity.class);
                startActivity(intent);
            } else if (id == R.id.nav_settings) {
                Intent intent = new Intent(MonitorActivity.this,
                        SettingsActivity.class);
                startActivity(intent);
            }
        }, 300);

        return true;
    }

    @Override
    protected final void onActivityResult(final int requestCode,
                                          final int resultCode,
                                          final Intent data) {
        switch (requestCode) {
            case LocationHelper.REQUEST_LOCATION_AUTO:
                if (resultCode == RESULT_OK) {
                    if (locationHelper != null) {
                        locationHelper.requestLocationUpdates();
                    }
                }
                break;
            case LocationHelper.REQUEST_LOCATION_MANUAL:
                if (LocationUtils.isLocationEnabled(this)) {
                    if (locationHelper != null) {
                        locationHelper.requestLocationUpdates();
                    }
                }
            default:
                break;
        }
    }

    @Override
    public final void onDurationUpdated(final long duration) {
        textTop.setText(Format.formatDuration(duration));
    }

    @Override
    public final void onDistanceUpdated(final float distance) {
        updateTile(Tile.DISTANCE, Format.formatDistance(distance));
        long elapsedTime = recorder.getCurrentDuration();

        // Speed average
        updateTile(Tile.SPEED_AVG,
                Format.formatSpeedAvg(elapsedTime, distance));

        // Pace average
        updateTile(Tile.PACE_AVG,
                Format.formatPaceAvg(elapsedTime, distance));
    }

    /**
     * Triggered when new coordinates are received.
     *
     * @param event Coordinate event
     */
    @Subscribe
    public final void onCoordinateEvent(final CoordinateEvent event) {
        lastCoordinate = event.getValues();
    }

    /**
     * Triggered when a new distance is calculated.
     *
     * @param event Distance event
     */
    @Subscribe
    public final void onDistanceEvent(final DistanceEvent event) {
        if ((!sessionRunning || sessionPaused) && showGpsAccuracy) {
            textGpsAccuracy.setText(Format.formatGpsAccuracy(
                    event.getValue()));
        }
    }

    /**
     * Triggered when a new speed is calculated.
     *
     * @param event Speed event
     */
    @Subscribe
    public final void onSpeedEvent(final SpeedEvent event) {
        if (sessionRunning && !sessionPaused) {
            updateTile(Tile.SPEED, Format.formatSpeed(event.getValue()));
        }
    }

    /**
     * Triggered when a new pace is calculated.
     *
     * @param event Pace event
     */
    @Subscribe
    public final void onPaceEvent(final PaceEvent event) {
        if (sessionRunning && !sessionPaused) {
            updateTile(Tile.PACE, Format.formatPace(event.getValue()));
        }
    }

    /**
     * Triggered on GPS status event.
     *
     * @param event GPS status event
     */
    @Subscribe
    public final void onGpsStatusEvent(final GpsStatusEvent event) {
        gpsStatusIconId = event.getIcon();
        invalidateOptionsMenu();
    }

    /**
     * Update the tiles containing the data type with the new value.
     *
     * @param data  Data type
     * @param value New value to set
     */
    private void updateTile(final int data, final String value) {
        currentValues[data] = value;
        for (Tile tile : tiles) {
            if (tile.getData() == data) {
                tile.setValue(value);
            }
        }
    }

    @Override
    public final void onTileLongClicked() {
        if (sessionRunning && !locked) {
            if (lockTimeoutOn) {
                lockTimeoutHandler.removeCallbacks(lockTimeoutTask);
                lockTimeoutOn = false;
            }
            lockTimeoutHandler.postDelayed(lockTimeoutTask, LOCK_DELAY);
            lockTimeoutOn = true;
        }
    }

    @Override
    public final void onTileValueRequested(final Tile tile, final int data) {
        tile.setValue(currentValues[data]);
    }

    /**
     * Triggered when the start button is clicked.
     */
    @OnClick(R.id.monitor_button_start)
    public final void onStartButtonClicked() {
        if (!sessionRunning) {
            // create recorder before posting the event
            // recorder also subscribes to SessionActionEvent
            recorder = new SessionRecorder(this);
            eventBus.post(new SessionActionEvent(SessionActionEvent.START));
        } else {
            eventBus.post(new SessionActionEvent(SessionActionEvent.RESUME));
        }
    }

    /**
     * Triggered when the pause button is clicked.
     */
    @OnClick(R.id.monitor_button_pause)
    public final void onPauseButtonClicked() {
        if (sessionRunning) {
            eventBus.post(new SessionActionEvent(SessionActionEvent.PAUSE));
        }
    }

    /**
     * Triggered when the stop button is clicked.
     */
    @OnClick(R.id.monitor_button_stop)
    public final void onStopButtonClicked() {
        if (sessionRunning) {
            eventBus.post(new SessionActionEvent(SessionActionEvent.STOP));
        }
    }

    /**
     * Triggered when the lock button is clicked.
     */
    @OnClick(R.id.monitor_button_lock)
    public final void onLockButtonClicked() {
        setLocked(false);
        lockTimeoutHandler.postDelayed(lockTimeoutTask, LOCK_DELAY);
        lockTimeoutOn = true;
    }

    /**
     * Task to make the stopwatch blink when the session is paused.
     */
    private final Runnable stopwatchBlinkTask = new Runnable() {
        @Override
        public void run() {
            stopwatchBlinkHandler.postDelayed(this, BLINK_DELAY);
            int currentVisibility = textTop.getVisibility();
            if (currentVisibility == View.VISIBLE) {
                textTop.setVisibility(View.GONE);
            } else {
                textTop.setVisibility(View.VISIBLE);
            }
        }
    };

    /**
     * Task to lock the screen again after timeout.
     */
    private final Runnable lockTimeoutTask = new Runnable() {
        @Override
        public void run() {
            lockTimeoutOn = false;
            setLocked(true);
        }
    };

    /**
     * Set keep screen on flag according to the preferences.
     */
    private void setKeepScreenOn() {
        boolean keepScreenOn = sharedPref.getBoolean(
                Pref.KEEP_SCREEN_ON, false);
        if (keepScreenOn) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
     * Called when a session action has been performed.
     *
     * @param event Session action event
     */
    @Subscribe
    public final void onSessionActionEvent(final SessionActionEvent event) {
        switch (event.getAction()) {
            case SessionActionEvent.START:
                startSession();
                break;
            case SessionActionEvent.RESUME:
                resumeSession();
                break;
            case SessionActionEvent.PAUSE:
                pauseSession();
                break;
            case SessionActionEvent.STOP:
                stopSession();
                break;
            default:
                break;
        }
    }

    /**
     * Start a session.
     */
    private void startSession() {
        setLocked(true);
        updateActionButton(ACTION_START);
        textGpsAccuracy.setText("");
        sessionRunning = true;
    }

    /**
     * Resume the session, called only when the session was paused.
     */
    private void resumeSession() {
        setLocked(true);
        stopwatchBlinkHandler.removeCallbacks(stopwatchBlinkTask);
        textTop.setVisibility(View.VISIBLE);
        updateActionButton(ACTION_RESUME);
        textGpsAccuracy.setText("");
        sessionPaused = false;
    }

    /**
     * Set the session on pause.
     */
    private void pauseSession() {
        if (lockTimeoutOn) {
            lockTimeoutHandler.removeCallbacks(lockTimeoutTask);
            lockTimeoutOn = false;
        }
        stopwatchBlinkHandler.post(stopwatchBlinkTask);
        updateActionButton(ACTION_PAUSE);
        sessionPaused = true;
    }

    /**
     * Stop the current session.
     */
    private void stopSession() {
        setLocked(false);

        if (lockTimeoutOn) {
            lockTimeoutHandler.removeCallbacks(lockTimeoutTask);
            lockTimeoutOn = false;
        }
        if (sessionPaused) {
            stopwatchBlinkHandler.removeCallbacks(stopwatchBlinkTask);
            textTop.setVisibility(View.VISIBLE);
        }

        updateActionButton(ACTION_STOP);

        Intent intent = new Intent(this, HistoryActivity.class);
        intent.putExtra(HistoryActivity.ARG_HIGHLIGHTED_SESSION_ID,
                recorder.getSession().getId());
        startActivity(intent);

        resetViews();
        initCurrentValues();

        sessionRunning = false;
    }

    /**
     * Set the lock status and set the view according to the status.
     *
     * @param locked True if the session is to be locked, false otherwise
     */
    private void setLocked(final boolean locked) {
        this.locked = locked;

        for (Tile tile : tiles) {
            tile.setEnabled(!locked);
        }

        if (this.locked) {
            updateActionButton(ACTION_LOCK);
            drawerLayout.setDrawerLockMode(
                    DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else {
            updateActionButton(ACTION_UNLOCK);
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
        drawerToggle.setDrawerIndicatorEnabled(!locked);
        drawerToggle.syncState();

        invalidateOptionsMenu();
    }

    /**
     * Reset the views that are updated during a session.
     */
    private void resetViews() {
        textTop.setText(R.string.default_stopwatch);
        for (Tile tile : tiles) {
            tile.resetValues();
        }
    }

    /**
     * Initialise the current values strings with their default values.
     */
    private void initCurrentValues() {
        currentValues = new String[]{
                getString(R.string.default_distance),
                getString(R.string.default_speed),
                getString(R.string.default_pace),
                getString(R.string.default_speed),
                getString(R.string.default_pace),
        };
    }

    /**
     * Update the action buttons according to the given action.
     *
     * @param action Action triggered.
     */
    private void updateActionButton(final int action) {
        switch (action) {
            case ACTION_START:
                layoutStart.setVisibility(View.GONE);
                layoutLock.setVisibility(View.VISIBLE);
                layoutPause.setVisibility(View.GONE);
                layoutStop.setVisibility(View.GONE);
                break;
            case ACTION_RESUME:
                layoutStart.setVisibility(View.GONE);
                layoutLock.setVisibility(View.VISIBLE);
                layoutPause.setVisibility(View.GONE);
                layoutStop.setVisibility(View.GONE);
                break;
            case ACTION_PAUSE:
                layoutStart.setVisibility(View.VISIBLE);
                layoutLock.setVisibility(View.GONE);
                layoutPause.setVisibility(View.GONE);
                layoutStop.setVisibility(View.VISIBLE);
                break;
            case ACTION_STOP:
                layoutStart.setVisibility(View.VISIBLE);
                layoutLock.setVisibility(View.GONE);
                layoutPause.setVisibility(View.GONE);
                layoutStop.setVisibility(View.GONE);
                break;
            case ACTION_LOCK:
                layoutStart.setVisibility(View.GONE);
                layoutLock.setVisibility(View.VISIBLE);
                layoutPause.setVisibility(View.GONE);
                layoutStop.setVisibility(View.GONE);
                break;
            case ACTION_UNLOCK:
                layoutStart.setVisibility(View.GONE);
                layoutLock.setVisibility(View.GONE);
                layoutPause.setVisibility(View.VISIBLE);
                layoutStop.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    @Override
    public final void onSharedPreferenceChanged(
            final SharedPreferences sharedPreferences, final String key) {
        if (key.equals(Pref.KEEP_SCREEN_ON)) {
            setKeepScreenOn();
        }
    }
}
