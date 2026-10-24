package app.eddy.browser

import app.eddy.browser.browser.HtmlPretty
import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlPrettyTest {
    @Test fun indentsTagsAndKeepsScriptsWhole() {
        val html = "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><script>if (a<b) go()</script></head>" +
            "<body><!-- note --><p class=\"x\">Hi<br>there</p></body></html>"
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="utf-8">
                <script>
                  if (a<b) go()
                </script>
              </head>
              <body>
                <!-- note -->
                <p class="x">
                  Hi
                  <br>
                  there
                </p>
              </body>
            </html>
            """.trimIndent() + "\n",
            HtmlPretty.format(html),
        )
    }

    @Test fun deepNestingStaysLinear() {
        val html = "<div>".repeat(1000) + "x" + "</div>".repeat(1000)
        // Uncapped indentation turned these 11,001 characters into about two million.
        assert(HtmlPretty.format(html).length < html.length * 10)
    }
}
