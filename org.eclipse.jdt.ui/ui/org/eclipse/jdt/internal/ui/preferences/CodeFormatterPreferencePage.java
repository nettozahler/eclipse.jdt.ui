/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.preferences;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.jface.preference.PreferencePage;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;

/*
 * The page to configure the code formatter options.
 */
public class CodeFormatterPreferencePage extends PreferencePage implements IWorkbenchPreferencePage, IStatusChangeListener {

	private CodeFormatterConfigurationBlock fConfigurationBlock;

	public CodeFormatterPreferencePage() {
		setPreferenceStore(JavaPlugin.getDefault().getPreferenceStore());
		setDescription(PreferencesMessages.getString("CodeFormatterPreferencePage.description")); //$NON-NLS-1$
		
		// only used when page is shown programatically
		setTitle(PreferencesMessages.getString("CodeFormatterPreferencePage.title"));		
		
		fConfigurationBlock= new CodeFormatterConfigurationBlock(this, null);
	}
		
	/*
	 * @see IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */	
	public void init(IWorkbench workbench) {
	}

	/*
	 * @see PreferencePage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		super.createControl(parent);
		WorkbenchHelp.setHelp(getControl(), IJavaHelpContextIds.CODEFORMATTER_PREFERENCE_PAGE);
	}	

	/*
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		return fConfigurationBlock.createContents(parent);
	}

	/*
	 * @see IPreferencePage#performOk()
	 */
	public boolean performOk() {
		if (!fConfigurationBlock.performOk(true)) {
			return false;
		}	
		return super.performOk();
	}
	
	/*
	 * @see PreferencePage#performDefaults()
	 */
	protected void performDefaults() {
		fConfigurationBlock.performDefaults();
		super.performDefaults();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener#statusChanged(org.eclipse.core.runtime.IStatus)
	 */
	public void statusChanged(IStatus status) {
		setValid(!status.matches(IStatus.ERROR));
		StatusUtil.applyToStatusLine(this, status);		
	}

}



