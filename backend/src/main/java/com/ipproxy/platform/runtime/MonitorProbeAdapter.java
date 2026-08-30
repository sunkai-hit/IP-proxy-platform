package com.ipproxy.platform.runtime;

import java.util.Map;

/**
 * 真实主动监控能力的适配边界。
 * 资源同步表里已有的在线状态/延迟/负载由平台直接采集；只有需要主动探测的补充指标才进入此适配器。
 * 未接入真实运维协议时，正式环境不得返回伪造探测数据。
 */
public interface MonitorProbeAdapter {
    Map<String,Object> probe(String objectType, Map<String,Object> objectContext);
}
