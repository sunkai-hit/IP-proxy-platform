package com.ipproxy.platform.service.adapter;

import java.util.Map;

public interface ServiceProvisionAdapter {
    record Result(boolean success,String message,Map<String,Object> metadata){}
    Result provision(String productType,Long serviceId,Map<String,Object> context);
    Result release(String productType,Long serviceId,Map<String,Object> context);
    /**
     * Actual data-plane commands are opt-in. Never infer a network pause, resume or term update
     * from a database status mutation or an old mock implementation.
     */
    default Result suspend(String productType,Long serviceId,Map<String,Object> context){
        return new Result(false,"真实数据面停服接口未对接",Map.of("verified",false));
    }
    default Result resume(String productType,Long serviceId,Map<String,Object> context){
        return new Result(false,"真实数据面恢复接口未对接",Map.of("verified",false));
    }
    default Result renew(String productType,Long serviceId,Map<String,Object> context){
        return new Result(false,"真实数据面续期接口未对接",Map.of("verified",false));
    }
}
