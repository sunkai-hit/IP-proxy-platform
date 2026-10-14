package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.audit.OperationAuditRepository;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Customer payment acknowledgement is separate from business order confirmation.
 * This endpoint records a privileged, externally verified receipt reference.
 * It does not charge cards, transfer money or automatically purchase from a supplier.
 */
@RestController
@RequestMapping("/api/admin/v1/orders")
public class V12OrderPaymentController {
    private final JdbcTemplate jdbc;
    private final OperationAuditRepository audit;
    public V12OrderPaymentController(JdbcTemplate jdbc,OperationAuditRepository audit) {
        this.jdbc=jdbc;this.audit=audit;
    }
    private void require(UserPrincipal actor,String permission) {
        if(actor==null || (!actor.roles().contains("SUPER_ADMIN") && !actor.permissions().contains(permission)))
            throw new BusinessException("AUTH_FORBIDDEN","无权核实客户付款凭证");
    }
    private Map<String,Object> lockedOrder(Long id) {
        List<Map<String,Object>> rows=jdbc.queryForList(
          "SELECT id,customer_id,order_no,status,order_type,total_amount,currency,payment_status,payment_reference FROM biz_order WHERE id=? AND deleted=FALSE FOR UPDATE",id);
        if(rows.isEmpty())throw new BusinessException("ORDER_NOT_FOUND","订单不存在");
        return rows.getFirst();
    }
    public record PaymentConfirmation(String paymentReference,String reason){}

    @PostMapping("/{id}/payment-confirmation")
    @Transactional
    public ApiResponse<?> confirm(@PathVariable Long id,@RequestBody PaymentConfirmation input,
            @AuthenticationPrincipal UserPrincipal actor,HttpServletRequest request) {
        require(actor,"order:payment:verify");
        if(input==null || input.paymentReference()==null || input.paymentReference().isBlank()
             || input.paymentReference().length()>255)
            throw new BusinessException("PAYMENT_PROOF_REQUIRED","必须提供外部已到账凭证号或核实依据编号");
        if(input.reason()==null || input.reason().isBlank() || input.reason().length()>1000)
            throw new BusinessException("PAYMENT_REASON_REQUIRED","必须说明已核实付款的依据");
        Map<String,Object> row=lockedOrder(id);
        if("CANCELLED".equals(row.get("status")))
            throw new BusinessException("ORDER_CANCELLED","取消的订单不得补录付款成功");
        String reference=input.paymentReference().trim();
        if("PAID_CONFIRMED".equals(row.get("payment_status"))) {
            if(Objects.equals(reference,row.get("payment_reference")))
                return ApiResponse.success(Map.of("orderId",id,"paymentStatus","PAID_CONFIRMED","alreadyConfirmed",true));
            throw new BusinessException("PAYMENT_ALREADY_CONFIRMED","该订单已有另一付款核实凭证，不可覆盖历史");
        }
        jdbc.update("""
          UPDATE biz_order SET payment_status='PAID_CONFIRMED',payment_reference=?,
          payment_verified_at=now(),payment_verified_by=?,payment_note=?,updated_at=now(),
          updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE
        """,reference,actor.userId(),input.reason().trim(),actor.userId(),id);
        audit.success(actor,"ORDER","BIZ_ORDER",id,"PAYMENT_RECEIPT_CONFIRMED",
          "运营已核实外部付款凭证；不代表平台代理自动收费。凭证编号已单独保存",
          request.getRemoteAddr());
        return ApiResponse.success(Map.of("orderId",id,"paymentStatus","PAID_CONFIRMED","alreadyConfirmed",false));
    }
}
