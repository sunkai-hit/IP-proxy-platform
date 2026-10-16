package com.ipproxy.platform.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Legacy res_ip snapshots must NEVER be served as verified proxies in production.
 *  The only compatibility path is an explicit opt-in under m6-mock test profile. */
class OpenApiLegacyExtractionGateTest {
    private OpenApiService service(OpenApiMapper db, boolean enabled, boolean mockProfile) {
        var service=new OpenApiService(db,mock(PasswordEncoder.class),
                new ObjectMapper(),new JwtProperties("ci-test-key-for-unit-gates-1234567890",3600));
        var env=mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(mockProfile);
        ReflectionTestUtils.setField(service,"legacyShortExtractEnabled",enabled);
        ReflectionTestUtils.setField(service,"environment",env);
        return service;
    }

    @Test void disabledByDefaultEvenInMockProfileAndDoesNotReadLegacyIp() {
        var db=mock(OpenApiMapper.class);
        var e=assertThrows(BusinessException.class,
                ()->service(db,false,true).extract("",1,"HTTP","","","127.0.0.1"));
        assertEquals("OPEN_REAL_SHORT_POOL_NOT_CONNECTED",e.getCode());
        verifyNoInteractions(db);
    }

    @Test void explicitOptInStillRequiresMockProfile() {
        var db=mock(OpenApiMapper.class);
        var e=assertThrows(BusinessException.class,
                ()->service(db,true,false).extract("",1,"HTTP","","","127.0.0.1"));
        assertEquals("OPEN_REAL_SHORT_POOL_NOT_CONNECTED",e.getCode());
        verifyNoInteractions(db);
    }

    @Test void mockWithOptInRetainsNormalTokenValidation() {
        var db=mock(OpenApiMapper.class);
        var e=assertThrows(BusinessException.class,
                ()->service(db,true,true).extract("",1,"HTTP","","","127.0.0.1"));
        assertEquals("OPEN_TOKEN_REQUIRED",e.getCode());
        verifyNoInteractions(db);
    }
}
