package app.eddy.browser.browser

/**
 * WebView rejects navigator.clipboard.readText() because it has no paste permission to ask for. This script
 * routes the call to the browser, which asks the user before handing the page any clipboard text.
 */
object ClipboardBridge {
    const val NAME = "EddyClipboard"

    val SOURCE = """
(function () {
  if (window !== window.top || !window.EddyClipboard || !navigator.clipboard) return;
  var bridge = window.EddyClipboard, waiting = [];
  bridge.onmessage = function (e) {
    var w = waiting.shift();
    if (!w) return;
    var r = JSON.parse(e.data);
    if (r.ok) w.resolve(r.text); else w.reject(new DOMException('Read permission denied.', 'NotAllowedError'));
  };
  navigator.clipboard.readText = function () {
    return new Promise(function (resolve, reject) {
      // Same rule as Chrome: only a tap or key press can start a paste, so a page cannot nag on its own.
      if (navigator.userActivation && !navigator.userActivation.isActive) {
        return reject(new DOMException('Read permission denied.', 'NotAllowedError'));
      }
      waiting.push({ resolve: resolve, reject: reject });
      bridge.postMessage('read');
    });
  };
})();
""".trimIndent()
}
