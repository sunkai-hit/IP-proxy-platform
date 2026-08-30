package com.ipproxy.platform.resource.adapter;

import com.ipproxy.platform.common.exception.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
@Profile("!m4-mock")
public class NoopResourceAdapter implements ResourceAdapter {
    @Override public List<Map<String,Object>> fetch(String sourceType,Map<String,Object> supplierContext){
        throw new BusinessException("RESOURCE_ADAPTER_NOT_CONFIGURED","尚未配置 "+sourceType+" 的上游资源适配器");
    }
    @Override public SupplierTestResult testSupplier(Map<String,Object> supplierContext){
        return new SupplierTestResult(null,"仅完成供应商配置校验；具体供应商接口协议尚未配置适配器");
    }
}
