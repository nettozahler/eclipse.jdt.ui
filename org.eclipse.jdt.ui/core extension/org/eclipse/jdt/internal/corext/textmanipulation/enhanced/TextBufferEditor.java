/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.textmanipulation.enhanced;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jdt.internal.corext.Assert;

/**
 * A <code>TextBufferEditor</code> manages a set of <code>TextEdit</code>s and applies
 * them as a whole to a <code>TextBuffer</code>. Added <code>TextEdit</code>s must 
 * not overlap. The only exception from this rule are insertion point. There can be more than
 * one insert point at the same text position. Clients should use the method <code>
 * canPerformEdits</code> to validate if all added text edits follow these rules.
 * <p>
 * Clients can attach more than one <code>TextBufferEditor</code> to a single <code>
 * TextBuffer</code>. If so <code>canPerformEdits</code> validates all text edits from
 * all text buffer editors working on the same text buffer.
 */
public class TextBufferEditor {
		
	private TextBuffer fBuffer;
	private MultiTextEdit fRoot;
	private UndoMemento fUndoMemento;
	private IStatus fCheckStatus;
	
	/**
	 * Creates a new <code>TextBufferEditor</code> for the given 
	 * <code>TextBuffer</code>.
	 * 
	 * @param the text buffer this editor is working on.
	 */
	public TextBufferEditor(TextBuffer buffer) {
		Assert.isNotNull(buffer);
		fBuffer= buffer;
		fCheckStatus= null;
	}
	
	/**
	 * Returns the text buffer this editor is working on.
	 * 
	 * @return the text buffer this editor is working on
	 */
	public TextBuffer getTextBuffer() {
		return fBuffer;
	}
	
	/**
	 * Adds a <code>TextEdit</code> to this text editor. Adding a <code>TextEdit</code>
	 * to a <code>TextBufferEditor</code> transfers ownership of the edit to the editor. So
	 * after a edit has been added to a editor the creator of that edit <b>must</b> not continue
	 * modifing it.
	 * 
	 * @param edit the text edit to be added
	 * @exception CoreException if the text edit can not be added
	 * 	to this text buffer editor
	 */
	public void add(TextEdit edit) throws CoreException {
		Assert.isTrue(fUndoMemento == null);
		if (fRoot == null)
			fRoot= new MultiTextEdit();
		edit.executeConnect(fBuffer);
		fRoot.setState(TextEdit.UNCONNECTED);
		fRoot.add(edit);
		fCheckStatus= null;
	}
		
	/**
	 * Adds a <code>UndoMemento</code> to this text editor. Adding a <code>UndoMemento</code>
	 * to a <code>TextBufferEditor</code> transfers ownership of the memento to the editor. So
	 * after a memento has been added to a editor the creator of that memento <b>must</b> not continue
	 * modifing it.
	 * 
	 * @param undo the undo memento to be added
	 * @exception CoreException if the undo memento can not be added
	 * 	to this text buffer editor
	 */
	public void add(UndoMemento undo) throws CoreException {
		Assert.isTrue(fRoot == null);
		fUndoMemento= undo;
		fCheckStatus= null;
	}
	
	/**
	 * Checks if the <code>TextEdit</code> added to this text editor can be executed.
	 * 
	 * @return <code>true</code> if the edits can be executed. Return  <code>false
	 * 	</code>otherwise. One major reason why text edits cannot be executed
	 * 	is a wrong offset or length value of a <code>TextEdit</code>.
	 */
	public IStatus canPerformEdits() {
		if (fCheckStatus != null)
			return fCheckStatus;
		if (fRoot != null) {
			fRoot.sortChildren();
			fRoot.setState(TextEdit.CONNECTED);
			fCheckStatus= fRoot.checkEdit(fBuffer.getLength());
		} else {
			fCheckStatus= fUndoMemento.checkEdits(fBuffer.getLength());
		}
		return fCheckStatus;
	}
	
	/**
	 * Clears the text buffer editor.
	 */
	public void clear() {
		fRoot= null;
		fUndoMemento= null;
		fCheckStatus= null;
	}
	
	/**
	 * Executes the text edits added to this text buffer editor and clears all added
	 * text edits.
	 * 
	 * @param pm a progress monitor to report progress or <code>null</code> if
	 * 	no progress is desired.
	 * @return an object representing the undo of the executed <code>TextEdit</code>s
	 * @exception CoreException if the edits cannot be executed
	 */
	public UndoMemento performEdits(IProgressMonitor pm) throws CoreException {
		IStatus s= canPerformEdits();
		if (!s.isOK())
			throw new CoreException(s);
		if (pm == null)
			pm= new NullProgressMonitor();
	
		try {
			if (fRoot != null) {
				return executeDo(pm);
			} else if (fUndoMemento != null) {
				return executeUndo(pm);
			} else {
				return new UndoMemento();
			}
		} finally {
			clear();
		}
	}
	
	//---- Helper methods ------------------------------------------------------------------------
		
	private UndoMemento executeDo(IProgressMonitor pm) throws CoreException {
		Updater updater= null;
		try {
			pm.beginTask("", 5);
			updater= Updater.createDoUpdater();
			fBuffer.registerUpdater(updater);
			fRoot.execute(fBuffer, updater, new SubProgressMonitor(pm, 4));
			List executed= updater.getProcessedEdits();
			SubProgressMonitor sm= new SubProgressMonitor(pm, 1);
			sm.beginTask("", executed.size());
			for (int i= executed.size() - 1; i >= 0; i--) {
				((TextEdit)executed.get(i)).performed();
				sm.worked(1);
			}
			return updater.undo;
		} finally {
			if (updater != null)
				fBuffer.unregisterUpdater(updater);
		}
	}
	
	private UndoMemento executeUndo(IProgressMonitor pm) throws CoreException {
		Updater updater= null;
		try {
			pm.beginTask("", 5);
			updater= Updater.createUndoUpdater();
			fBuffer.registerUpdater(updater);
			fUndoMemento.execute(fBuffer, new SubProgressMonitor(pm, 4));
			fUndoMemento.executed(new SubProgressMonitor(pm, 1));
			return updater.undo;
		} finally {
			if (updater != null)
				fBuffer.unregisterUpdater(updater);
		}
	}
}

