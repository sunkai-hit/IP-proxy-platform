package com.ipproxy.platform.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductAuthorizationSecurityTest {
    @Test void internalCheckFailsClosedIfIntegrationKeyIsAbsentOrShort() {
        ProductAuthorizationService decisions=mock(ProductAuthorizationService.class);
        var req=new ProductAuthorizationService.CheckRequest("2","3",null,"SHORT_EXTRACT",1,"r");
        var off=new ProductAuthorizationController(decisions,"");
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,
                ()->off.check("Bearer some-token",req));
        assertEquals(HttpStatus.FORBIDDEN,ex.getStatusCode());
        assertThrows(ResponseStatusException.class,()->off.check(null,req));
        assertThrows(ResponseStatusException.class,
                ()->new ProductAuthorizationController(decisions,"short").check("Bearer short",req));
        verifyNoInteractions(decisions);
    }
    @Test void wrongProductBearerCannotReadAnyDecision() {
        ProductAuthorizationService decisions=mock(ProductAuthorizationService.class);
        var ctrl=new ProductAuthorizationController(decisions,"this-is-a-long-private-product-integration-token");
        var req=new ProductAuthorizationService.CheckRequest("2","3",null,"SHORT_EXTRACT",1,"r");
        assertThrows(ResponseStatusException.class,()->ctrl.check("Bearer wrong",req));
        verifyNoInteractions(decisions);
    }
    @Test void revokedLegacyGrantCannotBeReissuedByLogin() {
        OpenApiMapper db=mock(OpenApiMapper.class);
        PasswordEncoder password=mock(PasswordEncoder.class);
        when(db.account("customer-a")).thenReturn(Map.of(
                "id",7L,"customer_id",9L,"password_hash","hash",
                "account_status","ACTIVE","customer_status","ACTIVE"));
        when(password.matches("secret","hash")).thenReturn(true);
        when(db.eligibleServices(9L)).thenReturn(List.of(Map.of("service_id",8L)));
        when(db.grant(7L,8L)).thenReturn(Map.of("id",10L,"token_version",3L,"status","REVOKED"));
        OpenApiService service=new OpenApiService(db,password,new ObjectMapper(),
                new JwtProperties("dev-test-secret-not-for-production-123456",3600));
        BusinessException ex=assertThrows(BusinessException.class,
                ()->service.issueToken("customer-a","secret",false,"127.0.0.1"));
        assertEquals("OPEN_AUTH_REVOKED",ex.getCode());
        verify(db,never()).rotateGrant(anyLong());
    }
}
