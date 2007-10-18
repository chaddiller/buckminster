/*******************************************************************************
 * Copyright (c) 2004, 2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/

package org.eclipse.buckminster.core.query.model;

import static org.eclipse.buckminster.core.XMLConstants.BM_CQUERY_NS;
import static org.eclipse.buckminster.core.XMLConstants.BM_CQUERY_PREFIX;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.buckminster.core.CorePlugin;
import org.eclipse.buckminster.core.common.model.Documentation;
import org.eclipse.buckminster.core.common.model.ExpandingProperties;
import org.eclipse.buckminster.core.common.model.SAXEmitter;
import org.eclipse.buckminster.core.cspec.model.ComponentName;
import org.eclipse.buckminster.core.cspec.model.ComponentRequest;
import org.eclipse.buckminster.core.helpers.BMProperties;
import org.eclipse.buckminster.core.metadata.model.UUIDKeyed;
import org.eclipse.buckminster.core.parser.IParser;
import org.eclipse.buckminster.core.parser.IParserFactory;
import org.eclipse.buckminster.core.rmap.model.ProviderScore;
import org.eclipse.buckminster.core.version.IVersionDesignator;
import org.eclipse.buckminster.core.version.VersionSelector;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.IOUtils;
import org.eclipse.buckminster.runtime.Trivial;
import org.eclipse.buckminster.runtime.URLUtils;
import org.eclipse.buckminster.sax.ISaxable;
import org.eclipse.buckminster.sax.ISaxableElement;
import org.eclipse.buckminster.sax.Utils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;


/**
 * @author Thomas Hallgren
 */
public class ComponentQuery extends UUIDKeyed implements ISaxable, ISaxableElement
{
	public static final String ATTR_PROPERTIES = "properties";

	public static final String ATTR_RESOURCE_MAP = "resourceMap";

	public static final String ATTR_SHORT_DESC = "shortDesc";

	public static final String ELEM_ROOT_REQUEST = "rootRequest";

	public static final String TAG = "componentQuery";

	public static final int SEQUENCE_NUMBER = 4;

	public static ComponentQuery fromStream(String systemId, InputStream stream) throws CoreException
	{
		try
		{
			IParserFactory pf = CorePlugin.getDefault().getParserFactory();
			IParser<ComponentQuery> parser = pf.getComponentQueryParser(false);
			return parser.parse(systemId, stream);
		}
		catch(Exception e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	public static ComponentQuery fromURL(URL url, IProgressMonitor monitor) throws CoreException
	{
		InputStream stream = null;
		try
		{
			stream = URLUtils.openStream(url, monitor);
			return fromStream(url.toString(), stream);
		}
		catch(IOException e)
		{
			throw BuckminsterException.wrap(e);
		}
		finally
		{
			IOUtils.close(stream);
		}		
	}

	private final List<AdvisorNode> m_advisorNodes;

	private final Documentation m_documentation;

	private final String m_shortDesc;

	private final Map<String, String> m_properties;

	private final URL m_propertiesURL;

	private final URL m_resourceMapURL;

	private final ComponentRequest m_rootRequest;

	private transient Map<String, String> m_allProperties;

	public ComponentQuery(Documentation documentation, String shortDesc, List<AdvisorNode> advisorNodes, Map<String,String> properties, URL propertiesURL, URL resourceMapURL, ComponentRequest rootRequest)
	{
		m_documentation = documentation;
		m_shortDesc = shortDesc;
		m_propertiesURL = propertiesURL;
		m_resourceMapURL = resourceMapURL;
		m_rootRequest = rootRequest;

		if(advisorNodes == null || advisorNodes.size() == 0)
			m_advisorNodes = Collections.emptyList();
		else
			m_advisorNodes = Collections.unmodifiableList(new ArrayList<AdvisorNode>(advisorNodes));
		
		if(properties == null || properties.size() == 0)
			m_properties = Collections.emptyMap();
		else
			m_properties = Collections.unmodifiableMap(new ExpandingProperties(properties));
	}

	public boolean allowCircularDependency(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? false : node.allowCircularDependency();
	}

	public List<AdvisorNode> getAdvisoryNodes()
	{
		return m_advisorNodes;
	}
	
	public List<String> getAttributes(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? Collections.<String>emptyList() : node.getAttributes();
	}

	public Map<String,String> getDeclaredProperties()
	{
		return m_properties;
	}

	public String getDefaultTag()
	{
		return TAG;
	}

	public Documentation getDocumentation()
	{
		return m_documentation;
	}

	public synchronized Map<String, String> getGlobalProperties()
	{
		if(m_allProperties != null)
			return m_allProperties;

		m_allProperties = new ExpandingProperties();
		m_allProperties.putAll(m_properties);

		if(m_propertiesURL != null)
		{
			InputStream input = null;
			try
			{
				input = new BufferedInputStream(URLUtils.openStream(m_propertiesURL, null));
				Map<String,String> urlProps = new BMProperties(input);
				if(urlProps.size() > 0)
				{
					m_allProperties = new ExpandingProperties(m_allProperties);
					m_allProperties.putAll(urlProps);
				}
			}
			catch(Exception e)
			{
				// We allow missing properties but we log it nevertheless
				//
				CorePlugin.getLogger().info("Unable to read property file '"
					+ m_propertiesURL + "' : " + e.toString());
			}
			finally
			{
				IOUtils.close(input);
			}
		}
		return m_allProperties;
	}

	public AdvisorNode getMatchingNode(ComponentName cName)
	{
		String name = cName.getName();
		for(AdvisorNode aNode : m_advisorNodes)
		{
			Pattern pattern = aNode.getNamePattern();
			if(pattern.matcher(name).find())
			{
				String matchingType = aNode.getComponentTypeID();
				if(matchingType == null || matchingType.equals(cName.getComponentTypeID()))
					return aNode;
			}
		}
		return null;
	}

	/**
	 * Primarily intended for the ResolverAdviceEditor.
	 * 
	 * @param pattern
	 * @return
	 */
	public AdvisorNode getNodeByPattern(String pattern, String componentTypeID)
	{
		for(AdvisorNode node : m_advisorNodes)
			if(node.getNamePattern().toString().equals(pattern)
			&& Trivial.equalsAllowNull(node.getComponentTypeID(), componentTypeID))
				return node;
		return null;
	}

	public URL getOverlayFolder(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? null : node.getOverlayFolder();
	}

	public URL getPropertiesURL()
	{
		return m_propertiesURL;
	}

	public ProviderScore getProviderScore(ComponentName cName, boolean mutable, boolean source)
	{
		AdvisorNode node = getMatchingNode(cName);
		if(node == null)
			return ProviderScore.GOOD;

		ProviderScore mutableScore = ProviderScore.FAIR;
		switch(node.getMutableLevel())
		{
		case REQUIRE:
			if(!mutable)
				return ProviderScore.REJECTED;
			mutableScore = ProviderScore.PREFERRED;
			break;
		case DESIRE:
			mutableScore = mutable ? ProviderScore.GOOD : ProviderScore.BAD;
			break;
		case REJECT:
			if(mutable)
				return ProviderScore.REJECTED;
			mutableScore = ProviderScore.PREFERRED;
			break;
		default:
		}

		ProviderScore sourceScore = ProviderScore.FAIR;
		switch(node.getSourceLevel())
		{
		case REQUIRE:
			if(!source)
				return ProviderScore.REJECTED;
			sourceScore = ProviderScore.PREFERRED;
			break;
		case DESIRE:
			sourceScore = source ? ProviderScore.GOOD : ProviderScore.BAD;
			break;
		case REJECT:
			if(source)
				return ProviderScore.REJECTED;
			sourceScore = ProviderScore.PREFERRED;
			break;
		default:
		}
		return ProviderScore.values()[(sourceScore.ordinal() + mutableScore.ordinal()) / 2];
	}

	public URL getResourceMapURL()
	{
		return m_resourceMapURL;
	}

	public ComponentRequest getRootRequest()
	{
		return m_rootRequest;
	}

	public VersionSelector[] getBranchTagPath(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? VersionSelector.EMPTY_PATH : node.getBranchTagPath();
	}

	public String[] getSpacePath(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? Trivial.EMPTY_STRING_ARRAY : node.getSpacePath();
	}

	public int[] getResolutionPrio(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? AdvisorNode.DEFAULT_RESOLUTION_PRIO : node.getResolutionPrio();
	}

	public long getRevision(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? -1 : node.getRevision();
	}

	public String getTagInfo()
	{
		return "Query for " + m_rootRequest;
	}

	public Date getTimestamp(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? null : node.getTimestamp();
	}

	public String getShortDesc()
	{
		return m_shortDesc;
	}

	public IVersionDesignator getVersionOverride(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? null : node.getVersionOverride();
	}

	public boolean isPersisted() throws CoreException
	{
		return false;
	}

	public boolean isPrune(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? false : node.isPrune();
	}

	public void remove() throws CoreException
	{
		throw new UnsupportedOperationException();
	}

	public void removeAdvisorNode(AdvisorNode node)
	{
		m_advisorNodes.remove(node);
	}

	public boolean skipComponent(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? false : node.skipComponent();
	}

	public void store() throws CoreException
	{
		throw new UnsupportedOperationException();
	}

	public void toSax(ContentHandler handler) throws SAXException
	{
		handler.startDocument();
		toSax(handler, BM_CQUERY_NS, BM_CQUERY_PREFIX, getDefaultTag());
		handler.endDocument();
	}

	public void toSax(ContentHandler handler, String namespace, String prefix, String localName) throws SAXException
	{
		handler.startPrefixMapping(BM_CQUERY_PREFIX, BM_CQUERY_NS);

		String qName = Utils.makeQualifiedName(prefix, localName);
		AttributesImpl attrs = new AttributesImpl();
		if(m_resourceMapURL != null)
			Utils.addAttribute(attrs, ATTR_RESOURCE_MAP, m_resourceMapURL.toString());
		if(m_propertiesURL != null)
			Utils.addAttribute(attrs, ATTR_PROPERTIES, m_propertiesURL.toString());
		if(m_shortDesc != null)
			Utils.addAttribute(attrs, ATTR_SHORT_DESC, m_shortDesc);

		handler.startElement(namespace, localName, qName, attrs);
		if(m_documentation != null)
			m_documentation.toSax(handler, BM_CQUERY_NS, BM_CQUERY_PREFIX, m_documentation.getDefaultTag());

		m_rootRequest.toSax(handler, BM_CQUERY_NS, BM_CQUERY_PREFIX, ELEM_ROOT_REQUEST);
		SAXEmitter.emitProperties(handler, m_properties, BM_CQUERY_NS, BM_CQUERY_PREFIX, true, false);

		for(AdvisorNode node : m_advisorNodes)
			node.toSax(handler, BM_CQUERY_NS, BM_CQUERY_PREFIX, node.getDefaultTag());

		handler.endElement(namespace, localName, qName);
		handler.endPrefixMapping(BM_CQUERY_PREFIX);
	}

	public boolean useExistingProject(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? true : node.useProject();
	}
	
	public boolean useInstalledComponent(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? true : node.useInstalled();
	}

	public boolean useMaterialization(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? true : node.useMaterialization();
	}

	public boolean useResolutionService(ComponentName cName)
	{
		AdvisorNode node = getMatchingNode(cName);
		return node == null ? true : node.useRemoteResolution();
	}
}
