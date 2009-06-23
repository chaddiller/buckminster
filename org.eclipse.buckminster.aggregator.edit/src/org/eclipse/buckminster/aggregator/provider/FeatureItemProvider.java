/**
 * <copyright>
 * </copyright>
 *
 * $Id$
 */
package org.eclipse.buckminster.aggregator.provider;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import org.eclipse.buckminster.aggregator.AggregatorPackage;
import org.eclipse.buckminster.aggregator.Feature;
import org.eclipse.buckminster.aggregator.IAggregatorConstants;
import org.eclipse.buckminster.aggregator.MappedRepository;
import org.eclipse.buckminster.aggregator.MappedUnit;
import org.eclipse.buckminster.aggregator.p2.InstallableUnit;
import org.eclipse.buckminster.runtime.Trivial;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.edit.provider.ComposeableAdapterFactory;
import org.eclipse.emf.edit.provider.IEditingDomainItemProvider;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.eclipse.emf.edit.provider.IItemPropertyDescriptor;
import org.eclipse.emf.edit.provider.IItemPropertySource;
import org.eclipse.emf.edit.provider.IStructuredItemContentProvider;
import org.eclipse.emf.edit.provider.ITreeItemContentProvider;
import org.eclipse.equinox.internal.provisional.p2.core.Version;
import org.eclipse.equinox.internal.provisional.p2.core.VersionedName;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.query.MatchQuery;
import org.eclipse.equinox.internal.provisional.p2.query.Query;

/**
 * This is the item provider adapter for a {@link org.eclipse.buckminster.aggregator.Feature} object. <!--
 * begin-user-doc --> <!-- end-user-doc -->
 * 
 * @generated
 */
public class FeatureItemProvider extends MappedUnitItemProvider implements IEditingDomainItemProvider,
		IStructuredItemContentProvider, ITreeItemContentProvider, IItemLabelProvider, IItemPropertySource
{
	/**
	 * This constructs an instance from a factory and a notifier.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	public FeatureItemProvider(AdapterFactory adapterFactory)
	{
		super(adapterFactory);
	}

	/**
	 * This returns the property descriptors for the adapted class.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public List<IItemPropertyDescriptor> getPropertyDescriptors(Object object)
	{
		if (itemPropertyDescriptors == null) {
			super.getPropertyDescriptors(object);

			addCategoriesPropertyDescriptor(object);
		}
		return itemPropertyDescriptors;
	}

	/**
	 * This adds a property descriptor for the Categories feature.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	protected void addCategoriesPropertyDescriptor(Object object)
	{
		itemPropertyDescriptors.add
			(createItemPropertyDescriptor
				(((ComposeableAdapterFactory)adapterFactory).getRootAdapterFactory(),
				 getResourceLocator(),
				 getString("_UI_Feature_categories_feature"),
				 getString("_UI_PropertyDescriptor_description", "_UI_Feature_categories_feature", "_UI_Feature_type"),
				 AggregatorPackage.Literals.FEATURE__CATEGORIES,
				 true,
				 false,
				 true,
				 null,
				 null,
				 null));
	}

	/**
	 * This returns Feature.gif. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated NOT
	 */
	@Override
	public Object getImage(Object object)
	{
		// Experimental. Should use an overlay and also affect parten with overlays
		//
		Feature feature = (Feature)object;
		Object image;
		if(feature.getInstallableUnit() != null && Trivial.trim(feature.getInstallableUnit().getId()) == null)
			try
			{
				image = new URL(
						URI.createPlatformPluginURI("/org.eclipse.ui.ide/icons/full/obj16/warning.gif", false).toString());
			}
			catch(MalformedURLException e)
			{
				image = getResourceLocator().getImage("full/obj16/Feature");
			}
		else
			image = getResourceLocator().getImage("full/obj16/Feature");
		return overlayImage(object, image);
	}

	/**
	 * This returns the label text for the adapted class. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated NOT
	 */
	@Override
	public String getText(Object object)
	{
		Feature feature = (Feature)object;
		InstallableUnit iu = feature.getInstallableUnit();
		StringBuilder bld = new StringBuilder();
		bld.append(getString("_UI_Feature_type"));
		bld.append(' ');
		boolean broken = false;
		String id = null;
		Version version = null;
		if(iu != null)
		{
			id = Trivial.trim(iu.getId());
			if(id == null)
			{
				broken = true;
				VersionedName vn = iu.getVersionedNameFromProxy();
				if(vn != null)
				{
					id = vn.getId();
					version = vn.getVersion();
				}
			}
			else
				version = iu.getVersion();
		}

		if(id == null)
			bld.append("not mapped");
		else
		{
			id = id.substring(0, id.length() - IAggregatorConstants.FEATURE_SUFFIX.length());
			bld.append(id);
			bld.append('/');
			bld.append(version);
			if(!feature.isEnabled())
				bld.append(" - disabled");
		}
		if(broken)
			// TODO: Indicate with graphic marker
			bld.append(" broken!");
		return bld.toString();
	}

	/**
	 * This handles model notifications by calling {@link #updateChildren} to update any cached children and by creating
	 * a viewer notification, which it passes to {@link #fireNotifyChanged}. <!-- begin-user-doc --> <!-- end-user-doc
	 * -->
	 * 
	 * @generated
	 */
	@Override
	public void notifyChanged(Notification notification)
	{
		updateChildren(notification);
		super.notifyChanged(notification);
	}

	/**
	 * This adds {@link org.eclipse.emf.edit.command.CommandParameter}s describing the children
	 * that can be created under this object.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected void collectNewChildDescriptors(Collection<Object> newChildDescriptors, Object object)
	{
		super.collectNewChildDescriptors(newChildDescriptors, object);
	}

	@Override
	protected List<? extends MappedUnit> getContainerChildren(MappedRepository container)
	{
		return container.getFeatures();
	}

	@Override
	protected Query getInstallableUnitQuery()
	{
		return new MatchQuery()
		{
			@Override
			public boolean isMatch(Object candidate)
			{
				return ((IInstallableUnit)candidate).getId().endsWith(IAggregatorConstants.FEATURE_SUFFIX);
			}
		};
	}
}
