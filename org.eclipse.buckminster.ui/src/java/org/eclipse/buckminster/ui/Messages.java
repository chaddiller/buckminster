package org.eclipse.buckminster.ui;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
	private static final String BUNDLE_NAME = "org.eclipse.buckminster.ui.messages"; //$NON-NLS-1$

	public static String action_error;

	public static String action_properties;

	public static String actions_of;

	public static String add_opml_bookmarks_to_rss_reader;

	public static String an_error_occured_when_creating_the_file;

	public static String bad_file_name;

	public static String browse_with_dots;

	public static String components_known_to_buckminster;

	public static String confirm_overwrite;

	public static String could_not_create_file;

	public static String dependencies;

	public static String duplicate_0_found_in_plugin_1;

	public static String error_during_action_perform;

	public static String error_while_opening_file_for_writing;

	public static String errors_during_loading;

	public static String file_does_not_exist;

	public static String file_with_colon;

	public static String internal_problem_when_generating_opml_xml;

	public static String name;

	public static String no_component_is_selected;

	public static String no_ompl;

	public static String not_yet_implemented;

	public static String opml_format_error;

	public static String please_enter_a_valid_url;

	public static String SplashScreen_splash_will_close_after_X_sec_but_you_can_click_to_close;

	public static String the_entered_file_name_is_not_valid;

	public static String the_file_0_already_exists_overwrite_question;

	public static String unable_to_open_editor;

	public static String unknown_0_derivate;

	public static String url_for_query_with_colon;

	public static String version;

	public static String write_error;
	static
	{
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages()
	{
	}
}
