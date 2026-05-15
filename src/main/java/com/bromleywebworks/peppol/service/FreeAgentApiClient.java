package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.freeagent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(OAuth2AuthorizedClientManager.class)
public class FreeAgentApiClient {

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final WebClient.Builder webClientBuilder;

    private WebClient getWebClient() {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
            new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Filter.setDefaultClientRegistrationId("freeagent");

        return webClientBuilder
            .baseUrl("https://api.freeagent.com/v2")
            .defaultHeader("Accept", "application/json")
            .apply(oauth2Filter.oauth2Configuration())
            .build();
    }

    public Mono<FreeAgentInvoice> getInvoice(String invoiceId) {
        return getWebClient()
            .get()
            .uri("/invoices/{id}", invoiceId)
            .retrieve()
            .bodyToMono(FreeAgentInvoiceResponse.class)
            .map(response -> response.getInvoice());
    }

    public Flux<FreeAgentInvoiceSummary> listInvoices(int page, int perPage) {
        return getWebClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/invoices")
                .queryParam("page", page)
                .queryParam("per_page", perPage)
                .queryParam("sort", "-dated_on")
                .build())
            .retrieve()
            .bodyToMono(FreeAgentInvoicesResponse.class)
            .flatMapMany(response -> Flux.fromIterable(response.getInvoices()));
    }

    public Mono<FreeAgentContact> getContact(String contactUrl) {
        String contactId = contactUrl.split("/contacts/")[1];
        return getWebClient()
            .get()
            .uri("/contacts/{id}", contactId)
            .retrieve()
            .bodyToMono(FreeAgentContactResponse.class)
            .map(response -> response.getContact());
    }

    public Mono<FreeAgentCompany> getCompany() {
        return getWebClient()
            .get()
            .uri("/company")
            .retrieve()
            .bodyToMono(FreeAgentCompanyResponse.class)
            .map(response -> response.getCompany());
    }
}
