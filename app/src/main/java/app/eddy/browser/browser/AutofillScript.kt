package app.eddy.browser.browser

/**
 * JavaScript injected into every frame. It reports submitted login forms and focus on login fields to
 * the native side, and fills fields when native code replies. It never reads a password until the user
 * submits a form, and it never fills anything unless the user tapped a suggestion in the browser UI.
 */
object AutofillScript {
    const val NAME = "EddyAutofill"

    /** True while a password field is visible; used to tell a failed login from a successful one. */
    const val PASSWORD_FIELD_VISIBLE =
        "(function(){return Array.prototype.some.call(document.querySelectorAll('input[type=password]')," +
            "function(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0})})()"

    val SOURCE = """
(function () {
  // Subframe messages are dropped natively, so running this in every iframe is pure cost on ad-heavy pages.
  if (window !== window.top || window.__eddyAF || !window.EddyAutofill) return;
  window.__eddyAF = true;
  var bridge = window.EddyAutofill;
  var TEXT = ['text', 'email', 'tel', 'search', 'url', ''];
  // Remember the file name a page asks for (a[download]); WebView does not pass it on for blob: and data: links.
  var nativeClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (this.hasAttribute('download')) window.__eddyDownloadName = this.getAttribute('download') || '';
    return nativeClick.apply(this, arguments);
  };
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest && e.target.closest('a[download]');
    if (a) window.__eddyDownloadName = a.getAttribute('download') || '';
  }, true);
  function post(o) { try { bridge.postMessage(JSON.stringify(o)); } catch (e) {} }
  function all(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function visible(el) { var r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0; }
  function isText(el) { return el.tagName === 'INPUT' && TEXT.indexOf((el.getAttribute('type') || '').toLowerCase()) >= 0; }
  // Every listener below runs on pages that have nothing to do with logins, so the check that skips them
  // must be near-free: one selector match per second, never a query per event.
  var pwSeenAt = 0, pwSeen = false;
  function hasPassword() {
    var now = Date.now();
    if (now - pwSeenAt > 1000) { pwSeen = !!document.querySelector('input[type=password]'); pwSeenAt = now; }
    return pwSeen;
  }

  function userField(pw, scope, needValue) {
    var inputs = all('input', scope), i = inputs.indexOf(pw), best = null;
    for (var k = i - 1; k >= 0; k--) {
      var el = inputs[k];
      if (isText(el) && visible(el) && (!needValue || el.value)) { best = el; break; }
    }
    if (!best) {
      best = all('input[autocomplete="username"],input[autocomplete="email"]', scope)
        .filter(function (e) { return visible(e) && (!needValue || e.value); })[0] || null;
    }
    return best;
  }

  function capture(scope) {
    var pws = all('input[type=password]', scope).filter(function (p) { return p.value; });
    if (!pws.length) return;
    var fresh = pws.filter(function (p) { return (p.getAttribute('autocomplete') || '').indexOf('new-password') >= 0; });
    var pw, signup = false;
    if (fresh.length) { pw = fresh[0]; signup = true; }
    else if (pws.length >= 3) { pw = pws[1]; signup = true; }
    else if (pws.length === 2 && pws[0].value === pws[1].value) { pw = pws[0]; signup = true; }
    else { pw = pws[pws.length - 1]; }
    var u = userField(pw, scope, true);
    post({ t: 'submit', u: u ? u.value.trim() : '', p: pw.value, s: signup });
  }

  document.addEventListener('submit', function (e) {
    if (e.target && e.target.tagName === 'FORM') capture(e.target);
  }, true);
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && e.target && e.target.type === 'password') capture(e.target.form || document);
  }, true);
  document.addEventListener('click', function (e) {
    if (!hasPassword()) return;
    var b = e.target && e.target.closest && e.target.closest('button,input[type=submit],[role=button]');
    if (!b) return;
    var f = b.form || b.closest('form');
    if (f) { if (f.querySelector('input[type=password]')) capture(f); }
    else if (document.querySelector('input[type=password]')) capture(document);
  }, true);
  document.addEventListener('focusin', function (e) {
    var t = e.target;
    if (!t || t.tagName !== 'INPUT' || !hasPassword()) return;
    var isPw = (t.getAttribute('type') || '').toLowerCase() === 'password';
    if (isPw || (isText(t) && all('input[type=password]').some(visible))) post({ t: 'focus', pw: isPw });
  }, true);

  function setValue(el, v) {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, v);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }
  bridge.onmessage = function (ev) {
    try {
      var d = JSON.parse(ev.data);
      var pws = all('input[type=password]').filter(visible);
      if (!pws.length) return;
      var pw = pws[0], u = userField(pw, pw.form || document, false);
      if (d.u && u) setValue(u, d.u);
      setValue(pw, d.p);
    } catch (e) {}
  };
})();
""".trimIndent()
}
