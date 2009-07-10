/*******************************************************************************
 * Copyright (c) 2006-2007, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 ******************************************************************************/

package org.eclipse.buckminster.ui.editor.cspec;

import java.util.List;

import org.eclipse.buckminster.core.common.model.Documentation;
import org.eclipse.buckminster.core.cspec.builder.CSpecBuilder;
import org.eclipse.buckminster.core.cspec.builder.TopLevelAttributeBuilder;
import org.eclipse.buckminster.core.helpers.TextUtils;
import org.eclipse.buckminster.ui.Messages;
import org.eclipse.buckminster.ui.UiUtils;
import org.eclipse.buckminster.ui.editor.EditorUtils;
import org.eclipse.buckminster.ui.general.editor.ValidatorException;
import org.eclipse.buckminster.ui.general.editor.structured.StructuredTable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;

/**
 * @author Karel Brezina
 * 
 */
public abstract class AttributesTable<T extends TopLevelAttributeBuilder> extends StructuredTable<T>
{
	private static final String ERROR_MESSAGE_EMPTY_NAME = Messages.name_cannnot_be_empty;

	private CSpecEditor m_editor;

	private CSpecBuilder m_cspec;

	private Text m_nameText;

	private Button m_publicCheck;

	private Text m_documentationText;

	private T m_currentBuilder;

	public AttributesTable(CSpecEditor editor, List<T> data, CSpecBuilder cspec, boolean readOnly)
	{
		super(data, readOnly);
		m_editor = editor;
		m_cspec = cspec;
	}

	public void enableFields(boolean enabled)
	{
		m_nameText.setEnabled(enabled);
		m_publicCheck.setEnabled(enabled);
		m_documentationText.setEnabled(enabled);
	}

	public CSpecEditor getCSpecEditor()
	{
		return m_editor;
	}

	public T getCurrentBuilder()
	{
		return m_currentBuilder;
	}

	public String[] getTableViewerColumnHeaders()
	{
		return new String[] { Messages.name, Messages.public_label };
	}

	public int[] getTableViewerColumnWeights()
	{
		return new int[] { 80, 20 };
	}

	public Object getTableViewerField(T builder, int columnIndex)
	{
		switch(columnIndex)
		{
		case 0:
			return builder.getName();
		case 1:
			return Boolean.valueOf(builder.isPublic());
		default:
			return null;
		}
	}

	protected Control createDocumentationStackLayer(Composite stackComposite)
	{
		Composite docComposite = new Composite(stackComposite, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = layout.marginWidth = 0;
		docComposite.setLayout(layout);

		EditorUtils.createHeaderLabel(docComposite, Messages.documentation, 1);

		m_documentationText = UiUtils.createGridText(docComposite, 1, 0, isReadOnly(), SWT.MULTI);
		m_documentationText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		m_documentationText.addModifyListener(FIELD_LISTENER);
		m_documentationText.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));

		docComposite.setData("focusControl", m_documentationText); //$NON-NLS-1$

		return docComposite;
	}

	protected CSpecBuilder getCSpecBuilder()
	{
		return m_cspec;
	}

	protected Text getNameText()
	{
		return m_nameText;
	}

	@Override
	protected void refreshRow(T builder)
	{
		m_currentBuilder = builder;

		m_nameText.setText(TextUtils.notNullString(builder.getName()));
		m_publicCheck.setSelection(builder.isPublic());

		Documentation doc = builder.getDocumentation();
		m_documentationText.setText(TextUtils.notNullString(doc == null
				? null
				: doc.toString()));
	}

	protected void setNameText(Text nameText)
	{
		m_nameText = nameText;
		m_nameText.addModifyListener(FIELD_LISTENER);
	}

	protected void setPublicCheck(Button publicCheck)
	{
		m_publicCheck = publicCheck;
		m_publicCheck.addSelectionListener(FIELD_LISTENER);
	}

	@Override
	protected void setRowValues(T builder) throws ValidatorException
	{
		if(UiUtils.trimmedValue(m_nameText) == null)
		{
			throw new ValidatorException(ERROR_MESSAGE_EMPTY_NAME);
		}

		builder.setName(UiUtils.trimmedValue(m_nameText));
		builder.setPublic(m_publicCheck.getSelection());

		String doc = UiUtils.trimmedValue(m_documentationText);

		try
		{
			builder.setDocumentation(doc == null
					? null
					: Documentation.parse(doc));
		}
		catch(Exception e)
		{
			throw new ValidatorException(e.getMessage());
		}
	}

}
