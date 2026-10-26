package io.fireflysoftware.core.organization.core.authority;

import java.net.URI;

public record InstitutionAuthorityObserverProfile(String tenantId,String registrationId,String issuer,String clientId,String subject,String audience,URI configurationBaseUri,URI tokenEndpoint) {
    public InstitutionAuthorityObserverProfile {
        AuthorityWire.uuid(tenantId);AuthorityWire.text(registrationId,255);AuthorityWire.text(clientId,255);AuthorityWire.text(subject,255);AuthorityWire.text(audience,500);
        endpoint(URI.create(issuer));endpoint(configurationBaseUri);endpoint(tokenEndpoint);
    }
    private static void endpoint(URI value) {
        if(value==null || !"https".equals(value.getScheme()) || value.getHost()==null || value.getUserInfo()!=null || value.getRawQuery()!=null || value.getFragment()!=null)throw new IllegalArgumentException("Trusted HTTPS observer endpoint required");
    }
}
