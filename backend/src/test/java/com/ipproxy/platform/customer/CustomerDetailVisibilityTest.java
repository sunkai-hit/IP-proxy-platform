package com.ipproxy.platform.customer;

import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** RBAC decisions must be enforced on HTTP JSON, not only Vue tabs. */
class CustomerDetailVisibilityTest {
    private static Map<String,Object> detail(){
        Map<String,Object> d=new LinkedHashMap<>();
        d.put("customer",Map.of("id",1L,"customer_name","Customer A"));
        d.put("usage",Map.of("service_total",1));
        d.put("authRecords",List.of(Map.of("submitted_data","sensitive identity form")));
        d.put("accounts",List.of(Map.of("username","customer-user")));
        d.put("services",List.of(Map.of("service_no","S-1")));
        return d;
    }
    private static UserPrincipal viewer(String... permissions){
        return new UserPrincipal(5L,"viewer","Viewer",List.of("OPERATOR"),List.of(permissions));
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> read(CustomerService service,UserPrincipal p){
        return (Map<String,Object>)new CustomerController(service).customer(1L,p).data();
    }
    @Test void basicCustomerReadDoesNotExposeSubtables(){
        CustomerService service=mock(CustomerService.class);
        Map<String,Object> source=detail();
        when(service.detail(1L)).thenReturn(source);
        Map<String,Object> result=read(service,viewer("customer:read"));
        assertEquals("Customer A",((Map<?,?>)result.get("customer")).get("customer_name"));
        assertFalse(result.containsKey("authRecords"));
        assertFalse(result.containsKey("accounts"));
        assertFalse(result.containsKey("services"));
        assertTrue(source.containsKey("authRecords"),"must not mutate original map");
        verify(service).detail(1L);
    }
    @Test void authenticationReviewerOnlySeesOwnRelatedSubtable(){
        CustomerService service=mock(CustomerService.class);
        when(service.detail(1L)).thenReturn(detail());
        Map<String,Object> result=read(service,viewer("customer:read","customer:auth:read"));
        assertTrue(result.containsKey("authRecords"));
        assertFalse(result.containsKey("accounts"));
        assertFalse(result.containsKey("services"));
    }
    @Test void accountReviewerOnlySeesOwnRelatedSubtable(){
        CustomerService service=mock(CustomerService.class);
        when(service.detail(1L)).thenReturn(detail());
        Map<String,Object> result=read(service,viewer("customer:read","customer:account:read"));
        assertFalse(result.containsKey("authRecords"));
        assertTrue(result.containsKey("accounts"));
        assertFalse(result.containsKey("services"));
    }
    @Test void serviceReaderCanAccessOnlyServiceSummary(){
        CustomerService service=mock(CustomerService.class);
        when(service.detail(1L)).thenReturn(detail());
        Map<String,Object> result=read(service,viewer("customer:read","service:read"));
        assertFalse(result.containsKey("authRecords"));
        assertFalse(result.containsKey("accounts"));
        assertTrue(result.containsKey("services"));
    }
    @Test void lackingCustomerReadAlwaysBlocksDetail(){
        CustomerService service=mock(CustomerService.class);
        BusinessException exception=assertThrows(BusinessException.class,
            ()->read(service,viewer("customer:auth:read","customer:account:read","service:read")));
        assertEquals("AUTH_FORBIDDEN",exception.getCode());
        verifyNoInteractions(service);
    }
    @Test void directCustomerServiceChildEndpointNeedsBothRoles(){
        CustomerService service=mock(CustomerService.class);
        CustomerController controller=new CustomerController(service);
        BusinessException error=assertThrows(BusinessException.class,
            ()->controller.services(1L,viewer("customer:read")));
        assertEquals("AUTH_FORBIDDEN",error.getCode());
        verifyNoInteractions(service);
        when(service.services(1L)).thenReturn(List.of(Map.of("service_no","S-1")));
        assertEquals(1,((List<?>)controller.services(1L,
            viewer("customer:read","service:read")).data()).size());
    }
    @Test void superAdminPreservesAllOriginalRelatedSubtables(){
        CustomerService service=mock(CustomerService.class);
        when(service.detail(1L)).thenReturn(detail());
        UserPrincipal admin=new UserPrincipal(1L,"admin","Admin",List.of("SUPER_ADMIN"),List.of());
        Map<String,Object> result=read(service,admin);
        assertTrue(result.containsKey("authRecords"));
        assertTrue(result.containsKey("accounts"));
        assertTrue(result.containsKey("services"));
    }
}
