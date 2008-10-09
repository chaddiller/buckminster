/**************************************************************************
* Copyright (c) 2006-2007, Cloudsmith Inc.
* The code, documentation and other materials contained herein have been
* licensed under the Eclipse Public License - v 1.0 by the copyright holder
* listed above, as the Initial Contributor under such license. The text of
* such license is available at www.eclipse.org.
***************************************************************************/
package org.eclipse.buckminster.ant.tasks;

import java.util.Map;

import org.eclipse.buckminster.core.commands.Perform;
import org.eclipse.buckminster.core.cspec.model.CSpec;
import org.eclipse.buckminster.core.cspec.model.ComponentIdentifier;
import org.eclipse.buckminster.core.metadata.WorkspaceInfo;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.core.runtime.CoreException;

/**
 * Perform a Buckminster action
 *
 * @author Thomas Hallgren
 */
public class PerformTask
{
	private final Perform m_command;

	public PerformTask(String component, String attribute, boolean inWorkspace, boolean quiet, Map<String,String> properties) throws CoreException
	{
		m_command = new Perform();

		CSpec cspec = WorkspaceInfo.getResolution(ComponentIdentifier.parse(component)).getCSpec();
		m_command.addAttribute(cspec.getRequiredAttribute(attribute));
		m_command.addProperties(properties);
		m_command.setInWorkspace(inWorkspace);
		m_command.setQuiet(true);
	}

	public int execute() throws CoreException
	{
		try
		{
			return m_command.run("perform");
		}
		catch(Exception e)
		{
			throw BuckminsterException.wrap(e);
		}
	}
}
