package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductGraceReleaseControllerTest {
    @Test void onlyServiceReleasePermissionMayInspectExitAllocation(){
        ProductGraceReleaseInspector inspector=mock(ProductGraceReleaseInspector.class);
        ProductGraceReleaseController ctl=new ProductGraceReleaseController(inspector);
        var reader=new UserPrincipal(7L,"viewer","viewer",List.of("OPERATOR"),List.of("service:read"));
        var denied=assertThrows(BusinessException.class,()->ctl.inspect("expiry-release-52",reader));
        assertEquals("AUTH_FORBIDDEN",denied.getCode());
        verifyNoInteractions(inspector);
        var admin=new UserPrincipal(1L,"admin","admin",List.of("SUPER_ADMIN"),List.of());
        ctl.inspect("expiry-release-52",admin);
        verify(inspector).inspect("expiry-release-52");
    }
}
