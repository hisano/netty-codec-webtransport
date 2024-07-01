package jp.hisano.netty.webtransport;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

public class WebTransportTest {
	@Test
	public void testBasic() {
		try (Playwright playwright = Playwright.create()) {
			Browser browser = playwright.chromium().launch();
			Page page = browser.newPage();
			page.navigate("http://playwright.dev");
			System.out.println(page.title());
		}
	}
}
