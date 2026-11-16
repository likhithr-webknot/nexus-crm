package com.nexus.crm.identity.auth;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.Role;
import com.nexus.crm.shared.web.RequestIdFilter;

/** Security rules: sign-in and token refresh are open; everything else needs a signed-in user, and changes need the CSRF token. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

	private static final String AUTHORIZATION_BASE_URI = "/api/v1/auth/oauth2/authorization";

	@Bean
	@Order(1)
	SecurityFilterChain signInFilterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrations,
			CookieOAuth2AuthorizationRequestRepository authorizationRequests, OidcSignInHandlers handlers) {
		http.securityMatcher("/api/v1/auth/oauth2/**", "/api/v1/auth/login/**", AuthCookies.REFRESH_PATH)
			.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
			// No CSRF token needed here. Google sign-in has its own protection (the state value), and the refresh cookie is never sent on requests from other sites.
			.csrf(AbstractHttpConfigurer::disable)
			.oauth2Login((login) -> login
				.authorizationEndpoint((endpoint) -> endpoint.baseUri(AUTHORIZATION_BASE_URI)
					.authorizationRequestResolver(authorizationRequestResolver(clientRegistrations))
					.authorizationRequestRepository(authorizationRequests))
				.redirectionEndpoint((endpoint) -> endpoint.baseUri("/api/v1/auth/login/oauth2/code/*"))
				// We only ask Google who the user is. We never call Google again, so its token is not kept.
				.authorizedClientRepository(new DiscardingAuthorizedClientRepository())
				.successHandler(handlers)
				.failureHandler(handlers));
		return stateless(http).build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain apiFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, UserService users,
			AuthProperties properties, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) {
		// Send "not signed in" (401) and "not allowed" (403) to the global exception handler, so they look like every other error response.
		AuthenticationEntryPoint notSignedIn = (request, response, ex) -> errors.resolveException(request, response,
				null, ex);
		AccessDeniedHandler notAllowed = (request, response, ex) -> errors.resolveException(request, response, null,
				ex);

		CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfTokens.setCookieCustomizer((cookie) -> cookie.secure(properties.cookies().secure()).sameSite("Lax"));

		http.authorizeHttpRequests((requests) -> requests
				// Health checks are open, because monitoring tools cannot sign in.
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.anyRequest().authenticated())
			// CSRF protection: the frontend reads the XSRF-TOKEN cookie and sends it back in the X-XSRF-TOKEN header.
			.csrf((csrf) -> csrf.csrfTokenRepository(csrfTokens).spa())
			.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
			.addFilter(accessTokenFilter(jwtDecoder, users, notSignedIn))
			.exceptionHandling((handling) -> handling.authenticationEntryPoint(notSignedIn)
				.accessDeniedHandler(notAllowed));
		return stateless(http).build();
	}

	/** Signs the user in on each request, using the access token in the {@code nx_at} cookie. Important: do not replace this with {@code http.oauth2ResourceServer(...)}. That is unsafe for us, because our token is in a cookie and the browser sends cookies automatically, even on requests made by other websites. */
	private static BearerTokenAuthenticationFilter accessTokenFilter(JwtDecoder jwtDecoder, UserService users,
			AuthenticationEntryPoint notSignedIn) {
		JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
		provider.setJwtAuthenticationConverter((jwt) -> currentUser(jwt, users));
		BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(new ProviderManager(provider));
		// Read the token from the cookie, not from an Authorization header.
		filter.setBearerTokenResolver((request) -> AuthCookies.read(request, AuthCookies.ACCESS_TOKEN));
		filter.setAuthenticationEntryPoint(notSignedIn);
		return filter;
	}

	/** Turns a valid access token into the {@link CurrentUser} for the request. */
	private static AbstractAuthenticationToken currentUser(Jwt jwt, UserService users) {
		UUID userId;
		UUID sessionId;
		try {
			userId = UUID.fromString(jwt.getSubject());
			sessionId = UUID.fromString(jwt.getClaimAsString(SessionService.SESSION_CLAIM));
		}
		catch (RuntimeException ex) {
			throw new InvalidBearerTokenException("Malformed access token");
		}
		Role role = users.activeRole(userId).orElseThrow(() -> new InvalidBearerTokenException("User is not active"));
		return UsernamePasswordAuthenticationToken.authenticated(new CurrentUser(userId, role, sessionId), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
	}

	private static HttpSecurity stateless(HttpSecurity http) {
		return http.cors(Customizer.withDefaults())
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.requestCache(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable);
	}

	/** Only the frontend's address may call the API from a browser. */
	@Bean
	CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(List.of(properties.frontendUrl()));
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		cors.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, "X-XSRF-TOKEN"));
		cors.setExposedHeaders(List.of(RequestIdFilter.HEADER, HttpHeaders.RETRY_AFTER));
		cors.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}

	private static OAuth2AuthorizationRequestResolver authorizationRequestResolver(
			ClientRegistrationRepository clientRegistrations) {
		DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
				clientRegistrations, AUTHORIZATION_BASE_URI);
		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce()
			// Always let the user pick a Google account. Otherwise someone who used the wrong account would keep being signed in with it and could never switch.
			.andThen((request) -> request
				.additionalParameters((parameters) -> parameters.put("prompt", "select_account"))));
		return resolver;
	}

	/** Makes sure the XSRF-TOKEN cookie is sent to the browser. */
	private static final class CsrfCookieFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
				token.getToken();
			}
			chain.doFilter(request, response);
		}

	}

	/** Throws away the token Google gives us, because we never use it. */
	private static final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

		@Override
		public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
				Authentication principal, HttpServletRequest request) {
			return null;
		}

		@Override
		public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
				HttpServletRequest request, HttpServletResponse response) {
		}

		@Override
		public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
				HttpServletRequest request, HttpServletResponse response) {
		}

	}

}
