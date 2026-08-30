package com.ipproxy.platform.service.adapter;

import java.util.Map;

public interface ServiceProvisionAdapter {
    record Result(boolean success,String message,Map<String,Object> metadata){}
    Result provision(String productType,Long serviceId,Map<String,Object> context);
    Result release(String productType,Long serviceId,Map<String,Object> context);
}
