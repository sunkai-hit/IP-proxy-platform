package com.ipproxy.platform.service.adapter;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@Profile("!m6-mock")
public class NoopServiceProvisionAdapter implements ServiceProvisionAdapter {
    @Override public Result provision(String productType,Long serviceId,Map<String,Object> context){
        if("VPN".equals(productType)||"TUNNEL".equals(productType)) return new Result(false,"VPN/隧道数据面适配器尚未配置",Map.of());
        return new Result(true,"管理面逻辑开通完成",Map.of("mode","MANAGEMENT_PLANE"));
    }
    @Override public Result release(String productType,Long serviceId,Map<String,Object> context){
        if("VPN".equals(productType)||"TUNNEL".equals(productType)) return new Result(false,"VPN/隧道数据面释放适配器尚未配置",Map.of());
        return new Result(true,"管理面逻辑释放完成",Map.of("mode","MANAGEMENT_PLANE"));
    }
}
