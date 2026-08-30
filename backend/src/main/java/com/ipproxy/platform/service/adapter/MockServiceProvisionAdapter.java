package com.ipproxy.platform.service.adapter;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@Profile("m6-mock")
public class MockServiceProvisionAdapter implements ServiceProvisionAdapter {
    @Override public Result provision(String productType,Long serviceId,Map<String,Object> context){return new Result(true,"M6 mock数据面开通成功",Map.of("adapter","m6-mock","productType",productType));}
    @Override public Result release(String productType,Long serviceId,Map<String,Object> context){return new Result(true,"M6 mock数据面释放成功",Map.of("adapter","m6-mock","productType",productType));}
}
