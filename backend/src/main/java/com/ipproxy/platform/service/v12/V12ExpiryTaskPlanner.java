package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Fail-closed expiry task planner. It never calls a proxy adapter or releases a line.
 * Scheduled scans create durable, deterministic operation keys; real stop/release
 * execution is a separate capability gated on verified data-plane receipts.
 */
@Service
public class V12ExpiryTaskPlanner {
    private final JdbcTemplate jdbc;
    public V12ExpiryTaskPlanner(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public Map<String,Object> planDue(int requestedLimit){
        final int limit=Math.min(200,Math.max(1,requestedLimit));
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        List<Map<String,Object>> due=jdbc.queryForList("""
          SELECT s.id,s.service_no,s.status,s.product_type,s.delivery_mode,
                 s.expire_at,s.grace_until_at,s.grace_days_snapshot,s.expiry_phase
          FROM svc_instance s WHERE s.deleted=FALSE
            AND s.product_type IN ('LONG_IP','EXCLUSIVE_IP')
            AND s.expire_at IS NOT NULL AND s.expire_at<=?
            AND (
              (s.status IN ('ACTIVE','SUSPENDED') AND s.expiry_phase IN ('NONE','STOP_PENDING'))
              OR (s.status='EXPIRED' AND s.expiry_phase IN ('GRACE_HELD','RELEASE_PENDING','RELEASE_BLOCKED')
                  AND COALESCE(s.grace_until_at,s.expire_at)<=?)
            )
          ORDER BY s.expire_at,s.id LIMIT ? FOR UPDATE OF s SKIP LOCKED
          """,now,now,limit);
        int created=0,existing=0,missingGrace=0;
        for(Map<String,Object> s:due){
            long id=((Number)s.get("id")).longValue();
            String phase=String.valueOf(s.get("expiry_phase"));
            boolean stop=Set.of("ACTIVE","SUSPENDED").contains(String.valueOf(s.get("status")));
            String action=stop?"STOP_FOR_GRACE":"RELEASE";
            OffsetDateTime expire=(OffsetDateTime)s.get("expire_at");
            String key="expiry:"+action+":"+id+":"+expire.toInstant().toEpochMilli();
            Integer grace=s.get("grace_days_snapshot")==null?null:((Number)s.get("grace_days_snapshot")).intValue();
            String error;
            if(grace==null){error="GRACE_SNAPSHOT_MISSING";missingGrace++;}
            else if("EXTERNAL_PROXY".equals(String.valueOf(s.get("delivery_mode")))){error="EXTERNAL_DATA_PLANE_UNVERIFIED";}
            else error=stop?"DATA_PLANE_STOP_UNVERIFIED":"RESOURCE_RELEASE_UNVERIFIED";
            int inserted=jdbc.update("""
              INSERT INTO svc_action_task(operation_key,service_id,action_type,task_status,stage,
                reason,request_summary,last_error_code,last_error_message)
              VALUES(?,? ,?,'PRECHECK_BLOCKED','WAITING_REAL_ADAPTER',
                '自动到期任务预检，真实动作需数据面核验',
                jsonb_build_object('effectiveExpireAt',CAST(? AS text),'graceDaysSnapshot',CAST(? AS integer)),
                ?,?)
              ON CONFLICT(operation_key) DO NOTHING
              """,key,id,action,expire.toString(),grace,error,
              grace==null?"存量服务缺宽限权益快照，须人工核对；不解除资源占用":
              "已登记到期任务，但未得到真实停服/出口回收的验证，不改变客户接入状态或IP占用");
            if(inserted==1)created++;else existing++;
            // STOP_PENDING / RELEASE_PENDING describe an outstanding task, not verified completion.
            String pending=stop?"STOP_PENDING":"RELEASE_PENDING";
            if(!pending.equals(phase))jdbc.update("""
              UPDATE svc_instance SET expiry_phase=?,updated_at=now(),version=version+1
              WHERE id=? AND deleted=FALSE AND expiry_phase=?
              """,pending,id,phase);
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("scanned",due.size());result.put("created",created);
        result.put("existing",existing);result.put("missingGrace",missingGrace);
        result.put("realActionsExecuted",0);
        result.put("dataPlaneVerified",false);
        result.put("message","仅登记待处理任务；未下发真实停服/回收，不修改业务状态与公网IPv4占用");
        result.put("scannedAt",now);
        return result;
    }

    public List<Map<String,Object>> queue(int limit,String phase){
        int size=Math.min(200,Math.max(1,limit));
        return jdbc.queryForList("""
          SELECT s.id "serviceId",s.service_no "serviceNo",s.product_type "productType",
                 s.status "businessStatus",s.delivery_stage "deliveryStage",
                 s.expire_at "expireAt",s.grace_days_snapshot "graceDaysSnapshot",
                 s.grace_until_at "graceUntilAt",s.expiry_phase "expiryPhase",
                 CASE WHEN s.grace_days_snapshot IS NULL THEN 'GRACE_SNAPSHOT_MISSING'
                   WHEN s.expire_at>now() THEN 'NOT_DUE'
                   WHEN s.status IN ('ACTIVE','SUSPENDED') THEN 'STOP_NOT_VERIFIED'
                   WHEN s.status='EXPIRED' AND COALESCE(s.grace_until_at,s.expire_at)>now()
                     THEN 'GRACE_HOLD_ACTIVE'
                   WHEN s.status='EXPIRED' THEN 'RELEASE_NOT_VERIFIED'
                   ELSE 'CHECK_SERVICE_STATE' END "nextRequiredAction",
                 (SELECT max(t.updated_at) FROM svc_action_task t
                    WHERE t.service_id=s.id) "lastTaskAt"
          FROM svc_instance s
          WHERE s.deleted=FALSE AND s.product_type IN ('LONG_IP','EXCLUSIVE_IP')
            AND (?='' OR s.expiry_phase=?)
          ORDER BY s.expire_at NULLS LAST,s.id LIMIT ?
          """,phase,phase,size);
    }
}
