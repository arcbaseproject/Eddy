package app.eddy.browser.settings

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
fun PrivacyPolicyPage() = LegalPage("Last updated September 2026", PrivacySections)

@Composable
fun TermsPage() = LegalPage("Last updated September 2026", TermsSections)

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
        "The short version",
        "Eddy does not collect, sell or share your personal data. It has no accounts, advertising, analytics or crash reporting. " +
            "Your history, bookmarks, settings and downloads list stay on your device.",
    ),
    Section(
        "What Eddy stores on your device",
        "• Browsing history. Eddy deletes entries older than 90 days and never saves history for incognito tabs.\n" +
            "• Bookmarks, folders and home-page shortcuts.\n" +
            "• Your settings and the choices you make for individual sites, such as camera or location access.\n" +
            "• Open tabs and their page previews, so Eddy can restore them when you reopen the app. Eddy never saves incognito tabs.\n" +
            "• Site icons, and the cookies, site data and cache that web pages create through the browser engine.\n" +
            "• A list of your downloads. The files themselves go to your Downloads folder.\n\n" +
            "You can delete all of this at any time in Settings > Privacy and security > Clear browsing data, or by uninstalling the app.",
    ),
    Section(
        "Network requests Eddy makes",
        "Besides loading the pages you ask for, Eddy makes these requests:\n" +
            "• Search suggestions: as you type, Eddy sends your text to your search engine to fetch suggestions. Turn this off in Settings > General.\n" +
            "• Site icons: Eddy may request /favicon.ico from a site on your home page.\n" +
            "• Filter lists: Eddy downloads the ad and tracker lists you enable from their publishers about once a day.\n" +
            "• Downloads you start.\n\n" +
            "Each site, search engine and list host you contact can see your IP address and the usual information a browser sends, and applies its own privacy policy.",
    ),
    Section(
        "Components Eddy relies on",
        "Eddy shows web pages with Android System WebView, a Google component built on Chromium. WebView includes Safe Browsing, which can send " +
            "partial, anonymised fingerprints of addresses to Google to warn you about dangerous sites. Eddy does not control this and adds no tracking of its own. " +
            "Google's privacy policy covers that feature.",
    ),
    Section(
        "Permissions",
        "• Camera, microphone and location: Eddy uses these only when a website asks and you allow it. You can change each choice per site.\n" +
            "• Notifications: show download progress.\n" +
            "• Clipboard: when you open the address bar, Eddy reads the clipboard once to offer a “paste link” suggestion. Eddy does not store or send it. " +
            "Android may show a notice when this happens.\n" +
            "• Network state: lets Eddy show offline pages and retry when you reconnect.",
    ),
    Section(
        "Do Not Track and blocking",
        "When you enable it, Eddy sends the Do Not Track and Global Privacy Control signals. Sites can ignore them. " +
            "Ad and tracker blocking is best-effort and cannot stop every form of tracking.",
    ),
    Section(
        "Incognito tabs",
        "Incognito tabs save no history, page previews or site choices. Eddy erases their cookies and site data when the last incognito tab closes. " +
            "On devices whose WebView cannot keep that storage separate, incognito tabs share cookies with normal tabs, and Settings > Privacy and security tells you when that applies. " +
            "Incognito does not hide your activity from websites, your network, your employer or your internet provider. Eddy keeps files you download and bookmarks you make.",
    ),
    Section(
        "Children",
        "Eddy is a general-purpose browser and is not directed at children.",
    ),
    Section(
        "Changes and questions",
        "When this policy changes, the new version ships with the app and the date above changes. " +
            "For questions, contact the developer through the app's store page.",
    ),
)

private val TermsSections = listOf(
    Section(
        "Accepting these terms",
        "Installing or using Eddy means you agree to these terms. If you disagree, do not use the app.",
    ),
    Section(
        "Beta software",
        "Eddy is in beta. It may contain bugs, features may change or disappear, and you could lose tabs, bookmarks or settings. " +
            "Keep your own copy of anything important, for example by exporting your bookmarks.",
    ),
    Section(
        "Your use of the app",
        "You may use Eddy for personal and lawful purposes. You are responsible for the sites you visit, the content you view or download, " +
            "and for following the laws that apply to you. Do not use Eddy to attack, disrupt or gain unauthorised access to any service.",
    ),
    Section(
        "Security warnings",
        "Eddy warns you about connection and certificate problems. If you continue anyway, you accept the risk. " +
            "Blocking of ads, trackers and dangerous sites is a convenience and does not catch everything.",
    ),
    Section(
        "Third-party content and software",
        "Web pages, search engines, filter lists and downloaded files come from third parties. Eddy does not control or endorse them, and their own terms apply. " +
            "Eddy uses Android System WebView and open-source libraries, which remain under their own licences.",
    ),
    Section(
        "No warranty",
        "Eddy is provided “as is” and “as available”, without warranties of any kind, express or implied, including fitness for a particular purpose, " +
            "reliability or freedom from errors.",
    ),
    Section(
        "Limitation of liability",
        "To the fullest extent permitted by law, the developer is not liable for any indirect, incidental or consequential damages, or for loss of data, " +
            "arising from your use of Eddy.",
    ),
    Section(
        "Ending your use",
        "Uninstall Eddy to stop using it. These terms continue to apply to what happened while you used it.",
    ),
    Section(
        "Changes",
        "New versions of the app may update these terms. If you keep using Eddy after an update, you accept the new terms.",
    ),
)
