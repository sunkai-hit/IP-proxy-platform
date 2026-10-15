#!/usr/bin/env python3
"""V1.3 offline frontend/backend API reconciliation.

Source-level route comparison is conservative: it verifies explicit method/path
declarations and outputs dynamically built or unmatched routes for review.
It does not claim the backend has been exercised in a browser or real network.
"""
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[1]
FRONT=ROOT/"frontend/src/api"
BACK=ROOT/"backend/src/main/java"
FRONT_RE=re.compile(r"""http\.(get|post|put|patch|delete)\(\s*(\x60[^\x60]+\x60|'[^']+'|"[^"]+")""")
ROOT_RE=re.compile(r"""(?:const|let)\s+([a-zA-Z]+)\s*=\s*(['"])(/[^'"]+)\2""")
CLASS_RE=re.compile(r"""@RequestMapping\(\s*"([^"]+)""")
MAP_RE=re.compile(r"""@(Get|Post|Put|Patch|Delete)Mapping\s*\(\s*"([^"]+)""")
EXP_RE=re.compile(r"\$\{([^}]+)\}")
VAR_RE=re.compile(r"\{[^}]+\}")
MUST_HAVE={
    ("get","/orders"),("post","/orders"),("get","/orders/{id}"),
    ("post","/orders/{id}/confirm"),("post","/orders/{id}/payment-confirmation"),
    ("get","/orders/{id}/provision-preview"),("post","/orders/{id}/provision"),
    ("get","/services"),("get","/services/{id}"),
    ("post","/services/{id}/renew"),("post","/services/{id}/suspend"),
    ("post","/services/{id}/resume"),("post","/services/{id}/terminate"),
    ("get","/relations/orders/{id}/services"),
    ("get","/relations/services/{id}/bindings"),
    ("get","/relations/services/{id}/paid-renewal-orders"),
    ("get","/v12/service-expiry/queue"),
    ("post","/v12/service-expiry/reconcile"),
    ("get","/v12/delivery-actions/{id}"),
    ("get","/v12/release-inspections/{id}"),
    ("get","/v12/resources/lines"),("get","/v12/resources/lines/{id}"),
    ("get","/v12/external-delivery/purchases"),
}

# These privileged product-group writes are deliberately NOT exposed to
# the management UI until real product infrastructure is signed off.
SERVER_ONLY={
    ("post","/v12/release-actions/{id}/dispatch"),
    ("post","/v12/release-actions/{id}/commit"),
    ("post","/v12/delivery-actions/{id}/dispatch-expiry-stop"),
    ("post","/v12/delivery-actions/{id}/commit-expiry-stop"),
}

def norm(path:str)->str:
    path=re.sub(r"/+","/",path)
    path=re.sub(r"^/api/admin/v1(?=/|$)","",path)
    return VAR_RE.sub("{id}",path.rstrip("/") or "/")

def frontend():
    endpoints=set()
    skips=[]
    # Some existing operational read panels issue GET directly from Vue views
    # instead of routing through the thin API module.
    for file in list(FRONT.glob("*.ts"))+list((ROOT/"frontend/src/views").rglob("*.vue")):
        src=file.read_text(encoding="utf-8")
        aliases={m.group(1):m.group(3) for m in ROOT_RE.finditer(src)}
        for match in FRONT_RE.finditer(src):
            path=match.group(2)[1:-1]
            # A frontend dynamic argument (including encodeURIComponent())
            # maps to the Spring path placeholder; it is not a route miss.
            # Truly dynamic resource categories need a separate category audit.
            dynamic_category="basePath[" in path
            path=EXP_RE.sub(lambda m: aliases.get(m.group(1),"{id}"),path)
            if dynamic_category:
                skips.append((file.name,match.group(1),path))
                continue
            endpoints.add((match.group(1),norm(path)))
    return endpoints,skips

def backend():
    endpoints=set()
    for file in BACK.rglob("*Controller.java"):
        src=file.read_text(encoding="utf-8")
        base=(CLASS_RE.search(src).group(1) if CLASS_RE.search(src) else "")
        for match in MAP_RE.finditer(src):
            endpoints.add((match.group(1).lower(),norm(base+match.group(2))))
    return endpoints

def main()->int:
    front,skips=frontend()
    back=backend()
    critical=[x for x in sorted(MUST_HAVE) if x not in front or x not in back]
    critical += [x for x in sorted(SERVER_ONLY) if x not in back]
    if critical:
        for route in critical:
            print("FAIL route:",route,"frontend",route in front,"backend",route in back)
        return 1
    overlap=front & back
    unmatched=sorted(front-back)
    print(f"V1.3 API contract: {len(MUST_HAVE)} frontend routes match and {len(SERVER_ONLY)} privileged backend-only write routes exist")
    print(f"Static coverage: {len(overlap)} of {len(front)} literal frontend endpoints matched; {len(back)} backend mappings discovered")
    for method,path in unmatched:
        print("REVIEW unmatched frontend route:",method.upper(),path)
    for file,method,path in skips:
        print("REVIEW dynamically constructed route:",file,method.upper(),path)
    # Other routes need integration or a reviewer, but must not be silently
    # reported as end-to-end validated by this file-level contract check.
    changes=(ROOT/"frontend/src/views/service/ServiceChangeView.vue").read_text(encoding="utf-8")
    detail=(ROOT/"frontend/src/views/service/ServiceDetailView.vue").read_text(encoding="utf-8")
    preview=(ROOT/"backend/src/main/java/com/ipproxy/platform/service/v12/V12ServiceLifecycleService.java").read_text(encoding="utf-8")
    guards=[
        ("status role for lifecycle actions", "auth.hasPermission('service:status')" in changes),
        ("renewal permission for lifecycle actions", "auth.hasPermission('service:renew')" in changes),
        ("lifecycle button uses computed permission guard", ':disabled="blocked"' in changes),
        ("service detail entry respects operation role", "auth.hasPermission('service:status')" in detail),
        ("long-IP uses shared-exit-first preview", 'case "LONG_IP"->"优先复用' in preview)
    ]
    for label,ok in guards:
        print("PASS" if ok else "FAIL",label)
    if not all(ok for _,ok in guards):return 1
    print("V1.3 API/permission static contract check passed; browser field-level acceptance still pending")
    return 0

if __name__=="__main__":
    sys.exit(main())
