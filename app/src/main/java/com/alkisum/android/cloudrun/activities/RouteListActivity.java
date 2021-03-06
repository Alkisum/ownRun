package com.alkisum.android.cloudrun.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.alkisum.android.cloudlib.events.DownloadEvent;
import com.alkisum.android.cloudlib.events.JsonFileReaderEvent;
import com.alkisum.android.cloudlib.events.JsonFileWriterEvent;
import com.alkisum.android.cloudlib.events.UploadEvent;
import com.alkisum.android.cloudlib.net.ConnectDialog;
import com.alkisum.android.cloudlib.net.ConnectInfo;
import com.alkisum.android.cloudrun.R;
import com.alkisum.android.cloudrun.adapters.RoutesListAdapter;
import com.alkisum.android.cloudrun.dialogs.AddRouteDialog;
import com.alkisum.android.cloudrun.dialogs.ErrorDialog;
import com.alkisum.android.cloudrun.events.DeletedEvent;
import com.alkisum.android.cloudrun.events.InsertedEvent;
import com.alkisum.android.cloudrun.events.RefreshEvent;
import com.alkisum.android.cloudrun.events.RestoredEvent;
import com.alkisum.android.cloudrun.interfaces.Deletable;
import com.alkisum.android.cloudrun.interfaces.Restorable;
import com.alkisum.android.cloudrun.model.Route;
import com.alkisum.android.cloudrun.net.Downloader;
import com.alkisum.android.cloudrun.net.Uploader;
import com.alkisum.android.cloudrun.tasks.Deleter;
import com.alkisum.android.cloudrun.tasks.Restorer;
import com.alkisum.android.cloudrun.utils.Deletables;
import com.alkisum.android.cloudrun.utils.Routes;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Activity listing available routes.
 *
 * @author Alkisum
 * @version 4.1
 * @since 4.0
 */
public class RouteListActivity extends AppCompatActivity implements
        ConnectDialog.ConnectDialogListener {
    /**
     * Subscriber id to use when receiving event.
     */
    private static final int SUBSCRIBER_ID = 716;

    /**
     * Operation id for download.
     */
    private static final int DOWNLOAD_OPERATION = 1;

    /**
     * Operation id for upload.
     */
    private static final int UPLOAD_OPERATION = 2;

    /**
     * Request code for RouteActivity result.
     */
    private static final int ROUTE_REQUEST_CODE = 922;

    /**
     * Result returned by RouteActivity when the route was deleted from the
     * RouteActivity.
     */
    public static final int ROUTE_DELETED = 958;

    /**
     * Toolbar.
     */
    @BindView(R.id.routes_toolbar)
    Toolbar toolbar;

    /**
     * SwipeRefreshLayout for list view.
     */
    @BindView(R.id.routes_swipe_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;

    /**
     * ListView containing the routes.
     */
    @BindView(R.id.routes_list)
    ListView listView;

    /**
     * TextView informing the user that no routes are available.
     */
    @BindView(R.id.routes_no_route)
    TextView noRouteTextView;

    /**
     * Progress bar to show the progress of operations.
     */
    @BindView(R.id.routes_progressbar)
    ProgressBar progressBar;

    /**
     * Floating action button to add routes.
     */
    @BindView(R.id.routes_fab_add)
    FloatingActionButton fab;

    /**
     * List adapter for the list of routes.
     */
    private RoutesListAdapter listAdapter;

    @Override
    protected final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_list);
        ButterKnife.bind(this);

        setGui();
    }

    @Override
    public final void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        refreshList();
    }

    @Override
    public final void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    /**
     * Set the GUI.
     */
    private void setGui() {
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        toolbar.setNavigationOnClickListener(v -> {
            if (isEditMode()) {
                disableEditMode();
            } else {
                finish();
            }
        });

        listView.setOnItemClickListener((adapterView, view, position, id) -> {
            if (listAdapter.isEditMode()) {
                listAdapter.changeRouteSelectedState(position);
                listAdapter.notifyDataSetInvalidated();
            } else {
                Intent intent = new Intent(RouteListActivity.this,
                        RouteActivity.class);
                intent.putExtra(RouteActivity.ARG_ROUTE_ID, id);
                startActivityForResult(intent, ROUTE_REQUEST_CODE);
            }
        });

        listView.setOnItemLongClickListener(
                (adapterView, view, i, l) -> {
                    enableEditMode(i);
                    return true;
                });

        List<Route> routes = Routes.loadRoutes();
        if (routes.isEmpty()) {
            listView.setVisibility(View.GONE);
            noRouteTextView.setVisibility(View.VISIBLE);
        }
        listAdapter = new RoutesListAdapter(this, routes);
        listView.setAdapter(listAdapter);

        swipeRefreshLayout.setOnRefreshListener(
                () -> {
                    refreshList();
                    swipeRefreshLayout.setRefreshing(false);
                }
        );
    }

    @Override
    public final boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_routelist, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public final boolean onPrepareOptionsMenu(final Menu menu) {
        boolean editMode = listAdapter.isEditMode();
        menu.findItem(R.id.action_download).setVisible(!editMode);
        menu.findItem(R.id.action_upload).setVisible(editMode);
        menu.findItem(R.id.action_delete).setVisible(editMode);
        menu.findItem(R.id.action_select_all).setVisible(editMode);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public final boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_download:
                ConnectDialog connectDialogDownload =
                        ConnectDialog.newInstance(DOWNLOAD_OPERATION);
                connectDialogDownload.setCallback(this);
                connectDialogDownload.show(getSupportFragmentManager(),
                        ConnectDialog.FRAGMENT_TAG);
                return true;
            case R.id.action_upload:
                if (!Routes.getSelectedRoutes().isEmpty()) {
                    ConnectDialog connectDialogUpload =
                            ConnectDialog.newInstance(UPLOAD_OPERATION);
                    connectDialogUpload.setCallback(this);
                    connectDialogUpload.show(getSupportFragmentManager(),
                            ConnectDialog.FRAGMENT_TAG);
                }
                return true;
            case R.id.action_delete:
                if (!Routes.getSelectedRoutes().isEmpty()) {
                    deleteRoutes();
                }
                return true;
            case R.id.action_select_all:
                List<Route> routes = Routes.loadRoutes();
                for (Route route : routes) {
                    route.setSelected(true);
                }
                listAdapter.notifyDataSetChanged();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected final void onActivityResult(final int requestCode,
                                          final int resultCode,
                                          final Intent data) {
        if (requestCode == ROUTE_REQUEST_CODE) {
            if (resultCode == ROUTE_DELETED) {
                String json = data.getStringExtra(RouteActivity.ARG_ROUTE_JSON);
                final Route route = new Gson().fromJson(json, Route.class);
                Snackbar.make(fab, R.string.route_delete_snackbar,
                        Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_undo,
                                v -> restoreRoutes(route)).show();
            }
        }
    }

    /**
     * Check if the list is in edit mode.
     *
     * @return true if the list is in edit mode, false otherwise
     */
    private boolean isEditMode() {
        return listAdapter != null && listAdapter.isEditMode();
    }

    /**
     * Called when the Back button is pressed. If enabled, the edit mode must be
     * disable, otherwise the activity should be finished.
     */
    @SuppressLint("RestrictedApi")
    private void disableEditMode() {
        fab.setVisibility(View.VISIBLE);
        listAdapter.disableEditMode();
        listAdapter.notifyDataSetInvalidated();
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        invalidateOptionsMenu();
    }

    /**
     * Enable the edit mode.
     *
     * @param position Position of the item that has been pressed long
     */
    @SuppressLint("RestrictedApi")
    private void enableEditMode(final int position) {
        if (!listAdapter.isEditMode()) {
            fab.setVisibility(View.GONE);
            listAdapter.enableEditMode(position);
            listAdapter.notifyDataSetInvalidated();
            toolbar.setNavigationIcon(R.drawable.ic_close_white_24dp);
            invalidateOptionsMenu();
        }
    }

    /**
     * Execute the task to delete the selected routes.
     */
    private void deleteRoutes() {
        Deletable[] routes = Routes.getSelectedRoutes().toArray(
                new Deletable[0]);
        new Deleter(new Integer[]{SUBSCRIBER_ID}, new Route()).execute(routes);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Execute the task to restore the given routes.
     *
     * @param routes Routes to restore
     */
    private void restoreRoutes(final Restorable... routes) {
        new Restorer(new Route()).execute(routes);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Reload the list of routes and notify the list adapter.
     */
    private void refreshList() {
        if (isEditMode()) {
            disableEditMode();
        }
        List<Route> routes = Routes.loadRoutes();
        if (routes.isEmpty()) {
            listView.setVisibility(View.GONE);
            noRouteTextView.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            noRouteTextView.setVisibility(View.GONE);
        }
        listAdapter.setRoutes(routes);
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public final void onSubmit(final int operation,
                               final ConnectInfo connectInfo) {
        if (operation == DOWNLOAD_OPERATION) {
            new Downloader<>(
                    getApplicationContext(),
                    connectInfo,
                    new Intent(this, RouteListActivity.class),
                    SUBSCRIBER_ID,
                    Route.class,
                    Routes.Json.FILE_REGEX);
        } else if (operation == UPLOAD_OPERATION) {
            try {
                new Uploader(
                        getApplicationContext(),
                        connectInfo,
                        new Intent(this, RouteListActivity.class),
                        Routes.getSelectedRoutes(),
                        SUBSCRIBER_ID);
            } catch (JSONException e) {
                ErrorDialog.show(this,
                        getString(R.string.upload_failure_title),
                        e.getMessage(), null);
            }
        }

        runOnUiThread(() -> {
            progressBar.setIndeterminate(true);
            progressBar.setVisibility(View.VISIBLE);
        });
    }

    /**
     * Triggered on download event.
     *
     * @param event Download event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onDownloadEvent(final DownloadEvent event) {
        if (!event.isSubscriberAllowed(SUBSCRIBER_ID)) {
            return;
        }
        switch (event.getResult()) {
            case DownloadEvent.DOWNLOADING:
                progressBar.setVisibility(View.VISIBLE);
                break;
            case DownloadEvent.OK:
                progressBar.setVisibility(View.VISIBLE);
                break;
            case DownloadEvent.NO_FILE:
                Snackbar.make(fab, R.string.routes_download_no_file_snackbar,
                        Snackbar.LENGTH_LONG).show();
                progressBar.setVisibility(View.GONE);
                break;
            case DownloadEvent.ERROR:
                ErrorDialog.show(this,
                        getString(R.string.download_failure_title),
                        event.getMessage(), null);
                progressBar.setVisibility(View.GONE);
                break;
            default:
                break;
        }
    }

    /**
     * Triggered on JSON file reader event.
     *
     * @param event JSON file reader event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onJsonFileReaderEvent(final JsonFileReaderEvent event) {
        if (!event.isSubscriberAllowed(SUBSCRIBER_ID)) {
            return;
        }
        switch (event.getResult()) {
            case JsonFileReaderEvent.OK:
                progressBar.setVisibility(View.VISIBLE);
                break;
            case JsonFileReaderEvent.ERROR:
                ErrorDialog.show(this,
                        getString(R.string.download_reading_failure_title),
                        event.getException().getMessage(), null);
                progressBar.setVisibility(View.GONE);
                break;
            default:
                break;
        }
    }

    /**
     * Triggered on inserted event.
     *
     * @param event inserted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onInsertedEvent(final InsertedEvent event) {
        if (!(event.getInsertable() instanceof Route)) {
            return;
        }
        switch (event.getResult()) {
            case InsertedEvent.OK:
                refreshList();
                Snackbar.make(fab, R.string.routes_download_success_snackbar,
                        Snackbar.LENGTH_LONG).show();
                progressBar.setVisibility(View.GONE);
                break;
            case InsertedEvent.ERROR:
                ErrorDialog.show(this,
                        getString(R.string.download_insert_failure_title),
                        event.getException().getMessage(), null);
                progressBar.setVisibility(View.GONE);
                break;
            default:
                break;
        }
    }

    /**
     * Triggered on JSON file writer event.
     *
     * @param event JSON file writer event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onJsonFileWriterEvent(final JsonFileWriterEvent event) {
        if (!event.isSubscriberAllowed(SUBSCRIBER_ID)) {
            return;
        }
        switch (event.getResult()) {
            case JsonFileWriterEvent.OK:
                progressBar.setVisibility(View.VISIBLE);
                break;
            case JsonFileWriterEvent.ERROR:
                ErrorDialog.show(this,
                        getString(R.string.upload_writing_failure_title),
                        event.getException().getMessage(), null);
                progressBar.setVisibility(View.GONE);
                break;
            default:
                break;
        }
    }

    /**
     * Triggered on upload event.
     *
     * @param event Upload event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onUploadEvent(final UploadEvent event) {
        if (!event.isSubscriberAllowed(SUBSCRIBER_ID)) {
            return;
        }
        switch (event.getResult()) {
            case UploadEvent.UPLOADING:
                progressBar.setVisibility(View.VISIBLE);
                break;
            case UploadEvent.OK:
                Snackbar.make(fab, R.string.routes_upload_success_snackbar,
                        Snackbar.LENGTH_LONG).show();
                progressBar.setVisibility(View.GONE);
                break;
            case UploadEvent.ERROR:
                ErrorDialog.show(this, getString(
                        R.string.upload_failure_title), event.getMessage(),
                        null);
                progressBar.setVisibility(View.GONE);
                break;
            default:
                break;
        }
    }

    /**
     * Triggered on deleted event.
     *
     * @param event Deleted event
     */
    @SuppressWarnings("unchecked")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onDeletedEvent(final DeletedEvent event) {
        if (!(event.getDeletable() instanceof Route)
                || !event.isSubscriberAllowed(SUBSCRIBER_ID)) {
            return;
        }
        progressBar.setVisibility(View.GONE);
        refreshList();

        // create snackbar
        Snackbar snackbar = Snackbar.make(fab, R.string.routes_delete_snackbar,
                Snackbar.LENGTH_LONG);

        // if the deleted entities are restorable, show UNDO action
        if (Restorable.class.isAssignableFrom(
                event.getDeletable().getClass())) {
            // convert deletable entities to restorable entities
            final Restorable[] routes = Deletables.toRestorables(
                    event.getDeletedEntities());
            snackbar.setAction(R.string.action_undo, v -> {
                // restore routes
                restoreRoutes(routes);
            });
        }
        snackbar.show();
    }

    /**
     * Triggered on refresh event.
     *
     * @param event Refresh event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onRefreshEvent(final RefreshEvent event) {
        refreshList();
    }

    /**
     * Triggered on restored event.
     *
     * @param event Restored event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onRestoredEvent(final RestoredEvent event) {
        if (!(event.getRestorable() instanceof Route)) {
            return;
        }
        progressBar.setVisibility(View.GONE);
        refreshList();
    }

    @Override
    public final void onBackPressed() {
        if (isEditMode()) {
            disableEditMode();
        } else {
            finish();
        }
    }

    /**
     * Called when the Add route button is clicked.
     */
    @OnClick(R.id.routes_fab_add)
    public final void onAddRouteClicked() {
        AddRouteDialog.show(this);
    }
}
