/*******************************************************************************
 * Copyright (c) 2006-2007, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 ******************************************************************************/

package org.eclipse.buckminster.subversive.internal;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.eclipse.buckminster.core.CorePlugin;
import org.eclipse.buckminster.core.RMContext;
import org.eclipse.buckminster.core.helpers.TextUtils;
import org.eclipse.buckminster.core.version.VersionSelector;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.Logger;
import org.eclipse.buckminster.runtime.Trivial;
import org.eclipse.buckminster.subversive.Messages;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.svn.core.SVNTeamPlugin;
import org.eclipse.team.svn.core.connector.ISVNConnector;
import org.eclipse.team.svn.core.connector.ISVNCredentialsPrompt;
import org.eclipse.team.svn.core.connector.ISVNProgressMonitor;
import org.eclipse.team.svn.core.connector.SVNConnectorException;
import org.eclipse.team.svn.core.connector.SVNEntry;
import org.eclipse.team.svn.core.connector.SVNEntryRevisionReference;
import org.eclipse.team.svn.core.connector.SVNRevision;
import org.eclipse.team.svn.core.extension.CoreExtensionsManager;
import org.eclipse.team.svn.core.extension.options.IOptionProvider;
import org.eclipse.team.svn.core.resource.IRepositoryLocation;
import org.eclipse.team.svn.core.svnstorage.SVNRemoteStorage;
import org.eclipse.team.svn.core.svnstorage.SVNRepositoryLocation.BaseCredentialsPromptWrapper;
import org.eclipse.team.svn.core.utility.SVNUtility;

/**
 * <p>
 * The Subversive repository will be able to use reader checks if a repository contains the three recommended
 * directories <code>trunk</code>, <code>tags</code>, and <code>branches</code>. A missing <code>tags</code> directory
 * is interpreted as no <code>tags</code>. A missing <code>branches</code> directory is interpreted as no branches. In
 * order to use <code>trunk</code>, <code>tags</code>, and <code>branches</code> repository identifier must contain the
 * path element <code>trunk</code>. Anything that follows the <code>trunk</code> element in the path will be considered
 * a <code>module</code> path. If no <code>trunk</code> element is present in the path, the last element will be
 * considered the <code>module</code>
 * </p>
 * <p>
 * The repository URL may also contain a query part that in turn may have four different flags:
 * <dl>
 * <dt>moduleBeforeTag</dt>
 * <dd>When resolving a tag, put the module name between the <code>tags</code> directory and the actual tag</dd>
 * <dt>moduleAfterTag</dt>
 * <dd>When resolving a tag, append the module name after the actual tag</dd>
 * <dt>moduleBeforeBranch</dt>
 * <dd>When resolving a branch, put the module name between the <code>branches</code> directory and the actual branch</dd>
 * <dt>moduleAfterBranch</dt>
 * <dd>When resolving a branch, append the module name after the actual branch</dd>
 * </dl>
 * </p>
 * A fragment in the repository URL will be treated as a sub-module. It will be appended at the end of the resolved URL.
 * 
 * @author Thomas Hallgren
 */
public class SubversiveSession implements Closeable
{
	private static class RepositoryAccess
	{
		private final URI m_svnURL;

		private final String m_user;

		private final String m_password;

		public RepositoryAccess(String str) throws URISyntaxException
		{
			int idx = str.indexOf('^');
			String user = null;
			String passwd = null;
			if(idx >= 0)
			{
				user = str.substring(idx + 1);
				str = str.substring(0, idx);
				idx = user.indexOf('@');
				if(idx >= 0)
				{
					passwd = user.substring(idx + 1);
					user = user.substring(0, idx);
				}
			}
			m_svnURL = new URI(str);
			m_user = user;
			m_password = passwd;
		}

		public RepositoryAccess(URI svnURL, String user, String password)
		{
			m_svnURL = svnURL;
			m_user = user;
			m_password = password;
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
				return true;
			if(!(o instanceof RepositoryAccess))
				return false;
			RepositoryAccess that = (RepositoryAccess)o;
			return m_svnURL.equals(that.m_svnURL) && Trivial.equalsAllowNull(m_user, that.m_user)
					&& Trivial.equalsAllowNull(m_password, that.m_password);
		}

		public String getPassword()
		{
			return m_password;
		}

		public URI getSvnURL()
		{
			return m_svnURL;
		}

		public String getUser()
		{
			return m_user;
		}

		@Override
		public int hashCode()
		{
			int hash = m_svnURL.hashCode();
			if(m_user != null)
				hash = hash * 31 + m_user.hashCode();
			if(m_password != null)
				hash = hash * 31 + m_password.hashCode();
			return hash;
		}

		@Override
		public String toString()
		{
			if(m_user == null)
				return m_svnURL.toString();

			StringBuilder bld = new StringBuilder();
			bld.append(m_svnURL.toString());
			bld.append('^');
			bld.append(m_user);
			if(m_password != null)
			{
				bld.append('@');
				bld.append(m_password);
			}
			return bld.toString();
		}
	}

	private class UnattendedPromptUserPassword implements ISVNCredentialsPrompt
	{
		private int m_promptLimit = 3;

		public int askTrustSSLServer(Object location, String info, boolean allowPermanently)
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.askTrustSSLServer(location, info, allowPermanently);
		}

		public String getPassword()
		{
			return m_password;
		}

		public String getProxyHost()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.getProxyHost();
		}

		public String getProxyPassword()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.getProxyPassword();
		}

		public int getProxyPort()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.getProxyPort();
		}

		public String getProxyUserName()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.getProxyUserName();
		}

		public String getRealmToSave()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.getRealmToSave();
		}

		public int getSSHPort()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.getSSHPort();
		}

		public String getSSHPrivateKeyPassphrase()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.getSSHPrivateKeyPassphrase();
		}

		public String getSSHPrivateKeyPath()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.getSSHPrivateKeyPath();
		}

		public String getSSLClientCertPassword()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.getSSLClientCertPassword();
		}

		public String getSSLClientCertPath()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.getSSLClientCertPath();
		}

		public String getUsername()
		{
			return m_username;
		}

		public boolean isProxyAuthenticationEnabled()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.isProxyAuthenticationEnabled();
		}

		public boolean isProxyEnabled()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.isProxyEnabled();
		}

		public boolean isSaveCredentialsEnabled()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.isSaveCredentialsEnabled();
		}

		public boolean isSaveProxyPassword()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.isSaveProxyPassword();
		}

		public boolean isSSHPrivateKeyPassphraseSaved()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.isSSHPrivateKeyPassphraseSaved();
		}

		public boolean isSSHPublicKeySelected()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.isSSHPublicKeySelected();
		}

		public boolean isSSLAuthenticationEnabled()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.isSSLAuthenticationEnabled();
		}

		public boolean isSSLSavePassphrase()
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.isSSLSavePassphrase();
		}

		public boolean prompt(Object arg0, String arg1)
		{
			// We support the password prompt only if we actually know the password
			// and only a limited number of times
			//
			return m_password != null && --m_promptLimit >= 0;
		}

		public boolean promptProxy(Object location)
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.promptProxy(location);
		}

		public boolean promptSSH(Object location, String realm)
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.promptSSH(location, realm);
		}

		public boolean promptSSL(Object location, String realm)
		{
			return ISVNCredentialsPrompt.DEFAULT_PROMPT.promptSSL(location, realm);
		}
	}

	private static SVNEntry[] s_emptyFolder = new SVNEntry[0];

	public static SVNRevision getSVNRevision(long revision, Date timestamp)
	{
		if(revision == -1)
		{
			if(timestamp == null)
				return SVNRevision.HEAD;

			return SVNRevision.fromDate(timestamp.getTime());
		}
		if(timestamp != null)
			throw new IllegalArgumentException(Messages.svn_session_cannot_use_both_timestamp_and_revision_number);
		return SVNRevision.fromNumber(revision);
	}

	public static URI getURIParent(URI uri)
	{
		if(uri == null)
			return null;

		String path = uri.toString();
		if(path == null)
			return null;

		int lastSlash = path.lastIndexOf('/');
		if(lastSlash == path.length() - 1 && lastSlash > 0)
		{
			path = path.substring(0, path.length() - 1);
			lastSlash = path.lastIndexOf('/');
		}
		if(lastSlash < 0)
			return null;

		String parentPath = path.substring(0, lastSlash);
		try
		{
			return new URI(parentPath);
		}
		catch(URISyntaxException e)
		{
			return null;
		}
	}

	/**
	 * Create a string in the form &quot;url[revision]&quot;
	 * 
	 * @param url
	 *            The url to append
	 * @param revision
	 *            The revision to append
	 * @return A string representation denoting an explicit revision of the URL
	 */
	static String cacheKey(URI url, SVNRevision revision)
	{
		StringBuilder bld = new StringBuilder();
		String protocol = url.getScheme();
		int port = url.getPort();
		bld.append(protocol);
		bld.append("://"); //$NON-NLS-1$
		if(url.getHost() != null)
		{
			bld.append(url.getHost());
			if(port != -1)
			{
				bld.append(":"); //$NON-NLS-1$
				bld.append(port);
			}
		}

		bld.append(url.getPath());
		bld.append('#');
		bld.append(revision);
		return bld.toString();
	}

	static void createCommonRoots(RMContext context) throws CoreException
	{
		List<RepositoryAccess> unknownRoots = getUnknownRoots(context.getBindingProperties());
		if(unknownRoots.size() == 0)
			return;

		Collection<RepositoryAccess> sourceRoots = unknownRoots;
		if(unknownRoots.size() > 1)
		{
			// Get all common roots with a segment count of at least 1
			//
			for(;;)
			{
				Collection<RepositoryAccess> commonRoots = getCommonRootsStep(sourceRoots);
				if(commonRoots == sourceRoots)
					break;

				// Common roots were found. Iterate again to find commons
				// amongst the commons
				//
				sourceRoots = commonRoots;
			}
		}

		// Create the needed repositories so that Subclipse doesn't create every single
		// root for us.
		//
		SVNRemoteStorage storage = SVNRemoteStorage.instance();
		for(RepositoryAccess root : sourceRoots)
		{
			IRepositoryLocation location = storage.newRepositoryLocation();
			location.setUrl(root.getSvnURL().toString());
			location.setPassword(root.getPassword());
			location.setUsername(root.getUser());
			storage.addRepositoryLocation(location);
		}
		try
		{
			storage.saveConfiguration();
		}
		catch(Exception e)
		{
			throw BuckminsterException.wrap(e);
		}
		clearUnknownRoots(context.getBindingProperties());
	}

	private static void clearUnknownRoots(Map<String, String> properties)
	{
		synchronized(properties)
		{
			Iterator<String> keys = properties.keySet().iterator();
			while(keys.hasNext())
			{
				String key = keys.next();
				if(key.startsWith(UNKNOWN_ROOT_PREFIX))
					keys.remove();
			}
		}
	}

	private static Collection<RepositoryAccess> getCommonRootsStep(Collection<RepositoryAccess> source)
			throws CoreException
	{
		Collection<RepositoryAccess> commonRoots = null;
		for(RepositoryAccess repoAccess : source)
		{
			URI url = repoAccess.getSvnURL();
			String[] urlSegs = Path.fromPortableString(url.getPath()).segments();
			for(RepositoryAccess repoAccessCmp : source)
			{
				if(repoAccess == repoAccessCmp)
					continue;

				URI cmp = repoAccessCmp.getSvnURL();
				if(!(Trivial.equalsAllowNull(url.getHost(), cmp.getHost())
						&& Trivial.equalsAllowNull(url.getScheme(), cmp.getScheme()) && url.getPort() == cmp.getPort()))
					continue;

				String[] cmpSegs = Path.fromPortableString(cmp.getPath()).segments();
				int maxSegs = urlSegs.length;
				if(maxSegs > cmpSegs.length)
					maxSegs = cmpSegs.length;

				int idx;
				for(idx = 0; idx < maxSegs; ++idx)
					if(!urlSegs[idx].equals(cmpSegs[idx]))
						break;

				if(idx < 1)
					continue;

				String user = repoAccess.getUser();
				String cmpUser = repoAccessCmp.getUser();
				if(user == null)
					user = cmpUser;
				else
				{
					if(!(cmpUser == null || user.equals(cmpUser)))
						continue;
				}

				String password = repoAccess.getPassword();
				String cmpPassword = repoAccessCmp.getPassword();
				if(password == null)
					password = cmpPassword;
				else
				{
					if(!(cmpPassword == null || password.equals(cmpPassword)))
						continue;
				}

				StringBuilder bld = new StringBuilder();
				bld.append(url.getScheme());
				bld.append("://"); //$NON-NLS-1$
				if(url.getHost() != null)
				{
					bld.append(url.getHost());
					if(url.getPort() != -1)
					{
						bld.append(":"); //$NON-NLS-1$
						bld.append(url.getPort());
					}
				}
				for(int pdx = 0; pdx < idx; ++pdx)
				{
					String seg = urlSegs[pdx];
					bld.append('/');
					if(idx > 0 && seg.equals("trunk") || seg.equals("tags") || seg.equals("branches")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						//
						// Assume that common root is above this folder
						//
						break;

					bld.append(seg);
				}
				try
				{
					if(commonRoots == null)
						commonRoots = new HashSet<RepositoryAccess>();
					commonRoots.add(new RepositoryAccess(new URI(bld.toString()), user, password));
				}
				catch(URISyntaxException e)
				{
					throw BuckminsterException.wrap(e);
				}
			}
		}

		if(commonRoots == null)
			//
			// No common roots found
			//
			return source;

		// Add all SVNUrl's for which we don't have a common root
		//
		Set<RepositoryAccess> rogueRoots = null;
		for(RepositoryAccess repoAccess : source)
		{
			boolean found = false;
			URI url = repoAccess.getSvnURL();
			String[] urlSegs = Path.fromPortableString(url.getPath()).segments();
			for(RepositoryAccess repoAccessCmp : commonRoots)
			{
				URI cmp = repoAccessCmp.getSvnURL();
				if(!(Trivial.equalsAllowNull(url.getHost(), cmp.getHost())
						&& Trivial.equalsAllowNull(url.getScheme(), cmp.getScheme()) && url.getPort() == cmp.getPort()))
					continue;

				String[] cmpSegs = Path.fromPortableString(cmp.getPath()).segments();
				int maxSegs = cmpSegs.length;
				if(maxSegs > urlSegs.length)
					continue;

				int idx;
				for(idx = 0; idx < maxSegs; ++idx)
					if(!urlSegs[idx].equals(cmpSegs[idx]))
						break;

				if(idx < maxSegs)
					continue;

				String user = repoAccess.getUser();
				String cmpUser = repoAccessCmp.getUser();
				if(!(user == null || cmpUser == null || user.equals(cmpUser)))
					continue;

				String password = repoAccess.getPassword();
				String cmpPassword = repoAccessCmp.getPassword();
				if(!(password == null || cmpPassword == null || password.equals(cmpPassword)))
					continue;

				found = true;
				break;
			}
			if(found)
				continue;

			if(rogueRoots == null)
				rogueRoots = new HashSet<RepositoryAccess>();
			rogueRoots.add(repoAccess);
		}
		if(rogueRoots != null)
			commonRoots.addAll(rogueRoots);
		return commonRoots;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, SVNEntry[]> getListCache(Map<UUID, Object> ctxUserCache)
	{
		synchronized(ctxUserCache)
		{
			Map<String, SVNEntry[]> listCache = (Map<String, SVNEntry[]>)ctxUserCache.get(CACHE_KEY_LIST_CACHE);
			if(listCache == null)
			{
				listCache = Collections.synchronizedMap(new HashMap<String, SVNEntry[]>());
				ctxUserCache.put(CACHE_KEY_LIST_CACHE, listCache);
			}
			return listCache;
		}
	}

	private static List<RepositoryAccess> getUnknownRoots(Map<String, String> properties)
	{
		synchronized(properties)
		{
			List<RepositoryAccess> unknownRoots = null;
			for(Map.Entry<String, String> entries : properties.entrySet())
			{
				String key = entries.getKey();
				if(key.startsWith(UNKNOWN_ROOT_PREFIX))
				{
					RepositoryAccess ra;
					try
					{
						ra = new RepositoryAccess(entries.getValue());
					}
					catch(URISyntaxException e)
					{
						// Bogus entry
						continue;
					}
					if(unknownRoots == null)
						unknownRoots = new ArrayList<RepositoryAccess>();
					unknownRoots.add(ra);
				}
			}
			if(unknownRoots == null)
				unknownRoots = Collections.emptyList();
			return unknownRoots;
		}
	}

	private final VersionSelector m_branchOrTag;

	private IRepositoryLocation m_repositoryLocation;

	private final ISVNConnector m_proxy;

	private final IPath m_module;

	private final boolean m_moduleAfterBranch;

	private final boolean m_moduleAfterTag;

	private final boolean m_moduleBeforeBranch;

	private final boolean m_moduleBeforeTag;

	private final boolean m_trunkStructure;

	private final String m_password;

	private final SVNRevision m_revision;

	private final IPath m_subModule;

	private final String m_urlLeadIn;

	private final String m_username;

	private static final UUID CACHE_KEY_LIST_CACHE = UUID.randomUUID();

	private static final String UNKNOWN_ROOT_PREFIX = SubversiveSession.class.getPackage().getName() + ".root."; //$NON-NLS-1$

	private static void addUnknownRoot(Map<String, String> properties, RepositoryAccess ra)
	{
		synchronized(properties)
		{
			int maxNum = -1;
			String raStr = ra.toString();
			for(Map.Entry<String, String> entries : properties.entrySet())
			{
				String key = entries.getKey();
				if(key.startsWith(UNKNOWN_ROOT_PREFIX))
				{
					int lastDot = key.lastIndexOf('.');
					if(lastDot < 0)
						continue;

					try
					{
						int keyNum = Integer.parseInt(key.substring(lastDot + 1));
						if(maxNum < keyNum)
							maxNum = keyNum;
					}
					catch(NumberFormatException e)
					{
						continue;
					}
					if(entries.getValue().equals(raStr))
						//
						// Entry is already present. Don't recreate
						//
						return;
				}
			}
			properties.put(UNKNOWN_ROOT_PREFIX + (maxNum + 1), raStr);
		}
	}

	private final Map<String, SVNEntry[]> m_listCache;

	/**
	 * @param repositoryURI
	 *            The string representation of the URI that appoints the trunk of repository module. No branch or tag
	 *            information must be included.
	 * @param branch
	 *            The desired branch or <code>null</code> if not applicable.
	 * @param tag
	 *            The desired tag or <code>null</code> if not applicable.
	 * @param revision
	 *            The desired revision or <code>-1</code> of not applicable
	 * @param timestamp
	 *            The desired timestamp or <code>null</code> if not applicable
	 * @param context
	 *            The context used for the resolution/materialization operation
	 * @throws CoreException
	 */
	public SubversiveSession(String repositoryURI, VersionSelector branchOrTag, long revision, Date timestamp,
			RMContext context) throws CoreException
	{
		m_revision = getSVNRevision(revision, timestamp);
		m_branchOrTag = branchOrTag;

		Map<UUID, Object> userCache = context.getUserCache();
		m_listCache = getListCache(userCache);

		try
		{
			URI uri = new URI(repositoryURI);

			// Find the repository root, i.e. the point just above 'trunk'.
			//
			IPath fullPath = new Path(uri.getPath());
			String[] pathSegments = fullPath.segments();
			int idx = pathSegments.length;
			while(--idx >= 0)
				if(pathSegments[idx].equals("trunk")) //$NON-NLS-1$
					break;

			if(idx >= 0)
				m_trunkStructure = true;
			else
			{
				m_trunkStructure = false;
				idx = pathSegments.length - 1; // Last element is considered the module name
			}

			if(m_branchOrTag != null && !m_trunkStructure)
				//
				// No use continuing with this session since there's no hope finding
				// the desired branch or tag.
				//
				throw BuckminsterException.fromMessage(Messages.branch_or_tag_0_not_found, m_branchOrTag);

			int relPathLen = pathSegments.length - idx;

			StringBuilder bld = new StringBuilder();
			String scheme = uri.getScheme();
			if(scheme != null)
			{
				bld.append(scheme);
				bld.append("://"); //$NON-NLS-1$
			}

			String username = null;
			String password = null;
			String authority = uri.getAuthority();
			if(authority != null)
			{
				int atIdx = authority.indexOf('@');
				if(atIdx > 0)
				{
					String authentication = authority.substring(0, atIdx);
					authority = authority.substring(atIdx + 1);

					int upSplit = authentication.indexOf(':');
					if(upSplit > 0)
					{
						username = authentication.substring(0, upSplit);
						if("null".equals(username)) //$NON-NLS-1$
							username = null;
						password = authentication.substring(upSplit + 1);
						if("null".equals(password)) //$NON-NLS-1$
							password = null;
					}
				}
				bld.append(authority);
			}
			m_username = username;
			m_password = password;

			if(fullPath.getDevice() != null)
				bld.append('/');

			bld.append(fullPath.removeLastSegments(relPathLen));
			String urlLeadIn = bld.toString();

			// Anything after 'trunk' is considered a module path. The module
			// path
			// will be used when finding branches and tags.
			//
			IPath modulePath = null;
			if(m_trunkStructure)
			{
				if(relPathLen > 1)
				{
					modulePath = fullPath.removeFirstSegments(idx + 1);
					modulePath = modulePath.setDevice(null);
				}
			}
			else
				modulePath = Path.fromPortableString(fullPath.lastSegment());
			m_module = modulePath;

			String tmp = uri.getFragment();
			IPath subModule = null;
			if(tmp != null)
				subModule = new Path(tmp).makeRelative();
			m_subModule = subModule;

			boolean moduleBeforeTag = false;
			boolean moduleAfterTag = false;
			boolean moduleBeforeBranch = false;
			boolean moduleAfterBranch = false;
			if(m_trunkStructure)
			{
				for(String entry : TextUtils.decodeToQueryPairs(uri.getQuery()))
				{
					if(entry.equalsIgnoreCase("moduleBeforeTag")) //$NON-NLS-1$
						moduleBeforeTag = true;
					else if(entry.equalsIgnoreCase("moduleAfterTag")) //$NON-NLS-1$
						moduleAfterTag = true;
					else if(entry.equalsIgnoreCase("moduleBeforeBranch")) //$NON-NLS-1$
						moduleBeforeBranch = true;
					else if(entry.equalsIgnoreCase("moduleAfterBranch")) //$NON-NLS-1$
						moduleAfterBranch = true;
				}
			}
			m_moduleBeforeTag = moduleBeforeTag;
			m_moduleAfterTag = moduleAfterTag;
			m_moduleBeforeBranch = moduleBeforeBranch;
			m_moduleAfterBranch = moduleAfterBranch;

			// Let's see if our SVNRootUrl matches any of the known
			// repositories.
			//

			int rank = 0;
			URI ourRoot = new URI(urlLeadIn);
			IRepositoryLocation bestMatch = null;
			SVNRemoteStorage storage = SVNRemoteStorage.instance();
			for(IRepositoryLocation location : storage.getRepositoryLocations())
			{
				URI repoRoot = new URI(location.getRoot().getUrl());
				if(!Trivial.equalsAllowNull(repoRoot.getHost(), ourRoot.getHost()))
					continue;

				String repoProto = repoRoot.getScheme().toLowerCase();
				String ourProto = ourRoot.getScheme().toLowerCase();

				// We let the protocol svn or http match a repo that uses svn+ssh or https
				//
				boolean repoIsSSH = repoProto.equals("svn+ssh") || repoProto.equals("https"); //$NON-NLS-1$ //$NON-NLS-2$
				if(rank > 200 && !repoIsSSH)
					continue;

				if(!(repoProto.equals(ourProto) || (repoProto.equals("svn") && ourProto.equals("http")) //$NON-NLS-1$ //$NON-NLS-2$
						|| (repoProto.equals("http") && ourProto.equals("svn")) //$NON-NLS-1$ //$NON-NLS-2$
				|| ((ourProto.equals("svn") || ourProto.equals("http")) && repoIsSSH))) //$NON-NLS-1$ //$NON-NLS-2$
					continue;

				String[] ourPath = Path.fromPortableString(ourRoot.getPath()).segments();
				String[] repoPath = Path.fromPortableString(repoRoot.getPath()).segments();

				idx = repoPath.length;
				final int top = ourPath.length;
				if(idx > top)
					//
					// repoPath is too qualified for our needs
					//
					continue;

				while(--idx >= 0)
					if(!ourPath[idx].equals(repoPath[idx]))
						break;

				if(idx >= 0)
					//
					// repoPath is not a prefix of ourPath
					//
					continue;

				urlLeadIn = repoRoot.toString();
				int diff = top - repoPath.length;
				if(diff > 0)
				{
					int myRank = (repoIsSSH
							? 400
							: 200) - diff;
					if(rank > myRank)
						continue;

					// Append the rest of our path
					//
					bld.setLength(0);
					bld.append(urlLeadIn);
					for(idx = repoPath.length; idx < top; ++idx)
					{
						bld.append('/');
						bld.append(ourPath[idx]);
					}
					urlLeadIn = bld.toString();
				}
				rank = (repoIsSSH
						? 400
						: 200) - diff;
				bestMatch = location;
				if(rank == 400)
					break;
			}

			m_urlLeadIn = urlLeadIn;
			m_repositoryLocation = bestMatch;
			synchronized(userCache)
			{
				m_proxy = CoreExtensionsManager.instance().getSVNConnectorFactory().newInstance();
				m_proxy.setTouchUnresolved(false);
				m_proxy.setCommitMissingFiles(false);
				m_proxy.setSSLCertificateCacheEnabled(true);
				m_proxy.setUsername(m_username);
				m_proxy.setPassword(m_password);
				m_proxy.setCredentialsCacheEnabled(true);
				if(bestMatch == null)
				{
					addUnknownRoot(context.getBindingProperties(),
							new RepositoryAccess(ourRoot, m_username, m_password));
				}
				else
				{
					SVNUtility.configureProxy(m_proxy, m_repositoryLocation);
					IOptionProvider optionProvider = SVNTeamPlugin.instance().getOptionProvider();
					ISVNCredentialsPrompt externalPrompt = optionProvider.getCredentialsPrompt();
					if(externalPrompt != null)
						m_proxy.setPrompt(new BaseCredentialsPromptWrapper(externalPrompt, m_repositoryLocation));
				}
			}

			// Add the UnattendedPromptUserPassword callback only in case
			// the authentication data (at least the username) is actually
			// specified in the URL
			//
			if(m_username != null)
				m_proxy.setPrompt(new UnattendedPromptUserPassword());
		}
		catch(URISyntaxException e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	public void close()
	{
		m_proxy.dispose();
	}

	public long getLastChangeNumber() throws CoreException
	{
		try
		{
			URI svnURL = getSVNUrl(null);
			SVNEntry root = getDirEntry(svnURL, m_revision, null);
			if(root == null)
				throw new FileNotFoundException(svnURL.toString());
			return root.revision;
		}
		catch(Exception e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	public Date getLastTimestamp() throws CoreException
	{
		try
		{
			URI svnURL = getSVNUrl(null);
			SVNEntry root = getDirEntry(svnURL, m_revision, null);
			if(root == null)
				throw new FileNotFoundException(svnURL.toString());
			return new Date(root.date);
		}
		catch(Exception e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	public SVNRevision getRevision()
	{
		return m_revision;
	}

	public boolean hasTrunkStructure()
	{
		return m_trunkStructure;
	}

	@Override
	public String toString()
	{
		try
		{
			return getSVNUrl(null).toString();
		}
		catch(CoreException e)
		{
			return super.toString();
		}
	}

	SVNEntry getDirEntry(URI uri, SVNRevision revision, IProgressMonitor monitor) throws CoreException
	{
		URI parent = getURIParent(uri);
		if(parent == null)
			return null;

		String path = uri.getPath();
		String entryPath = path.substring(path.lastIndexOf('/') + 1);
		SVNEntry[] entries = listFolder(parent, monitor);
		for(SVNEntry entry : entries)
		{
			if(entryPath.equals(entry.path))
				return entry;
		}
		return null;
	}

	IRepositoryLocation getRepositoryLocation()
	{
		return m_repositoryLocation;
	}

	SVNEntry getRootEntry(IProgressMonitor monitor) throws CoreException
	{
		return getDirEntry(getSVNUrl(null), m_revision, monitor);
	}

	ISVNConnector getSVNProxy()
	{
		return m_proxy;
	}

	/**
	 * Returns the directory where it's expected to find a list of branches or tags.
	 * 
	 * @param branches
	 *            true if branches, false if tags.
	 * @return The SVNUrl appointing the branches or tags directory.
	 * @throws MalformedURLException
	 */
	URI getSVNRootUrl(boolean branches) throws CoreException
	{
		StringBuilder bld = new StringBuilder();
		bld.append(m_urlLeadIn);

		if(branches)
		{
			bld.append("/branches"); //$NON-NLS-1$
			if(m_moduleBeforeBranch && m_module != null)
			{
				bld.append('/');
				bld.append(m_module);
			}
		}
		else
		{
			bld.append("/tags"); //$NON-NLS-1$
			if(m_moduleBeforeTag && m_module != null)
			{
				bld.append('/');
				bld.append(m_module);
			}
		}
		try
		{
			return new URI(bld.toString());
		}
		catch(URISyntaxException e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	URI getSVNUrl(String fileName) throws CoreException
	{
		StringBuilder bld = new StringBuilder();
		bld.append(m_urlLeadIn);

		if(m_branchOrTag == null)
		{
			if(m_trunkStructure)
				bld.append("/trunk"); //$NON-NLS-1$

			if(m_module != null)
			{
				bld.append('/');
				bld.append(m_module);
			}
		}
		else if(m_branchOrTag.getType() == VersionSelector.BRANCH)
		{
			bld.append("/branches"); //$NON-NLS-1$
			if(m_moduleBeforeBranch && m_module != null)
			{
				bld.append('/');
				bld.append(m_module);
			}
			bld.append('/');
			bld.append(m_branchOrTag.getName());
			if(m_moduleAfterBranch && m_module != null)
			{
				bld.append('/');
				bld.append(m_module);
			}
		}
		else
		{
			bld.append("/tags"); //$NON-NLS-1$
			if(m_moduleBeforeTag && m_module != null)
			{
				bld.append('/');
				bld.append(m_module);
			}
			bld.append('/');
			bld.append(m_branchOrTag.getName());
			if(m_moduleAfterTag && m_module != null)
			{
				bld.append('/');
				bld.append(m_module);
			}
		}

		if(m_subModule != null)
		{
			bld.append('/');
			bld.append(m_subModule);
		}
		if(fileName != null)
		{
			bld.append('/');
			bld.append(fileName);
		}
		try
		{
			return new URI(bld.toString());
		}
		catch(URISyntaxException e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	SVNEntry[] listFolder(URI url, IProgressMonitor monitor) throws CoreException
	{
		// Synchronizing on an interned string should make it impossible for two
		// sessions to request the same entry from the remote server
		//
		String key = cacheKey(url, m_revision).intern();
		synchronized(key)
		{
			SVNEntry[] list = m_listCache.get(key);
			if(list != null)
				return list;

			Logger logger = CorePlugin.getLogger();
			ISVNProgressMonitor svnMon = SimpleMonitorWrapper.beginTask(monitor, 100);
			try
			{
				logger.debug(Messages.listing_remote_folder_0, key);
				list = SVNUtility.list(m_proxy, new SVNEntryRevisionReference(url.toString(), null, m_revision),
						ISVNConnector.Depth.IMMEDIATES, SVNEntry.Fields.ALL, ISVNConnector.Options.NONE, svnMon);
				if(list == null || list.length == 0)
				{
					logger.debug(Messages.remote_folder_had_no_entries_0, key);
					list = s_emptyFolder;
				}
				m_listCache.put(key, list);
				return list;
			}
			catch(SVNConnectorException e)
			{
				String msg = e.getMessage();
				if(msg != null)
				{
					msg = msg.toLowerCase();
					if(msg.contains(Messages.exception_part_non_existent)
							|| msg.contains(Messages.exception_part_not_found))
					{
						logger.debug(Messages.remote_folder_does_not_exist_0, key);
						return s_emptyFolder;
					}
				}
				throw BuckminsterException.wrap(e);
			}
			finally
			{
				monitor.done();
			}
		}
	}
}
