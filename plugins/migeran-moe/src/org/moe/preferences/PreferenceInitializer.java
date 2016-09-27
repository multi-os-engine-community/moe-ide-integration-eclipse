package org.moe.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import org.moe.MOEPlugin;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	public void initializeDefaultPreferences() {
		IPreferenceStore store = MOEPlugin.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.GRADLE_RUN_MODE_KEY, PreferenceConstants.GRADLE_DEFAULT_VALUE);
	}

}
