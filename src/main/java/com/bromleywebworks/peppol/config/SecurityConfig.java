package com.bromleywebworks.peppol.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final FreeAgentOAuth2UserService freeAgentOAuth2UserService;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers(
                    "/", "/about", "/contact", "/faq", "/how-it-works",
                    "/pricing", "/privacy", "/terms",
                    "/blog", "/blog/**",
                    "/freeagent-to-peppol", "/freeagent-to-peppol/**",
                    "/api/convert",
                    "/freeagent-login",
                    "/css/**", "/js/**", "/img/**", "/webjars/**",
                    "/favicon.ico", "/error"
                ).permitAll()
                .antMatchers("/freeagent/invoices", "/freeagent/convert/**", "/freeagent/my-invoices", "/freeagent/logout")
                .authenticated()
                .anyRequest().permitAll()
            .and()
            .oauth2Login()
                .loginPage("/freeagent-login")
                .defaultSuccessUrl("/freeagent/invoices", true)
                .failureHandler((request, response, exception) -> {
                    log.error("OAuth2 login failed: {}", exception.getMessage(), exception);
                    if (exception.getCause() != null) {
                        log.error("Root cause: {}", exception.getCause().getMessage(), exception.getCause());
                    }
                    response.sendRedirect("/freeagent-login?error=true");
                })
                .userInfoEndpoint()
                    .userService(freeAgentOAuth2UserService)
            .and()
            .and()
            .logout()
                .logoutUrl("/freeagent/logout")
                .logoutSuccessUrl("/")
                .clearAuthentication(true)
                .invalidateHttpSession(true)
            .and()
            .csrf()
                .ignoringAntMatchers("/api/convert", "/freeagent-to-peppol/upload", "/freeagent/convert/**");
    }
}
