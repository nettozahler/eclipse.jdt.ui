/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.code;

import java.util.List;

import org.eclipse.jdt.internal.corext.codemanipulation.ImportEdit;

public class CallContext {

	public String[] arguments;
	public String receiver; 
	public boolean receiverIsStatic;
	public List usedCallerNames;
	public int callMode;
	public ImportEdit importer;

	public CallContext(List names, int cm, ImportEdit i) {
		super();
		usedCallerNames= names;
		callMode= cm;
		importer= i;
	}
}
