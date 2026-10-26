package io.fireflysoftware.core.organization.core.services.authority.impl;

import io.fireflysoftware.common.config.sdk.api.InstitutionAuthorityApi;
import io.fireflysoftware.common.config.sdk.invoker.ApiClient;
import io.fireflysoftware.core.organization.core.authority.*;
import io.fireflysoftware.core.organization.core.services.authority.InstitutionAuthorityObservationService;
import io.fireflysoftware.core.organization.interfaces.authority.FinancialCalendarUse;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.time.*;
import java.util.*;

public final class InstitutionAuthorityObservationServiceImpl implements InstitutionAuthorityObservationService {
    private static final String SYSTEM_CACHE_KEY="organization-institution-observer";
    private record Caller(String issuer,String subject,String tenant,String kind,String employee,String client,long expiresAt) {}
    private final SecurityContextPort callers;
    private final PolicyDecisionPort policies;
    private final ReactiveClientRegistrationRepository registrations;
    private final ReactiveOAuth2AuthorizedClientManager acquisitions;
    private final TokenValidationPort configurationAudienceValidator;
    private final Map<String,InstitutionAuthorityObserverProfile> profiles;
    private final WebClient transport;
    private final Clock clock;
    private final Set<String> callerIssuers;

    public InstitutionAuthorityObservationServiceImpl(SecurityContextPort callers,PolicyDecisionPort policies,ReactiveClientRegistrationRepository registrations,ReactiveOAuth2AuthorizedClientManager acquisitions,TokenValidationPort configurationAudienceValidator,Map<String,InstitutionAuthorityObserverProfile> profiles,WebClient transport,Clock clock,Set<String> callerIssuers) {
        this.callers=Objects.requireNonNull(callers);this.policies=Objects.requireNonNull(policies);this.registrations=Objects.requireNonNull(registrations);this.acquisitions=Objects.requireNonNull(acquisitions);this.configurationAudienceValidator=Objects.requireNonNull(configurationAudienceValidator);this.profiles=Map.copyOf(profiles);this.transport=Objects.requireNonNull(transport);this.clock=Objects.requireNonNull(clock);this.callerIssuers=Set.copyOf(callerIssuers);
        if(this.callerIssuers.isEmpty())throw new IllegalArgumentException("Organization caller issuer trust required");
        this.profiles.forEach((tenant,profile)->{if(!tenant.equals(profile.tenantId()))throw new IllegalArgumentException("Exact observer tenant registration required");});
    }
    @Override public Mono<VerifiedInstitutionGrantObservation> observe(String tenant,String institution,String issuer,FinancialCalendarUse use) {
        return Mono.defer(()->org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction()
            .flatMap(manager->manager.isActualTransactionActive()?Mono.<VerifiedInstitutionGrantObservation>error(new ResponseStatusException(HttpStatus.CONFLICT,"Organization observation requires a pre-execution transaction boundary")):observeOutsideTransaction(tenant,institution,issuer,use))
            .onErrorResume(org.springframework.transaction.NoTransactionException.class,error->observeOutsideTransaction(tenant,institution,issuer,use)));
    }
    private Mono<VerifiedInstitutionGrantObservation> observeOutsideTransaction(String tenant,String institution,String issuer,FinancialCalendarUse use) {
        return Mono.defer(()-> {
            ConfigurationGrantObservationVerifier.RequestScope scope;
            try{scope=ConfigurationGrantObservationVerifier.requestScope(tenant,institution,issuer,use);}catch(IllegalArgumentException error){return Mono.error(denied());}
            var profile=profiles.get(tenant);if(profile==null)return Mono.error(denied());
            return authorize(scope).flatMap(original->registrations.findByRegistrationId(profile.registrationId()).timeout(Duration.ofSeconds(5)).filter(registration->registered(profile,registration)).switchIfEmpty(Mono.error(denied()))
                .flatMap(registration->acquisitions.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(profile.registrationId()).principal(SYSTEM_CACHE_KEY).build()).timeout(Duration.ofSeconds(5)).switchIfEmpty(Mono.error(denied())))
                .flatMap(client->{
                    var token=client.getAccessToken();
                    if(!registered(profile,client.getClientRegistration()) || token==null || !OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType()) || token.getExpiresAt()==null || !token.getExpiresAt().isAfter(clock.instant()) || token.getTokenValue()==null || token.getTokenValue().length()>16384 || !token.getTokenValue().matches("[A-Za-z0-9._~+/-]+=*"))return Mono.error(denied());
                    return Mono.defer(()->configurationAudienceValidator.validate(BearerToken.of(token.getTokenValue()))).timeout(Duration.ofSeconds(5)).switchIfEmpty(Mono.error(denied())).flatMap(machine->{
                        long machineUntil=machineExpiry(profile,machine,token.getExpiresAt());
                        var clientApi=new ApiClient(transport).setBasePath(profile.configurationBaseUri().toString());clientApi.addDefaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+token.getTokenValue());clientApi.addDefaultHeader(HttpHeaders.CACHE_CONTROL,"no-cache");
                        var api=new InstitutionAuthorityApi(clientApi);
                        return api.observeCurrentInstitutionAuthorityGrantWithResponseSpec(tenant,institution,issuer,use.name(),null).toEntity(byte[].class).timeout(Duration.ofSeconds(5)).switchIfEmpty(Mono.error(unavailable()))
                            .flatMap(response->{
                                if(response.getStatusCode()!=HttpStatus.OK || response.getBody()==null || response.getHeaders().getContentType()==null || !MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType()) || !response.getHeaders().getCacheControl().contains("no-store"))return Mono.error(unavailable());
                                return authorize(scope).map(current->{
                                    if(!original.equals(current) || clock.millis()>=machineUntil)throw denied();
                                    VerifiedInstitutionGrantObservation evidence;
                                    try{evidence=ConfigurationGrantObservationVerifier.verify(tenant,institution,issuer,use,response.getBody(),clock.millis());}catch(IllegalArgumentException error){throw unavailable();}
                                    if(evidence.observation().validUntilEpochMillis()!=null && evidence.observation().validUntilEpochMillis()>machineUntil)throw unavailable();
                                    var bounded=evidence.limitedTo(Math.min(current.expiresAt(),machineUntil));
                                    if("CURRENT_AUTHORIZED".equals(bounded.observation().state()))bounded.requireCurrentAt(clock.millis());return bounded;
                                });
                            });
                    });
                }));
        }).onErrorMap(error->{
            if(error instanceof ResponseStatusException)return error;
            if(error instanceof WebClientResponseException response && (response.getStatusCode().value()==401 || response.getStatusCode().value()==403))return denied();
            return unavailable();
        });
    }
    private Mono<Caller> authorize(ConfigurationGrantObservationVerifier.RequestScope scope) {
        return callers.currentPrincipal().switchIfEmpty(Mono.error(denied())).flatMap(principal->{
            var caller=caller(principal);if(!caller.tenant().equals(scope.tenantId()))return Mono.error(denied());
            var context=new HashMap<String,Object>();context.put("tenantId",scope.tenantId());context.put("institutionId",scope.institutionId());context.put("issuerAuthorityId",scope.issuerAuthorityId());context.put("use",scope.use().name());context.put("canonicalIntent",scope.canonicalIntent());context.put("intentDigest",scope.intentDigest());context.put("actorIssuer",caller.issuer());context.put("actorSubject",caller.subject());context.put("actorTenantId",caller.tenant());context.put("actorKind",caller.kind());
            return Mono.defer(()->policies.authorize(principal,"organization.institution.authority.grant.observe","tenant/"+scope.tenantId()+"/institution-authority/"+scope.issuerAuthorityId(),Map.copyOf(context))).timeout(Duration.ofSeconds(5)).filter(decision->decision.granted()&&decision.obligations().isEmpty()).switchIfEmpty(Mono.error(denied())).map(decision->{if(clock.millis()>=caller.expiresAt())throw denied();return caller;});
        }).onErrorMap(error->denied());
    }
    private Caller caller(SecurityPrincipal principal) {
        if(!callerIssuers.contains(principal.issuer()) || !Objects.equals(principal.claims().get("tenant_id"),principal.tenantId()))throw denied();
        uuid(principal.tenantId());text(principal.subject());var expiry=expiry(principal);var kind=principal.claims().get("principal_kind");String employee=null,client=null;
        if("STAFF".equals(kind)){if(!(principal.claims().get("staff_actor_id")instanceof String value))throw denied();uuid(value);employee=value;if(principal.claims().get("client_id")instanceof String valueClient){text(valueClient);client=valueClient;}}
        else if("MACHINE".equals(kind)){if(principal.claims().containsKey("staff_actor_id") || !(principal.claims().get("client_id")instanceof String value))throw denied();text(value);client=value;}
        else throw denied();
        return new Caller(principal.issuer(),principal.subject(),principal.tenantId(),(String)kind,employee,client,expiry);
    }
    private long machineExpiry(InstitutionAuthorityObserverProfile profile,SecurityPrincipal principal,Instant tokenExpiry) {
        var rawTenant=principal.claims().get("tenant_id");var projected=principal.tenantId()==null?rawTenant:principal.tenantId();
        if(!profile.issuer().equals(principal.issuer()) || !profile.subject().equals(principal.subject()) || !profile.tenantId().equals(projected) || !profile.tenantId().equals(rawTenant) || !"MACHINE".equals(principal.claims().get("principal_kind")) || principal.claims().containsKey("staff_actor_id") || !profile.clientId().equals(principal.claims().get("client_id")) || !audience(principal.claims().get("aud"),profile.audience()))throw denied();
        return Math.min(expiry(principal),tokenExpiry.toEpochMilli());
    }
    private long expiry(SecurityPrincipal principal) {
        if(!(principal.claims().get("exp")instanceof Instant expiry) || !expiry.isAfter(clock.instant()) || expiry.isAfter(Instant.parse("9999-12-31T23:59:59Z")))throw denied();
        var notBefore=principal.claims().get("nbf");if(notBefore!=null && (!(notBefore instanceof Instant instant) || instant.isAfter(clock.instant())))throw denied();return expiry.toEpochMilli();
    }
    private static boolean audience(Object value,String expected){return value instanceof String text?expected.equals(text):value instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance) && values.contains(expected);}
    private static boolean registered(InstitutionAuthorityObserverProfile profile,ClientRegistration registration){return registration!=null && profile.registrationId().equals(registration.getRegistrationId()) && AuthorizationGrantType.CLIENT_CREDENTIALS.equals(registration.getAuthorizationGrantType()) && profile.clientId().equals(registration.getClientId()) && profile.tokenEndpoint().toString().equals(registration.getProviderDetails().getTokenUri());}
    private static void text(String value){if(value==null || value.isBlank() || value.length()>255 || value.codePoints().anyMatch(Character::isISOControl))throw denied();}
    private static void uuid(String value){try{if(value==null || value.equals("00000000-0000-0000-0000-000000000000") || !UUID.fromString(value).toString().equals(value))throw denied();}catch(IllegalArgumentException error){throw denied();}}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Organization institution observation denied");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Configuration authority observation unavailable");}
}
