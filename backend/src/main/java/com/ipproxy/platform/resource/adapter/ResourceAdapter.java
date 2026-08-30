package com.ipproxy.platform.resource.adapter;

import java.util.List;
import java.util.Map;

/**
 * 上游资源适配层。核心资源域只消费标准化字段，不绑定任何尚未确认的运维/供应商接口协议。
 */
public interface ResourceAdapter {
    List<Map<String,Object>> fetch(String sourceType, Map<String,Object> supplierContext);
    SupplierTestResult testSupplier(Map<String,Object> supplierContext);
    record SupplierTestResult(Boolean reachable,String message){}
}
