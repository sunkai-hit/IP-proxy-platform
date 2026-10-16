package com.ipproxy.platform.service.v12;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.audit.OperationAuditRepository;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.service.adapter.MockServiceProvisionAdapter;
import com.ipproxy.platform.service.adapter.NoopServiceProvisionAdapter;
import com.ipproxy.platform.service.adapter.ServiceProvisionAdapter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V12ProvisionSafetyTest {
    private static final String[] ALL_PRODUCTS={"SHORT_IP","LONG_IP","EXCLUSIVE_IP","VPN","TUNNEL"};

    @Test void noProductGroupAdapterMeansNoRealProvisionOrReleaseForAnyProduct() {
        NoopServiceProvisionAdapter noop=new NoopServiceProvisionAdapter();
        for(String type:ALL_PRODUCTS) {
            assertFalse(noop.canProvision(type),type);
            assertFalse(noop.provision(type,1L,Map.of()).success(),type);
            assertFalse(noop.release(type,1L,Map.of()).success(),type);
            assertEquals(false,noop.provision(type,1L,Map.of()).metadata().get("verified"));
        }
    }

    @Test void productAdapterCapabilityIsFailClosedByDefault() {
        ServiceProvisionAdapter unknown=new ServiceProvisionAdapter() {
            @Override public Result provision(String p,Long id,Map<String,Object> ctx) {
                return new Result(true,"ACK only",Map.of());
            }
            @Override public Result release(String p,Long id,Map<String,Object> ctx) {
                return new Result(true,"ACK only",Map.of());
            }
        };
        for(String product:ALL_PRODUCTS)assertFalse(unknown.canProvision(product));
        assertTrue(new MockServiceProvisionAdapter().canProvision("SHORT_IP"),
                "Synthetic CI profile remains explicit");
    }

    @Test void paidOrderGuardBlocksProductionManualProvisionEvenWithOtherAdapter() {
        V12ServiceMapper database=mock(V12ServiceMapper.class);
        BuiltinResourcePolicy policy=mock(BuiltinResourcePolicy.class);
        OperationAuditRepository audit=mock(OperationAuditRepository.class);
        V12ServiceLifecycleService svc=new V12ServiceLifecycleService(
                database,policy,new NoopServiceProvisionAdapter(),new ObjectMapper(),audit);
        V12ServiceLifecycleService.ProvisionInput data=new V12ServiceLifecycleService.ProvisionInput(
                1L,2L,3L,"SELF_OWNED",null,null,null,null,"manual");
        BusinessException error=assertThrows(BusinessException.class,
                ()->svc.manualProvision(data,null,"127.0.0.1"));
        assertEquals("ORDER_PAYMENT_REQUIRED",error.getCode());
        verifyNoInteractions(database,policy,audit);
    }
}
