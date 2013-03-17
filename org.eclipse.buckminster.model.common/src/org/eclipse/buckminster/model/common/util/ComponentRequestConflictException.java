/*****************************************************************************
 * Copyright (c) 2006-2013, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.model.common.util;

import org.eclipse.buckminster.model.common.ComponentRequest;
import org.eclipse.osgi.util.NLS;

/**
 * @author Thomas Hallgren
 */
public class ComponentRequestConflictException extends IllegalArgumentException {
	private static final long serialVersionUID = -1279777286044718638L;

	public ComponentRequestConflictException(ComponentRequest rq1, ComponentRequest rq2) {
		super(NLS.bind("Component request {0} is in conflict with request {1}", rq1, rq2));
	}
}
