package it.niedermann.owncloud.notes.android.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import at.bitfire.cert4android.CustomCertManager;
import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.persistence.NoteServerSyncHelper;
import it.niedermann.owncloud.notes.util.NotesClientUtil;
import it.niedermann.owncloud.notes.util.NotesClientUtil.LoginStatus;
import it.niedermann.owncloud.notes.util.SupportUtil;

import static it.niedermann.owncloud.notes.R.drawable.settings;

/**
 * Allows to set Settings like URL, Username and Password for Server-Synchronization
 * Created by stefan on 22.09.15.
 */
public class SettingsActivity extends AppCompatActivity {

    public static final String SETTINGS_URL = "settingsUrl";
    public static final String SETTINGS_USERNAME = "settingsUsername";
    public static final String SETTINGS_PASSWORD = "settingsPassword";
    public static final String SETTINGS_KEY_ETAG = "notes_last_etag";
    public static final String SETTINGS_KEY_LAST_MODIFIED = "notes_last_modified";
    public static final String DEFAULT_SETTINGS = "";
    public static final int CREDENTIALS_CHANGED = 3;

    private SharedPreferences preferences = null;
    private EditText field_url = null;
    private EditText field_username = null;
    private EditText field_password = null;
    private TextInputLayout password_wrapper = null;
    private String old_password = "";
    private Button btn_submit = null;
    private boolean first_run = false;
    private CustomCertManager customCertManager = null;

    @Override
    protected void finalize() throws Throwable {
        customCertManager.close();
        super.finalize();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        customCertManager = SupportUtil.getCertManager(getApplicationContext());
        setContentView(R.layout.activity_settings);

        preferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        if (!NoteServerSyncHelper.isConfigured(this)) {
            first_run = true;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        }

        field_url = (EditText) findViewById(R.id.settings_url);
        field_username = (EditText) findViewById(R.id.settings_username);
        field_password = (EditText) findViewById(R.id.settings_password);
        password_wrapper = (TextInputLayout) findViewById(R.id.settings_password_wrapper);
        btn_submit = (Button) findViewById(R.id.settings_submit);

        field_url.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String url = field_url.getText().toString().trim();

                if (!url.endsWith("/")) {
                    url += "/";
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                new URLValidatorAsyncTask().execute(url);

                if (NotesClientUtil.isHttp(url)) {
                    findViewById(R.id.settings_url_warn_http).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.settings_url_warn_http).setVisibility(View.GONE);
                }

                handleSubmitButtonEnabled(field_url.getText(), field_username.getText());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        field_username.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handleSubmitButtonEnabled(field_url.getText(), field_username.getText());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        // Load current Preferences
        field_url.setText(preferences.getString(SETTINGS_URL, DEFAULT_SETTINGS));
        field_username.setText(preferences.getString(SETTINGS_USERNAME, DEFAULT_SETTINGS));
        old_password = preferences.getString(SETTINGS_PASSWORD, DEFAULT_SETTINGS);

        field_password.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                login();
                return true;
            }
        });
        field_password.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                setPasswordHint(hasFocus);
            }
        });
        setPasswordHint(false);

        btn_submit.setEnabled(false);
        btn_submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });
    }

    private void setPasswordHint(boolean hasFocus) {
        boolean unchangedHint = !hasFocus && field_password.getText().toString().isEmpty() && !old_password.isEmpty();
        password_wrapper.setHint(getString(unchangedHint ? R.string.settings_password_unchanged : R.string.settings_password));
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Occurs in this scenario: User opens the app but doesn't configure the server settings, they then add the Create Note widget to home screen and configure
        // server settings there. The stale SettingsActivity is then displayed hence finish() here to close it down.
        if ((first_run) && (NoteServerSyncHelper.isConfigured(this))) {
            finish();
        }
    }

    /**
     * Prevent pressing back button on first run
     */
    @Override
    public void onBackPressed() {
        if (!first_run) {
            super.onBackPressed();
        }
    }

    private void login() {
        String url = field_url.getText().toString().trim();
        String username = field_username.getText().toString();
        String password = field_password.getText().toString();
        if(password.isEmpty()) {
            password = old_password;
        }

        if (!url.endsWith("/")) {
            url += "/";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        new LoginValidatorAsyncTask().execute(url, username, password);
    }

    private void handleSubmitButtonEnabled(Editable url, Editable username) {
        if (field_username.getText().length() > 0 && field_url.getText().length() > 0) {
            btn_submit.setEnabled(true);
        } else {
            btn_submit.setEnabled(false);
        }
    }

    /************************************ Async Tasks ************************************/

    /**
     * Checks if the given URL returns a valid status code and sets the Check next to the URL-Input Field to visible.
     * Created by stefan on 23.09.15.
     */
    private class URLValidatorAsyncTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            ((EditText) findViewById(R.id.settings_url)).setCompoundDrawables(null, null, null, null);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            return NotesClientUtil.isValidURL(customCertManager, params[0]);
        }

        @Override
        protected void onPostExecute(Boolean o) {
            if (o) {
                Drawable actionDoneDark = ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_action_done_dark);
                actionDoneDark.setBounds(0, 0, 50, 50);
                ((EditText) findViewById(R.id.settings_url)).setCompoundDrawables(null, null, actionDoneDark, null);
            } else {
                ((EditText) findViewById(R.id.settings_url)).setCompoundDrawables(null, null, null, null);
            }
        }
    }

    /**
     * If Log-In-Credentials are correct, save Credentials to Shared Preferences and finish First Run Wizard.
     */
    private class LoginValidatorAsyncTask extends AsyncTask<String, Void, LoginStatus> {
        String url, username, password;

        @Override
        protected void onPreExecute() {
            setInputsEnabled(false);
            btn_submit.setText(R.string.settings_submitting);
        }

        /**
         * @param params url, username and password
         * @return isValidLogin Boolean
         */
        @Override
        protected LoginStatus doInBackground(String... params) {
            url = params[0];
            username = params[1];
            password = params[2];
            return NotesClientUtil.isValidLogin(customCertManager, url, username, password);
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            if (LoginStatus.OK.equals(status)) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(SETTINGS_URL, url);
                editor.putString(SETTINGS_USERNAME, username);
                editor.putString(SETTINGS_PASSWORD, password);
                editor.remove(SETTINGS_KEY_ETAG);
                editor.remove(SETTINGS_KEY_LAST_MODIFIED);
                editor.apply();

                final Intent data = new Intent();
                data.putExtra(NotesListViewActivity.CREDENTIALS_CHANGED, CREDENTIALS_CHANGED);
                setResult(RESULT_OK, data);
                finish();
            } else {
                Log.e("Note", "invalid login");
                btn_submit.setText(R.string.settings_submit);
                setInputsEnabled(true);
                Toast.makeText(getApplicationContext(), getString(R.string.error_invalid_login, getString(status.str)), Toast.LENGTH_LONG).show();
            }
        }

        /**
         * Sets all Input-Fields and Buttons to enabled or disabled depending on the given boolean.
         *
         * @param enabled - boolean
         */
        private void setInputsEnabled(boolean enabled) {
            btn_submit.setEnabled(enabled);
            field_url.setEnabled(enabled);
            field_username.setEnabled(enabled);
            field_password.setEnabled(enabled);
        }
    }
}
