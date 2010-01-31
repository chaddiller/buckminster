/*******************************************************************************
 * Copyright (c) 2004, 2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/

package org.eclipse.buckminster.core.rmap.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.buckminster.core.KeyConstants;
import org.eclipse.buckminster.core.common.model.Documentation;
import org.eclipse.buckminster.core.common.model.Format;
import org.eclipse.buckminster.core.common.parser.DocumentationHandler;
import org.eclipse.buckminster.core.common.parser.FormatHandler;
import org.eclipse.buckminster.core.common.parser.PropertyManagerHandler;
import org.eclipse.buckminster.core.ctype.IComponentType;
import org.eclipse.buckminster.core.helpers.TextUtils;
import org.eclipse.buckminster.core.reader.IReaderType;
import org.eclipse.buckminster.core.rmap.model.Provider;
import org.eclipse.buckminster.core.rmap.model.SearchPath;
import org.eclipse.buckminster.core.rmap.model.URIMatcher;
import org.eclipse.buckminster.core.rmap.model.VersionConverterDesc;
import org.eclipse.buckminster.osgi.filter.Filter;
import org.eclipse.buckminster.osgi.filter.FilterFactory;
import org.eclipse.buckminster.sax.AbstractHandler;
import org.eclipse.buckminster.sax.ChildHandler;
import org.eclipse.buckminster.sax.MissingRequiredAttributeException;
import org.osgi.framework.InvalidSyntaxException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author Thomas Hallgren
 */
public class ProviderHandler extends PropertyManagerHandler
{
	public final static String TAG = Provider.TAG;

	private DocumentationHandler m_documentationHandler;

	private FormatHandler m_uriHandler;

	private DigestHandler m_digestHandler;

	private VersionConverterHandler m_versionConverterHandler;

	private URIMatcherHandler m_uriMetaDataHandler;

	private URIMatcher m_uriMatcher;

	private Documentation m_documentation;

	private String m_readerType;

	private String[] m_componentTypes;

	private Format m_uriFormat;

	private Format m_digestFormat;

	private String m_digestAlgorithm;

	private VersionConverterDesc m_versionConverter;

	private Filter m_resolutionFilter;

	private final Map<String, String> m_properties = new HashMap<String, String>();

	public ProviderHandler(AbstractHandler parent)
	{
		super(parent, Provider.TAG);
	}

	@Override
	public void childPopped(ChildHandler child) throws SAXException
	{
		if(child == m_uriHandler)
			m_uriFormat = (Format)m_uriHandler.getValueHolder();
		else if(child == m_digestHandler)
		{
			m_digestFormat = (Format)m_digestHandler.getValueHolder();
			m_digestAlgorithm = m_digestHandler.getAlgorithm();
		}
		else if(child == m_versionConverterHandler)
			m_versionConverter = m_versionConverterHandler.getVersionConverter();
		else if(child == m_documentationHandler)
			m_documentation = m_documentationHandler.createDocumentation();
		else if(child == m_uriMetaDataHandler)
		{
			try
			{
				m_uriMatcher = m_uriMetaDataHandler.createURIMetaData();
			}
			catch(Exception e)
			{
				throw new SAXParseException(e.getMessage(), getDocumentLocator(), e);
			}
		}
		else
			super.childPopped(child);
	}

	@Override
	public ChildHandler createHandler(String uri, String localName, Attributes attrs) throws SAXException
	{
		ChildHandler ch;
		if(Provider.TAG_URI.equals(localName))
		{
			if(m_uriHandler == null)
				m_uriHandler = new FormatHandler(this);
			ch = m_uriHandler;
		}
		else if(Provider.TAG_DIGEST.equals(localName))
		{
			if(m_digestHandler == null)
				m_digestHandler = new DigestHandler(this);
			ch = m_digestHandler;
		}
		else if(DocumentationHandler.TAG.equals(localName))
		{
			if(m_documentationHandler == null)
				m_documentationHandler = new DocumentationHandler(this);
			ch = m_documentationHandler;
		}
		else if(VersionConverterDesc.TAG.equals(localName))
		{
			if(m_versionConverterHandler == null)
				m_versionConverterHandler = new VersionConverterHandler(this);
			ch = m_versionConverterHandler;
		}
		else if(URIMatcher.TAG.equals(localName))
		{
			if(m_uriMetaDataHandler == null)
				m_uriMetaDataHandler = new URIMatcherHandler(this);
			ch = m_uriMetaDataHandler;
		}
		else
			ch = super.createHandler(uri, localName, attrs);
		return ch;
	}

	@Override
	public Map<String, String> getProperties()
	{
		return m_properties;
	}

	public Provider getProvider()
	{
		return new Provider(getSearchPath(), m_readerType, m_componentTypes, m_versionConverter, m_uriFormat,
				m_digestFormat, m_digestAlgorithm, m_resolutionFilter, m_properties, m_uriMatcher, m_documentation);
	}

	@Override
	public void handleAttributes(Attributes attrs) throws SAXException
	{
		m_readerType = getStringValue(attrs, Provider.ATTR_READER_TYPE);
		if(m_readerType.equals(IReaderType.ECLIPSE_IMPORT))
		{
			logAttributeValueDeprecation(getTAG(), Provider.ATTR_READER_TYPE, IReaderType.ECLIPSE_IMPORT,
					IReaderType.P2);
			m_readerType = IReaderType.P2;
		}

		m_componentTypes = null;
		String tmp = getOptionalStringValue(attrs, Provider.ATTR_COMPONENT_TYPES);
		if(tmp == null)
		{
			// legacy. Version 0.1.0 had "managedCategories". The category concept is
			// merged into the componentType in 0.2.0.
			//
			// Limit component types using managed categories
			//
			tmp = getOptionalStringValue(attrs, "componentType"); //$NON-NLS-1$
			if(tmp == null)
				throw new MissingRequiredAttributeException(getTAG(), Provider.ATTR_COMPONENT_TYPES,
						getDocumentLocator());
			logAttributeDeprecation(getTAG(), "componentType", Provider.ATTR_COMPONENT_TYPES); //$NON-NLS-1$

			boolean canManageBundle = true;
			boolean canManageFeature = true;
			String[] managedCategories = TextUtils.split(getOptionalStringValue(attrs, "managedCategories"), ","); //$NON-NLS-1$ //$NON-NLS-2$
			if(managedCategories.length > 0)
			{
				logAttributeDeprecation(getTAG(), "managedCategories", Provider.ATTR_COMPONENT_TYPES); //$NON-NLS-1$

				canManageBundle = false;
				canManageFeature = false;
				for(String category : managedCategories)
				{
					if("plugin".equals(category)) //$NON-NLS-1$
						canManageBundle = true;
					else if("feature".equals(category)) //$NON-NLS-1$
						canManageFeature = true;
				}
			}

			if(tmp.equals(IComponentType.ECLIPSE_PROJECT))
			{
				ArrayList<String> expanded = new ArrayList<String>(3);
				if(canManageBundle)
					expanded.add(IComponentType.OSGI_BUNDLE);
				if(canManageFeature)
					expanded.add(IComponentType.ECLIPSE_FEATURE);
				expanded.add(IComponentType.BUCKMINSTER);
				m_componentTypes = expanded.toArray(new String[expanded.size()]);
			}
			else if(tmp.equals(IComponentType.ECLIPSE_INSTALLED))
			{
				ArrayList<String> expanded = new ArrayList<String>(3);
				if(canManageBundle)
					expanded.add(IComponentType.OSGI_BUNDLE);
				if(canManageFeature)
					expanded.add(IComponentType.ECLIPSE_FEATURE);
				m_componentTypes = expanded.toArray(new String[expanded.size()]);
			}
		}
		else if(IComponentType.ECLIPSE_PROJECT.equals(tmp))
			tmp = IComponentType.OSGI_BUNDLE + ',' + IComponentType.ECLIPSE_FEATURE + ',' + IComponentType.BUCKMINSTER;

		if(m_componentTypes == null)
			m_componentTypes = TextUtils.split(tmp, ","); //$NON-NLS-1$

		if(!getOptionalBooleanValue(attrs, "mutable", true)) //$NON-NLS-1$
			m_properties.put(KeyConstants.IS_MUTABLE, "false"); //$NON-NLS-1$
		if(!getOptionalBooleanValue(attrs, "source", true)) //$NON-NLS-1$
			m_properties.put(KeyConstants.IS_SOURCE, "false"); //$NON-NLS-1$

		tmp = getOptionalStringValue(attrs, "space"); //$NON-NLS-1$
		if(tmp != null)
			tmp = "(buckminster.spacepath=" + tmp + ')'; //$NON-NLS-1$

		String resFilter = getOptionalStringValue(attrs, Provider.ATTR_RESOLUTION_FILTER);
		if(resFilter == null)
			resFilter = tmp;
		else if(tmp != null)
			resFilter = "(&" + resFilter + tmp + ')'; //$NON-NLS-1$

		if(resFilter != null)
		{
			try
			{
				m_resolutionFilter = FilterFactory.newInstance(resFilter);
			}
			catch(InvalidSyntaxException e)
			{
				throw new SAXParseException(e.getMessage(), getDocumentLocator(), e);
			}
		}
		else
			m_resolutionFilter = null;

		m_uriFormat = null;
		m_digestFormat = null;
		m_digestAlgorithm = null;
		m_versionConverter = null;
		m_uriMatcher = null;
		m_documentation = null;
	}

	protected final String[] getComponentTypes()
	{
		return m_componentTypes;
	}

	protected final String getDigestAlgorithm()
	{
		return m_digestAlgorithm;
	}

	protected final Format getDigestFormat()
	{
		return m_digestFormat;
	}

	protected final Documentation getDocumentation()
	{
		return m_documentation;
	}

	protected final String getReaderType()
	{
		return m_readerType;
	}

	protected final Filter getResolutionFilter()
	{
		return m_resolutionFilter;
	}

	protected SearchPath getSearchPath()
	{
		SearchPath searchPath = null;
		AbstractHandler parent = getParentHandler();
		if(parent instanceof SearchPathHandler)
			searchPath = ((SearchPathHandler)parent).getSearchPath();
		return searchPath;
	}

	protected final Format getUriFormat()
	{
		return m_uriFormat;
	}

	protected final VersionConverterDesc getVersionConverter()
	{
		return m_versionConverter;
	}
}
