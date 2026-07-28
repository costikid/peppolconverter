package com.bromleywebworks.peppol.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class FreeAgentOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String userInfoUri = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUri();

        String userNameAttr = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();
        if (userNameAttr == null) {
            userNameAttr = "email";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(userRequest.getAccessToken().getTokenValue());
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    userInfoUri, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("freeagent_userinfo_error",
                                "Failed to fetch FreeAgent user info: " + response.getStatusCode(), null));
            }

            JsonNode root = MAPPER.readTree(response.getBody());
            JsonNode userNode = root.has("user") ? root.get("user") : root;

            Map<String, Object> attributes = new HashMap<>();
            userNode.fields().forEachRemaining(e ->
                    attributes.put(e.getKey(), e.getValue().isTextual() ? e.getValue().asText() : e.getValue()));

            log.info("FreeAgent user info loaded: {}={}", userNameAttr, attributes.get(userNameAttr));

            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
            return new DefaultOAuth2User(authorities, attributes, userNameAttr);

        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load FreeAgent user info", e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("freeagent_userinfo_error", e.getMessage(), null), e);
        }
    }
}
