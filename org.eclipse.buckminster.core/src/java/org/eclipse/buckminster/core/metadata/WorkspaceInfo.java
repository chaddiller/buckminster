/*****************************************************************************
 * Copyright (c) 2006-2007, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.core.metadata;

import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;

import org.eclipse.buckminster.core.CorePlugin;
import org.eclipse.buckminster.core.cspec.model.CSpec;
import org.eclipse.buckminster.core.cspec.model.ComponentIdentifier;
import org.eclipse.buckminster.core.cspec.model.ComponentName;
import org.eclipse.buckminster.core.cspec.model.ComponentRequest;
import org.eclipse.buckminster.core.internal.version.VersionDesignator;
import org.eclipse.buckminster.core.metadata.model.Materialization;
import org.eclipse.buckminster.core.metadata.model.Resolution;
import org.eclipse.buckminster.core.query.builder.AdvisorNodeBuilder;
import org.eclipse.buckminster.core.query.builder.ComponentQueryBuilder;
import org.eclipse.buckminster.core.resolver.IResolver;
import org.eclipse.buckminster.core.resolver.MainResolver;
import org.eclipse.buckminster.core.resolver.ResolutionContext;
import org.eclipse.buckminster.core.version.IVersion;
import org.eclipse.buckminster.core.version.IVersionDesignator;
import org.eclipse.buckminster.runtime.MonitorUtils;
import org.eclipse.buckminster.runtime.Trivial;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;

/**
 * @author Thomas Hallgren
 */
public class WorkspaceInfo
{
	/**
	 * Qualified name of the project persistent property where the {@link UUID} of the component is stored.<br/> This
	 * property will be set on IResource elements that represent component roots such as projects or resources that is
	 * inner bindings in projects.
	 */
	public static final QualifiedName PPKEY_COMPONENT_ID = new QualifiedName(CorePlugin.CORE_NAMESPACE, "componentID");

	/**
	 * Qualified name of the project persistent property where the a boolean value that indicates if the CSpec has been
	 * generated from other artifacts is stored.<br/> This property will be set on IResource elements that represent
	 * component roots such as projects or resources that is inner bindings in projects.
	 */
	public static final QualifiedName PPKEY_GENERATED_CSPEC = new QualifiedName(CorePlugin.CORE_NAMESPACE, "generatedCSpec");

	private static final HashMap<ComponentIdentifier,IPath> s_locationCache = new HashMap<ComponentIdentifier, IPath>();

	private static final IResource[] s_noResources = new IResource[0];

	public static void clearCachedLocation(CSpec cspec)
	{
		synchronized(s_locationCache)
		{
			s_locationCache.remove(cspec);
		}
	}

	public static void clearPersistentPropertyOnAll() throws CoreException
	{
		ResourcesPlugin.getWorkspace().getRoot().accept(new IResourceVisitor()
		{
			public boolean visit(IResource resource) throws CoreException
			{
				if((resource instanceof IProject) && !((IProject)resource).isOpen())
					return false;

				String cidStr = resource.getPersistentProperty(PPKEY_COMPONENT_ID);
				if(cidStr != null)
					resource.setPersistentProperty(PPKEY_COMPONENT_ID, null);
				return true;
			}
		});
	}

	public static void forceRefreshOnAll(IProgressMonitor monitor)
	{
		MultiStatus status = new MultiStatus(CorePlugin.getID(), IStatus.OK, "Problems during metadata refresh",null);
		try
		{
			clearPersistentPropertyOnAll();
			MetadataSynchronizer mds = MetadataSynchronizer.getDefault();
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			monitor.beginTask(null, projects.length * 50);
			for(IProject project : projects)
			{
				monitor.subTask("Refreshing " + project.getName());
				try
				{
					mds.refreshProject(project, MonitorUtils.subMonitor(monitor, 50));
				}
				catch(CoreException e)
				{
					status.add(e.getStatus());
				}
			}
		}
		catch(CoreException e)
		{
			status.add(e.getStatus());
		}
		finally
		{
			monitor.done();
		}
		CorePlugin.logWarningsAndErrors(status);
	}

	public static Resolution[] getActiveResolutions() throws CoreException
	{
		// Add the newest version of each known resoluton
		//
		HashMap<ComponentName,TimestampedKey> resolutionKeys = new HashMap<ComponentName, TimestampedKey>();
		ISaxableStorage<Resolution> ress = StorageManager.getDefault().getResolutions();
		for(Resolution res : ress.getElements())
		{
			UUID resId = res.getId();
			ComponentIdentifier ci = res.getComponentIdentifier();
			TimestampedKey tsKey = new TimestampedKey(resId, ress.getCreationTime(resId));
			TimestampedKey prevTsKey = resolutionKeys.put(ci, tsKey);
			if(prevTsKey != null && prevTsKey.getCreationTime() > tsKey.getCreationTime())
				//
				// We just replaced a newer entry. Put it back!
				//
				resolutionKeys.put(ci, prevTsKey);
		}

		int top = resolutionKeys.size();
		Resolution[] result = new Resolution[top];
		int idx = 0;
		for(TimestampedKey tsKey : resolutionKeys.values())
			result[idx++] = ress.getElement(tsKey.getKey());
		return result;
	}

	public static ComponentIdentifier getComponentIdentifier(IResource resource)
	{
		String componentId = null;
		try
		{
			componentId = resource.getPersistentProperty(PPKEY_COMPONENT_ID);
			return componentId == null ? null : ComponentIdentifier.parse(componentId);
		}
		catch(CoreException e)
		{
			return null;
		}
	}

	/**
	 * Returns the full path of the materialization for the component
	 * denoted by the <code>componentIdentifier</code>
	 *
	 * @param componentIdentifier The identifier of the component
	 * @return The path of the location
	 * @throws MissingComponentException if the component cannot be found
	 * @throws AmbigousComponentException if more then one component is an equally good match
	 * @throws CoreException for other persistent storage related issues
	 */
	public static IPath getComponentLocation(ComponentIdentifier componentIdentifier) throws CoreException
	{
		synchronized(s_locationCache)
		{
			IPath location = s_locationCache.get(componentIdentifier);
			if(location != null)
				return location;

			Materialization mat = getMaterialization(componentIdentifier);
			if(mat == null)
			{
				Resolution resolution = getResolution(componentIdentifier);
				location = resolution.getProvider().getReaderType().getFixedLocation(resolution);
				if(location == null)
					throw new MissingComponentException(componentIdentifier.toString());
			}
			else
				location = mat.getComponentLocation();

			s_locationCache.put(componentIdentifier, location);
			return location;
		}
	}

	/**
	 * Returns the full path of the materialization for the component
	 * denoted by the <code>componentIdentifier</code>
	 *
	 * @param cspec The cspec of the component
	 * @return The path of the location
	 * @throws MissingComponentException if the component cannot be found
	 * @throws AmbigousComponentException if more then one component is an equally good match
	 * @throws CoreException for other persistent storage related issues
	 */
	public static IPath getComponentLocation(CSpec cspec) throws CoreException
	{
		return getComponentLocation(cspec.getComponentIdentifier());
	}

	public static CSpec getCSpec(IResource resource) throws CoreException
	{
		ComponentIdentifier id = getComponentIdentifier(resource);
		return id == null ? null : getResolution(id).getCSpec();
	}

	public static Materialization getMaterialization(ComponentIdentifier cid) throws CoreException
	{
		// Add all components for which we have a materialization
		//
		for(Materialization mat : StorageManager.getDefault().getMaterializations().getElements())
			if(cid.equals(mat.getComponentIdentifier()))
				return mat;
		return null;
	}

	/**
	 * Returns the optional <code>Materialization</code> for the component. Components found in the
	 * target platform will not have a materialization.
	 * @param resolution The resolution for which we want a Materialization
	 * @return The materialization or <code>null</code> if it could not be found.
	 * @throws CoreException
	 */
	public static Materialization getMaterialization(Resolution resolution) throws CoreException
	{
		return getMaterialization(resolution.getComponentIdentifier());
	}

	/**
	 * Finds the open project that corresponds to the <code>componentIdentifier</code> and return it.
	 * @param componentIdentifier
	 * @return The found project or <code>null</code> if no open project was found.
	 * @throws CoreException
	 */
	public static IProject getProject(ComponentIdentifier componentIdentifier) throws CoreException
	{
		return extractProject(getResources(componentIdentifier));
	}

	/**
	 * Finds the open project that corresponds to the <code>materialization</code> and return it.
	 * @param materialization
	 * @return The found project or <code>null</code> if no open project was found.
	 * @throws CoreException
	 */
	public static IProject getProject(Materialization materialization) throws CoreException
	{
		return extractProject(getResources(materialization));
	}

	public static Resolution getResolution(ComponentIdentifier wanted) throws CoreException
	{
		Resolution candidate = null;
		for(Resolution res : getActiveResolutions())
		{
			ComponentIdentifier cid = res.getCSpec().getComponentIdentifier();
			if(!wanted.matches(cid))
				continue;

			if(wanted.getVersion() != null)
			{
				candidate = res;
				break;
			}

			if(candidate != null)
				throw new AmbigousComponentException(wanted.toString());
			
			candidate = res;
		}

		if(candidate == null)
		{
			IVersion v = wanted.getVersion();
			IVersionDesignator vd = (v == null) ? null : VersionDesignator.explicit(v);
			try
			{
				candidate = resolveLocal(new ComponentRequest(wanted.getName(), wanted.getCategory(), vd));
			}
			catch(CoreException e)
			{
				throw new MissingComponentException(wanted.toString());				
			}
		}
		return candidate;
	}

	/**
	 * Returns the <code>CSpec</code> that best corresponds to the given <code>request</code>.
	 * @param request The component request
	 * @return The found Resolution
	 * @throws MissingComponentException if the component cannot be found
	 * @throws AmbigousComponentException if more then one component is an equally good match
	 * @throws CoreException for other persistent storage related issues
	 */
	public static Resolution getResolution(ComponentRequest request, boolean fromResolver) throws CoreException
	{
		Resolution candidate = null;
		for(Resolution res : getActiveResolutions())
		{
			ComponentIdentifier id = res.getComponentIdentifier();
			if(!request.designates(id))
				continue;

			if(candidate != null)
			{
				// Compare versions
				//
				int cmp = Trivial.compareAllowNull(id.getVersion(), candidate.getComponentIdentifier().getVersion());
				if(cmp == 0)
					throw new AmbigousComponentException(id.toString());
				if(cmp < 0)
					continue;
			}
			candidate = res;
		}

		if(candidate == null)
		{
			if(fromResolver)
				throw new MissingComponentException(request.toString());				

			try
			{
				candidate = resolveLocal(request);
			}
			catch(CoreException e)
			{
				throw new MissingComponentException(request.toString());				
			}
		}
		return candidate;
	}

	/**
	 * Obtains the resources currently bound to the given <code>componentIdentifier</code> and
	 * returns them. An empty array is returned when no resource was found.
	 * @param componentIdentifier The component to search for
	 * @return The found workspace resources.
	 * @throws CoreException
	 */
	public static IResource[] getResources(ComponentIdentifier componentIdentifier) throws CoreException
	{
		ISaxableStorage<Materialization> mats = StorageManager.getDefault().getMaterializations();
		for(Materialization mat : mats.getElements())
		{
			if(componentIdentifier.equals(mat.getComponentIdentifier()))
				return getResources(mat);
		}
		return s_noResources;
	}

	public static IResource[] getResources(ComponentRequest request) throws CoreException
	{		
		IResource[] allFound = s_noResources;
		ISaxableStorage<Materialization> mats = StorageManager.getDefault().getMaterializations();
		for(Materialization mat : mats.getElements())
		{
			if(!request.designates(mat.getComponentIdentifier()))
				continue;

			IResource[] resources = getResources(mat);
			int top = resources.length;
			if(top == 0)
				continue;

			if(allFound == s_noResources)
				allFound = resources;
			else
			{
				IResource[] concat = new IResource[allFound.length + top];
				System.arraycopy(allFound, 0, concat, 0, allFound.length);
				System.arraycopy(resources, 0, concat, allFound.length, top);
				allFound = concat;
			}
		}
		return allFound;
	}

	public static IResource[] getResources(Materialization mat) throws CoreException
	{		
		IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
		IPath location = mat.getComponentLocation();
		return location.hasTrailingSeparator()
			? wsRoot.findContainersForLocation(location)
			: wsRoot.findFilesForLocation(location);
	}

	public static Resolution resolveLocal(ComponentRequest request) throws CoreException
	{
		ComponentQueryBuilder qbld = new ComponentQueryBuilder();
		qbld.setRootRequest(request);

		// Add an advisor node that matches all queries and prohibits that we
		// do something external.
		//
		AdvisorNodeBuilder nodeBld = new AdvisorNodeBuilder();
		nodeBld.setNamePattern(Pattern.compile(".*"));
		nodeBld.setUseInstalled(true);
		nodeBld.setUseProject(true);
		nodeBld.setUseMaterialization(false); // We would have found it already
		nodeBld.setUseResolutionSchema(false);
		qbld.addAdvisorNode(nodeBld);

		IResolver main = new MainResolver(new ResolutionContext(qbld.createComponentQuery()));
		Resolution res = main.resolve(new NullProgressMonitor()).getResolution();
		if(res == null)
			throw new MissingComponentException(request.toString());
		return res;
	}

	public static void setComponentIdentifier(IResource resource, ComponentIdentifier identifier) throws CoreException
	{
		resource.setPersistentProperty(PPKEY_COMPONENT_ID, identifier.toString());
	}

	public static void validateMaterializations() throws CoreException
	{
		for(Materialization mt : StorageManager.getDefault().getMaterializations().getElements())
			if(!mt.getComponentLocation().toFile().exists())
				mt.remove();
	}

	private static IProject extractProject(IResource[] resources)
	{
		int idx = resources.length;
		while(--idx >= 0)
		{
			IResource resource = resources[idx];
			if(resource instanceof IProject)
			{
				IProject project = (IProject)resource;
				if(project.isOpen())
					return project;
			}
		}
		return null;
	}
}
