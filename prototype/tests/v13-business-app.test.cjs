'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const script=fs.readFileSync(path.join(__dirname,'..','v13-business-app.js'),'utf8');
function boot(){
 const listeners={};
 const nodes={app:{innerHTML:''},layer:{innerHTML:''},toasts:{innerHTML:''}};
 const doc={
  getElementById(id){return nodes[id]||null;},
  addEventListener(type,fn){listeners[type]=fn;},
 };
 const window={};
 vm.runInNewContext(script,{document:doc,window,console,Date},{filename:'v13-business-app.js',timeout:2500});
 return {api:window.V13Business,nodes,listeners};
}
test('36 V1.3 requirements expose individual, navigable business screens',()=>{
 const {api,nodes}=boot();
 assert.equal(api.pageIds.length,36);
 assert.equal(new Set(api.pageIds).size,36);
 for(const id of api.pageIds){
   api.go(id);
   assert.match(nodes.app.innerHTML,/b-head/);
   assert.match(nodes.app.innerHTML,/b-panel|b-kpis|b-chipbar/);
   assert.match(nodes.app.innerHTML,/业务模拟|模拟数据|原型沙箱/);
 }
});
test('dashboard and lifecycle pages show functional forms, linked states and actions, not acceptance tables',()=>{
 const {api,nodes}=boot();
 assert.match(nodes.app.innerHTML,/交付闭环|业务交付闭环/);
 api.go('orders.provision');
 assert.match(nodes.app.innerHTML,/data-biz-action="flow-next"/);
 assert.match(nodes.app.innerHTML,/选择订单/);
 api.go('services.failover');
 assert.match(nodes.app.innerHTML,/data-biz-action="failover-next"/);
 api.go('resources.purchases');
 assert.match(nodes.app.innerHTML,/data-biz-action="begin:purchase-new"/);
 api.go('services.changes');
 assert.match(nodes.app.innerHTML,/data-biz-action="begin:service-renew"/);
 api.go('longmonitor.bots');
 assert.match(nodes.app.innerHTML,/data-biz-action="begin:bot-new"/);
});
test('unpaid order blocked before step and cannot be regarded as deliverable',()=>{
 const {api}=boot();
 api.selectOrder('ORD-2418');
 assert.match(api.checkProvision().fail.join(' '),/付款未核实/);
});
test('exclusive claim is blocked during grace even if locally registered as stopped',()=>{
 const {api}=boot();
 api.selectOrder('ORD-2419');
 api.selectEgress('203.0.113.18');
 assert.match(api.checkProvision().fail.join(' '),/EGRESS_IPV4_EXCLUSIVE_CONFLICT/);
 api.selectEgress('203.0.113.82');
 assert.doesNotMatch(api.checkProvision().fail.join(' '),/EGRESS_IPV4_EXCLUSIVE_CONFLICT/);
});
test('external supplier coverage must be confirmed for paid mixed order',()=>{
 const {api}=boot();
 api.selectOrder('ORD-2417');
 api.setCover('no');
 assert.match(api.checkProvision().fail.join(' '),/外部上游套餐有效期/);
 api.setCover('yes');
 assert.equal(api.checkProvision().fail.length,0);
});
test('purchase prevents unpaid order and expiry shorter than customer entitlement',()=>{
 const {api}=boot();
 api.setForm({order:'ORD-2418',supplier:'SUP-01',validTo:'2027-01-01'});
 assert.match(api.validate('purchase-new','').join(' '),/PAYMENT_NOT_CONFIRMED/);
 api.setForm({order:'ORD-2420',supplier:'SUP-01',validTo:'2026-10-30'});
 assert.match(api.validate('purchase-new','').join(' '),/采购有效期不能早于订单服务期限/);
});
test('reverse proxy cannot exceed upstream purchase and mismatch customer',()=>{
 const {api}=boot();
 api.setForm({purchase:'PUR-201',service:'S-LONG-01',validTo:'2026-12-31'});
 const msg=api.validate('proxy-new','').join(' ');
 assert.match(msg,/客户不一致/);
 assert.match(msg,/超过上游采购期限/);
});
test('renewal rejects cross-customer target and unpaid source',()=>{
 const {api}=boot();
 api.setForm({service:'S-LONG-01',order:'ORD-2420',days:'30',reason:'演示预检'});
 assert.match(api.validate('service-renew','').join(' '),/CUSTOMER_MISMATCH/);
 api.setForm({service:'S-LONG-01',order:'ORD-2418',days:'30',reason:'演示预检'});
 assert.match(api.validate('service-renew','').join(' '),/已付款续费订单/);
});
test('release never pretends to release without real adapter acknowledgement',()=>{
 const {api}=boot();
 api.setForm({reason:'到期释放检查'});
 assert.match(api.validate('release-check','S-EX-02').join(' '),/REAL_REMOTE_RECEIPT_MISSING/);
});
test('click navigation triggers a full screen without exposing production calls',()=>{
 const {api,nodes,listeners}=boot();
 listeners.click({target:{closest(s){return s==='[data-biz-action]'?{dataset:{bizAction:'go:services.list'}}:null;}},preventDefault(){}});
 assert.equal(api.snapshot().route,'services.list');
 assert.match(nodes.app.innerHTML,/服务及交付状态/);
 assert.match(nodes.app.innerHTML,/实际网络/);
});
