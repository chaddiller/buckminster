/*******************************************************************************
 * Copyright (c) 2006-2007, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 ******************************************************************************/

package org.eclipse.buckminster.ui.editor.cspec;

import java.util.List;

import org.eclipse.buckminster.core.cspec.builder.CSpecBuilder;
import org.eclipse.buckminster.core.cspec.builder.GeneratorBuilder;
import org.eclipse.buckminster.core.helpers.TextUtils;
import org.eclipse.buckminster.ui.Messages;
import org.eclipse.buckminster.ui.general.editor.IValidator;
import org.eclipse.buckminster.ui.general.editor.ValidatorException;
import org.eclipse.buckminster.ui.general.editor.simple.IWidgetin;
import org.eclipse.buckminster.ui.general.editor.simple.SimpleTable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

/**
 * @author Karel Brezina
 * 
 */
public class GeneratorsTable extends SimpleTable<GeneratorBuilder>
{
	private CSpecEditor m_editor;

	private CSpecBuilder m_cspecBuilder;

	public GeneratorsTable(CSpecEditor editor, List<GeneratorBuilder> data, CSpecBuilder cspecBuilder, boolean readOnly)
	{
		super(data, readOnly);
		m_editor = editor;
		m_cspecBuilder = cspecBuilder;
	}

	public GeneratorBuilder createRowClass()
	{
		return m_cspecBuilder.createGeneratorBuilder();
	}

	public String[] getColumnHeaders()
	{
		return new String[] { Messages.name, Messages.attribute, Messages.component };
	}

	public int[] getColumnWeights()
	{
		return new int[] { 40, 20, 20 };
	}

	@Override
	public IValidator getFieldValidator(int idx)
	{
		switch(idx)
		{
		case 0:
			return SimpleTable.createNotEmptyStringValidator(Messages.generator_name_cannot_be_empty);
		case 1:
			return SimpleTable.createNotEmptyStringValidator(Messages.attribute_cannot_be_empty);
		case 2:
			return SimpleTable.createNotEmptyStringValidator(Messages.component_cannot_be_empty);
		default:
			return SimpleTable.getEmptyValidator();
		}
	}

	@Override
	public IWidgetin getWidgetin(Composite parent, int idx, Object value)
	{
		switch(idx)
		{
		case 0:
			return getTextWidgetin(parent, idx, value);
		case 1:
			return getComboWidgetin(parent, idx, value, m_editor.getAttributeNames(null), SWT.NONE);
		case 2:
			return getComboWidgetin(parent, idx, value, m_editor.getComponentNames(), SWT.NONE);
		default:
			return getTextWidgetin(parent, idx, value);
		}
	}

	public Object[] toRowArray(GeneratorBuilder t)
	{
		Object[] array = new Object[getColumns()];

		array[0] = t.getName();
		array[1] = t.getAttribute();
		array[2] = t.getComponent();

		return array;
	}

	public void updateRowClass(GeneratorBuilder builder, Object[] args) throws ValidatorException
	{
		builder.setName(TextUtils.notEmptyString((String)args[0]));
		builder.setAttribute(TextUtils.notEmptyString((String)args[1]));
		builder.setComponent(TextUtils.notEmptyString((String)args[2]));
	}
}
