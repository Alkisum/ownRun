package com.alkisum.android.ownrun.history;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.alkisum.android.cloudops.file.json.JsonFile;
import com.alkisum.android.cloudops.net.ConnectDialog;
import com.alkisum.android.cloudops.net.ConnectInfo;
import com.alkisum.android.cloudops.net.owncloud.OcDownloader;
import com.alkisum.android.cloudops.net.owncloud.OcUploader;
import com.alkisum.android.ownrun.R;
import com.alkisum.android.ownrun.data.Deleter;
import com.alkisum.android.ownrun.data.Inserter;
import com.alkisum.android.ownrun.data.Sessions;
import com.alkisum.android.ownrun.dialog.ConfirmDialog;
import com.alkisum.android.ownrun.dialog.ErrorDialog;
import com.alkisum.android.ownrun.model.Session;
import com.alkisum.android.ownrun.utils.Json;
import com.owncloud.android.lib.resources.files.RemoteFile;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Activity listing the history of sessions.
 *
 * @author Alkisum
 * @version 2.4
 * @since 1.0
 */
public class HistoryActivity extends AppCompatActivity implements
        ConnectDialog.ConnectDialogListener, OcUploader.UploaderListener,
        OcDownloader.OcDownloaderListener, Deleter.DeleterListener,
        Inserter.InserterListener {

    /**
     * Argument for the session's ID that has just been stopped. This ID is
     * given only when the history activity is started automatically after
     * the session is stopped.
     */
    public static final String ARG_HIGHLIGHTED_SESSION_ID =
            "arg_highlighted_session_id";

    /**
     * Argument for the session's ID that should not be listed because it is
     * still running. When no session is running, this argument is null.
     */
    public static final String ARG_IGNORE_SESSION_ID = "arg_ignore_session_id";

    /**
     * Operation id for download.
     */
    private static final int DOWNLOAD_OPERATION = 1;

    /**
     * Operation id for upload.
     */
    private static final int UPLOAD_OPERATION = 2;

    /**
     * Request code for SessionActivity result.
     */
    private static final int SESSION_REQUEST_CODE = 286;

    /**
     * Result returned by SessionActivity when the session was deleted from the
     * SessionActivity.
     */
    public static final int SESSION_DELETED = 627;

    /**
     * Result returned by AddSessionActivity when the session has been inserted
     * into the database.
     */
    public static final int SESSION_ADDED = 861;

    /**
     * Toolbar.
     */
    @BindView(R.id.history_toolbar)
    Toolbar mToolbar;

    /**
     * ListView containing the sessions.
     */
    @BindView(R.id.history_list)
    ListView mListView;

    /**
     * TextView informing the user that no sessions are available.
     */
    @BindView(R.id.history_no_session)
    TextView mNoSessionTextView;

    /**
     * List adapter for the list of session.
     */
    private HistoryListAdapter mListAdapter;

    /**
     * Progress dialog to show the progress of uploading.
     */
    private ProgressDialog mProgressDialog;

    /**
     * OcDownloader instance created when the user presses on the Download item
     * from the context menu, and initialized when the connect dialog is submit.
     */
    private OcDownloader mDownloader;

    /**
     * OcUploader instance created when the user presses on the Upload item from
     * the context menu, and initialized when the connect dialog is submit.
     */
    private OcUploader mUploader;

    /**
     * ID of the session that should be highlighted.
     */
    private Long mHighlightedSessionId;

    /**
     * ID of the session that should be ignored because it is still running. The
     * ID is null if no session is running.
     */
    private Long mIgnoreSessionId;

    @Override
    protected final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_history);
        ButterKnife.bind(this);

        Bundle extras = getIntent().getExtras();
        mHighlightedSessionId = null;
        mIgnoreSessionId = null;

        if (extras != null) {
            mHighlightedSessionId = extras.getLong(ARG_HIGHLIGHTED_SESSION_ID);
            mIgnoreSessionId = extras.getLong(ARG_IGNORE_SESSION_ID);
        }

        Sessions.fixSessions(mIgnoreSessionId);

        setGui();
    }

    @Override
    protected final void onActivityResult(final int requestCode,
                                          final int resultCode,
                                          final Intent data) {
        if (requestCode == SESSION_REQUEST_CODE) {
            if (resultCode == SESSION_DELETED) {
                refreshList();
            } else if (resultCode == SESSION_ADDED) {
                refreshList();
            }
        }
    }

    /**
     * Set the GUI.
     */
    private void setGui() {
        mToolbar.setTitle(R.string.history_title);
        setSupportActionBar(mToolbar);
        mToolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (isEditMode()) {
                    disableEditMode();
                } else {
                    finish();
                }
            }
        });

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> adapterView,
                                    final View view, final int position,
                                    final long id) {
                if (mListAdapter.isEditMode()) {
                    mListAdapter.changeSessionSelectedState(position);
                    mListAdapter.notifyDataSetInvalidated();
                } else {
                    Intent intent = new Intent(HistoryActivity.this,
                            SessionActivity.class);
                    intent.putExtra(SessionActivity.ARG_SESSION_ID, id);
                    startActivityForResult(intent, SESSION_REQUEST_CODE);
                }
            }
        });

        mListView.setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(
                            final AdapterView<?> adapterView, final View view,
                            final int i, final long l) {
                        enableEditMode(i);
                        return true;
                    }
                });

        List<Session> sessions = Sessions.loadSessions(mIgnoreSessionId);
        if (sessions.isEmpty()) {
            mListView.setVisibility(View.GONE);
            mNoSessionTextView.setVisibility(View.VISIBLE);
        }
        mListAdapter = new HistoryListAdapter(this, sessions,
                mHighlightedSessionId);
        mListView.setAdapter(mListAdapter);
    }

    @Override
    public final boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_history, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public final boolean onPrepareOptionsMenu(final Menu menu) {
        boolean editMode = mListAdapter.isEditMode();
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
                DialogFragment connectDialogDownload =
                        ConnectDialog.newInstance(DOWNLOAD_OPERATION);
                connectDialogDownload.show(getSupportFragmentManager(),
                        ConnectDialog.FRAGMENT_TAG);
                mDownloader = new OcDownloader(this);
                return true;
            case R.id.action_upload:
                List<Session> selectedSessions = Sessions.getSelectedSessions();
                if (!selectedSessions.isEmpty()) {
                    DialogFragment connectDialogUpload =
                            ConnectDialog.newInstance(UPLOAD_OPERATION);
                    connectDialogUpload.show(getSupportFragmentManager(),
                            ConnectDialog.FRAGMENT_TAG);
                    try {
                        mUploader = new OcUploader(this,
                                Json.buildJsonFilesFromSessions(
                                        selectedSessions));
                    } catch (JSONException e) {
                        ErrorDialog.build(this,
                                getString(R.string.upload_failure_title),
                                e.getMessage(), null).show();
                    }
                }
                return true;
            case R.id.action_delete:
                if (!Sessions.getSelectedSessions().isEmpty()) {
                    showDeleteConfirmation();
                }
                return true;
            case R.id.action_select_all:
                List<Session> sessions = Sessions.loadSessions(
                        mIgnoreSessionId);
                for (Session session : sessions) {
                    session.setSelected(true);
                }
                mListAdapter.notifyDataSetChanged();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Check if the list is in edit mode.
     *
     * @return true if the list is in edit mode, false otherwise
     */
    private boolean isEditMode() {
        return mListAdapter != null && mListAdapter.isEditMode();
    }

    /**
     * Called when the Back button is pressed. If enabled, the edit mode must be
     * disable, otherwise the activity should be finished.
     */
    private void disableEditMode() {
        mListAdapter.disableEditMode();
        mListAdapter.notifyDataSetInvalidated();
        mToolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        invalidateOptionsMenu();
    }

    /**
     * Enable the edit mode.
     *
     * @param position Position of the item that has been pressed long
     */
    private void enableEditMode(final int position) {
        if (!mListAdapter.isEditMode()) {
            mListAdapter.enableEditMode(position);
            mListAdapter.notifyDataSetInvalidated();
            mToolbar.setNavigationIcon(R.drawable.ic_close_white_24dp);
            invalidateOptionsMenu();
        }
    }

    /**
     * Show dialog to confirm the deletion of the selected sessions.
     */
    private void showDeleteConfirmation() {
        ConfirmDialog.build(this,
                getString(R.string.history_delete_title),
                getString(R.string.history_delete_msg),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialogInterface,
                                        final int i) {
                        deleteSelectedSessions();
                    }
                }).show();
    }

    /**
     * Execute the task to delete the selected sessions.
     */
    private void deleteSelectedSessions() {
        new Deleter(this).execute();
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setProgressNumberFormat(null);
        mProgressDialog.setMessage(getString(R.string.history_delete_progress));
        mProgressDialog.show();
    }

    /**
     * Reload the list of sessions and notify the list adapter.
     */
    private void refreshList() {
        if (isEditMode()) {
            disableEditMode();
        }
        List<Session> sessions = Sessions.loadSessions(mIgnoreSessionId);
        if (sessions.isEmpty()) {
            mListView.setVisibility(View.GONE);
            mNoSessionTextView.setVisibility(View.VISIBLE);
        } else {
            mListView.setVisibility(View.VISIBLE);
            mNoSessionTextView.setVisibility(View.GONE);
        }
        mListAdapter.setSessions(sessions);
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    public final void onSubmit(final int operation,
                               final ConnectInfo connectInfo) {
        if (operation == DOWNLOAD_OPERATION) {
            startDownload(connectInfo);
        } else if (operation == UPLOAD_OPERATION) {
            startUpload(connectInfo);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog = new ProgressDialog(HistoryActivity.this);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setProgressStyle(
                        ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setProgressNumberFormat(null);
                mProgressDialog.setMessage(getString(
                        R.string.operation_progress_init_msg));
                mProgressDialog.show();
            }
        });
    }

    /**
     * Start the download operation.
     *
     * @param connectInfo Connection information given by user
     */
    private void startDownload(final ConnectInfo connectInfo) {
        if (mDownloader == null) {
            return;
        }
        mDownloader.init(
                connectInfo.getAddress(),
                connectInfo.getPath(),
                connectInfo.getUsername(),
                connectInfo.getPassword()).start();
    }

    /**
     * Start the upload operation.
     *
     * @param connectInfo Connection information given by user
     */
    private void startUpload(final ConnectInfo connectInfo) {
        if (mUploader == null) {
            return;
        }
        mUploader.init(
                connectInfo.getAddress(),
                connectInfo.getPath(),
                connectInfo.getUsername(),
                connectInfo.getPassword()).start();
    }

    @Override
    public final void onDownloadStart(final RemoteFile file) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.setMessage("Downloading "
                            + file.getRemotePath() + " ...");
                    mProgressDialog.setIndeterminate(false);
                }
            }
        });
    }

    @Override
    public final void onNoFileToDownload() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                Toast.makeText(HistoryActivity.this, getString(R.string.
                        download_no_file_toast), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public final void onDownloading(final int percentage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.setProgress(percentage);
                }
            }
        });
    }

    @Override
    public final void onAllDownloadComplete() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.setMessage(
                            getString(R.string.download_reading_msg));
                    mProgressDialog.setProgressPercentFormat(null);
                    mProgressDialog.setIndeterminate(true);
                }
            }
        });
    }

    @Override
    public final void onDownloadFailed(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                ErrorDialog.build(HistoryActivity.this, getString(
                        R.string.download_failure_title), message, null).show();
            }
        });
    }

    @Override
    public final void onJsonFilesRead(final List<JsonFile> jsonFiles) {
        List<JSONObject> jsonObjects = new ArrayList<>();
        for (JsonFile jsonFile : jsonFiles) {
            if (Json.isFileNameValid(jsonFile)
                    && !Json.isSessionAlreadyInDb(jsonFile)) {
                jsonObjects.add(jsonFile.getJsonObject());
            }
        }

        if (jsonObjects.isEmpty()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                    }
                    Toast.makeText(HistoryActivity.this, getString(R.string.
                            download_no_file_toast),
                            Toast.LENGTH_LONG).show();
                }
            });
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mProgressDialog != null) {
                        mProgressDialog.setMessage(
                                getString(R.string.download_inserting_msg));
                        mProgressDialog.setProgressPercentFormat(null);
                        mProgressDialog.setIndeterminate(true);
                    }
                }
            });
            new Inserter(this, jsonObjects).execute();
        }
    }

    @Override
    public final void onReadingFileFailed(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                ErrorDialog.build(HistoryActivity.this,
                        getString(R.string.download_reading_failure_title),
                        e.getMessage(), null).show();
            }
        });
    }

    @Override
    public final void onDataInserted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                refreshList();
                Toast.makeText(HistoryActivity.this, getString(R.string.
                        download_success_toast), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public final void onInsertDataFailed(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                ErrorDialog.build(HistoryActivity.this,
                        getString(R.string.download_insert_failure_title),
                        e.getMessage(), null).show();
            }
        });
    }

    @Override
    public final void onWritingFileFailed(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                ErrorDialog.build(HistoryActivity.this,
                        getString(R.string.upload_writing_failure_title),
                        e.getMessage(), null).show();
            }
        });
    }

    @Override
    public final void onUploadStart(final JsonFile jsonFile) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.setMessage("Uploading "
                            + jsonFile.getName() + Json.FILE_EXT + " ...");
                    mProgressDialog.setIndeterminate(false);
                }
            }
        });
    }

    @Override
    public final void onUploading(final int percentage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.setProgress(percentage);
                }
            }
        });
    }

    @Override
    public final void onAllUploadComplete() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                Toast.makeText(HistoryActivity.this,
                        getString(R.string.history_upload_success_toast),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public final void onUploadFailed(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                ErrorDialog.build(HistoryActivity.this, getString(
                        R.string.upload_failure_title), message, null).show();
            }
        });
    }

    @Override
    public final void onSessionsDeleted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                refreshList();
            }
        });
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
     * Called when the Add session button is clicked.
     */
    @OnClick(R.id.history_fab_add)
    public final void onAddSessionClicked() {
        Intent intent = new Intent(this, AddSessionActivity.class);
        startActivityForResult(intent, SESSION_REQUEST_CODE);
    }
}
