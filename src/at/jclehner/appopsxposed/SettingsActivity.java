/*
 * AppOpsXposed - AppOps for Android 4.3+
 * Copyright (C) 2013 Joseph C. Lehner
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of  MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.jclehner.appopsxposed;


import eu.chainfire.libsuperuser.Shell.SU;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import at.jclehner.appopsxposed.util.OpsLabelHelper;
import at.jclehner.appopsxposed.util.Util;

public class SettingsActivity extends Activity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		PreferenceManager.setDefaultValues(this, R.xml.settings, false);

		if(savedInstanceState == null)
		{
			getFragmentManager().beginTransaction().replace(android.R.id.content,
					new SettingsFragment()).commit();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		if(Util.isSystemApp(this))
		{
			menu.add(R.string.uninstall)
					.setIcon(android.R.drawable.ic_menu_delete)
					.setOnMenuItemClickListener(new OnMenuItemClickListener() {

						@Override
						public boolean onMenuItemClick(MenuItem item)
						{
							final AlertDialog.Builder ab = new AlertDialog.Builder(SettingsActivity.this);
							ab.setMessage(getString(R.string.uninstall) + "? " + getString(R.string.will_reboot));
							ab.setNegativeButton(android.R.string.cancel, null);
							ab.setPositiveButton(android.R.string.ok, new OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which)
								{
									if(!SU.available())
									{
										Toast.makeText(SettingsActivity.this,
												R.string.toast_needs_root, Toast.LENGTH_SHORT).show();
									}
									else
									{
										final String[] commands = {
												"mount -o remount,rw /system",
												"rm " + LauncherActivity.SYSTEM_APK,
												"mount -o remount,ro /system",
												"sync",
												"reboot",
										};

										Toast.makeText(SettingsActivity.this, R.string.will_reboot,
												Toast.LENGTH_LONG).show();
										Util.runAsSu(commands);
									}
								}
							});

							ab.show();

							return true;
						}
					})
					.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		}

		return true;
	}

	public static class SettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener
	{
		private SharedPreferences mPrefs;

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.settings);
			setupPreferences();
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
		{
			final View v = inflater.inflate(R.layout.settings, container, false);
			v.findViewById(R.id.xposed_settings_warning).setVisibility(
					Util.isXposedModuleEnabled() ? View.GONE : View.VISIBLE);
			return v;
		}

		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			if("force_variant".equals(preference.getKey()))
			{
				final String variant = (String) newValue;
				if(variant.length() == 0)
					preference.setSummary(R.string.automatic);
				else
					preference.setSummary(variant);
			}
			else if("failsafe_mode".equals(preference.getKey()))
			{
				final boolean failsafe = (Boolean) newValue;

				if(failsafe && !mPrefs.getBoolean("show_launcher_icon", true))
				{
					final CheckBoxPreference p = (CheckBoxPreference) findPreference("show_launcher_icon");
					p.setChecked(true);
				}

				findPreference("show_launcher_icon").setEnabled(!failsafe);
				findPreference("force_variant").setEnabled(!failsafe);
				findPreference("use_layout_fix").setEnabled(!failsafe);
				findPreference("hacks").setEnabled(!failsafe);
			}
			else if("compatibility_mode".equals(preference.getKey()))
			{
				final boolean useCompatibilityMode = (Boolean) newValue;

				if(useCompatibilityMode && !mPrefs.getBoolean("show_launcher_icon", true))
				{
					final CheckBoxPreference p = (CheckBoxPreference) findPreference("show_launcher_icon");
					p.setChecked(true);

					Toast.makeText(getActivity(), R.string.must_reboot_device, Toast.LENGTH_LONG).show();
				}

				final PackageManager pm = getActivity().getPackageManager();
				pm.setComponentEnabledSetting(new ComponentName(getActivity(), "at.jclehner.appopsxposed.LauncherActivity$HtcActivity"),
						useCompatibilityMode ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
						PackageManager.DONT_KILL_APP);

				pm.setComponentEnabledSetting(new ComponentName(getActivity(), "at.jclehner.appopsxposed.LauncherActivity$HtcFragment"),
						useCompatibilityMode ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
						PackageManager.DONT_KILL_APP);
			}
			else if("show_launcher_icon".equals(preference.getKey()))
			{
				if(!Util.isXposedModuleEnabled() && !(Boolean) newValue)
					return false;

				final boolean show = (Boolean) newValue;
				final PackageManager pm = getActivity().getPackageManager();
				pm.setComponentEnabledSetting(new ComponentName(getActivity(), "at.jclehner.appopsxposed.LauncherActivity-Icon"),
						show ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
						PackageManager.DONT_KILL_APP);
			}

			return true;
		}

		private void setupPreferences()
		{
			final ListPreference lp = (ListPreference) findPreference("force_variant");
			callOnChangeListenerWithCurrentValue(lp);

			final CharSequence[] entries = lp.getEntries();
			final CharSequence[] values = new CharSequence[entries.length];
			System.arraycopy(entries, 0, values, 0, entries.length);
			values[0] = "";
			lp.setEntryValues(values);
			lp.setOnPreferenceChangeListener(this);

			Preference p = findPreference("failsafe_mode");
			callOnChangeListenerWithCurrentValue(p);
			p.setOnPreferenceChangeListener(this);


			findPreference("show_launcher_icon").setOnPreferenceChangeListener(this);

			p = findPreference("use_hack_boot_completed");
			p.setSummary(getString(R.string.use_hack_boot_completed_summary,
					OpsLabelHelper.getOpLabel(getActivity(), "OP_POST_NOTIFICATION"),
					OpsLabelHelper.getOpLabel(getActivity(), "OP_VIBRATE")));

			p = findPreference("use_hack_wake_lock");
			p.setSummary(getString(R.string.use_hack_wake_lock_summary,
					OpsLabelHelper.getOpLabel(getActivity(), "OP_WAKE_LOCK")));

			p = findPreference("use_hack_pm_crash");
			if(p != null)
			{
				p.setSummary(getString(R.string.use_hack_pm_crash_summary,
						getString(R.string.app_ops_settings)));
			}

			p = findPreference("hacks");
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					if(mPrefs.getBoolean("show_hacks_warning_dialog", true))
						showHacksWarningDialog(preference);

					return true;
				}
			});

			p = findPreference("version");
			p.setTitle("AppOpsXposed " + Util.getAoxVersion(getActivity()));
			p.setSummary("Copyright (C) Joseph C. Lehner 2013, 2014\n"
					+ "<joseph.c.lehner@gmail.com> / caspase @XDA");

			if(BuildConfig.DEBUG)
			{
				p.setOnPreferenceClickListener(new OnPreferenceClickListener() {

					@Override
					public boolean onPreferenceClick(Preference preference)
					{
						final Intent intent = new Intent(getActivity(), AppOpsActivity.class);
						startActivity(intent);

						return true;
					}
				});

				p.setEnabled(true);
			}

			p = findPreference("build_bugreport");
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					BugReportBuilder.buildAndSend(getActivity());
					return true;
				}
			});
		}

		private void callOnChangeListenerWithCurrentValue(Preference p)
		{
			final Object value;
			if(p instanceof CheckBoxPreference)
				value = mPrefs.getBoolean(p.getKey(), false);
			else
				value = mPrefs.getString(p.getKey(), "");

			onPreferenceChange(p, value);
		}

		private void showHacksWarningDialog(final Preference pref)
		{
			final AlertDialog.Builder ab = new AlertDialog.Builder(getActivity());
			ab.setCancelable(false);
			ab.setIcon(android.R.drawable.ic_dialog_alert);
			ab.setTitle(R.string.hacks_dialog_title);
			ab.setPositiveButton(android.R.string.ok, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					mPrefs.edit().putBoolean("show_hacks_warning_dialog", false).apply();
				}
			});
			ab.setNegativeButton(android.R.string.cancel, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					((PreferenceScreen) pref).getDialog().dismiss();
				}
			});

			final AlertDialog dialog = ab.create();
			dialog.setMessage(Html.fromHtml(getString(R.string.hacks_dialog_message)));
			dialog.setOnShowListener(new OnShowListener() {

				@Override
				public void onShow(DialogInterface dialogInterface)
				{
					final Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
					b.setEnabled(false);
					final CharSequence origText = b.getText();

					new Thread() {
						public void run()
						{
							for(int i = 9; i > 0; --i)
							{
								final String tempText =
										origText + " (" + i + ")";

								b.post(new Runnable() {

									@Override
									public void run() {
										b.setText(tempText);
									}
								});

								try
								{
									Thread.sleep(1000);
								}
								catch (InterruptedException e)
								{
									// ignore
								}
							}

							b.post(new Runnable() {

								@Override
								public void run() {
									b.setText(origText);
									b.setEnabled(true);
								}
							});
						}
					}.start();
				}
			});

			dialog.show();
		}
	}
}





