/*******************************************************************************
 * Copyright (c) 2008
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed below, as Initial Contributors under such license.
 * The text of such license is available at 
 * http://www.eclipse.org/legal/epl-v10.html.
 * 
 * Contributors:
 * 		Henrik Lindberg
 *******************************************************************************/

package org.eclipse.equinox.p2.authoring.internal;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.equinox.internal.p2.metadata.ArtifactKey;
import org.eclipse.equinox.internal.p2.metadata.InstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.Copyright;
import org.eclipse.equinox.internal.provisional.p2.metadata.IArtifactKey;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.IUpdateDescriptor;
import org.eclipse.equinox.internal.provisional.p2.metadata.License;
import org.eclipse.equinox.internal.provisional.p2.metadata.MetadataFactory;
import org.eclipse.equinox.internal.provisional.p2.metadata.ProvidedCapability;
import org.eclipse.equinox.internal.provisional.p2.metadata.RequiredCapability;
import org.eclipse.equinox.internal.provisional.p2.metadata.TouchpointData;
import org.eclipse.equinox.internal.provisional.p2.metadata.TouchpointType;
import org.eclipse.osgi.service.resolver.VersionRange;
import org.osgi.framework.Version;

/**
 * A mutable version of InstallableUnit and its subclasses used for authoring.
 * 
 * @author Henrik Lindberg
 * 
 */
@SuppressWarnings("restriction")
public class InstallableUnitBuilder extends ModelRoot
{
	public static class ArtifactKeyBuilder extends ModelPart
	{
		private String m_classifier;

		private String m_id;

		private Version m_version;

		public ArtifactKeyBuilder(IArtifactKey key)
		{
			m_classifier = key.getClassifier();
			m_id = key.getId();
			m_version = key.getVersion();
			
		}

		public IArtifactKey createArtifactKey()
		{
			return new ArtifactKey(m_classifier, m_id, m_version);
		}

		public String getClassifier()
		{
			return m_classifier;
		}

		public String getId()
		{
			return m_id;
		}

		public Version getVersion()
		{
			return m_version;
		}
		public void setClassifier(String classifier)
		{
			m_classifier = classifier;
			notifyChanged();
		}
		public void setId(String id)
		{
			m_id = id;
			notifyChanged();
		}

		public void setVersion(Version version)
		{
			m_version = version;
			notifyChanged();
		}
	}
	public static class IUInfoBuilder extends ModelPart
	{
		protected String m_body;
		protected String m_url;

		public IUInfoBuilder()
		{
			m_body = ""; //$NON-NLS-1$
			m_url = ""; //$NON-NLS-1$
		}
		public IUInfoBuilder(String url, String body)
		{
			m_url = url;
			m_body = body;
		}

		public String getBody()
		{
			return m_body;
		}

		public String getUrl()
		{
			return m_url;
		}

		public void setBody(String body)
		{
			m_body = body;
			notifyChanged();
		}
		public void setUrl(String url)
		{
			m_url = url;
			notifyChanged();
		}

	}

	public static class CopyrightBuilder extends IUInfoBuilder
	{
		public CopyrightBuilder(Copyright copyright)
		{
			this(copyright.getURL() != null ? copyright.getURL().toString() : "", copyright.getBody()); //$NON-NLS-1$
		}
		public CopyrightBuilder(String url, String body)
		{
			super(url, body);
		}
		public CopyrightBuilder()
		{
			super();
		}
		public Copyright createCopyright()
		{
			return new Copyright(m_url, m_body);
		}

	}
	public static class LicenseBuilder extends IUInfoBuilder
	{

		public LicenseBuilder(License license)
		{
			m_url = license.getURL() != null ? license.getURL().toString() : ""; //$NON-NLS-1$
			m_body = license.getBody();
		}
		public LicenseBuilder()
		{
			super();
		}
		public LicenseBuilder(String url, String body)
		{
			super(url, body);
		}
		public License createLicense()
		{
			return new License(m_url, m_body);
		}
	}
	public static class ProvidedCapabilityBuilder extends ModelPart
	{
		private String m_name;
		private String m_namespace;
		private String m_version;

		public ProvidedCapabilityBuilder(ProvidedCapability capability)
		{
			m_namespace = capability.getNamespace();
			m_name = capability.getName();
			m_version = capability.getVersion().toString();
		}
		public ProvidedCapabilityBuilder(String namespace, String name, String version)
		{
			m_name = name;
			m_namespace = namespace;
			m_version = version;
		}
		public ProvidedCapability createProvidedCapability()
		{
			return MetadataFactory.createProvidedCapability(m_namespace, m_name, new Version(m_version));
		}
		public String getName()
		{
			return m_name;
		}
		public String getNamespace()
		{
			return m_namespace;
		}
		public String getVersion()
		{
			return m_version;
		}
		
		public void setName(String name)
		{
			m_name = name;
			notifyChanged();
		}
		public void setNamespace(String namespace)
		{
			m_namespace = namespace;
			notifyChanged();
		}
		public void setVersion(String version)
		{
			m_version = version;
			notifyChanged();
		}
	}
	public static class RequiredCapabilityBuilder extends ModelPart
	{
		private String m_capfilter;
		private boolean m_greedy;
		private boolean m_multiple;
		private String m_name;
		private String m_namespace;
		private boolean m_optional;
		private String m_range;
		public RequiredCapabilityBuilder(RequiredCapability capability)
		{
			m_capfilter = capability.getFilter();
			m_name = capability.getName();
			m_namespace = capability.getNamespace();
			m_range = capability.getRange().toString();
			m_greedy = capability.isGreedy();
			m_multiple = capability.isMultiple();
			m_optional = capability.isOptional();
		}
		public RequiredCapabilityBuilder(String filter, String name, String namespace, String range, boolean greedy, boolean multiple, boolean optional)
		{
			m_capfilter = filter;
			m_name = name;
			m_namespace = namespace;
			m_range = range;
			m_greedy = greedy;
			m_multiple = multiple;
			m_optional = optional;
		}
		public RequiredCapability createRequiredCapability()
		{
			return MetadataFactory.createRequiredCapability(m_namespace, m_name, new VersionRange(m_range), m_capfilter, m_optional, m_multiple, m_greedy);
		}
		public String getCapfilter()
		{
			return m_capfilter;
		}
		public String getName()
		{
			return m_name;
		}
		public String getNamespace()
		{
			return m_namespace;
		}
		public String getRange()
		{
			return m_range;
		}
		public boolean isGreedy()
		{
			return m_greedy;
		}
		public boolean isMultiple()
		{
			return m_multiple;
		}
		public boolean isOptional()
		{
			return m_optional;
		}
		public void setCapfilter(String capfilter)
		{
			m_capfilter = capfilter;
			notifyChanged();
		}
		public void setGreedy(boolean greedy)
		{
			m_greedy = greedy;
			notifyChanged();
		}
		public void setMultiple(boolean multiple)
		{
			m_multiple = multiple;
			notifyChanged();
		}
		public void setName(String name)
		{
			m_name = name;
			notifyChanged();
		}
		public void setNamespace(String namespace)
		{
			m_namespace = namespace;
			notifyChanged();
		}
		public void setOptional(boolean optional)
		{
			m_optional = optional;
			notifyChanged();
		}
		public void setRange(String range)
		{
			m_range = range;
			notifyChanged();
		}
	}
	public static class TouchpointDataBuilder extends ModelPart
	{
		LinkedHashMap<String, String>m_instructions;
		@SuppressWarnings("unchecked")
		public TouchpointDataBuilder(TouchpointData touchpointData)
		{
			Map m = touchpointData.getInstructions();
			m_instructions = new LinkedHashMap<String, String>(m.size());
			m_instructions.putAll(m);	
		}
		public TouchpointData createTouchpointData()
		{
			return MetadataFactory.createTouchpointData(m_instructions);
		}
		public String getInstruction(String key)
		{
			return m_instructions.get(key);
		}
		
		public LinkedHashMap<String, String> getInstructions()
		{
			return m_instructions;
		}
		public void putInstruction(String key, String value)
		{
			m_instructions.put(key, value);
			notifyChanged();
		}
		public void removeInstruction(String key)
		{
			m_instructions.remove(key);
			notifyChanged();
		}
	}
	public static class TouchpointTypeBuilder extends ModelPart
	{
		private String m_typeid;
		private String m_version;
		public TouchpointTypeBuilder(TouchpointType type)
		{
			m_typeid = type.getId();
			m_version = type.getVersion().toString();
		}
		public TouchpointType createTouchpointType()
		{
			return MetadataFactory.createTouchpointType(m_typeid, new Version(m_version));
		}
		public String getTypeid()
		{
			return m_typeid;
		}
		public String getVersion()
		{
			return m_version;
		}

		public void setTypeid(String typeid)
		{
			m_typeid = typeid;
			notifyChanged();
		}
		public void setVersion(String version)
		{
			m_version = version;
			notifyChanged();
		}
	}
	public static class UpdateDescriptorBuilder extends ModelPart
	{
		private String m_description;
		private String m_range;
		private int m_severity;
		private String m_updateid;
		
		public UpdateDescriptorBuilder(IUpdateDescriptor updateDescriptor)
		{
			if(updateDescriptor == null)
				return;
			
			m_description = updateDescriptor.getDescription();
			m_updateid = updateDescriptor.getId();
			m_range = updateDescriptor.getRange().toString();
			m_severity = updateDescriptor.getSeverity();
		}
		public IUpdateDescriptor createUpdateDescriptor()
		{
			return MetadataFactory.createUpdateDescriptor(m_updateid, new VersionRange(m_range), m_severity, m_description);
		}
		public String getDescription()
		{
			return m_description;
		}
		public String getRange()
		{
			return m_range;
		}
		public int getSeverity()
		{
			return m_severity;
		}
		public String getUpdateid()
		{
			return m_updateid;
		}
		public boolean isEmpty()
		{
			if((m_description == null || m_description.length() < 1) && (m_range == null || m_range.length() < 1)
			&& (m_updateid == null || m_updateid.length() < 1))
				return true;
			return false;
		}
		public void setDescription(String description)
		{
			m_description = description;
			notifyChanged();
		}
		public void setRange(String range)
		{
			m_range = range;
			notifyChanged();
		}

		public void setSeverity(int severity)
		{
			m_severity = severity;
			notifyChanged();
		}
		public void setUpdateid(String updateid)
		{
			m_updateid = updateid;
			notifyChanged();
		}
	}
	private ArtifactKeyBuilder[] m_artifacts;
	private CopyrightBuilder m_copyright;
	private String m_filter;
	private String m_id;
	private LicenseBuilder m_license;
	private LinkedHashMap<String, String> m_properties;
	private ProvidedCapabilityBuilder[] m_providedCapabilities;
	private RequiredCapabilityBuilder[] m_requiredCapabilities;
	private boolean  m_singleton;
	private TouchpointDataBuilder[] m_touchpointData;
	private TouchpointTypeBuilder m_touchpointType;
	private UpdateDescriptorBuilder m_updateDescriptor;
	private String m_version;
	
	@SuppressWarnings("unchecked")
	public InstallableUnitBuilder(IInstallableUnit unit)
	{
		// Artifact keys
		IArtifactKey[] artifactKeys = unit.getArtifacts();
		m_artifacts = new ArtifactKeyBuilder[artifactKeys.length];
		for(int i = 0; i < artifactKeys.length; i++)
		{
			m_artifacts[i] = new ArtifactKeyBuilder(artifactKeys[i]);
			m_artifacts[i].setParent(this);
		}
		m_copyright = new CopyrightBuilder(unit.getCopyright());
		m_copyright.setParent(this);
		
		m_filter = unit.getFilter();
		
		// Editing of bound fragments not supported - this is only for resolved IUs
		// unit.getFragments();
		
		m_id = unit.getId();
		
		m_license = new LicenseBuilder(unit.getLicense());
		m_license.setParent(this);
		
		m_properties = new LinkedHashMap();
		m_properties.putAll(unit.getProperties());

		ProvidedCapability[] providedCapabilities = unit.getProvidedCapabilities();
		m_providedCapabilities = new ProvidedCapabilityBuilder[providedCapabilities.length];
		for(int i = 0; i < providedCapabilities.length;i++)
		{
			m_providedCapabilities[i] = new ProvidedCapabilityBuilder(providedCapabilities[i]);
			m_providedCapabilities[i].setParent(this);
		}
		RequiredCapability[] requiredCapabilities = unit.getRequiredCapabilities();
		m_requiredCapabilities = new RequiredCapabilityBuilder[requiredCapabilities.length];
		for(int i = 0; i < requiredCapabilities.length;i++)
		{
			m_requiredCapabilities[i] = new RequiredCapabilityBuilder(requiredCapabilities[i]);
			m_requiredCapabilities[i].setParent(this);
		}
		TouchpointData[] touchpointData = unit.getTouchpointData();
		m_touchpointData = new TouchpointDataBuilder[touchpointData.length];
		for(int i = 0; i < touchpointData.length;i++)
		{
			m_touchpointData[i] = new TouchpointDataBuilder(touchpointData[i]);
			m_touchpointData[i].setParent(this);
		}
		m_touchpointType = new TouchpointTypeBuilder(unit.getTouchpointType());
		m_touchpointType.setParent(this);
		
		m_updateDescriptor = new UpdateDescriptorBuilder(unit.getUpdateDescriptor());
		m_updateDescriptor.setParent(this);
		
		m_version = unit.getVersion().toString();
		m_singleton = unit.isSingleton();
		
	}
	public InstallableUnit createInstallableUnit()
	{
		InstallableUnit iud = new InstallableUnit();
		
		// Artifact keys
		IArtifactKey[] keys = new IArtifactKey[m_artifacts.length];
		for(int i = 0; i < m_artifacts.length;i++)
			keys[i++] = m_artifacts[i].createArtifactKey();
		iud.setArtifacts(keys);
		
		iud.setCopyright(m_copyright.createCopyright());		
		iud.setFilter(m_filter);
		iud.setId(m_id);
		iud.setLicense(m_license.createLicense());
		iud.setSingleton(m_singleton);

		// properties can not be set in bulk
		for( Entry<String, String> entry : m_properties.entrySet())
			iud.setProperty(entry.getKey(), entry.getValue());

		// provided capabilities
		ProvidedCapability[] providedCapabilities = new ProvidedCapability[m_providedCapabilities.length];
		for(int i = 0; i < m_providedCapabilities.length;i++)
			providedCapabilities[i] = m_providedCapabilities[i].createProvidedCapability();
		iud.setCapabilities(providedCapabilities);
		
		// required capabilities
		RequiredCapability[] requiredCapabilities = new RequiredCapability[m_requiredCapabilities.length];
		for(int i = 0; i <  m_requiredCapabilities.length; i++)
			requiredCapabilities[i] = m_requiredCapabilities[i].createRequiredCapability();
		iud.setRequiredCapabilities(requiredCapabilities);
		
		for(int i = 0; i < m_touchpointData.length; i++)
			iud.addTouchpointData(m_touchpointData[i].createTouchpointData());
		
		iud.setUpdateDescriptor(m_updateDescriptor.createUpdateDescriptor());
		iud.setTouchpointType(m_touchpointType.createTouchpointType());
		
		iud.setVersion(new Version(m_version));
		return iud;
	}
	public ArtifactKeyBuilder[] getArtifacts()
	{
		return m_artifacts;
	}
	public CopyrightBuilder getCopyright()
	{
		return m_copyright;
	}
	public String getFilter()
	{
		return m_filter;
	}
	public String getId()
	{
		return m_id;
	}
	public LicenseBuilder getLicense()
	{
		return m_license;
	}
	public LinkedHashMap<String, String> getProperties()
	{
		return m_properties;
	}
	public String getProperty(String key)
	{
		return m_properties.get(key);
	}
	public ProvidedCapabilityBuilder[] getProvidedCapabilities()
	{
		return m_providedCapabilities;
	}
	/**
	 * Add a provided capability last.
	 * @param provided
	 * @return index where this capability was added
	 */
	public int addProvidedCapability(ProvidedCapabilityBuilder provided)
	{
		return addProvidedCapability(provided, -1);
	}
	/**
	 * Adds a provided capability at a given index. If index is outsite of range (or more specificly is -1), the new
	 * provided capability is added last.
	 * @param provided
	 * @param index
	 * @return the index where the capability was added.
	 */
	public int addProvidedCapability(ProvidedCapabilityBuilder provided, int index)
	{
		ProvidedCapabilityBuilder[] reqCap2 = new ProvidedCapabilityBuilder[m_providedCapabilities.length+1];
		int j = 0;
		for(int i = 0; i < m_providedCapabilities.length;i++)
		{
			if(i == index)
				reqCap2[j++] = provided;
			reqCap2[j++] = m_providedCapabilities[i];
		}
		if(index < 0 || index >= m_providedCapabilities.length)
		{
			index = m_providedCapabilities.length;
			reqCap2[index] = provided;
		}
		m_providedCapabilities = reqCap2;
		provided.setParent(this);
		notifyChanged();
		return index;
	}
	/**
	 * Removes the provided capability from the set of provided capabilities.
	 * @param provided
	 * @return the index where the provided capability was found, -1 if not found
	 */
	public int removeProvidedCapability(ProvidedCapabilityBuilder provided)
	{
		int index = -1; // not found (yet)
		for(int i = 0; i < m_providedCapabilities.length;i++)
			if(m_providedCapabilities[i] == provided)
			{
				index = i;
				break;
			}
		if(index == -1)
			return index; // not found

		ProvidedCapabilityBuilder[] reqCap2 = new ProvidedCapabilityBuilder[m_providedCapabilities.length-1];
		int j = 0;
		for(int i = 0; i < m_providedCapabilities.length;i++)
		{
			if(i == index)
				continue; // skip the item to remove
			reqCap2[j++] = m_providedCapabilities[i];
		}
		m_providedCapabilities = reqCap2;
		provided.setParent(null);
		notifyChanged();
		return index;
		
	}
	/**
	 * Moves the provided capability up (+1) or down(-1) in the array of provided capabilities
	 * @param provided
	 * @param delta - +1 or -1 (throws IllegalArgumentException of not +1 or -1)
	 * @return -1 if move was not made, else the position before the move is returned
	 */
	public int moveProvidedCapability(ProvidedCapabilityBuilder provided, int delta)
	{
		if(!(delta == 1 || delta == -1))
			throw new IllegalArgumentException("can only move +1 or -1");
		int index = -1; // not found (yet)
		for(int i = 0; i < m_providedCapabilities.length;i++)
			if(m_providedCapabilities[i] == provided)
			{
				index = i;
				break;
			}
		if(index == -1)
			return index; // not found
		int swapIndex = index + delta;
		if(swapIndex < 0 || swapIndex >= m_providedCapabilities.length)
			return -1; // outsite of range - no move
		
		ProvidedCapabilityBuilder tmp = m_providedCapabilities[swapIndex];
		m_providedCapabilities[swapIndex] = m_providedCapabilities[index];
		m_providedCapabilities[index] = tmp;
		notifyChanged();
		return index;
	}
	

	public RequiredCapabilityBuilder[] getRequiredCapabilities()
	{
		return m_requiredCapabilities;
	}
	/**
	 * Add a required capability last.
	 * @param required
	 * @return index where this capability was added
	 */
	public int addRequiredCapability(RequiredCapabilityBuilder required)
	{
		return addRequiredCapability(required, -1);
	}
	/**
	 * Adds a required capability at a given index. If index is outsite of range (or more specificly is -1), the new
	 * required capability is added last.
	 * @param required
	 * @param index
	 * @return the index where the capability was added.
	 */
	public int addRequiredCapability(RequiredCapabilityBuilder required, int index)
	{
		RequiredCapabilityBuilder[] reqCap2 = new RequiredCapabilityBuilder[m_requiredCapabilities.length+1];
		int j = 0;
		for(int i = 0; i < m_requiredCapabilities.length;i++)
		{
			if(i == index)
				reqCap2[j++] = required;
			reqCap2[j++] = m_requiredCapabilities[i];
		}
		if(index < 0 || index >= m_requiredCapabilities.length)
		{
			index = m_requiredCapabilities.length;
			reqCap2[index] = required;
		}
		m_requiredCapabilities = reqCap2;
		required.setParent(this);
		notifyChanged();
		return index;
	}
	/**
	 * Removes the required capability from the set of required capabilities.
	 * @param required
	 * @return the index where the required capability was found, -1 if not found
	 */
	public int removeRequiredCapability(RequiredCapabilityBuilder required)
	{
		int index = -1; // not found (yet)
		for(int i = 0; i < m_requiredCapabilities.length;i++)
			if(m_requiredCapabilities[i] == required)
			{
				index = i;
				break;
			}
		if(index == -1)
			return index; // not found

		RequiredCapabilityBuilder[] reqCap2 = new RequiredCapabilityBuilder[m_requiredCapabilities.length-1];
		int j = 0;
		for(int i = 0; i < m_requiredCapabilities.length;i++)
		{
			if(i == index)
				continue; // skip the item to remove
			reqCap2[j++] = m_requiredCapabilities[i];
		}
		m_requiredCapabilities = reqCap2;
		required.setParent(null);
		notifyChanged();
		return index;
		
	}
	/**
	 * Moves the required capability up (+1) or down(-1) in the array of required capabilities
	 * @param required
	 * @param delta - +1 or -1 (throws IllegalArgumentException of not +1 or -1)
	 * @return -1 if move was not made, else the position before the move is returned
	 */
	public int moveRequiredCapability(RequiredCapabilityBuilder required, int delta)
	{
		if(!(delta == 1 || delta == -1))
			throw new IllegalArgumentException("can only move +1 or -1");
		int index = -1; // not found (yet)
		for(int i = 0; i < m_requiredCapabilities.length;i++)
			if(m_requiredCapabilities[i] == required)
			{
				index = i;
				break;
			}
		if(index == -1)
			return index; // not found
		int swapIndex = index + delta;
		if(swapIndex < 0 || swapIndex >= m_requiredCapabilities.length)
			return -1; // outsite of range - no move
		
		RequiredCapabilityBuilder tmp = m_requiredCapabilities[swapIndex];
		m_requiredCapabilities[swapIndex] = m_requiredCapabilities[index];
		m_requiredCapabilities[index] = tmp;
		notifyChanged();
		return index;
	}
	public Serializable getTouchpointData()
	{
		return m_touchpointData;
	}
	public TouchpointTypeBuilder getTouchpointType()
	{
		return m_touchpointType;
	}
	public UpdateDescriptorBuilder getUpdateDescriptor()
	{
		return m_updateDescriptor;
	}
	public String getVersion()
	{
		return m_version;
	}
	public boolean isSingleton()
	{
		return m_singleton;
	}
	public void removeProperty(String key)
	{
		m_properties.remove(key);
		notifyChanged();
	}
	public void setArtifacts(ArtifactKeyBuilder[] artifacts)
	{
		if(m_artifacts != null)
			for(int i = 0; i < m_artifacts.length;i++)
				m_artifacts[i].setParent(null);
		m_artifacts = artifacts;
		for(int i = 0; i < m_artifacts.length;i++)
			m_artifacts[i].setParent(this);
		notifyChanged();
	}
	public void setCopyright(CopyrightBuilder copyright)
	{
		if(m_copyright != null)
			m_copyright.setParent(null);
		m_copyright = copyright;
		m_copyright.setParent(this);
		notifyChanged();
	}
	public void setFilter(String filter)
	{
		m_filter = filter;
		notifyChanged();
	}

	public void setId(String id)
	{
		m_id = id;
		notifyChanged();
	}
	public void setLicense(LicenseBuilder license)
	{
		if(m_license != null)
			m_license.setParent(null);
		m_license = license;
		m_license.setParent(this);
		notifyChanged();
	}
	public void setProperties(LinkedHashMap<String, String> properties)
	{
		m_properties = properties;
		notifyChanged();
	}
	
	public void setProperty(String key, String value)
	{
		m_properties.put(key, value);
		notifyChanged();
	}
	public void setProvidedCapabilities(ProvidedCapabilityBuilder[] providedCapabilities)
	{
		if(m_providedCapabilities != null)
			for(int i = 0; i < m_providedCapabilities.length;i++)
				m_providedCapabilities[i].setParent(null);
		
		m_providedCapabilities = providedCapabilities;
		for(int i = 0; i < m_providedCapabilities.length;i++)
			m_providedCapabilities[i].setParent(this);
		notifyChanged();
	}
	public void setRequiredCapabilities(RequiredCapabilityBuilder[] requiredCapabilities)
	{
		if(m_requiredCapabilities != null)
			for(int i = 0; i < m_requiredCapabilities.length;i++)
				m_requiredCapabilities[i].setParent(null);
		
		m_requiredCapabilities = requiredCapabilities;
		for(int i = 0; i < m_requiredCapabilities.length;i++)
			m_requiredCapabilities[i].setParent(this);
		notifyChanged();
	}
	public void setSingleton(boolean singleton)
	{
		m_singleton = singleton;
		notifyChanged();
	}
	public void setTouchpointData(TouchpointDataBuilder[] touchpointData)
	{
		// remove old parenthood
		if(m_touchpointData != null)
			for(int i = 0; i < m_touchpointData.length;i++)
				m_touchpointData[i].setParent(null);
		// set new and set parenthood	
		m_touchpointData = touchpointData;
		for(int i = 0; i < touchpointData.length;i++)
			touchpointData[i].setParent(this);
		notifyChanged();
	}
	/**
	 * Sets the touchpoint type, and the passed touchpointType's parent is set to this installable unit builder.
	 * Any previously set touchpoint type's parent is set to null (to avoid change events from this instance to
	 * propagate into the model).
	 * 
	 * @param touchpointType
	 */
	public void setTouchpointType(TouchpointTypeBuilder touchpointType)
	{
		if(m_touchpointType != null)
			m_touchpointType.setParent(null);
		m_touchpointType = touchpointType;
		m_touchpointType.setParent(this);
		notifyChanged();
	}
	/**
	 * Sets the update descriptor, and the passed update descriptor's parent is set to this installable unit builder.
	 * Any previously set update descriptor's parent is set to null (to avoid change events from this instance to
	 * propagate into the model).
	 * 
	 * @param updateDescriptor
	 */
	public void setUpdateDescriptor(UpdateDescriptorBuilder updateDescriptor)
	{
		if(m_updateDescriptor != null)
			m_updateDescriptor.setParent(null);
		m_updateDescriptor = updateDescriptor;
		m_updateDescriptor.setParent(this);
		notifyChanged();
	}
	public void setVersion(String version)
	{
		m_version = version;
		notifyChanged();
	}
}
