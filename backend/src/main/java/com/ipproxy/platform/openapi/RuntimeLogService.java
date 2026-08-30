package com.ipproxy.platform.openapi;

import com.ipproxy.platform.common.api.PageResult;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RuntimeLogService {
    private final OpenApiMapper db;
    public RuntimeLogService(OpenApiMapper db){this.db=db;}
    public PageResult<Map<String,Object>> extracts(int page,int size,String keyword,Long serviceId){page=Math.max(1,page);size=Math.min(200,Math.max(1,size));keyword=keyword==null?"":keyword.trim();return new PageResult<>(page,size,db.countExtractLogs(keyword,serviceId),db.extractLogs(keyword,serviceId,size,(page-1)*size));}
    public PageResult<Map<String,Object>> apis(int page,int size,String keyword,Long serviceId){page=Math.max(1,page);size=Math.min(200,Math.max(1,size));keyword=keyword==null?"":keyword.trim();return new PageResult<>(page,size,db.countApiLogs(keyword,serviceId),db.apiLogs(keyword,serviceId,size,(page-1)*size));}
    public PageResult<Map<String,Object>> usage(int page,int size,String keyword,Long serviceId){page=Math.max(1,page);size=Math.min(200,Math.max(1,size));keyword=keyword==null?"":keyword.trim();return new PageResult<>(page,size,db.countUsageLogs(keyword,serviceId),db.usageLogs(keyword,serviceId,size,(page-1)*size));}
}
