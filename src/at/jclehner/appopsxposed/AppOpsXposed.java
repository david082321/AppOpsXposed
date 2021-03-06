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

import static at.jclehner.appopsxposed.util.Util.log;
import android.content.res.XModuleResources;
import at.jclehner.appopsxposed.util.Res;
import at.jclehner.appopsxposed.util.Util;
import at.jclehner.appopsxposed.util.XUtils;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class AppOpsXposed implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookInitPackageResources
{
	public static final String MODULE_PACKAGE = AppOpsXposed.class.getPackage().getName();
	public static final String SETTINGS_PACKAGE = "com.android.settings";
	public static final String SETTINGS_MAIN_ACTIVITY = SETTINGS_PACKAGE + ".Settings";
	public static final String APP_OPS_FRAGMENT = "com.android.settings.applications.AppOpsSummary";
	public static final String APP_OPS_DETAILS_FRAGMENT = "com.android.settings.applications.AppOpsDetails";

	private String mModPath;

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable
	{
		Util.logger = new Util.Logger() {

			@Override
			public void log(Throwable t)
			{
				XposedBridge.log(t);
			}

			@Override
			public void log(String s)
			{
				XposedBridge.log(s);
			}
		};

		mModPath = startupParam.modulePath;
		Res.modRes = XModuleResources.createInstance(mModPath, null);
		Res.modPrefs = new XSharedPreferences(AppOpsXposed.class.getPackage().getName());
		if(!Res.modPrefs.makeWorldReadable())
			log("Failed to make preference file world-readable");

		if(Res.modPrefs.getBoolean("failsafe_mode", false))
			return;

		for(Hack hack : Hack.getAllEnabled(false))
		{
			try
			{
				hack.initZygote(startupParam);
			}
			catch(Throwable t)
			{
				log(hack.getClass().getSimpleName() + ": [!!]");
				log(t);
			}
		}
	}

	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable
	{
		if(Res.modPrefs.getBoolean("failsafe_mode", false))
			return;

		if(!ApkVariant.isSettingsPackage(resparam.packageName))
			return;

		Res.appOpsPreferenceIconWhite = resparam.res.addResource(Res.modRes, R.drawable.ic_appops_white);
		Res.appOpsPreferenceIconBlack = resparam.res.addResource(Res.modRes, R.drawable.ic_appops_black);
		Res.appOpsPreferenceIconSense = resparam.res.addResource(Res.modRes, R.drawable.ic_appops_sense6);
		Res.appOpsLauncherIcon = resparam.res.addResource(Res.modRes, R.drawable.ic_launcher2);

		final boolean useLayoutFix = SETTINGS_PACKAGE.equals(resparam.packageName) &&
				Res.modPrefs.getBoolean("use_layout_fix", true);

		for(ApkVariant variant : ApkVariant.getAllMatching(resparam.packageName))
		{
			// TODO move this to AOSP.handleInitPackageResources
			if(useLayoutFix && variant.canUseLayoutFix())
			{
				resparam.res.setReplacement("com.android.settings", "layout", "app_ops_details_item",
						Res.modRes.fwd(R.layout.app_ops_details_item));
			}

			try
			{
				variant.handleInitPackageResources(resparam);
			}
			catch(Throwable t)
			{
				log(variant.getClass().getSimpleName() + ": [!!]");
				log(t);
			}

			break;
		}

		for(Hack hack : Hack.getAllEnabled(true))
		{
			try
			{
				hack.handleInitPackageResources(resparam);
			}
			catch(Throwable t)
			{
				log(hack.getClass().getSimpleName() + ": [!!]");
				log(t);
			}
		}
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable
	{
		final boolean isSettings = ApkVariant.isSettingsPackage(lpparam);

		if(MODULE_PACKAGE.equals(lpparam.packageName))
		{
			XposedHelpers.findAndHookMethod(Util.class.getName(), lpparam.classLoader,
					"isXposedModuleEnabled", XC_MethodReplacement.returnConstant(true));
		}

		if(!XUtils.isInFailsafeMode())
		{
			for(Hack hack : Hack.getAllEnabled(true))
			{
				try
				{
					hack.handleLoadPackage(lpparam);
				}
				catch(Throwable t)
				{
					log(hack.getClass().getSimpleName() + ": [!!]");
					log(t);
				}
			}

			if(!isSettings)
				return;
		}
		else if(isSettings)
		{
			log("Running in failsafe mode");
			ApkVariant.hookIsValidFragment(lpparam);
			return;
		}

		Res.settingsRes = XModuleResources.createInstance(lpparam.appInfo.sourceDir, null);

		final String forceVariant = Res.modPrefs.getString("force_variant", "");
		if(forceVariant.length() == 0)
		{
			log("Trying variants...");

			for(ApkVariant variant : ApkVariant.getAllMatching(lpparam))
			{
				final String variantName = "  " + variant.getClass().getSimpleName();

				try
				{
					variant.handleLoadPackage(lpparam);
					log(variantName + ": [OK]");
					break;
				}
				catch(Throwable t)
				{
					log(variantName + ": [!!]");
					log(t);
				}
			}
		}
		else
		{
			log("Using forced variant: " + forceVariant);
			final Class<?> variantClazz = Class.forName("at.jclehner.appopsxposed.variants." + forceVariant.replace('.', '$'));
			((ApkVariant) variantClazz.newInstance()).handleLoadPackage(lpparam);
		}
	}
}
