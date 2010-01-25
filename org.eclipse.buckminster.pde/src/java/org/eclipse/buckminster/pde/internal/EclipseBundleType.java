/*******************************************************************************
 * Copyright (c) 2006-2007, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 ******************************************************************************/

package org.eclipse.buckminster.pde.internal;

import org.eclipse.buckminster.core.CorePlugin;
import org.eclipse.buckminster.core.cspec.builder.CSpecBuilder;
import org.eclipse.buckminster.core.ctype.AbstractComponentType;
import org.eclipse.buckminster.core.ctype.IResolutionBuilder;
import org.eclipse.buckminster.core.metadata.model.BOMNode;
import org.eclipse.buckminster.core.reader.IComponentReader;
import org.eclipse.buckminster.core.reader.IReaderType;
import org.eclipse.buckminster.core.version.ProviderMatch;
import org.eclipse.buckminster.pde.cspecgen.PDEBuilder;
import org.eclipse.buckminster.runtime.MonitorUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;

/**
 * @author Thomas Hallgren
 */
public class EclipseBundleType extends AbstractComponentType
{
	public IResolutionBuilder getResolutionBuilder(IComponentReader reader, IProgressMonitor monitor)
			throws CoreException
	{
		MonitorUtils.complete(monitor);
		return CorePlugin.getDefault().getResolutionBuilder(IResolutionBuilder.PLUGIN2CSPEC);
	}

	@Override
	protected BOMNode getResolution(ProviderMatch rInfo, boolean forResolutionAidOnly, IProgressMonitor monitor)
			throws CoreException
	{
		IReaderType readerType = rInfo.getReaderType();
		if(readerType instanceof EclipseImportReaderType)
		{
			EclipseImportReaderType eiReaderType = (EclipseImportReaderType)readerType;
			IMetadataRepository mdr = eiReaderType.getCachedMDR(rInfo);
			if(mdr != null)
			{
				IInstallableUnit iu = eiReaderType.getCachedInstallableUnit(mdr, rInfo);
				if(iu != null)
					return PDEBuilder.createNode(rInfo, new CSpecBuilder(mdr, iu), null);
			}
		}
		return super.getResolution(rInfo, forResolutionAidOnly, monitor);
	}
}
