package app.eddy.browser.settings

import app.eddy.browser.R
import app.eddy.browser.util.str
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.theme.Dimens

private class Section(val heading: String, val body: String)

@Composable
fun PrivacyPolicyPage() = LegalPage(str(R.string.last_updated_october_2026), PrivacySections)

@Composable
fun TermsPage() = LegalPage(str(R.string.last_updated_september_2026), TermsSections)

@Composable
private fun LegalPage(updated: String, sections: List<Section>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutterLarge, vertical = 8.dp)) {
        Text(updated, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sections.forEach { s ->
            Text(s.heading, Modifier.padding(top = 22.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(s.body, style = MaterialTheme.typography.bodyMedium, lineHeight = MaterialTheme.typography.bodyMedium.lineHeight)
        }
    }
}

private val PrivacySections = listOf(
    Section(
        str(R.string.the_short_version),
        str(R.string.eddy_does_not_collect_sell_or_share_your) +
            str(R.string.the_only_thing_it_sends_to_the_developer) +
            str(R.string.your_history_bookmarks_settings_and_down),
    ),
    Section(
        str(R.string.what_eddy_stores_on_your_device),
        str(R.string.you_can_delete_all_of_this_at_any_time_i),
    ),
    Section(
        str(R.string.network_requests_eddy_makes),
        str(R.string.besides_loading_the_pages_you_ask_for_ed),
    ),
    Section(
        str(R.string.components_eddy_relies_on),
        str(R.string.eddy_shows_web_pages_with_android_system),
    ),
    Section(
        str(R.string.permissions),
        str(R.string.android_may_show_a_notice_when_this_happ),
    ),
    Section(
        str(R.string.passwords),
        str(R.string.when_you_sign_in_or_sign_up_on_a_site_ed_2) +
            str(R.string.passwords_stay_on_your_device_eddy_does) +
            str(R.string.if_your_device_has_a_screen_lock_eddy_as) +
            str(R.string.passwords_you_copy_leave_the_clipboard_a),
    ),
    Section(
        str(R.string.support_messages),
        str(R.string.settings_support_lets_you_send_a_message) +
            str(R.string.it_includes_your_message_your_email_addr) +
            str(R.string.eddy_delivers_it_through_web3forms_web3f) +
            str(R.string.the_developer_uses_it_only_to_answer_you) +
            str(R.string.to_limit_abuse_eddy_stores_the_time_of_y),
    ),
    Section(
        str(R.string.do_not_track_and_blocking),
        str(R.string.when_you_enable_it_eddy_sends_the_do_not) +
            str(R.string.ad_and_tracker_blocking_is_best_effort_a),
    ),
    Section(
        str(R.string.incognito_tabs),
        str(R.string.incognito_tabs_save_no_history_page_prev) +
            str(R.string.on_devices_whose_webview_cannot_keep_tha) +
            str(R.string.incognito_does_not_hide_your_activity_fr),
    ),
    Section(
        str(R.string.children),
        str(R.string.eddy_is_a_general_purpose_browser_and_is),
    ),
    Section(
        str(R.string.changes_and_questions),
        str(R.string.when_this_policy_changes_the_new_version) +
            str(R.string.for_questions_use_settings_support_or_co),
    ),
)

private val TermsSections = listOf(
    Section(
        str(R.string.beta_software),
        str(R.string.eddy_is_in_beta_it_may_contain_bugs_feat) +
            str(R.string.keep_your_own_copy_of_anything_important),
    ),
    Section(
        str(R.string.licence),
        str(R.string.eddy_is_free_software_under_the_apache_l),
    ),
    Section(
        str(R.string.security_warnings),
        str(R.string.eddy_warns_you_about_connection_and_cert) +
            str(R.string.blocking_of_ads_trackers_and_dangerous_s),
    ),
    Section(
        str(R.string.third_party_content_and_software),
        str(R.string.web_pages_search_engines_filter_lists_an) +
            str(R.string.eddy_uses_android_system_webview_and_ope),
    ),
    Section(
        str(R.string.no_warranty),
        str(R.string.eddy_is_provided_as_is_and_as_available),
    ),
    Section(
        str(R.string.limitation_of_liability),
        str(R.string.to_the_fullest_extent_permitted_by_law_t),
    ),
    Section(
        str(R.string.changes),
        str(R.string.new_versions_of_the_app_may_update_this),
    ),
)
