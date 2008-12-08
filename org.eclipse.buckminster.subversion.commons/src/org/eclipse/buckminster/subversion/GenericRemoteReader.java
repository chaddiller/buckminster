package org.eclipse.buckminster.subversion;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.buckminster.core.CorePlugin;
import org.eclipse.buckminster.core.RMContext;
import org.eclipse.buckminster.core.helpers.FileHandle;
import org.eclipse.buckminster.core.reader.AbstractRemoteReader;
import org.eclipse.buckminster.core.reader.IReaderType;
import org.eclipse.buckminster.core.version.ProviderMatch;
import org.eclipse.buckminster.core.version.VersionMatch;
import org.eclipse.buckminster.core.version.VersionSelector;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.IOUtils;
import org.eclipse.buckminster.runtime.Logger;
import org.eclipse.buckminster.runtime.MonitorUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.svn.core.connector.SVNRevision;
import org.eclipse.team.svn.core.connector.SVNEntry.Kind;

public abstract class GenericRemoteReader<SVNENTRY extends Object> extends AbstractRemoteReader
{
	protected final ISubversionSession<SVNENTRY> m_session;

	protected final ISvnEntryHelper<SVNENTRY> m_helper;

	private final SVNENTRY[] m_topEntries;

	protected GenericRemoteReader(IReaderType readerType, ProviderMatch provider, IProgressMonitor monitor)
			throws CoreException
	{
		super(readerType, provider);
		VersionMatch vm = provider.getVersionMatch();
		VersionSelector branchOrTag = vm.getBranchOrTag();
		m_session = getSession(provider.getRepositoryURI(), branchOrTag, vm.getRevision(), vm.getTimestamp(), provider
				.getNodeQuery().getContext());
		m_helper = m_session.getSvnEntryHelper();
		m_topEntries = getTopEntries(monitor);
		if(m_topEntries.length == 0)
			throw BuckminsterException.fromMessage(NLS.bind(Messages.unable_to_find_artifacts_at_0, m_session));
	}

	abstract protected SVNENTRY[] getTopEntries(IProgressMonitor monitor) throws CoreException;

	@Override
	final public void close()
	{
		m_session.close();
	}

	@Override
	final public String toString()
	{
		return m_session.toString();
	}

	@Override
	final protected FileHandle innerGetContents(String fileName, IProgressMonitor monitor) throws CoreException,
			IOException
	{
		Logger logger = CorePlugin.getLogger();
		IPath path = Path.fromPortableString(fileName);
		String topEntry = path.segment(0);

		boolean found = false;
		for(SVNENTRY dirEntry : m_topEntries)
		{
			if(topEntry.equals(m_helper.getEntryPath(dirEntry)))
			{
				found = true;
				break;
			}
		}

		URI url = m_session.getSVNUrl(fileName);
		String key = storeInCache(fileName);
		if(!found)
			throw new FileNotFoundException(key);
		// now copying file to temporary file
		OutputStream output = null;
		File destFile = null;
		try
		{
			logger.debug(NLS.bind(Messages.reading_remote_file_0, key));
			destFile = createTempFile();
			output = new FileOutputStream(destFile);
			final SVNRevision revision = m_session.getRevision();
			fetchRemoteFile(url, revision, output, MonitorUtils.subMonitor(monitor, 10));
			IOUtils.close(output);
			if(destFile.length() == 0)
			{
				// Suspect file not found
				if(!remoteFileExists(url, revision, monitor))
				{
					logger.debug(NLS.bind(Messages.remote_file_not_found_0, key));
					throw new FileNotFoundException(url.toString());
				}
			}
			final FileHandle fh = new FileHandle(fileName, destFile, true);
			destFile = null;
			return fh;
		}
		catch(Exception e)
		{
			final Throwable rootCause = SvnExceptionHandler.getRootCause(e);
			final boolean hasParts = SvnExceptionHandler.hasParts(rootCause, Messages.exception_part_file_not_found,
					Messages.exception_part_path_not_found, Messages.exception_part_unable_to_find_repository_location);
			if(hasParts)
			{
				if(logger.isDebugEnabled())
					logger.debug(NLS.bind(Messages.remote_file_not_found_0, key));
				throw new FileNotFoundException(key);
			}
			IOException ioe = new IOException(rootCause.getMessage());
			ioe.initCause(rootCause);
			throw ioe;
		}
		finally
		{
			IOUtils.close(output);
			if(destFile != null)
				destFile.delete();
			monitor.done();
		}
	}

	@Override
	final protected void innerGetMatchingRootFiles(Pattern pattern, List<FileHandle> files, IProgressMonitor monitor)
			throws CoreException, IOException
	{
		ArrayList<String> names = null;
		for(SVNENTRY dirEntry : m_topEntries)
		{
			final String fileName = m_helper.getEntryPath(dirEntry);
			if(pattern.matcher(fileName).matches())
			{
				if(names == null)
					names = new ArrayList<String>();
				names.add(fileName);
			}
		}
		if(names == null)
			return;

		if(names.size() == 1)
			files.add(innerGetContents(names.get(0), monitor));
		else
		{
			monitor.beginTask(null, names.size() * 100);
			for(String name : names)
				files.add(innerGetContents(name, MonitorUtils.subMonitor(monitor, 100)));
			monitor.done();
		}
	}

	@Override
	final protected void innerList(List<String> files, IProgressMonitor monitor) throws CoreException
	{
		for(SVNENTRY dirEntry : m_topEntries)
		{
			String fileName = m_helper.getEntryPath(dirEntry);
			if(m_helper.getEntryKind(dirEntry) == Kind.DIR && !fileName.endsWith("/")) //$NON-NLS-1$
				fileName = fileName + '/'; //$NON-NLS-1$
			files.add(fileName);
		}
	}

	abstract protected boolean remoteFileExists(URI url, SVNRevision revision, IProgressMonitor monitor)
			throws CoreException;

	abstract protected void fetchRemoteFile(URI url, SVNRevision revision, OutputStream output,
			IProgressMonitor subMonitor) throws Exception;

	abstract protected String storeInCache(String fileName) throws CoreException;

	/**
	 * Implemented by subclasses. Used to retrieve a particular a concrete session instance.
	 * 
	 * @param repositoryURI
	 * @param branchOrTag
	 * @param revision
	 * @param timestamp
	 * @param context
	 * @return
	 * @throws CoreException
	 */
	protected abstract ISubversionSession<SVNENTRY> getSession(String repositoryURI, VersionSelector branchOrTag,
			long revision, Date timestamp, RMContext context) throws CoreException;

}
