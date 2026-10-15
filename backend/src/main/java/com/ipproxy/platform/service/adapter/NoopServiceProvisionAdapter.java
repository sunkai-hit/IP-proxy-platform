package com.ipproxy.platform.service.adapter;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@Profile("!m6-mock")
public class NoopServiceProvisionAdapter implements ServiceProvisionAdapter {
    /**
     * Noop deliberately fails closed for ALL five products. Returning true
     * here would create an ACTIVE business service without a real proxy.
     */
    @Override public Result provision(String productType,Long serviceId,Map<String,Object> context){
        return new Result(false,"代理产品服务未接入，禁止将管理面记录视为真实开通",Map.of("verified",false,"mode","UNAVAILABLE"));
    }
    @Override public Result release(String productType,Long serviceId,Map<String,Object> context){
        return new Result(false,"代理产品服务未接入，无法确认已真实释放资源",Map.of("verified",false,"mode","UNAVAILABLE"));
    }
}
