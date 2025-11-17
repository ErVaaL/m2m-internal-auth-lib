## Library for m2m secured connections

This library provides a simple interface for establishing machine-to-machine (m2m) secured connections to REST API projects. It handles requests based on client and server ids, secret and scopes.

## Protocol overview

The library implements a custom OAuth2-like protocol for internal service-to-service authentication.

```
POST /internal/oauth/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(client_id:client_secret)
response: { access_token, token_type, expires_in, scope, exp }
usage: Secured-Authorization: Bearer {access_token}

(custom header name can be configured)
```

This protocol should be implemented for situations where one service is made in Java and another in different language/framework.

The library / protocol itself is not tied to REST and should also work with technologies like: gRPC, WebSockets, RabbbitMQ, Kafka, etc.

### Java Maven project implementation:

To have library visible for your project either:

Download the repo and put it as a submodule in your project or:

Download the project and run in terminal:
```bash
 mvn clean install
```
Then you will be able to find the .jar files in your local maven repository (on your machine)
The files should be under
```
{maven localization}/repository/com/m2m/internal/.../server|client/...
```
- current version: 1.0-SNAPSHOT
### Add them to your projects:
### Server (issuer)
```xml
<dependency>
    <groupId>com.m2m</groupId>
    <artifactId>internal-auth-server</artifactId>
    <version>${version}</version>
</dependency>
```

### Client (recipient)
```xml
<dependency>
    <groupId>com.m2m</groupId>
    <artifactId>internal-auth-client</artifactId>
    <version>${version}</version>
</dependency>
```

## Required application.yml entries:
> The library does not automatically load clients from YAML.
For Spring Boot users, see "Multiple clients from application.yml" section below.

### Client:
```yaml
internal:
    oauth:
        token-url: "main backend url (suggested: .../oauth/token)"
        client-id: "can be module name"
        client-secret: "client secret, may be generated random and passed with env variables"
        scopes:
            - SCOPE_1
            - SCOPE_2
        header-name: "Header to pass Bearer {token} with (default: Secured-Authorization)"
        skew-seconds: 30
```

### Server:
```yaml
internal:
    clients:
      servicename1:
          secret: "secret - client secret for chosen backend (the same as internal.oauth.client-secret on client side)"
          scopes:
              - SCOPE_1
              - SCOPE_2
      servicename2:
          secret: "secret - client secret for chosen backend (the same as internal.oauth.client-secret on client side)"
          scopes:
              - SCOPE_3
```

# Example usage:
The library requires configuration file. See below example config file in Spring Boot application.

## Server:

### Configuration for master server, manages all the client servers
```java
@Configuration
public class InternalAuthConfig {
    @Bean
    public KeyPair internalKeyPair() {

        // For demo: generate random key pair at startup
        // In prod: load from PEM files, place them in secure location ex.
        // resources/keys/{private/public}.pem

        return PemKeyLoader.generateRsaKeyPair(2048);
    }

    @Bean
    public InternalTokenIssuer internalTokenIssuer(KeyPair internalKeyPair) {

        // For demo: fixed values
        // In prod: configure via properties

        String issuer = "m2m-demo"; // Issuer: acts as the master server's name
        String audience = "m2m-internal"; // Audience: intended recipients of the token, security configuration can
                                          // verify if given audience can access chosen resource
        long ttlSeconds = 600; // Token time-to-live in seconds
        return new RsaInternalTokenIssuer(internalKeyPair, issuer, audience, ttlSeconds);
    }

    @Bean
    public InternalClientStore internalClientStore() {
        // For demo: in-memory store with a single client
        // In prod: implement a persistent store, using properties is recommended for
        // configuration. Suggested: redis
        InMemoryInternalClientStore.Client demoClient = new InMemoryInternalClientStore.Client(
                "demo-client", // can be replaced with ${...} to read from properties
                "super-secret", // can and should be replaced with ${...} to read from secure properties
                Set.of("SCOPE_TEST"));
        Map<String, InMemoryInternalClientStore.Client> map = Map.of(demoClient.id(), demoClient);
        return new InMemoryInternalClientStore(map);
    }

    @Bean
    public InternalOAuthService internalOAuthService(
            InternalClientStore store,
            InternalTokenIssuer issuer) {
        // Combines client store and token issuer to provide OAuth service
        return new InternalOAuthService(store, issuer);
    }
}
```
### Note:
> For multiple clients consider adding properties config like these:

```java
@Configuration
@EnableConfigurationProperties(InternalClientsProperties.class)
public class InternalClientProperties {
    // empty: just turns on @ConfigurationProperties
}
```
```java
@ConfigurationProperties(prefix = "internal.clients")
public record InternalClientsProperties(
    Map<String, ClientProps> entries
) {
    public record ClientProps(
        String secret,
        List<String> scopes
    ) {}
}
```
And then config for client store:
```java
@Configuration
public class InternalClientStoreConfig {

    @Bean
    public InternalClientStore internalClientStore(InternalClientsProperties props) {
        Map<String, InMemoryInternalClientStore.Client> map =
            props.entries().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> toClient(e.getKey(), e.getValue())
                ));

        return new InMemoryInternalClientStore(map);
    }

    private InMemoryInternalClientStore.Client toClient(
        String clientId,
        InternalClientsProperties.ClientProps props
    ) {
        Set<String> scopes = props.scopes() == null
            ? Set.of()
            : Set.copyOf(props.scopes());

        return new InMemoryInternalClientStore.Client(
            clientId,
            props.secret(),
            scopes
        );
    }
}
```

### Example usage in security config:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final KeyPair internalKeyPair;

    public SecurityConfig(KeyPair internalKeyPair) {
        this.internalKeyPair = internalKeyPair;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var resolver = new DefaultBearerTokenResolver();
        resolver.setBearerTokenHeaderName("Secured-Authorization");
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/internal/auth/**").permitAll() // Open endpoint for token acquisition
                .requestMatchers("/secured/**").hasAuthority("SCOPE_TEST")// Endpoint that requires token with: SCOPE_TEST
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth -> oauth
                .bearerTokenResolver(resolver)
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthConverter())
                )
            );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) internalKeyPair.getPublic()).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        var converter = new JwtAuthenticationConverter();
        var gac = new JwtGrantedAuthoritiesConverter();
        gac.setAuthoritiesClaimName("scp");
        gac.setAuthorityPrefix("");
        converter.setJwtGrantedAuthoritiesConverter(gac);
        return converter;
    }
}
```
### Lastly controller endpoint is required to mint and obtain the token for the service
```java
    // OAuth2 Token Endpoint, required by the Internal Auth spec
    // Used to provide access tokens to client backends
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "scope", required = false) String scope) {
        InternalOAuthService.TokenResult result = service
                .token(new InternalOAuthService.TokenRequest(auth, grantType, scope));

        if (result instanceof InternalOAuthService.TokenResult.Error(int httpStatus, String errorCode)) {
            return ResponseEntity
                    .status(httpStatus)
                    .body(Map.of("error", errorCode));
        }

        InternalOAuthService.TokenResult.Success s = (InternalOAuthService.TokenResult.Success) result;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", s.accessToken());
        body.put("token_type", s.tokenType());
        body.put("expires_in", s.expiresIn());
        body.put("scope", s.scope());
        body.put("exp", s.exp());
        return ResponseEntity.ok(body);
    }
```


## Client:
```java
@Configuration
public class InternalAuthClientConfig {

    // Base HttpClient bean used by Internal OAuth Client
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Bean
    public InternalOAuthClientConfig internalOAuthClientConfig(
        @Value("${internal.oauth.token-url}") URI tokenUrl, // Main service URL
        @Value("${internal.oauth.client-id}") String clientId, // Client ID registered for this service
        @Value("${internal.oauth.client-secret}") String clientSecret, // Client secret registered for this service
        @Value("${internal.oauth.scopes:}") String scopes, // Scopes requested for access tokens
        @Value("${internal.oauth.header-name:Secured-Authorization}") String headerName, // Header name to use for auth,
        // must match server config
        @Value("${internal.oauth.skew-seconds:30}") long skewSeconds // Clock skew allowance in seconds
    ) {
        var cfg = new InternalOAuthClientConfig();
        cfg.setTokenUrl(tokenUrl);
        cfg.setClientId(clientId);
        cfg.setClientSecret(clientSecret);
        if (scopes != null && !scopes.isBlank()) {
            cfg.setScopes(Set.of(scopes.split("\\s+")));
        }
        cfg.setHeaderName(headerName);
        cfg.setSkewSeconds(skewSeconds);
        return cfg;
    }

    // Beans for Internal Auth Client
    // Used to obtain access tokens from master service
    @Bean
    public InternalOAuthClient internalOAuthClient(HttpClient http, InternalOAuthClientConfig cfg) {
        return new InternalOAuthClient(
            http,
            cfg.getTokenUrl(),
            cfg.getClientId(),
            cfg.getClientSecret(),
            cfg.joinedScope()
        );
    }

    // Bean to manage access tokens, including refreshing them as needed
    // Uses the InternalOAuthClient to obtain tokens
    @Bean
    public InternalAccessTokenManager internalAccessTokenManager(
        InternalOAuthClient oauth,
        InternalOAuthClientConfig cfg
    ) {
        return new InternalAccessTokenManager(oauth, Duration.ofSeconds(cfg.getSkewSeconds()));
    }

    // Bean to provide the auth header for requests
    // Uses the InternalAccessTokenManager to get current token
    @Bean
    public InternalAuthHeader internalAuthHeader(
        InternalAccessTokenManager tm,
        InternalOAuthClientConfig cfg
    ) {
        return new InternalAuthHeader(tm, cfg.getHeaderName());
    }

    // Bean to periodically refresh tokens in the background
    // Uses the InternalAccessTokenManager to refresh tokens
    @Bean(destroyMethod = "close")
    public TokenRefresher tokenRefresher(InternalAccessTokenManager tm) {
        return new TokenRefresher(tm);
    }

    // WebClient filter to add the internal auth header to requests
    // Uses the InternalAuthHeader to get the header name and current token
    @Bean
    public ExchangeFilterFunction internalBearerFilter(InternalAuthHeader header) {
        return (request, next) -> next.exchange(
            ClientRequest.from(request)
                .headers(h -> h.set(header.headerName(), "Bearer " + header.tokenManager().currentToken()))
                .build()
        );
    }

    // WebClient bean configured to use the internal auth filter
    // If there are multiple web clients, separate web client config class is preferred
    // Move it to that class to avoid circular dependencies
    @Bean
    public WebClient backendAClient(WebClient.Builder builder,
                                    ExchangeFilterFunction internalBearerFilter) {
        return builder
            .baseUrl("http://localhost:8080")
            .filter(internalBearerFilter)
            .build();
    }
}
```
#### Also if there are multiple webclients adding this config is suggested:
```java
@Configuration
public class WebClientConfig {

    @Bean
    @Qualifier("mainBackendClient")
    public WebClient mainBackendClient(WebClient.Builder builder,
                                       ExchangeFilterFunction internalBearerFilter,
                                       @Value("${microservice.main.ip}") String mainServiceIp) {
                return builder
                    .baseUrl(mainServiceIp)
                    .filter(internalBearerFilter)
                    .build();
    }

    // Other clients
}
```
