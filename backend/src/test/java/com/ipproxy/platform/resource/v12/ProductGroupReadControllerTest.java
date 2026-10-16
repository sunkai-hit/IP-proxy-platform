package com.ipproxy.platform.resource.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductGroupReadControllerTest {
    @Test void cannotReadProductDataWithoutBusinessPlatformPermission() {
        ProductGroupReadClient client=mock(ProductGroupReadClient.class);
        ProductGroupReadController ctrl=new ProductGroupReadController(client);
        UserPrincipal user=new UserPrincipal(2L,"test","test",List.of("OPERATOR"),List.of());
        BusinessException e=assertThrows(BusinessException.class,()->ctrl.shortPool(user));
        assertEquals("AUTH_FORBIDDEN",e.getCode());
        assertThrows(BusinessException.class,()->ctrl.actualService("7",user));
        assertThrows(BusinessException.class,()->ctrl.task("op-1",user));
        verifyNoInteractions(client);
    }

    @Test void operatorResourceReadDoesNotGrantServiceStatusRead() {
        ProductGroupReadClient client=mock(ProductGroupReadClient.class);
        ProductGroupReadController ctrl=new ProductGroupReadController(client);
        UserPrincipal user=new UserPrincipal(2L,"test","test",List.of("OPERATOR"),List.of("resource:read"));
        ctrl.shortPool(user);
        verify(client).shortPool();
        assertThrows(BusinessException.class,()->ctrl.actualService("7",user));
    }
}
