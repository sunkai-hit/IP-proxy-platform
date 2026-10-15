package com.ipproxy.platform.service;

import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import com.ipproxy.platform.service.v12.V12ServiceLifecycleService;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceCredentialVisibilityTest {
    private static OrderServiceController controller(V12ServiceLifecycleService lifecycle) {
        return new OrderServiceController(mock(OrderServiceService.class),lifecycle);
    }
    private static Map<String,Object> sample(){
        Map<String,Object> svc=new LinkedHashMap<>();
        svc.put("id",17L);
        svc.put("service_no","CI-MASKED");
        svc.put("credentials",List.of(Map.of(
            "id",6L,"account_name","masked-user","secret_mask","***1234",
            "status","ACTIVE")));
        svc.put("resourceBindings",List.of());
        svc.put("changes",List.of(Map.of("change_no","C-1","before_snapshot","sensitive audit details")));
        svc.put("releaseRecords",List.of(Map.of("release_no","R-1")));
        svc.put("longMonitors",List.of(Map.of("monitor_no","M-1")));
        return svc;
    }
    @Test void serviceReadAloneCannotSeeCredentialsSubtable(){
        V12ServiceLifecycleService lifecycle=mock(V12ServiceLifecycleService.class);
        Map<String,Object> original=sample();
        when(lifecycle.service(17L)).thenReturn(original);
        UserPrincipal reader=new UserPrincipal(5L,"reader","Reader",List.of("OPERATOR"),
                List.of("service:read"));
        Object response=controller(lifecycle).detail(17L,reader).data();
        assertInstanceOf(Map.class,response);
        @SuppressWarnings("unchecked")
        Map<String,Object> body=(Map<String,Object>)response;
        assertEquals("CI-MASKED",body.get("service_no"));
        assertFalse(body.containsKey("credentials"),"service:read cannot bypass credential:read");
        assertFalse(body.containsKey("changes"),"change history has independent read permission");
        assertFalse(body.containsKey("releaseRecords"),"release records require service:release");
        assertFalse(body.containsKey("longMonitors"),"monitor history requires long-monitor:read");
        assertTrue(original.containsKey("credentials"),"the filtered response must not mutate source data");
        verify(lifecycle).service(17L);
    }
    @Test void independentCredentialPermissionMaySeeOnlyMaskedRecord(){
        V12ServiceLifecycleService lifecycle=mock(V12ServiceLifecycleService.class);
        when(lifecycle.service(17L)).thenReturn(sample());
        UserPrincipal credentialReader=new UserPrincipal(6L,"auditor","Auditor",List.of("OPERATOR"),
                List.of("service:read","service:credential:read"));
        @SuppressWarnings("unchecked")
        Map<String,Object> body=(Map<String,Object>)controller(lifecycle).detail(17L,credentialReader).data();
        assertTrue(body.containsKey("credentials"));
        assertEquals("***1234",((Map<?,?>)((List<?>)body.get("credentials")).getFirst()).get("secret_mask"));
    }
    @Test void detailedSubtablePermissionsAreIndependent(){
        V12ServiceLifecycleService lifecycle=mock(V12ServiceLifecycleService.class);
        when(lifecycle.service(17L)).thenReturn(sample());
        UserPrincipal changeReader=new UserPrincipal(9L,"change","Change",List.of("OPERATOR"),
                List.of("service:read","service:change:read"));
        @SuppressWarnings("unchecked")
        Map<String,Object> changeResult=(Map<String,Object>)controller(lifecycle).detail(17L,changeReader).data();
        assertTrue(changeResult.containsKey("changes"));
        assertFalse(changeResult.containsKey("releaseRecords"));
        assertFalse(changeResult.containsKey("longMonitors"));
        UserPrincipal releaseMonitorReader=new UserPrincipal(10L,"audit","Audit",List.of("OPERATOR"),
                List.of("service:read","service:release","long-monitor:read"));
        @SuppressWarnings("unchecked")
        Map<String,Object> releaseResult=(Map<String,Object>)controller(lifecycle).detail(17L,releaseMonitorReader).data();
        assertFalse(releaseResult.containsKey("changes"));
        assertTrue(releaseResult.containsKey("releaseRecords"));
        assertTrue(releaseResult.containsKey("longMonitors"));
    }
    @Test void credentialRoleCannotBypassServiceAccessToDetail(){
        V12ServiceLifecycleService lifecycle=mock(V12ServiceLifecycleService.class);
        UserPrincipal credentialOnly=new UserPrincipal(7L,"other","Other",List.of("OPERATOR"),
                List.of("service:credential:read"));
        BusinessException e=assertThrows(BusinessException.class,
            ()->controller(lifecycle).detail(17L,credentialOnly));
        assertEquals("AUTH_FORBIDDEN",e.getCode());
        verifyNoInteractions(lifecycle);
    }
    @Test void isolatedCredentialEndpointStillRequiresOwnPrivilege(){
        V12ServiceLifecycleService lifecycle=mock(V12ServiceLifecycleService.class);
        UserPrincipal serviceOnly=new UserPrincipal(8L,"viewer","Viewer",List.of("OPERATOR"),
                List.of("service:read"));
        assertEquals("AUTH_FORBIDDEN",assertThrows(BusinessException.class,
            ()->controller(lifecycle).credentials(17L,serviceOnly)).getCode());
        verifyNoInteractions(lifecycle);
    }
}
