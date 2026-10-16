package com.ipproxy.platform.longmonitor;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class NoopLongProxyCheckAdapter implements LongProxyCheckAdapter {
    @Override public CheckResult check(Map<String,Object> monitor){return CheckResult.unknown("长效代理真实检测Adapter尚未配置");}
}
