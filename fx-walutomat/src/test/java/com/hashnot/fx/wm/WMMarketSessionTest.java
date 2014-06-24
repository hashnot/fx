package com.hashnot.fx.wm;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.hashnot.fx.spi.Offer;
import org.junit.Test;

import java.util.List;

import static java.lang.System.out;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WMMarketSessionTest {

    @Test
    public void testGetOffers() throws Exception {
        WebClient webClient = mock(WebClient.class);
        WebResponse response = mock(WebResponse.class);
        Page page = mock(Page.class);

        when(webClient.getPage((WebRequest)any())).thenReturn(page);
        when(page.getWebResponse()).thenReturn(response);
        when(response.getContentAsStream()).thenReturn(getClass().getClassLoader().getResourceAsStream("wymiana_walut.xml"));

        List<Offer> offers = new WMMarketSession(null, webClient).getOffers("EUR", "PLN");
        for (Offer offer : offers) {
            out.println(offer);
        }
    }
}