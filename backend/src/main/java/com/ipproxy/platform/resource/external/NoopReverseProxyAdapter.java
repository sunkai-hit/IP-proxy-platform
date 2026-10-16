package com.ipproxy.platform.resource.external;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class NoopReverseProxyAdapter implements ReverseProxyAdapter {
    @Override public Result apply(Long reverseProxyId,Map<String,Object> config){return Result.pending("反向代理配置已保存；真实数据面Adapter尚未接入");}
    @Override public Result test(Long reverseProxyId,Map<String,Object> config){return Result.pending("已完成管理面配置校验；真实反向代理连通性待数据面Adapter接入");}
    @Override public Result disable(Long reverseProxyId,Map<String,Object> config){return Result.pending("平台状态已停用；真实数据面释放待Adapter接入");}
}
