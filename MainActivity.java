/**
 * Copyright (C) 2015  RasPi Check Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package de.eidottermihi.rpicheck.activity;

import java.io.File;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sheetrock.panda.changelog.ChangeLog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SimpleCursorAdapter;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.google.common.base.Strings;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshScrollView;

import de.eidottermihi.raspicheck.R;
import de.eidottermihi.rpicheck.activity.helper.Constants;
import de.eidottermihi.rpicheck.activity.helper.FormatHelper;
import de.eidottermihi.rpicheck.activity.helper.LoggingHelper;
import de.eidottermihi.rpicheck.beans.DiskUsageBean;
import de.eidottermihi.rpicheck.beans.NetworkInterfaceInformation;
import de.eidottermihi.rpicheck.beans.ProcessBean;
import de.eidottermihi.rpicheck.beans.QueryBean;
import de.eidottermihi.rpicheck.beans.ShutdownResult;
import de.eidottermihi.rpicheck.beans.WlanBean;
import de.eidottermihi.rpicheck.db.DeviceDbHelper;
import de.eidottermihi.rpicheck.db.RaspberryDeviceBean;
import de.eidottermihi.rpicheck.fragment.PassphraseDialog;
import de.eidottermihi.rpicheck.fragment.PassphraseDialog.PassphraseDialogListener;
import de.eidottermihi.rpicheck.fragment.QueryErrorMessagesDialog;
import de.eidottermihi.rpicheck.fragment.QueryExceptionDialog;
import de.eidottermihi.rpicheck.fragment.RebootDialogFragment;
import de.eidottermihi.rpicheck.fragment.RebootDialogFragment.ShutdownDialogListener;
import de.eidottermihi.rpicheck.ssh.LoadAveragePeriod;
import de.eidottermihi.rpicheck.ssh.impl.RaspiQueryException;

public class MainActivity extends SherlockFragmentActivity implements
		ActionBar.OnNavigationListener, OnRefreshListener<ScrollView>,
		ShutdownDialogListener, PassphraseDialogListener, AsyncQueryDataUpdate,
		AsyncShutdownUpdate {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(MainActivity.class);

	private static final String CURRENT_DEVICE = "currentDevice";
	private static final String ALL_DEVICES = "allDevices";

	private static final String KEY_PREF_REFRESH_BY_ACTION_COUNT = "refreshCountByAction";

	private Intent settingsIntent;
	private Intent newRaspiIntent;
	private Intent editRaspiIntent;
	private Intent commandIntent;

	private TextView coreTempText;
	private TextView armFreqText;
	private TextView coreFreqText;
	private TextView coreVoltText;
	private TextView lastUpdateText;
	private TextView uptimeText;
	private TextView averageLoadText;
	private TextView totalMemoryText;
	private TextView freeMemoryText;
	private TextView serialNoText;
	private TextView distriText;
	private TextView firmwareText;
	private TableLayout diskTable;
	private TableLayout processTable;
	private TableLayout networkTable;
	private ProgressBar progressBar;
	private Button commandButton;
	private PullToRefreshScrollView refreshableScrollView;

	private SharedPreferences sharedPrefs;

	private DeviceDbHelper deviceDb;

	private RaspberryDeviceBean currentDevice;
	private SparseArray<RaspberryDeviceBean> allDevices;

	private Cursor deviceCursor;

	private SimpleCursorAdapter spinadapter;

	private static boolean isOnBackground;

	static {
		Security.insertProviderAt(
				new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// LOGGER.debug("onCreate()....");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// assigning Shared Preferences
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		// init intents
		settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
		newRaspiIntent = new Intent(MainActivity.this, NewRaspiActivity.class);
		editRaspiIntent = new Intent(MainActivity.this, EditRaspiActivity.class);
		commandIntent = new Intent(MainActivity.this,
				CustomCommandActivity.class);

		// assigning progressbar and command button
		progressBar = (ProgressBar) findViewById(R.id.progressBar1);
		commandButton = (Button) findViewById(R.id.commandButton);

		// assigning textviews to fields
		armFreqText = (TextView) findViewById(R.id.armFreqText);
		coreFreqText = (TextView) findViewById(R.id.coreFreqText);
		coreVoltText = (TextView) findViewById(R.id.coreVoltText);
		coreTempText = (TextView) findViewById(R.id.coreTempText);
		firmwareText = (TextView) findViewById(R.id.firmwareText);
		lastUpdateText = (TextView) findViewById(R.id.lastUpdateText);
		uptimeText = (TextView) findViewById(R.id.startedText);
		averageLoadText = (TextView) findViewById(R.id.loadText);
		totalMemoryText = (TextView) findViewById(R.id.totalMemText);
		freeMemoryText = (TextView) findViewById(R.id.freeMemText);
		serialNoText = (TextView) findViewById(R.id.cpuSerialText);
		distriText = (TextView) findViewById(R.id.distriText);
		diskTable = (TableLayout) findViewById(R.id.diskTable);
		processTable = (TableLayout) findViewById(R.id.processTable);
		networkTable = (TableLayout) findViewById(R.id.networkTable);

		// assigning refreshable root scrollview
		refreshableScrollView = (PullToRefreshScrollView) findViewById(R.id.scrollView1);
		refreshableScrollView.setOnRefreshListener(this);

		boolean isDebugLogging = sharedPrefs.getBoolean(
				SettingsActivity.KEY_PREF_DEBUG_LOGGING, false);
		LoggingHelper.changeLogger(isDebugLogging);

		// Changelog
		final ChangeLog cl = new ChangeLog(this);
		if (cl.firstRun()) {
			cl.getLogDialog().show();
		}

		// init device database
		deviceDb = new DeviceDbHelper(this);
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				deviceCursor = deviceDb.getFullDeviceCursor();
				return null;
			}

			@Override
			protected void onPostExecute(Void r) {
				if (deviceCursor.getCount() == 0) {
					MainActivity.this.startActivityForResult(newRaspiIntent,
							NewRaspiActivity.REQUEST_SAVE);
				} else {
					// init spinner
					initSpinner();
				}
			}
		}.execute();
	}

	@Override
	protected void onResume() {
		super.onResume();
		isOnBackground = false;
	}

	@Override
	protected void onPause() {
		super.onPause();
		isOnBackground = true;
	}

	/**
	 * Reset the view.
	 */
	private void resetView() {
		coreTempText.setText("");
		armFreqText.setText("");
		coreFreqText.setText("");
		coreVoltText.setText("");
		firmwareText.setText("");
		uptimeText.setText("");
		averageLoadText.setText("");
		totalMemoryText.setText("");
		freeMemoryText.setText("");
		distriText.setText("");
		serialNoText.setText("");
		lastUpdateText.setText("");
		// tables
		updateDiskTable(null);
		updateNetworkTable(null);
		updateProcessTable(null);
	}

	private void updateQueryDataInView(QueryBean result) {
		final String tempScale = sharedPrefs.getString(
				SettingsActivity.KEY_PREF_TEMPERATURE_SCALE,
				getString(R.string.pref_temperature_scala_default));
		coreTempText.setText(FormatHelper.formatTemperature(currentDevice
				.getLastQueryData().getVcgencmdInfo().getCpuTemperature(),
				tempScale));
		final String freqScale = sharedPrefs.getString(
				SettingsActivity.KEY_PREF_FREQUENCY_UNIT,
				getString(R.string.pref_frequency_unit_default));
		armFreqText.setText(FormatHelper.formatFrequency(currentDevice
				.getLastQueryData().getVcgencmdInfo().getArmFrequency(),
				freqScale));
		coreFreqText.setText(FormatHelper.formatFrequency(currentDevice
				.getLastQueryData().getVcgencmdInfo().getCoreFrequency(),
				freqScale));
		coreVoltText.setText(FormatHelper.formatDecimal(currentDevice
				.getLastQueryData().getVcgencmdInfo().getCoreVolts()));
		firmwareText.setText(result.getVcgencmdInfo().getVersion());
		lastUpdateText.setText(SimpleDateFormat.getDateTimeInstance().format(
				result.getLastUpdate()));
		// uptime and average load may contain errors
		if (result.getAvgLoad() != null) {
			averageLoadText.setText(result.getAvgLoad().toString());
		}
		if (result.getStartup() != null) {
			uptimeText.setText(result.getStartup());
		}
		if (result.getFreeMem() != null) {
			freeMemoryText.setText(result.getFreeMem().humanReadableByteCount(
					false));
		}
		if (result.getTotalMem() != null) {
			totalMemoryText.setText(result.getTotalMem()
					.humanReadableByteCount(false));
		}
		serialNoText.setText(result.getSerialNo());
		distriText.setText(result.getDistri());
		// update tables
		updateNetworkTable(result);
		updateDiskTable(result);
		updateProcessTable(result);
		this.handleQueryError(result.getErrorMessages());
	}

	/**
	 * Shows a dialog containing the ErrorMessages.
	 * 
	 * @param errorMessages
	 *            the messages
	 */
	private void handleQueryError(List<String> errorMessages) {
		final ArrayList<String> messages = new ArrayList<String>(errorMessages);
		if (errorMessages.size() > 0 && !isOnBackground) {
			LOGGER.debug("Showing query error messages.");
			Bundle args = new Bundle();
			args.putStringArrayList(
					QueryErrorMessagesDialog.KEY_ERROR_MESSAGES, messages);
			final QueryErrorMessagesDialog messageDialog = new QueryErrorMessagesDialog();
			messageDialog.setArguments(args);
			messageDialog.show(getSupportFragmentManager(),
					"QueryErrorMessagesDialog");
		}
	}

	private void updateNetworkTable(QueryBean result) {
		// remove rows except header
		networkTable.removeViews(1, networkTable.getChildCount() - 1);
		if (result != null && result.getNetworkInfo() != null) {
			for (NetworkInterfaceInformation interfaceInformation : result
					.getNetworkInfo()) {
				networkTable.addView(createNetworkRow(interfaceInformation));
			}

		}
	}

	private View createNetworkRow(
			NetworkInterfaceInformation interfaceInformation) {
		final TableRow tempRow = new TableRow(this);
		tempRow.addView(createTextView(interfaceInformation.getName()));
		CharSequence statusText;
		if (interfaceInformation.isHasCarrier()) {
			statusText = getText(R.string.network_status_up);
		} else {
			statusText = getText(R.string.network_status_down);
		}
		tempRow.addView(createTextView(statusText.toString()));
		if (interfaceInformation.getIpAdress() != null) {
			tempRow.addView(createTextView(interfaceInformation.getIpAdress()));
		} else {
			tempRow.addView(createTextView(" - "));
		}
		if (interfaceInformation.getWlanInfo() != null) {
			final WlanBean wlan = interfaceInformation.getWlanInfo();
			tempRow.addView(createTextView(
					FormatHelper.formatPercentage(wlan.getSignalLevel()), 3));
			tempRow.addView(createTextView(
					FormatHelper.formatPercentage(wlan.getLinkQuality()), 3));
		} else {
			tempRow.addView(createTextView(" - ", 3));
			tempRow.addView(createTextView(" - ", 3));
		}
		return tempRow;
	}

	private void updateProcessTable(QueryBean result) {
		// remove current rows except header row
		processTable.removeViews(1, processTable.getChildCount() - 1);
		if (result != null && result.getProcesses() != null) {
			for (ProcessBean processBean : result.getProcesses()) {
				processTable.addView(createProcessRow(processBean));
			}
		}
	}

	private View createProcessRow(ProcessBean processBean) {
		final TableRow tempRow = new TableRow(this);
		tempRow.addView(createTextView(processBean.getpId() + ""));
		tempRow.addView(createTextView(processBean.getTty()));
		tempRow.addView(createTextView(processBean.getCpuTime()));
		tempRow.addView(createTextView(processBean.getCommand()));
		return tempRow;
	}

	private View createDiskRow(DiskUsageBean disk) {
		final TableRow tempRow = new TableRow(this);
		tempRow.addView(createTextView(disk.getFileSystem()));
		tempRow.addView(createTextView(disk.getSize()));
		tempRow.addView(createTextView(disk.getAvailable()));
		tempRow.addView(createTextView(disk.getUsedPercent()));
		tempRow.addView(createTextView(disk.getMountedOn()));
		return tempRow;
	}

	private View createTextView(String text) {
		return createTextView(text, null);
	}

	private View createTextView(String text, Integer paddingLeft) {
		final TextView tempText = new TextView(this);
		tempText.setText(text);
		if (paddingLeft != null) {
			float pix = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
					paddingLeft.intValue(), getResources().getDisplayMetrics());
			tempText.setPadding((int) (pix), 0, 0, 0);
		}
		return tempText;
	}

	private void updateDiskTable(QueryBean result) {
		// remove current rows except header row
		diskTable.removeViews(1, diskTable.getChildCount() - 1);
		if (result != null && result.getDisks() != null) {
			for (DiskUsageBean diskUsageBean : result.getDisks()) {
				// add row to table
				diskTable.addView(createDiskRow(diskUsageBean));
			}
		}
	}

	@SuppressWarnings("deprecation")
	private void initSpinner() {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				deviceCursor = deviceDb.getFullDeviceCursor();
				LOGGER.debug("Device cursor rows: " + deviceCursor.getCount());
				return null;
			}

			@Override
			protected void onPostExecute(Void r) {
				// only show spinner if theres already a device to show
				if (deviceCursor.getCount() > 0) {
					// make adapter
					spinadapter = new SimpleCursorAdapter(MainActivity.this,
							R.layout.sherlock_spinner_dropdown_item,
							deviceCursor, new String[] { "name", "_id" },
							new int[] { android.R.id.text1 });
					spinadapter
							.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
					getSupportActionBar().setNavigationMode(
							ActionBar.NAVIGATION_MODE_LIST);
					getSupportActionBar().setListNavigationCallbacks(
							spinadapter, MainActivity.this);
					getSupportActionBar().setDisplayShowTitleEnabled(false);
					commandButton.setVisibility(View.VISIBLE);
				} else {
					getSupportActionBar().setNavigationMode(
							ActionBar.DISPLAY_SHOW_TITLE);
					getSupportActionBar().setDisplayShowTitleEnabled(true);
					currentDevice = null;
					// disable edit/restart/delete action menu items
					supportInvalidateOptionsMenu();
					commandButton.setVisibility(View.GONE);

				}
			}
		}.execute();
	}

	/**
	 * Shows a dialog with a detailed error message.
	 * 
	 * @param exception
	 */
	private void handleQueryException(final RaspiQueryException exception) {
		final String errorMessage = this.mapExceptionToErrorMessage(exception);
		// only show dialog when app is not in background
		if (!isOnBackground) {
			LOGGER.debug("Query caused exception. Showing dialog.");
			// build dialog
			Bundle dialogArgs = new Bundle();
			dialogArgs
					.putString(QueryExceptionDialog.MESSAGE_KEY, errorMessage);
			QueryExceptionDialog dialogFragment = new QueryExceptionDialog();
			dialogFragment.setArguments(dialogArgs);
			dialogFragment.show(getSupportFragmentManager(),
					"QueryExceptionDialog");
		}
	}

	private String mapExceptionToErrorMessage(RaspiQueryException exception) {
		String message = null;
		switch (exception.getReasonCode()) {
		case RaspiQueryException.REASON_CONNECTION_FAILED:
			message = getString(R.string.connection_failed);
			break;
		case RaspiQueryException.REASON_AUTHENTIFICATION_FAILED:
			message = getString(R.string.authentication_failed);
			break;
		case RaspiQueryException.REASON_TRANSPORT_EXCEPTION:
			message = getString(R.string.transport_exception);
			break;
		case RaspiQueryException.REASON_IO_EXCEPTION:
			message = getString(R.string.unexpected_exception);
			break;
		case RaspiQueryException.REASON_VCGENCMD_NOT_FOUND:
			message = getString(R.string.exception_vcgencmd);
			break;
		default:
			message = getString(R.string.weird_exception);
			break;
		}
		return message;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.activity_main, menu);
		// set delete, edit and reboot visible if there is a current device
		boolean currDevice = currentDevice != null ? true : false;
		menu.findItem(R.id.menu_delete).setVisible(currDevice);
		menu.findItem(R.id.menu_edit_raspi).setVisible(currDevice);
		menu.findItem(R.id.menu_reboot).setVisible(currDevice);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			this.startActivity(settingsIntent);
			break;
		case R.id.menu_new_raspi:
			this.startActivityForResult(newRaspiIntent,
					NewRaspiActivity.REQUEST_SAVE);
			break;
		case R.id.menu_delete:
			this.deleteCurrentDevice();
			break;
		case R.id.menu_edit_raspi:
			final Bundle extras = new Bundle();
			extras.putInt(Constants.EXTRA_DEVICE_ID, currentDevice.getId());
			editRaspiIntent.putExtras(extras);
			this.startActivityForResult(editRaspiIntent,
					EditRaspiActivity.REQUEST_EDIT);
			break;
		case R.id.menu_reboot:
			this.showRebootDialog();
			break;
		case R.id.menu_refresh:
			this.doQuery(false);
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void showPullToRefreshHint() {
		// Shows 3 toasts if refresh is happening via action button and not pull
		// to refresh
		int count = sharedPrefs.getInt(KEY_PREF_REFRESH_BY_ACTION_COUNT, 0);
		if (count < 3) {
			Toast.makeText(this, getString(R.string.hint_pulltorefresh),
					Toast.LENGTH_LONG).show();
			sharedPrefs.edit()
					.putInt(KEY_PREF_REFRESH_BY_ACTION_COUNT, ++count).commit();
		}
	}

	private void showRebootDialog() {
		LOGGER.trace("Showing reboot dialog.");
		DialogFragment rebootDialog = new RebootDialogFragment();
		rebootDialog.show(getSupportFragmentManager(), "reboot");
	}

	private void doRebootOrHalt(String type) {
		LOGGER.info("Doing {} on {}...", type, currentDevice.getName());
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected()) {
			// get connection settings from shared preferences
			final String host = currentDevice.getHost();
			final String user = currentDevice.getUser();
			final String port = currentDevice.getPort() + "";
			final String sudoPass = currentDevice.getSudoPass();
			if (currentDevice.getAuthMethod().equals(
					NewRaspiAuthActivity.SPINNER_AUTH_METHODS[0])) {
				// ssh password
				final String pass = currentDevice.getPass();
				new SSHShutdownTask(this).execute(host, user, pass, port,
						sudoPass, type, null, null);
			} else if (currentDevice.getAuthMethod().equals(
					NewRaspiAuthActivity.SPINNER_AUTH_METHODS[1])) {
				// keyfile
				final String keyfilePath = currentDevice.getKeyfilePath();
				if (keyfilePath != null) {
					final File privateKey = new File(keyfilePath);
					if (privateKey.exists()) {
						new SSHShutdownTask(this).execute(host, user, null,
								port, sudoPass, type, keyfilePath, null);
					} else {
						Toast.makeText(this,
								"Cannot find keyfile at location: "
										+ keyfilePath, Toast.LENGTH_LONG);
					}
				} else {
					Toast.makeText(this, "No keyfile specified!",
							Toast.LENGTH_LONG);
				}
			} else if (currentDevice.getAuthMethod().equals(
					NewRaspiAuthActivity.SPINNER_AUTH_METHODS[2])) {
				// keyfile and passphrase
				final String keyfilePath = currentDevice.getKeyfilePath();
				if (keyfilePath != null) {
					final File privateKey = new File(keyfilePath);
					if (privateKey.exists()) {
						if (!Strings.isNullOrEmpty(currentDevice
								.getKeyfilePass())) {
							final String passphrase = currentDevice
									.getKeyfilePass();
							new SSHShutdownTask(this).execute(host, user, null,
									port, sudoPass, type, keyfilePath,
									passphrase);
						} else {
							final String dialogType = type
									.equals(Constants.TYPE_REBOOT) ? PassphraseDialog.SSH_SHUTDOWN
									: PassphraseDialog.SSH_HALT;
							final DialogFragment passphraseDialog = new PassphraseDialog();
							final Bundle args = new Bundle();
							args.putString(PassphraseDialog.KEY_TYPE,
									dialogType);
							passphraseDialog.setArguments(args);
							passphraseDialog.setCancelable(false);
							passphraseDialog.show(getSupportFragmentManager(),
									"passphrase");
						}
					} else {
						Toast.makeText(this,
								"Cannot find keyfile at location: "
										+ keyfilePath, Toast.LENGTH_LONG);
					}
				} else {
					Toast.makeText(this, "No keyfile specified!",
							Toast.LENGTH_LONG);
				}
			}
		} else {
			Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT)
					.show();
		}
	}

	private void deleteCurrentDevice() {
		LOGGER.info("Deleting pi {}.", currentDevice.getName());
		deviceDb.delete(currentDevice.getId());
		if (allDevices != null) {
			allDevices.delete(currentDevice.getId());
		}
		initSpinner();
	}

	private void doQuery(boolean initByPullToRefresh) {
		if (currentDevice == null) {
			// no device available, show hint for user
			Toast.makeText(this, R.string.no_device_available,
					Toast.LENGTH_LONG).show();
			// stop refresh animation from pull-to-refresh
			refreshableScrollView.onRefreshComplete();
			return;
		}
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected()) {
			// show progressbar
			progressBar.setVisibility(View.VISIBLE);
			// get connection settings from shared preferences
			String host = currentDevice.getHost();
			String user = currentDevice.getUser();
			String port = currentDevice.getPort() + "";
			String pass = null;
			// reading process preference
			final Boolean hideRoot = Boolean.valueOf(sharedPrefs.getBoolean(
					SettingsActivity.KEY_PREF_QUERY_HIDE_ROOT_PROCESSES, true));
			String keyPath = null;
			String keyPass = null;
			boolean canConnect = false;
			// check authentification method
			final String authMethod = currentDevice.getAuthMethod();
			if (authMethod.equals(NewRaspiAuthActivity.SPINNER_AUTH_METHODS[0])) {
				// only ssh password
				pass = currentDevice.getPass();
				if (pass != null) {
					canConnect = true;
				} else {
					Toast.makeText(this, R.string.no_password_specified,
							Toast.LENGTH_LONG).show();
				}
			} else if (authMethod
					.equals(NewRaspiAuthActivity.SPINNER_AUTH_METHODS[1])) {
				// keyfile must be present
				final String keyfilePath = currentDevice.getKeyfilePath();
				if (keyfilePath != null) {
					final File privateKey = new File(keyfilePath);
					if (privateKey.exists()) {
						keyPath = keyfilePath;
						canConnect = true;
					} else {
						Toast.makeText(
								this,
								"Cannot find keyfile at location: "
										+ keyfilePath, Toast.LENGTH_LONG)
								.show();
					}
				} else {
					Toast.makeText(this, "No keyfile specified!",
							Toast.LENGTH_LONG).show();
				}
			} else if (authMethod
					.equals(NewRaspiAuthActivity.SPINNER_AUTH_METHODS[2])) {
				// keyfile and keypass must be present
				final String keyfilePath = currentDevice.getKeyfilePath();
				if (keyfilePath != null) {
					final File privateKey = new File(keyfilePath);
					if (privateKey.exists()) {
						keyPath = keyfilePath;
						final String keyfilePass = currentDevice
								.getKeyfilePass();
						if (keyfilePass != null) {
							canConnect = true;
							keyPass = keyfilePass;
						} else {
							final DialogFragment newFragment = new PassphraseDialog();
							final Bundle args = new Bundle();
							args.putString(PassphraseDialog.KEY_TYPE,
									PassphraseDialog.SSH_QUERY);
							newFragment.setArguments(args);
							newFragment.setCancelable(false);
							newFragment.show(getSupportFragmentManager(),
									"passphrase");
							canConnect = false;
						}
					} else {
						Toast.makeText(
								this,
								"Cannot find keyfile at location: "
										+ keyfilePath, Toast.LENGTH_LONG)
								.show();
					}
				} else {
					Toast.makeText(this, "No keyfile specified!",
							Toast.LENGTH_LONG).show();
				}
			}
			if (host == null) {
				Toast.makeText(this, R.string.no_hostname_specified,
						Toast.LENGTH_LONG);
				canConnect = false;
			} else if (user == null) {
				Toast.makeText(this, R.string.no_username_specified,
						Toast.LENGTH_LONG);
				canConnect = false;
			}
			if (canConnect) {
				// disable pullToRefresh (if refresh initiated by action bar)
				if (!initByPullToRefresh) {
					refreshableScrollView.setMode(Mode.DISABLED);
					// show hint for pull-to-refresh
					this.showPullToRefreshHint();
				}
				// execute query
				new SSHQueryTask(this, getLoadAveragePreference()).execute(
						host, user, pass, port, hideRoot.toString(), keyPath,
						keyPass);
			}
		} else {
			Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT)
					.show();
			// stop refresh animation from pull-to-refresh
			refreshableScrollView.onRefreshComplete();
		}
	}

	private LoadAveragePeriod getLoadAveragePreference() {
		final String loadAvgPrefString = sharedPrefs.getString("pref_load_avg",
				"FIVE_MINUTES");
		LoadAveragePeriod period;
		switch (loadAvgPrefString) {
		case "ONE_MINUTE":
			period = LoadAveragePeriod.ONE_MINUTE;
			break;
		case "FIVE_MINUTES":
			period = LoadAveragePeriod.FIVE_MINUTES;
			break;
		case "FIFTEEN_MINUTES":
			period = LoadAveragePeriod.FIFTEEN_MINUTES;
			break;
		default:
			period = LoadAveragePeriod.FIVE_MINUTES;
			break;
		}
		LOGGER.debug("Load average preference: {}", period);
		return period;
	}

	@Override
	public boolean onNavigationItemSelected(final int itemPosition, long itemId) {
		LOGGER.debug("Spinner item selected: pos=" + itemPosition + ", id="
				+ itemId);
		new AsyncTask<Long, Void, RaspberryDeviceBean>() {
			@Override
			protected RaspberryDeviceBean doInBackground(Long... params) {
				// get device with id
				return deviceDb.read(params[0]);
			}

			@Override
			protected void onPostExecute(RaspberryDeviceBean read) {
				if (currentDevice == null) {
					currentDevice = read;
				} else {
					// set current device only when device has changed (query
					// data get
					// lost otherwise)
					if (read.getId() != currentDevice.getId()) {
						LOGGER.debug(
								"Switch from device id {} to device id {}.",
								currentDevice.getId(), read.getId());
						currentDevice = read;
						// switched to other device
						// check if last query data for new device is present
						boolean lastQueryPresent = false;
						if (allDevices != null) {
							RaspberryDeviceBean deviceBean = allDevices
									.get(currentDevice.getId());
							if (deviceBean != null) {
								if (deviceBean.getLastQueryData() != null
										&& deviceBean.getLastQueryData()
												.getException() == null) {
									currentDevice.setLastQueryData(deviceBean
											.getLastQueryData());
									updateQueryDataInView(currentDevice
											.getLastQueryData());
									lastQueryPresent = true;
								}
							}
						}
						if (!lastQueryPresent) {
							resetView();
						}
					} else {
						// device was maybe updated
						if (currentDevice.getLastQueryData() != null) {
							final QueryBean data = currentDevice
									.getLastQueryData();
							currentDevice = read;
							currentDevice.setLastQueryData(data);
						} else {
							currentDevice = read;
						}
					}
				}
				if (currentDevice != null) {
					currentDevice.setSpinnerPosition(itemPosition);
				}
				// refresh options menu
				supportInvalidateOptionsMenu();
				// if current device == null (if only device was deleted), start
				// new
				// raspi activity
				if (currentDevice == null) {
					Toast.makeText(MainActivity.this,
							R.string.please_add_a_raspberry_pi,
							Toast.LENGTH_LONG).show();
					startActivity(newRaspiIntent);
				}
			}
		}.execute(itemId);
		return true;
	}

	@Override
	public void onRefresh(PullToRefreshBase<ScrollView> refreshView) {
		LOGGER.trace("Query initiated by PullToRefresh.");
		this.doQuery(true);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// saving query data of current device
		if (currentDevice != null) {
			LOGGER.debug("Saving instance state (current device)");
			outState.putSerializable(CURRENT_DEVICE, currentDevice);
			if (allDevices == null) {
				LOGGER.debug("Saving new instance of all devices.");
				allDevices = new SparseArray<RaspberryDeviceBean>();
				allDevices.put(currentDevice.getId(), currentDevice);
			} else {
				LOGGER.debug("Adding current device to all devices.");
				allDevices.put(currentDevice.getId(), currentDevice);
			}
		}
		if (allDevices != null) {
			outState.putSparseParcelableArray(ALL_DEVICES, allDevices);
		}
		outState.putString("bug:fix", "no empty outstate");
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		if (savedInstanceState.getSerializable(CURRENT_DEVICE) != null) {
			LOGGER.debug("Restoring device..");
			currentDevice = (RaspberryDeviceBean) savedInstanceState
					.getSerializable(CURRENT_DEVICE);
			// restoring tables
			LOGGER.debug("Setting spinner to show last Pi.");
			this.getSupportActionBar().setNavigationMode(
					ActionBar.NAVIGATION_MODE_LIST);
			this.getSupportActionBar().setSelectedNavigationItem(
					currentDevice.getSpinnerPosition());
			if (currentDevice.getLastQueryData() != null
					&& currentDevice.getLastQueryData().getException() == null) {
				LOGGER.debug("Restoring query data..");
				this.updateQueryDataInView(currentDevice.getLastQueryData());
			} else {
				LOGGER.debug("No last query data present.");
				this.resetView();
			}
		}
		if (savedInstanceState.getSparseParcelableArray(ALL_DEVICES) != null) {
			LOGGER.debug("Restoring all devices.");
			allDevices = savedInstanceState
					.getSparseParcelableArray(ALL_DEVICES);
		}
	}

	@Override
	public void onHaltClick(DialogInterface dialog) {
		LOGGER.trace("ShutdownDialog: Halt chosen.");
		this.doRebootOrHalt(Constants.TYPE_HALT);
	}

	@Override
	public void onRebootClick(DialogInterface dialog) {
		LOGGER.trace("ShutdownDialog: Reboot chosen.");
		this.doRebootOrHalt(Constants.TYPE_REBOOT);
	}

	@Override
	public void onPassphraseOKClick(DialogFragment dialog, String passphrase,
			boolean savePassphrase, String type) {
		if (savePassphrase) {
			// save passphrase in db
			LOGGER.debug("Saving passphrase for device {}.",
					currentDevice.getName());
			currentDevice.setKeyfilePass(passphrase);
			new Thread() {
				@Override
				public void run() {
					deviceDb.update(currentDevice);
				}
			}.start();
		}
		if (type.equals(PassphraseDialog.SSH_QUERY)) {
			// connect
			final Boolean hideRoot = Boolean.valueOf(sharedPrefs.getBoolean(
					SettingsActivity.KEY_PREF_QUERY_HIDE_ROOT_PROCESSES, true));
			new SSHQueryTask(this, getLoadAveragePreference()).execute(
					currentDevice.getHost(), currentDevice.getUser(), null,
					currentDevice.getPort() + "", hideRoot.toString(),
					currentDevice.getKeyfilePath(), passphrase);
		} else if (type.equals(PassphraseDialog.SSH_SHUTDOWN)) {
			new SSHShutdownTask(this).execute(currentDevice.getHost(),
					currentDevice.getUser(), null,
					currentDevice.getPort() + "", currentDevice.getSudoPass(),
					Constants.TYPE_REBOOT, currentDevice.getKeyfilePath(),
					passphrase);
		} else if (type.equals(PassphraseDialog.SSH_HALT)) {
			new SSHShutdownTask(this).execute(currentDevice.getHost(),
					currentDevice.getUser(), null,
					currentDevice.getPort() + "", currentDevice.getSudoPass(),
					Constants.TYPE_HALT, currentDevice.getKeyfilePath(),
					passphrase);
		}
	}

	@Override
	public void onPassphraseCancelClick() {
		// hide and reset progress bar
		progressBar.setVisibility(View.GONE);
		progressBar.setProgress(0);
		// update and reset pullToRefresh
		refreshableScrollView.onRefreshComplete();
		refreshableScrollView.setMode(Mode.PULL_FROM_START);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		switch (requestCode) {
		case NewRaspiActivity.REQUEST_SAVE:
			initSpinner();
			break;
		case EditRaspiActivity.REQUEST_EDIT:
			initSpinner();
			break;
		default:
			break;
		}
	}

	/**
	 * Gets called when Command Button is clicked. Starts activity for custom
	 * Commands.
	 * 
	 * @param view
	 */
	public void onCommandButtonClick(View view) {
		switch (view.getId()) {
		case R.id.commandButton:
			Bundle currPi = new Bundle();
			currPi.putSerializable("pi", currentDevice);
			commandIntent.putExtras(currPi);
			this.startActivity(commandIntent);
			break;
		default:
			break;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (deviceDb != null) {
			deviceDb.close();
		}
	}

	@Override
	public void onQueryFinished(QueryBean result) {
		currentDevice.setLastQueryData(result);
		// hide and reset progress bar
		progressBar.setVisibility(View.GONE);
		progressBar.setProgress(0);
		// update and reset pullToRefresh
		refreshableScrollView.onRefreshComplete();
		refreshableScrollView.setMode(Mode.PULL_FROM_START);
		if (result.getException() == null) {
			// update view data
			this.updateQueryDataInView(result);
			// update entry in allDevices-Map
			if (allDevices != null) {
				allDevices.put(this.currentDevice.getId(), this.currentDevice);
			} else {
				allDevices = new SparseArray<RaspberryDeviceBean>();
				allDevices.put(this.currentDevice.getId(), this.currentDevice);
			}
		} else {
			this.handleQueryException(result.getException());
		}

	}

	@Override
	public void onQueryProgress(int progress) {
		progressBar.setProgress(progress);
	}

	@Override
	public void onShutdownFinished(ShutdownResult result) {
		if (result.getType().equals(Constants.TYPE_REBOOT)) {
			if (result.getExcpetion() == null) {
				Toast.makeText(this, R.string.reboot_success, Toast.LENGTH_LONG)
						.show();
			} else {
				Toast.makeText(this, R.string.reboot_fail, Toast.LENGTH_LONG)
						.show();
			}
		} else if (result.getType().equals(Constants.TYPE_HALT)) {
			if (result.getExcpetion() == null) {
				Toast.makeText(this, R.string.halt_success, Toast.LENGTH_LONG)
						.show();
			} else {
				Toast.makeText(this, R.string.halt_fail, Toast.LENGTH_LONG)
						.show();
			}
		}

	}
}
