#!/usr/bin/env python3
"""V1.3 cross-page workflow/source RBAC gate.

Check *existing* navigation + permission relationships, no new product flows.
A passing result means declared Vue routes and guard code agree. It is NOT a
claim that browser interactions or real proxy delivery have been accepted.
"""
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[1]
VIEWS=ROOT/"frontend/src/views"
ROUTER=(ROOT/"frontend/src/router/index.ts").read_text(encoding="utf-8")
ROUTES=set(re.findall(r"path:'([^']+)'\s*,\s*(?:component|redirect)",ROUTER))

REQUIRED_ROUTE_PATHS={
 "customers","customers/:id","customer-auth","customer-accounts",
 "resources/centos","resources/ros","resources/lines","resources/lines/:id",
 "resource-pools","resource-pools/:id","exclusive-allocations",
 "suppliers","suppliers/:id","supplier-purchases","supplier-purchases/:id",
 "reverse-proxies","reverse-proxies/:id","products/:id","packages/:id",
 "orders","orders/:id","service-provision","services","services/:id",
 "service-changes","service-releases","long-monitor/monitors",
 "long-monitor/bots","long-monitor/alarms","statistics","system"
}

REQUIRED_MARKERS={
 # Important business handoffs rather than arbitrary strings.
 "customer/CustomerDetailView.vue":[
  "openOrder(true)","openOrder(false)","'/orders/'+s.row.id",
  "customer:auth:read","customer:account:read","service:read",
  "customer:credential:read","service:renew","service:status",
 ],
 "service/OrderListView.vue":[
  "route.query.customerId","route.query.renewalServiceId",
  "loadRenewTargets","renewLookupVersion","renewalServiceId",
 ],
 "service/OrderDetailView.vue":[
  "businessRelationsApi.orderServices","RENEWAL_TARGET","paymentConfirm",
  "goRenew","'/service-provision'","row.items",
 ],
 "service/ServiceProvisionView.vue":[
  "provisionPreview","paymentReady","alreadyProvisioned","reverseProxyIds",
  "deliveryStage","oneTimeCredential",
 ],
 "service/ServiceDetailView.vue":[
  "resourceBindings","actionTasks","egressClaims","service:credential:read",
  "service:change:read","service:release","long-monitor:read",
  "/service-changes",
 ],
 "service/ServiceChangeView.vue":[
  "renewalLookupVersion","version!==renewalLookupVersion",
  "Number(form.serviceId)!==serviceId","auth.hasPermission('service:status')",
  "auth.hasPermission('service:renew')","paidTermDays",
 ],
 "service/ServiceReleaseView.vue":[
  "delivery-actions","release-inspections","operationKey","taskStatus",
 ],
 "resource/ResourcePoolDetailView.vue":[
  "candidatePoolLines","poolLines","addPoolLines","removePoolLines",
  "businessRelationsApi.poolServices",
 ],
 "resource/SupplierPurchaseDetailView.vue":[
  "businessRelationsApi.purchaseProxies","businessRelationsApi.purchaseServices",
  "source_customer_order_id","/reverse-proxies",
 ],
 "resource/SupplierPurchaseView.vue":[
  "paidOrderRequestVersion","version!==paidOrderRequestVersion",
  "Number(form.customerId)!==customerId","sourceCustomerOrderId",
  "paidOrders.value.some","effectiveAt:''","expireAt:''",
 ],
 "resource/ReverseProxyView.vue":[
  "purchaseLookupVersion","version!==purchaseLookupVersion",
  "Number(form.purchaseId)!==purchaseId","Number(form.customerId)!==customerId",
  "services.value.some","isPayEligible",
 ],
 "product/PackageDetailView.vue":[
  "businessRelationsApi.packageServices","/packages",
 ],
 "resource/V12ResourceDetailView.vue":[
  "businessRelationsApi.linePools","businessRelationsApi.lineServices",
 ],
}

BACKEND_GUARDS={
 "backend/src/main/java/com/ipproxy/platform/customer/CustomerController.java":[
  'detail.remove("authRecords")','detail.remove("accounts")',
  'detail.remove("services")',
  'require(p,"customer:read");require(p,"service:read")',
 ],
 "backend/src/main/java/com/ipproxy/platform/service/OrderServiceController.java":[
  'result.remove("credentials")','result.remove("changes")',
  'result.remove("releaseRecords")','result.remove("longMonitors")',
  'service:credential:read',
 ],
}
TESTS=[
 "backend/src/test/java/com/ipproxy/platform/customer/CustomerDetailVisibilityTest.java",
 "backend/src/test/java/com/ipproxy/platform/service/OrderServiceCredentialVisibilityTest.java"
]

def main():
    failures=[]
    missing=sorted(REQUIRED_ROUTE_PATHS-ROUTES)
    failures.extend(f"missing Vue router path {p}" for p in missing)
    for file,markers in REQUIRED_MARKERS.items():
        path=VIEWS/file
        if not path.is_file():
            failures.append(f"missing Vue view: {file}")
            continue
        source=path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in source:
                failures.append(f"{file}: missing business navigation/guard {marker}")
    for file,markers in BACKEND_GUARDS.items():
        source=(ROOT/file).read_text(encoding="utf-8")
        for marker in markers:
            if marker not in source:
                failures.append(f"{file}: missing server-side access restriction {marker}")
    mapper=(ROOT/"backend/src/main/java/com/ipproxy/platform/service/v12/V12ServiceMapper.java").read_text(encoding="utf-8")
    if "status,metadata,created_at FROM svc_resource_binding" in mapper:
        failures.append("service resource binding read still includes arbitrary raw metadata")
    for file in TESTS:
        if not (ROOT/file).is_file():
            failures.append(f"RBAC regression not present: {file}")
    prototype=(ROOT/"prototype/README.md").read_text(encoding="utf-8")
    for marker in ("V1.3","B段/C段不作为ROS运营指标","不设计ROS自动主备"):
        if marker not in prototype:
            failures.append(f"prototype README still uses outdated ROS semantics: {marker}")
    for issue in failures:
        print("FAIL",issue)
    print(f"V1.3 UI/route gate: {len(REQUIRED_ROUTE_PATHS)} core routes, "
          f"{len(REQUIRED_MARKERS)} related Vue pages, "
          f"{len(BACKEND_GUARDS)} server RBAC guards, {len(TESTS)} tests")
    print("Static navigation and RBAC regression only; 36-page browser acceptance and network proof remain pending")
    return 1 if failures else 0

if __name__=="__main__":
    sys.exit(main())
