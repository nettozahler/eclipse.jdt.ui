/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.search;

import junit.framework.Test;
import junit.framework.TestSuite;

public class SearchTest {
	public static Test suite() {
		TestSuite suite= new TestSuite("Java Search Tests"); //$NON-NLS-1$
		//suite.addTestSuite(WorkspaceScopeTest.class);
		suite.addTest(WorkspaceReferenceTest.allTests());
		return suite;
	}
}
