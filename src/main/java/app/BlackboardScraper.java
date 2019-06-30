package app;

import app.util.WebTools;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.util.NameValuePair;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * Interacts with NTNU Blackboard to get relevant announcement for your class subjects.
 */
public class BlackboardScraper {
    private WebClient client;
    private CookieManager cm;

    private String username;
    private String password;

    /**
     * Sets up the client that will navigate though and get the announcements.
     *
     * @param username  FEIDE username
     * @param password  FEIDE password
     */
    public BlackboardScraper(String username, String password) {
        this.username = username;
        this.password = password;

        // Turn off console logging
        java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);

        // Define web browser options optimized for Blackboard
        client = new WebClient(BrowserVersion.CHROME);
        client.getOptions().setJavaScriptEnabled(true);
        client.getOptions().setThrowExceptionOnScriptError(false);
        client.getOptions().setCssEnabled(false);

        // Enable and save the cookie manager (so we can extract each cookies later)
        cm = client.getCookieManager();
        cm.setCookiesEnabled(true);
    }

    /**
     * Collects announcement from Blackboard using the given credentials.
     * First, it logs into Blackboard using FEIDE and tries to go past all additional steps because of JS
     * incompatibilities. By then, all cookies has been generated. The cookies is used to send a request to the
     * Blackboard API to fetch the activity stream.
     *
     * @return                  Blackboard activity stream data as JSON string
     * @throws IOException      when something goes wrong, ironically
     */
    public String getStreamEntries() throws IOException {
        // Navigate to blackboard-feide login
        HtmlPage page = (HtmlPage) client.getPage("http://innsida.ntnu.no/blackboard");

        // Fetch the appropriate form to modify and send
        HtmlForm form = ((HtmlForm) page.getFormByName("f"));

        // Assign user credentials to the text fields
        form.getInputByName("feidename").setValueAttribute(username);
        form.getInputByName("password").setValueAttribute(password);

        // Press login button and grab the redirected page
        page = ((HtmlButton) page.getFirstByXPath("//button[@type='submit']")).click();

        // FEIDE requires pressing the submit button after submission (because of javscript issues)
        client.waitForBackgroundJavaScript(5000);
        page = ((HtmlSubmitInput) page.getElementById("postLoginSubmitButton")).click();

        // The login session has been started and the session will be used to read announcements
        page = client.getPage("https://ntnu.blackboard.com/webapps/portal/execute/tabs/tabAction?tab_tab_group_id=_70_1");
        client.waitForBackgroundJavaScript(1000);

        // Since problems occur with JS on this client, it must bypass another layer of confirmation before entering
        // the dashboard
        page = ((HtmlHiddenInput) page.getFirstByXPath("//input[@name='SAMLResponse']")).click();
        client.waitForBackgroundJavaScript(5000);

        // Create a post request for BB announcements
        URL announcementUrl = new URL("https://ntnu.blackboard.com/webapps/streamViewer/streamViewer");
        WebRequest request = new WebRequest(announcementUrl, HttpMethod.POST);

        // Request body to get data
        request.setRequestParameters(new ArrayList<NameValuePair>());
        request.getRequestParameters().add(new NameValuePair("cmd", "loadStream"));
        request.getRequestParameters().add(new NameValuePair("streamName", "alerts"));
        request.getRequestParameters().add(new NameValuePair("providers", "%7B%7D"));
        request.getRequestParameters().add(new NameValuePair("forOverview", "false"));

        // Request header
        request.setAdditionalHeader("Accept", "*/*");
        request.setAdditionalHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:68.0) Gecko/20100101 Firefox/68.0");
        request.setAdditionalHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        request.setAdditionalHeader("X-Requested-With", "XMLHttpRequest");
        request.setAdditionalHeader("Cache-Control", "no-cache");
        request.setAdditionalHeader("Cookie", WebTools.cookiesAsRequestHeader(cm.getCookies()));

        // Issues the request, but it will empty and useless json. To fix this, the same request must be sent again
        client.getPage(request);
        client.waitForBackgroundJavaScript(2000);

        // The proper results will display on the 2nd request with the exact same request values
        return client.getPage(request).getWebResponse().getContentAsString();
    }
}
