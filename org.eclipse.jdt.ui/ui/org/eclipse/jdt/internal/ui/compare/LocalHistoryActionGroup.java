/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.ui.compare;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.util.Assert;

import org.eclipse.ui.actions.ActionGroup;

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;

public class LocalHistoryActionGroup extends ActionGroup {

	private String fGroupName;

	private JavaHistoryEditorAction fCompareWith;
	private JavaHistoryEditorAction fReplaceWithPrevious;
	private JavaHistoryEditorAction fReplaceWith;
	private JavaHistoryEditorAction fAddFrom;
	
	public LocalHistoryActionGroup(CompilationUnitEditor editor, String groupName) {
		Assert.isNotNull(groupName);
		fGroupName= groupName;
		fCompareWith= new JavaHistoryEditorAction(editor, new JavaCompareWithEditionAction(), CompareMessages.getString("LocalHistoryActionGroup.action.compare_with")); //$NON-NLS-1$
		fReplaceWithPrevious= new JavaHistoryEditorAction(editor, new JavaReplaceWithPreviousEditionAction(), CompareMessages.getString("LocalHistoryActionGroup.action.replace_with_previous")); //$NON-NLS-1$
		fReplaceWith= new JavaHistoryEditorAction(editor, new JavaReplaceWithEditionAction(), CompareMessages.getString("LocalHistoryActionGroup.action.replace_with")); //$NON-NLS-1$
		fAddFrom= new JavaHistoryEditorAction(editor, new JavaAddElementFromHistory(), CompareMessages.getString("LocalHistoryActionGroup.action.add")); //$NON-NLS-1$
	}

	public void fillContextMenu(IMenuManager menu) {
		super.fillContextMenu(menu);
		IMenuManager localMenu= new MenuManager(CompareMessages.getString("LocalHistoryActionGroup.menu.local_history")); //$NON-NLS-1$
		int added= 0;
		added+= addAction(localMenu, fCompareWith);
		added+= addAction(localMenu, fReplaceWithPrevious);
		added+= addAction(localMenu, fReplaceWith);
		added+= addAction(localMenu, fAddFrom);
		if (added > 0) {
			menu.appendToGroup(fGroupName, localMenu);
		}
	}
	
	private int addAction(IMenuManager menu, JavaHistoryEditorAction action) {
		action.update();
		if (action.isEnabled()) {
			menu.add(action);
			return 1;
		}
		return 0;
	}
}
