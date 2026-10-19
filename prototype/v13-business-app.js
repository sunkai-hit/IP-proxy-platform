(function(){
'use strict';
/* V1.3 independent business-facing prototype. Every action is browser-local; no API, database or real proxy is accessed. */
var h=function(v){return String(v==null?'':v).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});};
var P=[
['dashboard','首页工作台','总览','运营总览与待办'],
['customers.list','客户列表','客户管理','客户建档、状态与影响'],
['customers.detail','客户详情','客户管理','客户—订单—服务关联'],
['customers.auth','客户认证','客户管理','认证资料脱敏与审核'],
['customers.accounts','客户账号与服务认证','客户管理','账号与授权隔离'],
['resources.overview','资源概览','资源管理','自有资源与采购双链'],
['resources.centos','CentOS列表与详情','资源管理','主机状态与B/C段汇总'],
['resources.ros','ROS列表与详情','资源管理','拨号设备与线路'],
['resources.lines','家宽/线路详情','资源管理','稳定线路ID与拨号IP历史'],
['resources.pools','来源资源池','资源管理','线路成员与在用引用'],
['resources.exclusive','独享IPv4出口占用','资源管理','实际公网IPv4唯一性'],
['services.backups','备用候选管理','交付与服务','服务级备用资源'],
['services.failover','人工主备切换向导','交付与服务','授权、影响与回查'],
['resources.domains','域名配置','资源管理','域名解析变更审核'],
['resources.suppliers','供应商管理','外部资源交付','供应商档案与凭证脱敏'],
['resources.purchases','外部采购套餐','外部资源交付','付款先行与采购期限'],
['resources.reverse','反向代理管理','外部资源交付','上游→平台入口→服务'],
['products.list','产品列表/配置','产品管理','五类产品与能力'],
['products.packages','套餐管理','产品管理','服务周期与宽限快照'],
['orders.list','订单管理','订单与开通','订单项、实例数与付款'],
['orders.provision','订单五步开通','订单与开通','多订单项、多服务实例'],
['orders.direct','特权直接开通','订单与开通','授权原因与模拟拦截'],
['services.list','服务实例列表与详情','交付与服务','管理、远端、网络三态'],
['services.bindings','服务资源绑定','交付与服务','线路/池/出口占用'],
['services.changes','服务续费/变更','交付与服务','归属、防重及期限'],
['services.release','到期/宽限与受控释放','交付与服务','停服保留与真实释放门禁'],
['monitor.centos','CentOS监控','运行保障','主机监控及数据新鲜度'],
['monitor.ros','ROS监控','运行保障','拨号设备监控'],
['logs.center','十一类日志中心','运行保障','分类、筛选与审计关联'],
['longmonitor.config','长效代理专项监控配置','长效专项保障','客户服务探测策略'],
['longmonitor.bots','客户钉钉/飞书机器人','长效专项保障','通知机器人与密钥保护'],
['longmonitor.alarms','专项告警与通知','长效专项保障','告警、通知及恢复'],
['statistics','七类数据统计','统计与系统','口径、来源和未知值'],
['system.roles','后台用户角色权限','统计与系统','角色与高危操作'],
['system.audit','登录日志与操作审计','统计与系统','账号与业务操作追踪'],
['workflows.links','全局业务关联与高危交互','统计与系统','客户→订单→服务→资源']
];
var routeIds=P.map(function(x){return x[0];});
var customers=[
{id:'C1001',name:'青岛星途数据',type:'企业',auth:'已通过',owner:'王运营',status:'正常',phone:'138****2816'},
{id:'C1002',name:'济南启航科技',type:'企业',auth:'待审核',owner:'李运营',status:'正常',phone:'186****6723'},
{id:'C1003',name:'杭州云程网络',type:'企业',auth:'已通过',owner:'周运营',status:'正常',phone:'157****0931'}
];
var auths=[{id:'AUTH-026',customer:'C1002',name:'济南启航科技',kind:'企业营业执照',status:'待审核',number:'9137**********52',attachment:'2份（授权访问）'}, {id:'AUTH-024',customer:'C1001',name:'青岛星途数据',kind:'企业营业执照',status:'已通过',number:'9137**********34',attachment:'1份（授权访问）'}];
var accounts=[{id:'ACC-1001',customer:'C1001',name:'star_admin',status:'正常',token:'tok_****9f2c'}, {id:'ACC-1002',customer:'C1002',name:'sail_user',status:'冻结',token:'tok_****3b1a'}];
var centos=[{id:'CEN-01',name:'青岛接入节点01',ip:'10.20.1.10',status:'在线（示例）',cpu:'37%',extra:'B段23 / C段112',sample:'2026-10-19 13:24'}, {id:'CEN-02',name:'济南接入节点02',ip:'10.20.1.11',status:'异常（示例）',cpu:'UNKNOWN',extra:'B/C 未核实',sample:'采样过期'}];
var ros=[{id:'ROS-01',name:'青岛拨号网关',ip:'10.30.1.15',status:'在线（示例）',cpu:'28%',extra:'线路 12 / 在线 11',sample:'2026-10-19 13:23'}, {id:'ROS-02',name:'杭州拨号网关',ip:'10.30.1.16',status:'UNKNOWN',cpu:'UNKNOWN',extra:'线路 8 / 在线未知',sample:'采样过期'}];
var lines=[{id:'LINE-QD-03',name:'青岛联通线路03',ros:'ROS-01',ip:'198.51.100.42',status:'在线（示例）',pool:'POOL-SHARED',history:'198.51.100.21 → 198.51.100.42'}, {id:'LINE-QD-07',name:'青岛电信线路07',ros:'ROS-01',ip:'203.0.113.18',status:'在线（示例）',pool:'POOL-EDGE',history:'203.0.113.14 → 203.0.113.18'}, {id:'LINE-HZ-02',name:'杭州备用线路02',ros:'ROS-02',ip:'198.51.100.77',status:'UNKNOWN',pool:'POOL-BACKUP',history:'198.51.100.64 → 198.51.100.77'}];
var pools=[{id:'POOL-SHARED',name:'来源共享池',members:['LINE-QD-03'],status:'启用'}, {id:'POOL-EDGE',name:'独享候选来源池',members:['LINE-QD-07'],status:'启用'}, {id:'POOL-BACKUP',name:'备用候选池',members:['LINE-HZ-02'],status:'启用'}];
var suppliers=[{id:'SUP-01',name:'星云供应商',contact:'ops@example.invalid',status:'启用',secret:'sk_****e2f1'}, {id:'SUP-02',name:'华东备用供应商',contact:'support@example.invalid',status:'待核验',secret:'sk_****a221'}];
var purchases=[{id:'PUR-201',supplier:'SUP-01',customer:'C1003',order:'ORD-2420',validTo:'2026-11-06',status:'管理面已登记',cost:'未核实'}];
var proxies=[{id:'PX-008',purchase:'PUR-201',customer:'C1003',service:'S-EXT-03',host:'edge.example.invalid:443',status:'待网关验证',validTo:'2026-11-06'}];
var productTypes=[{id:'PR-01',name:'短效IP',cap:'授权提取 · 频次/数量',status:'模拟可售'}, {id:'PR-02',name:'长效IP',cap:'共享出口优先 · 固定IPv4',status:'模拟可售'}, {id:'PR-03',name:'独享IP',cap:'公网IPv4跨客户排他',status:'模拟可售'}, {id:'PR-04',name:'VPN',cap:'隧道接入能力待产品组核签',status:'能力未知'}, {id:'PR-05',name:'隧道',cap:'外部/自有双交付链',status:'能力未知'}];
var packages=[{id:'PK-01',name:'长效固定IP-30天',product:'长效IP',days:30,grace:7,price:399}, {id:'PK-02',name:'独享出口-30天',product:'独享IP',days:30,grace:15,price:899}, {id:'PK-03',name:'隧道企业版-30天',product:'隧道',days:30,grace:0,price:299}, {id:'PK-04',name:'短效10万次',product:'短效IP',days:30,grace:0,price:199}];
var orders=[
{id:'ORD-2417',customer:'C1001',paid:true,status:'已确认付款（样例）',items:[{product:'长效IP',qty:2,mode:'自有资源'},{product:'隧道',qty:1,mode:'外部资源'}],validTo:'2026-11-20'},
{id:'ORD-2418',customer:'C1002',paid:false,status:'待支付',items:[{product:'短效IP',qty:1,mode:'自有资源'}],validTo:'2026-11-25'},
{id:'ORD-2419',customer:'C1002',paid:true,status:'已确认付款（样例）',items:[{product:'独享IP',qty:1,mode:'自有资源'}],validTo:'2026-11-28'},
{id:'ORD-2420',customer:'C1003',paid:true,status:'已确认付款（样例）',items:[{product:'隧道',qty:1,mode:'外部资源'}],validTo:'2026-12-10'}
];
var services=[
{id:'S-LONG-01',customer:'C1001',order:'ORD-2417',product:'长效IP',mode:'SELF_OWNED',line:'LINE-QD-03',pool:'POOL-SHARED',egress:'198.51.100.42',status:'管理面已登记',remote:'UNKNOWN',network:'UNKNOWN',expiry:'2026-11-20',grace:7},
{id:'S-EX-02',customer:'C1002',order:'ORD-2419',product:'独享IP',mode:'SELF_OWNED',line:'LINE-QD-07',pool:'POOL-EDGE',egress:'203.0.113.18',status:'宽限停服待回执',remote:'UNKNOWN',network:'UNKNOWN',expiry:'2026-10-14',grace:15},
{id:'S-EXT-03',customer:'C1003',order:'ORD-2420',product:'隧道',mode:'EXTERNAL_PROXY',line:'上游采购 PUR-201',pool:'不适用',egress:'未承诺',status:'网关待核验',remote:'UNKNOWN',network:'UNKNOWN',expiry:'2026-12-10',grace:0}
];
var candidates=[{id:'BK-01',service:'S-LONG-01',line:'LINE-HZ-02',egress:'198.51.100.77',health:'UNKNOWN',fresh:'采样过期'}, {id:'BK-02',service:'S-LONG-01',line:'LINE-QD-07',egress:'203.0.113.18',health:'在线（示例）',fresh:'13:22 · 模拟'}];
var domains=[{id:'DNS-01',domain:'long.example.invalid',line:'LINE-QD-03',ip:'198.51.100.42',status:'PENDING_UPDATE（模拟）'}, {id:'DNS-02',domain:'edge.example.invalid',line:'LINE-QD-07',ip:'203.0.113.18',status:'管理面已登记'}];
var monitors=[{id:'MON-01',customer:'C1001',service:'S-LONG-01',target:'https://probe.example.invalid',interval:60,fail:3,recover:2,status:'OPEN · 未恢复'}, {id:'MON-02',customer:'C1001',service:'S-LONG-01',target:'https://backup.example.invalid',interval:120,fail:3,recover:2,status:'UNKNOWN'}];
var bots=[{id:'BOT-01',customer:'C1001',kind:'钉钉',name:'星途运维机器人',url:'https://oapi.dingtalk.com/****',status:'启用'}, {id:'BOT-02',customer:'C1003',kind:'飞书',name:'杭州通知群',url:'https://open.feishu.cn/****',status:'停用'}];
var alarms=[{id:'AL-031',service:'S-LONG-01',target:'https://probe.example.invalid',status:'OPEN',notice:'FAILED · 未确认',time:'2026-10-19 13:10'}, {id:'AL-030',service:'S-LONG-01',target:'https://backup.example.invalid',status:'UNKNOWN',notice:'未发送',time:'2026-10-19 12:50'}];
var roles=[{id:'ROLE-ADMIN',name:'系统管理员',perms:['查看','编辑','高危操作','审计'],users:2}, {id:'ROLE-OPS',name:'业务运营',perms:['查看','编辑'],users:4}, {id:'ROLE-AUDIT',name:'审计只读',perms:['查看','审计'],users:1}];
var tasks=[],history=[],counter=1;
var x={route:'dashboard',openGroup:'总览',navQuery:'',filter:'',selected:null,modal:null,form:{},tab:'',step:0,chosenOrder:'ORD-2417',chosenService:'S-LONG-01',chosenCandidate:'BK-01',mode:'SELF_OWNED',egress:'203.0.113.18',cover:'no',consent:'no',role:'管理员'};
function item(arr,id){return arr.find(function(a){return a.id===id;});}
function customerName(id){var a=item(customers,id);return a?a.name:id;}
function countItems(o){return o.items.reduce(function(n,i){return n+i.qty;},0);}
function button(text,act,style){return '<button type="button" class="b-btn '+(style||'')+'" data-biz-action="'+h(act)+'">'+h(text)+'</button>';}
function badge(s){var k=/拒绝|冲突|失败|异常|超期|停服|OPEN|冻结/.test(s)?'red':/UNKNOWN|待|未|宽限|PENDING|模拟/.test(s)?'amber':'green';return '<span class="b-tag '+k+'">'+h(s)+'</span>';}
function tip(text,kind){return '<div class="b-tip '+(kind||'')+'">'+text+'</div>';}
function stat(label,n,sub){return '<div class="b-kpi"><div class="b-kpi-label">'+h(label)+'</div><div class="b-kpi-value">'+h(n)+'</div><small>'+h(sub)+'</small></div>';}
function field(label,name,val,type,opts){var control='';if(opts){control='<select data-biz-field="'+h(name)+'">'+opts.map(function(o){var a=Array.isArray(o)?o:[o,o];return '<option value="'+h(a[0])+'"'+(String(a[0])===String(val)?' selected':'')+'>'+h(a[1])+'</option>';}).join('')+'</select>';}else if(type==='textarea'){control='<textarea data-biz-field="'+h(name)+'" rows="3">'+h(val)+'</textarea>';}else{control='<input data-biz-field="'+h(name)+'" type="'+h(type||'text')+'" value="'+h(val)+'" />';}return '<label class="b-field"><span>'+h(label)+'</span>'+control+'</label>';}
function panel(title,inner,extra){return '<section class="b-panel"><div class="b-section-title"><strong>'+h(title)+'</strong>'+(extra||'')+'</div>'+inner+'</section>';}
function head(title,subtitle,extra){return '<div class="b-head"><div><div class="b-crumb">V1.3 / '+h(P.find(function(p){return p[0]===x.route;})[2])+' / 业务演示</div><h1>'+h(title)+'</h1><p>'+h(subtitle)+'</p></div><div class="b-head-tools">'+(extra||'')+'</div></div>';}
function pairs(rows){return '<div class="b-pairs">'+rows.map(function(a){return '<div><small>'+h(a[0])+'</small><strong>'+h(a[1])+'</strong></div>';}).join('')+'</div>';}
function table(headers,arr,mapper){return '<div class="b-table-scroll"><table class="b-table"><thead><tr>'+headers.map(function(a){return '<th>'+h(a)+'</th>';}).join('')+'</tr></thead><tbody>'+arr.map(mapper).join('')+'</tbody></table></div>';}
function tr(cols,act){return '<tr>'+cols.map(function(v){return '<td>'+v+'</td>';}).join('')+(act?'<td>'+act+'</td>':'')+'</tr>';}
function link(text,route){return '<button class="b-link" type="button" data-biz-action="go:'+h(route)+'">'+h(text)+'</button>';}
function op(name,action){return button(name,'begin:'+action,'primary');}
function nav(route){x.route=routeIds.indexOf(route)>=0?route:'dashboard';x.filter='';x.selected=null;x.tab='';var page=P.find(function(p){return p[0]===x.route;});x.openGroup=page[2];render();}
function notify(txt){history.unshift({at:new Date().toLocaleTimeString('zh-CN'),text:txt});history=history.slice(0,20);}
function freshBanner(){return tip('<b>原型沙箱 · 全部为模拟数据</b>　操作只更新当前浏览器内存。管理面记录 / 远端操作回执 / 实际代理网络状态分别展示；UNKNOWN 不能当作成功。','warning');}
function cardGrid(cards){return '<div class="b-kpis">'+cards.join('')+'</div>';}
function overview(){
return head('业务运营工作台','围绕客户、付款订单、产品服务和资源交付开展日常工作',button('开始五步开通','go:orders.provision','primary')+button('查看服务详情','go:services.list'))+
cardGrid([stat('客户业务主体',customers.length,'企业/个人管理档案'),stat('客户服务实例',services.length,'未含新增本地开通记录'),stat('待处理开通',orders.filter(function(o){return o.paid;}).length,'付款先行 · 仍需核验'),stat('真实联网证明','UNKNOWN','全部为模拟数据')])+
'<div class="b-two">'+panel('业务交付闭环','<div class="b-journey">'+[['01','客户与实名','customers.detail'],['02','订单与付款','orders.list'],['03','资源/采购预检','resources.purchases'],['04','交付开通','orders.provision'],['05','服务与监控','services.list']].map(function(t){return '<div class="b-journey-node" data-biz-action="go:'+t[2]+'"><em>'+t[0]+'</em><b>'+t[1]+'</b><span>查看交互页面 →</span></div>';}).join('')+'</div>')+
panel('需关注事项','<div class="b-todo"><div><b>独享出口冲突</b><small>203.0.113.18 已被宽限服务占用</small>'+link('查看占用','resources.exclusive')+'</div><div><b>外部采购有效期不足</b><small>PUR-201 不能覆盖 ORD-2420 到期日</small>'+link('采购预检','resources.purchases')+'</div><div><b>长效代理告警未恢复</b><small>UNKNOWN 不清空 OPEN 告警</small>'+link('专项告警','longmonitor.alarms')+'</div></div>')+'</div>'+
panel('示例服务交付状态',serviceTable(services.slice(0,3)))+
tip('覆盖：需求第10章36个业务界面、第12章18个验收场景。当前演示不代表正式前端36页已经逐项人工签署。');
}
function serviceTable(arr){return table(['服务 / 客户','产品与模式','资源绑定','管理面阶段','远端执行','实际网络','操作'],arr,function(s){return tr([link(s.id,'services.list')+'<small class="b-sub">'+h(customerName(s.customer))+'</small>',h(s.product)+'<small class="b-sub">'+h(s.mode)+'</small>',h(s.egress),badge(s.status),badge(s.remote),badge(s.network)],button('详情','open:service:'+s.id));});}
function rawTable(source,category){return table(['业务标识','名称/说明','当前状态','关联/来源','操作'],source,function(a){return tr([h(a.id),h(a.name||a.domain||a.target||a.customer||''),badge(a.status||a.health||'已登记'),h(a.ros||a.pool||a.line||a.product||a.order||'演示来源')],button('查看详情','open:'+category+':'+a.id));});}
function filterArray(arr){var f=x.filter.trim().toLowerCase();return !f?arr:arr.filter(function(o){return JSON.stringify(o).toLowerCase().indexOf(f)>=0;});}
function filterbar(placeholder,action){return '<div class="b-filter">'+field('关键词','filter',x.filter,'search')+button('筛选','filter','primary')+button('清空','clear-filter')+(action||'')+'</div>';}
function customersPage(kind){
if(kind==='customers.list')return head('客户列表','业务主体状态管理，冻结/恢复必须查看在用服务影响',op('新增客户','customer-new'))+
cardGrid([stat('客户总数',customers.length,'演示'),stat('已认证',customers.filter(function(c){return c.auth==='已通过';}).length,'本地档案'),stat('存在服务',3,'应逐客户核对')])+
panel('客户档案',filterbar('客户姓名 / ID')+table(['客户ID','主体 / 联系方式','认证','客户状态','关联服务','操作'],filterArray(customers),function(c){return tr([h(c.id),'<b>'+h(c.name)+'</b><small class="b-sub">'+h(c.phone)+'</small>',badge(c.auth),badge(c.status),h(services.filter(function(s){return s.customer===c.id;}).length)],button('详情','open:customer:'+c.id)+button('状态变更','begin:customer-status:'+c.id));}))+
tip('高危操作预览必须锁定客户ID、原状态、影响服务数和操作原因；取消不执行。');
if(kind==='customers.detail'){
var c=item(customers,x.selected)||customers[0];return head('客户详情','客户/订单/服务主子表及其归属关系',button('选择客户','begin:choose-customer')+button('查看订单','go:orders.list','primary'))+
panel('业务主体',pairs([['客户ID',c.id],['名称',c.name],['认证状态',c.auth],['联系人电话',c.phone],['账户状态',c.status],['负责人',c.owner]]))+
'<div class="b-two">'+panel('归属订单',table(['订单号','付款','数量','操作'],orders.filter(function(o){return o.customer===c.id;}),function(o){return tr([h(o.id),badge(o.status),h(countItems(o))],button('订单详情','open:order:'+o.id));}))+
panel('归属服务',serviceTable(services.filter(function(s){return s.customer===c.id;})))+'</div>';
}
if(kind==='customers.auth')return head('客户认证审批','审核对象、脱敏证件与状态必须绑定申请号',op('查看待审资料','auth-open'))+
panel('认证申请',table(['申请编号','客户','申请类别','脱敏证件','附件','状态','操作'],filterArray(auths),function(a){return tr([h(a.id),h(customerName(a.customer)),h(a.kind),h(a.number),h(a.attachment),badge(a.status)],button('查看/审核','open:auth:'+a.id));}))+
tip('原始认证材料不在普通列表或原型中暴露，拒绝跨申请ID错审。');
return head('客户账号与服务认证','账号冻结、密码重置与服务令牌隔离',op('新建账号','account-new'))+
panel('账号与凭证',table(['账号ID','登录名','所属客户','令牌（遮盖）','状态','操作'],accounts,function(a){return tr([h(a.id),h(a.name),h(customerName(a.customer)),h(a.token),badge(a.status)],button('详情','open:account:'+a.id)+button('冻结/恢复','begin:account-status:'+a.id));}))+tip('服务密钥仅能在正式鉴权渠道单次展示；本地演示不会生成或显示真实密钥。');
}
function resourcesPage(route){
if(route==='resources.overview')return head('资源概览','自有设备主链与外部资源采购链分开管理')+
cardGrid([stat('CentOS',centos.length,'承载主机'),stat('ROS',ros.length,'拨号设备'),stat('家宽/线路',lines.length,'稳定线路ID'),stat('外部采购',purchases.length,'不进入自有池')])+
'<div class="b-two">'+panel('自有资源链路','<div class="b-graph">'+['CentOS','ROS','家宽/线路','公网IPv4（运行属性）'].map(function(n){return '<div>'+h(n)+'</div>';}).join('<span>→</span>')+'</div>'+button('线路详情','go:resources.lines','primary'))+
panel('外部采购链路','<div class="b-graph">'+['供应商','已付款采购','反向代理','客户服务'].map(function(n){return '<div>'+h(n)+'</div>';}).join('<span>→</span>')+'</div>'+button('采购新建','go:resources.purchases','primary'))+'</div>';
if(route==='resources.centos'||route==='resources.ros'||route==='resources.lines'){
var cen=route==='resources.centos',rosMode=route==='resources.ros',arr=cen?centos:rosMode?ros:lines,entity=cen?'centos':rosMode?'ros':'line';
return head(P.find(function(p){return p[0]===route;})[1],cen?'CentOS包含B/C段聚合；ROS不得出现B/C段或自动主备':rosMode?'ROS展示拨号与线路，不提供自动主备':'当前公网IP随拨号变化；线路稳定来源ID不变',op('模拟刷新数据','refresh'))+
cardGrid([stat('资源对象',arr.length,'仅演示样本'),stat('已验证在线','UNKNOWN','无实时设备访问'),stat('最近采样','部分过期','必须标未知')])+
panel('资源清单及关联明细',filterbar('资源标识 / 地区')+
table(cen||rosMode?['稳定设备ID','设备别名','IP','CPU','设备指标','采样时间','操作']:['稳定线路ID','ROS','当前公网IP','所属来源池','拨号/IP历史','操作'],filterArray(arr),function(a){return cen||rosMode?tr([h(a.id),h(a.name),h(a.ip),h(a.cpu),h(a.extra),h(a.sample)],button('查看设备','open:'+entity+':'+a.id)):tr([h(a.id),h(a.ros),h(a.ip),h(a.pool),h(a.history)],button('线路详情','open:line:'+a.id));}))+
tip('展示的是自造样本，不提供资源 API 真实同步或实时采样。');
}
if(route==='resources.pools')return head('来源资源池','线路可属于多个来源池；在用服务最后来源不能直接移除',op('新建来源池','pool-new'))+
panel('资源池成员及服务引用',table(['资源池','名称','成员线路','引用服务数','状态','操作'],pools,function(p){return tr([h(p.id),h(p.name),h(p.members.join(', ')),h(services.filter(function(s){return s.pool===p.id;}).length),badge(p.status)],button('管理成员','open:pool:'+p.id));}))+
tip('不按五种产品重复建五种资源池；SHARED/LONG 来源成员可存在受控重叠。');
if(route==='resources.exclusive'){
return head('独享公网IPv4出口','以公网出口IPv4而不是端口或线路实施全客户排他',op('预检新独享分配','exclusive-check'))+
cardGrid([stat('示例占用IPv4','1','203.0.113.18'),stat('宽限保留','1','未真实释放不可复卖'),stat('冲突阻断','可交互','可切换目标IPv4')])+
panel('独享历史占用与归属',table(['实际公网出口','占用服务','所属客户','原线路','当前业务阶段','操作'],services.filter(function(s){return s.product==='独享IP';}),function(s){return tr([h(s.egress),h(s.id),h(customerName(s.customer)),h(s.line),badge(s.status)],button('核验占用','open:service:'+s.id));}))+
tip('同一公网IPv4：跨客户、同客户其他独享服务、不同端口均不得重复占用；宽限停服不等于释放。','danger');
}
if(route==='resources.domains')return head('域名配置','DNS修改需任务回执与公网实际解析核验',op('发起解析变更','dns-update'))+
panel('域名与线路关联',rawTable(domains,'domain'))+tip('变更提交仅产生 PENDING_UPDATE 模拟任务，不自动将DNS标为已生效。');
if(route==='resources.suppliers')return head('外部供应商管理','供应商资料、接口能力和密钥隐藏；采购按付款来源追溯',op('新建供应商','supplier-new'))+
panel('供应商列表',table(['供应商编号','名称','联系方式','凭证状态','供应商状态','操作'],suppliers,function(s){return tr([h(s.id),h(s.name),h(s.contact),h(s.secret),badge(s.status)],button('详情','open:supplier:'+s.id));}))+tip('不在原型录入真实Token、Secret或供应商密钥。');
if(route==='resources.purchases')return head('外部资源采购','采购订单必须已付款，期限需覆盖拟交付客户服务',op('创建采购登记','purchase-new'))+
cardGrid([stat('采购登记',purchases.length,'按客户与订单关联'),stat('到期覆盖冲突',1,'PUR-201 早于服务到期'),stat('供应商网络连通','UNKNOWN','无真实采购操作')])+
panel('采购与订单付款链',table(['采购ID','供应商','付款订单','所属客户','采购有效期','操作'],purchases,function(p){return tr([h(p.id),h(p.supplier),h(p.order),h(customerName(p.customer)),badge(p.validTo)],button('查看详情','open:purchase:'+p.id));}))+
tip('新增采购只在内存登记；如果没有已付款订单或有效期不足，确认前必须阻断。','warning');
if(route==='resources.reverse')return head('反向代理交付','上游套餐 → 网关配置 → 平台入口 → 客户服务',op('配置反向代理','proxy-new'))+
panel('平台入口与采购的绑定',table(['代理ID','采购记录','绑定服务','平台入口（示例）','代理期限','交付状态','操作'],proxies,function(p){return tr([h(p.id),h(p.purchase),h(p.service),h(p.host),h(p.validTo),badge(p.status)],button('检查代理','open:proxy:'+p.id));}))+
tip('HTTP 200、Mock、Noop 和平台代理登记都不能替代网关生效、上游访问及真实网络连通。','danger');
return '';
}
function productPage(route){
if(route==='products.list')return head('五类代理产品','可售产品配置；技术调度/转换规则在产品代码侧，不对运营开放通用策略配置',op('查看产品能力','product-capability'))+
cardGrid(productTypes.slice(0,4).map(function(p){return stat(p.name,p.status,'能力与接口待联调');}))+
panel('产品能力',table(['产品','名称','交付/调度约束','管理面状态','操作'],productTypes,function(p){return tr([h(p.id),h(p.name),h(p.cap),badge(p.status)],button('查看配置','open:product:'+p.id));}));
return head('套餐管理','新服务记录套餐宽限快照；编辑套餐不追溯覆盖既有服务',op('新增套餐','package-new'))+
panel('销售套餐与宽限策略',table(['套餐号','产品','销售名称','天数','宽限（天）','示例价格','操作'],packages,function(p){return tr([h(p.id),h(p.product),h(p.name),h(p.days),h(p.grace),h('¥'+p.price)],button('配置宽限','open:package:'+p.id));}))+
tip('长效/独享宽限由套餐决定，历史服务保留开通时快照；0天的业务批准条件仍待确认。');
}
function orderTable(arr){return table(['订单号','客户','付款状态','订单项','预计实例数','操作'],arr,function(o){return tr([h(o.id),h(customerName(o.customer)),badge(o.status),o.items.map(function(a){return h(a.product)+' ×'+a.qty;}).join('<small class="b-sub">') ,h(countItems(o))],button('订单详情','open:order:'+o.id));});}
function checkProvision(){
var o=item(orders,x.chosenOrder),fail=[],notes=[];
if(!o){fail.push('订单不存在');return {fail:fail,notes:notes};}
if(!o.paid)fail.push('付款未核实：禁止采购、预占、开通');
if(o.items.some(function(i){return i.product==='独享IP';}) && x.egress==='203.0.113.18')fail.push('EGRESS_IPV4_EXCLUSIVE_CONFLICT：该公网IPv4已由服务 S-EX-02 占用并在宽限保留');
if(o.items.some(function(i){return i.mode==='外部资源';})&&x.cover!=='yes')fail.push('外部上游套餐有效期未覆盖订单服务期限或未核实');
notes.push('长效优先复用健康且有共享容量的既有出口，无合格候选才转换短效资源');
notes.push('本地预检不代表A01/B01/C01/C07/F03真实可用，未得到远端操作回执');
return {fail:fail,notes:notes};
}
function flowPage(){
var o=item(orders,x.chosenOrder)||orders[0],ck=checkProvision(),titles=['选择订单/核付款','拆分实例/交付模式','资源与期限预检','确认幂等业务任务','远端与网络核验'];
var stepper='<div class="b-stepper">'+titles.map(function(a,i){return '<div class="'+(i===x.step?'active':i<x.step?'done':'')+'"><span>'+(i+1)+'</span><b>'+h(a)+'</b></div>';}).join('')+'</div>';
var s='';
if(x.step===0)s=field('演示订单','chosenOrder',x.chosenOrder,'select',orders.map(function(z){return [z.id,z.id+' · '+customerName(z.customer)+(z.paid?' · 已付款':' · 未付款')];}))+
pairs([['客户',customerName(o.customer)],['付款状态',o.status],['订单项数量',o.items.length],['预期服务实例数',countItems(o)]])+tip('订单确认≠付款核实。未付款订单不能进入开通；示例付款事实只在原型本地成立。','warning');
if(x.step===1)s=panel('独立订单项/实例拆分',o.items.map(function(a,i){return '<div class="b-split"><b>订单项 '+(i+1)+' · '+h(a.product)+'</b><span>数量：'+a.qty+' 个独立服务实例</span><span>推荐交付：'+h(a.mode)+'</span></div>';}).join(''))+
tip('每个订单项按数量建立独立服务ID，任务防重使用订单项与实例序号组成的稳定键。');
if(x.step===2)s='<div class="b-two">'+panel('选择候选与采购有效期',field('独享公网出口（用于独享演示订单）','egress',x.egress,'select',[['203.0.113.18','203.0.113.18 · 已占用（预期阻断）'],['203.0.113.82','203.0.113.82 · 本地未发现占用']])+field('外部采购期限覆盖（演示核验）','cover',x.cover,'select',[['no','未核实 / 期限不足'],['yes','模拟：已覆盖客户服务期限']]))+
panel('业务资格校验',ck.fail.length?tip(ck.fail.map(h).join('<p>'),'danger'):tip('本地业务条件未发现阻断；真实网关能力仍为 UNKNOWN','good')+ck.notes.map(function(a){return '<p class="b-bullet">'+h(a)+'</p>';}).join(''))+'</div>';
if(x.step===3)s=panel('将要创建的本地任务',pairs([['业务订单',o.id],['模拟服务实例',countItems(o)],['实例幂等键前缀','demo:v13:'+o.id],['管理面状态','PENDING_REMOTE（模拟）'],['远端回执','UNKNOWN'],['公网实际联网','UNKNOWN']]))+(ck.fail.length?tip(ck.fail.map(h).join('; '),'danger'):tip('确认只会生成内存任务，不会创建真实服务、预占IP、购买采购或调用接口。','warning'));
if(x.step===4){var related=tasks.filter(function(t){return t.order===o.id&&t.type==='开通';});s=cardGrid([stat('本地任务数',related.length,'按订单项/数量拆分'),stat('远端执行','UNKNOWN','operationId 待真实回查'),stat('代理网络','UNKNOWN','未进行公网探测')])+panel('操作号与服务实例',table(['模拟任务','订单项/实例','操作号','管理面','远端','网络'],related,function(t){return tr([h(t.id),h(t.desc),h(t.operationId),badge('待远端核验'),badge('UNKNOWN'),badge('UNKNOWN')]);}))+tip('模拟任务登记完成 ≠ 真实交付成功；C07/E01、稳定来源ID、服务网关和外网连接尚无真实回执。','danger');}
var controls=button('重新选择订单','flow-reset')+(x.step>0&&x.step<4?button('上一步','flow-prev'):'')+(x.step<3?button('下一步','flow-next','primary'):x.step===3?button('创建本地待回查任务','flow-submit','primary'):button('进入服务实例','go:services.list','primary'));
return head('订单五步开通','完整演示付款门禁、订单项拆分、冲突预检、幂等与远端回查',button('查看订单详情','go:orders.list'))+
panel('交付流程',stepper+'<div class="b-flow-content">'+s+'</div><div class="b-flow-controls">'+controls+'</div>')+
tip('这是需求第21页可操作业务原型，不调用任何生产或测试接口。');
}
function ordersPage(route){
if(route==='orders.provision')return flowPage();
if(route==='orders.direct')return head('特权直接开通','独立权限、高危原因与客户归属核验；不能绕过付款及真实执行证据')+
panel('开通权限预检',field('发起角色','role',x.role,'select',[['管理员','管理员（演示）'],['只读','只读角色（预期阻断）'],['运营','普通运营（预期阻断）']])+op('发起特权直开','direct-open'))+
tip('原型不会创建真实服务。正式权限必须由后端RBAC控制，不能仅通过前端菜单隐藏。');
return head('订单管理','多订单项、多实例与付款确认独立记录',button('进入开通向导','go:orders.provision','primary')+op('新建订单','order-new'))+
panel('业务订单',filterbar('订单号 / 客户')+orderTable(filterArray(orders)))+
tip('修改订单项不能改变已开通服务的套餐/到期快照；相同操作号必须拒绝重复开通。');
}
function servicesPage(route){
if(route==='services.failover')return failoverPage();
if(route==='services.backups')return head('备用候选管理','备用候选属于具体客户服务；健康、最近上报IP及线路来源需分开校验',button('切换向导','go:services.failover','primary')+op('登记备用候选','backup-new'))+
panel('服务级备用候选',table(['候选编号','所属服务','线路','预期出口','采样新鲜度','健康','操作'],candidates,function(c){return tr([h(c.id),h(c.service),h(c.line),h(c.egress),h(c.fresh),badge(c.health)],button('详情','open:backup:'+c.id));}))+tip('备用候选不是ROS自动主备；真实切换前必须获得客户同意、资源资格和产品组能力回执。','warning');
if(route==='services.list')return head('客户服务实例','一项订单可以产生多个服务；管理面、远端执行、网络可用性三态独立',button('开通新服务','go:orders.provision','primary'))+
cardGrid([stat('服务实例',services.length,'示例数据'),stat('远端待核验',services.length,'全部无真实回执'),stat('实际网络可用','UNKNOWN','没有外部网络测试')])+
panel('服务及交付状态',filterbar('服务号 / 客户')+serviceTable(filterArray(services)))+
tip('点击“详情”展开资源绑定、订单归属、宽限快照、任务和跨页操作。');
if(route==='services.bindings')return head('服务资源绑定','线路来源、来源池、公网出口Claim、外部采购与代理分别校验',button('查看占用冲突','go:resources.exclusive','primary'))+
panel('服务与资源绑定链',table(['服务','交付方式','稳定线路/采购','来源池','承诺出口','生命周期','操作'],services,function(s){return tr([h(s.id),h(s.mode),h(s.line),h(s.pool),h(s.egress),badge(s.status)],button('查看绑定','open:service:'+s.id));}))+
tip('绑定失败、网关UNKNOWN或出口claim无远端回执时不得标示“资源已释放/成功分配”。');
if(route==='services.changes')return head('服务续费、暂停与变更','从服务上下文发起；付款、原到期锚点、采购覆盖、防重复提交',op('创建续费预检','service-renew')+op('暂停/恢复预检','service-change'))+
panel('可操作服务',serviceTable(services))+
tip('续费先确认已付款目标订单，跨客户/跨服务、重复操作号与上游期限不足一律阻断。','warning');
return head('到期、宽限与受控释放','到期停服→出口保留→续费恢复／超宽限真实释放；无回执不能自动回池',op('发起释放预检','release-check'))+
panel('到期任务管理',table(['服务','产品','业务到期','宽限天数','出口占用','远端停服','操作'],services,function(s){return tr([h(s.id),h(s.product),h(s.expiry),h(s.grace),h(s.egress),badge(s.remote)],button('释放预检','begin:release-check:'+s.id));}))+
tip('没有真实 C03/C04/E01 回执，不会把停服、恢复或释放显示成功。宽限停服期间仍保留原公网出口占用。','danger');
}
function failoverPage(){
var s=item(services,x.chosenService)||services[0],c=item(candidates,x.chosenCandidate)||candidates[0],stepLabels=['选择主服务与备用','授权与风险预检','影响确认与原因','结果与远端回查'];
var checks=[];if(x.consent!=='yes')checks.push('缺少客户明确授权/确认');if(c.service!==s.id)checks.push('候选不属于当前服务');if(c.health!=='健康且已实测')checks.push('候选只有本地状态或采样过期，缺少真实健康证明');checks.push('C05/C06 真实切换适配尚未签署，不允许发起真实切换');
var body='';
if(x.step===0)body=field('当前服务','chosenService',x.chosenService,'select',services.filter(function(z){return z.product==='长效IP';}).map(function(z){return [z.id,z.id+' / '+customerName(z.customer)];}))+field('备用候选','chosenCandidate',x.chosenCandidate,'select',candidates.map(function(z){return [z.id,z.id+' / '+z.line+' / '+z.egress];}))+pairs([['现主出口',s.egress],['备用预期出口',c.egress],['IP变化影响',s.egress===c.egress?'不变（需实测）':'固定出口可能发生变化'],['候选健康',c.health]]);
if(x.step===1)body=field('客户对切换的明确授权','consent',x.consent,'select',[['no','未收到明确授权'],['yes','演示：客户已同意']])+tip(checks.map(h).join('<p>'),'danger');
if(x.step===2)body=field('切换业务原因（必填）','reason',x.form.reason||'','textarea')+
panel('影响范围',pairs([['客户',customerName(s.customer)],['旧/新公网出口',s.egress+' → '+c.egress],['客户代理入口','可能需要通知迁移'],['专项监控','应随实际服务切换调整'],['实际切换能力','UNKNOWN · 不执行']]));
if(x.step===3)body=panel('安全阻断结果',pairs([['管理面','已完成模拟预检'],['真实产品组切换','未调用 C06'],['原服务主出口','保持 '+s.egress],['网络效果','UNKNOWN'],['人工验收','待外部联调']]))+tip('不可用演示界面虚构人工主备切换成功，也不能在真实执行失败时清空原主绑定。','danger');
return head('人工主备切换向导','按照服务→候选→授权→影响确认执行；本地模拟不允许真实切换',button('查看备用候选','go:services.backups'))+
panel('切换业务步骤','<div class="b-stepper">'+stepLabels.map(function(t,i){return '<div class="'+(i===x.step?'active':i<x.step?'done':'')+'"><span>'+(i+1)+'</span><b>'+h(t)+'</b></div>';}).join('')+'</div><div class="b-flow-content">'+body+'</div><div class="b-flow-controls">'+button('重置预检','failover-reset')+(x.step>0?button('上一步','failover-prev'):'')+(x.step<3?button('下一步','failover-next','primary'):button('返回服务详情','go:services.list','primary'))+'</div>')+
tip('真实切换案例07/08另需 C05/C06/C07、出口IP证明、监控迁移与客户确认的实网签收。');
}
function monitorPages(route){
if(route==='monitor.centos'||route==='monitor.ros'){var a=route==='monitor.centos'?centos:ros;return head(route==='monitor.centos'?'CentOS监控':'ROS监控','健康、最近采样时间、异常指标须以来源回执为准',op('模拟刷新采样','refresh'))+
cardGrid([stat('设备数',a.length,'示例设备'),stat('实时采样','UNKNOWN','未连接监控接口'),stat('采样过期',1,'必须标记UNKNOWN')])+
panel('运行指标',a.map(function(d){return '<div class="b-device"><div><b>'+h(d.name)+'</b><small>'+h(d.id)+' · 最近采样：'+h(d.sample)+'</small></div>'+badge(d.status)+'<div class="b-device-bar"><span style="width:'+(d.cpu==='UNKNOWN'?0:parseInt(d.cpu,10))+'%"></span></div><div>'+h(d.extra)+'</div>'+button('查看设备来源','open:'+(route==='monitor.centos'?'centos':'ros')+':'+d.id)+'</div>';}).join(''))+
tip('监控采样过期/缺失不能按CPU=0或设备离线推断；ROS不展示B/C段和自动主备。');}
if(route==='logs.center'){var types=['IP提取','代理使用','API调用','ROS运行','线路拨号','IP变化','远程操作','域名变更','外部代理','登录','操作审计'];var selected=x.tab||types[0];return head('十一类日志中心','查询范围、来源、请求标识、时间及敏感字段脱敏',op('导出当前模拟筛选','export-logs'))+
'<div class="b-chipbar">'+types.map(function(t){return button(t,'tab:'+t,t===selected?'primary':'');}).join('')+'</div>'+
panel(selected+' · 查询记录',filterbar('请求号 / 服务 / 原因')+
table(['事件号','时间','业务对象','事件/结果','来源/关联操作号','操作'],filterArray([{id:'EVT-101',at:'2026-10-19 13:16',target:'S-LONG-01',result:'远端任务待回查',src:'operationId: demo-101'}, {id:'EVT-102',at:'2026-10-19 13:08',target:'LINE-QD-03',result:'采样信息未知',src:'数据源：模拟'}, {id:'EVT-103',at:'2026-10-19 12:54',target:'ORD-2418',result:'付款未核实已拦截',src:'权限审计：样例'}]),function(d){return tr([h(d.id),h(d.at),h(d.target),badge(d.result),h(d.src)],button('追溯','open:log:'+d.id));}))+
tip('此处 11 类日志均为模拟入口。正式来源异常、畸形HTTP200、超时均须报读取失败/UNKNOWN，不得显示虚假0行。');
}
if(route==='longmonitor.config')return head('长效代理专项监控配置','客户＋长效服务＋代理探测目标，连续失败/恢复阈值独立配置',op('创建监控任务','monitor-new'))+
panel('专项监控',table(['监控编号','客户与服务','探测目标','间隔(s)','失败/恢复阈值','业务状态','操作'],monitors,function(m){return tr([h(m.id),h(customerName(m.customer))+'<small class="b-sub">'+h(m.service)+'</small>',h(m.target),h(m.interval),h(m.fail+'/'+m.recover),badge(m.status)],button('配置','open:monitor:'+m.id));}))+
tip('UNKNOWN 探测不应累计为成功，不得自动恢复仍 OPEN 的告警。','danger');
if(route==='longmonitor.bots')return head('客户钉钉/飞书机器人','客户独立密钥保存与授权测试；关闭机器人不得发送',op('添加机器人','bot-new'))+
panel('通知机器人',table(['机器人','客户','平台','Webhook（遮盖）','启用状态','操作'],bots,function(b){return tr([h(b.id),h(customerName(b.customer)),h(b.kind),h(b.url),badge(b.status)],button('配置/测试','open:bot:'+b.id));}))+
tip('当前测试按钮仅生成“投递待确认”的本地日志，不发送真实钉钉/飞书消息，也不暴露密钥。');
return head('专项告警与通知','OPEN 告警保持，UNKNOWN 不假恢复；投递成功必须有实际发送回执',op('模拟探测未知','probe-unknown'))+
cardGrid([stat('OPEN告警',1,'未真实恢复'),stat('UNKNOWN探测',1,'不清除已开告警'),stat('成功投递','UNKNOWN','没有真实机器人回执')])+
panel('告警及通知',table(['告警ID','关联服务','探测目标','告警阶段','通知回执','采样','操作'],alarms,function(a){return tr([h(a.id),h(a.service),h(a.target),badge(a.status),badge(a.notice),h(a.time)],button('详情','open:alarm:'+a.id));}))+
tip('人工归档≠真实恢复；真实恢复要求连续有效 NORMAL 的达到恢复阈值。','danger');
}
function chartBar(a,b){return '<div class="b-chart-row"><span>'+h(a)+'</span><div><i style="width:'+Math.max(4,Math.min(100,b))+'%"></i></div><b>'+b+'%</b></div>';}
function statsPage(){
var list=['基础资源','客户使用','IP提取','产品服务','外部采购','长效代理保障','运营与收入'],tab=x.tab||list[0];
return head('七类数据统计','原型数值仅为示例；正式数据必须标明来源、采样时间和单位',op('筛选统计口径','stats-period'))+
'<div class="b-chipbar">'+list.map(function(t){return button(t,'tab:'+t,t===tab?'primary':'');}).join('')+'</div>'+
cardGrid([stat('统计主题',tab,'第33页面'),stat('数据来源','DEMO','非生产统计'),stat('实际流量','UNKNOWN','无真实数据面事件')])+
'<div class="b-two">'+panel('结构占比 · 模拟示意',chartBar('自有资源',72)+chartBar('外部采购',28)+chartBar('需人工核验',43)+chartBar('样例业务有效',65))+
panel('统计指标说明',pairs([['统计分类',tab],['区间','2026-10-01 ~ 2026-10-19（示例）'],['来源事实','前端本地模拟数组'],['单位','随分类而定，需业务方确认'],['真实数据采样','UNKNOWN']]))+'</div>'+
tip('业务统计接口缺少 items、total 非法、503 或异常HTTP200，不能把未知写成0，也不应沿用上次数据。');
}
function systemPage(route){
if(route==='system.roles')return head('后台用户角色权限','查看、编辑、高危写与审计权限分别控制；模拟切换不改变真实RBAC',op('角色授权变更','role-edit'))+
panel('角色与权限',table(['角色编码','角色名称','权限范围','账号数','操作'],roles,function(r){return tr([h(r.id),h(r.name),h(r.perms.join('、')),h(r.users)],button('权限详情','open:role:'+r.id));}))+
tip('前端角色模拟与按钮隐藏不构成真正安全控制。真实权限需后端403、双确认、操作审计。');
if(route==='system.audit')return head('登录日志与操作审计','业务操作、失败及取消事件的来源、关联主键与requestId需可追溯',op('查看操作记录','audit-show'))+
panel('本次模拟会话操作',history.length?table(['本地时间','操作说明'],history,function(a){return tr([h(a.at),h(a.text)]);}):tip('当前浏览器会话暂无操作；尝试订单预检、客户审核或采购新建后返回这里。'))+
tip('这些记录只存在浏览器内存，不属于后端持久化审计证据。');
return head('全局业务关联与高危交互','以稳定主外键串联客户、付款订单、采购、代理、服务及资源')+
panel('示例端到端业务对象追踪','<div class="b-linkmap">'+[['客户','C1003','customers.detail'],['付款订单','ORD-2420','orders.list'],['采购','PUR-201','resources.purchases'],['反向代理','PX-008','resources.reverse'],['服务','S-EXT-03','services.list']].map(function(t,i){return (i?'<span>→</span>':'')+'<div><small>'+h(t[0])+'</small><b>'+h(t[1])+'</b>'+button('打开','go:'+t[2])+'</div>';}).join('')+'</div>')+
panel('高危交互防护',pairs([['客户冻结/停用','冻结业务对象与状态，提示服务影响'],['付款采购','先核付款，不能变更原供应商/付款来源'],['独享分配','出口IPv4唯一占用，宽限期保留'],['人工切换','客户确认 + 真实预检 + 原主保持'],['续费与回池','幂等操作号 + 产品远端真实回执']]))+
tip('关联浏览必须校验 source ID/customer ID/service ID，不从旧页面复用不可信对象。');
}
function page(){
var r=x.route;
if(r==='dashboard')return overview();
if(r.indexOf('customers.')===0)return customersPage(r);
if(r.indexOf('resources.')===0)return resourcesPage(r);
if(r.indexOf('products.')===0)return productPage(r);
if(r.indexOf('orders.')===0)return ordersPage(r);
if(r.indexOf('services.')===0)return servicesPage(r);
if(r.indexOf('monitor.')===0||r==='logs.center'||r.indexOf('longmonitor.')===0)return monitorPages(r);
if(r==='statistics')return statsPage();
return systemPage(r);
}
function details(type,id){
var models={customer:customers,auth:auths,account:accounts,centos:centos,ros:ros,line:lines,pool:pools,domain:domains,supplier:suppliers,purchase:purchases,proxy:proxies,product:productTypes,package:packages,order:orders,service:services,backup:candidates,monitor:monitors,bot:bots,alarm:alarms,role:roles};
var record=item(models[type]||[],id);
if(!record&&type!=='log')return tip('对象不存在或本地数据已变化','danger');
if(type==='log')return pairs([['事件号',id],['数据类型',x.tab||'IP提取'],['数据来源','模拟'],['真实原始审计','不可用']]);
var rows=Object.keys(record).map(function(k){var v=record[k];return [k,Array.isArray(v)?JSON.stringify(v):typeof v==='object'?JSON.stringify(v):v];});
var context='';
if(type==='service')context='<div class="b-detail-actions">'+button('资源绑定','go:services.bindings')+button('续费预检','begin:service-renew:'+record.id)+button('释放预检','begin:release-check:'+record.id)+button('人工切换','go:services.failover')+'</div>'+tip('管理状态：'+h(record.status)+'　远端：'+h(record.remote)+'　真实网络：'+h(record.network),'warning');
if(type==='customer')context='<div class="b-detail-actions">'+button('查看客户详情','go:customers.detail')+button('冻结/恢复预检','begin:customer-status:'+record.id)+'</div>';
if(type==='auth')context='<div class="b-detail-actions">'+button('审核申请','begin:auth-review:'+record.id,'primary')+'</div>';
if(type==='pool')context='<div class="b-detail-actions">'+button('管理成员','begin:pool-members:'+record.id,'primary')+'</div>';
if(type==='package')context='<div class="b-detail-actions">'+button('调整宽限天数','begin:package-edit:'+record.id,'primary')+'</div>';
if(type==='bot')context='<div class="b-detail-actions">'+button('测试模拟投递','begin:bot-test:'+record.id,'primary')+'</div>';
if(type==='role')context='<div class="b-detail-actions">'+button('编辑权限','begin:role-edit:'+record.id,'primary')+'</div>';
if(type==='order')context='<div class="b-detail-actions">'+button('进入五步开通','go:orders.provision','primary')+'</div>';
return pairs(rows)+context;
}
function drawer(title,body){x.modal={title:title,body:body,kind:'detail'};renderLayer();}
function open(type,id){if(type==='customer'){x.selected=id;}if(type==='service')x.chosenService=id;if(type==='order')x.chosenOrder=id;
drawer(type+' · '+id,details(type,id));}
function flowReset(){x.step=0;x.chosenOrder='ORD-2417';x.egress='203.0.113.18';x.cover='no';}
function flowSubmit(){var ck=checkProvision(),o=item(orders,x.chosenOrder);if(ck.fail.length){notify('订单 '+o.id+' 开通被阻断：'+ck.fail.join('；'));return;}
var exists=tasks.some(function(t){return t.order===o.id&&t.type==='开通';});if(exists){notify('订单 '+o.id+' 相同模拟幂等键重复提交被拦截');x.step=4;return;}
o.items.forEach(function(it,ix){for(var j=0;j<it.qty;j++){var id='LOCAL-T'+String(counter++).padStart(3,'0');tasks.push({id:id,type:'开通',order:o.id,desc:'订单项 '+(ix+1)+' · '+it.product+' · 实例 '+(j+1),operationId:'demo:'+o.id+':'+ix+':'+j,status:'PENDING_REMOTE'});}});
notify('订单 '+o.id+' 模拟登记 '+countItems(o)+' 个待远端核验任务（非实际开通）');x.step=4;}
function modalForm(kind,id){
var title='',body='',foot='',f=x.form;f.reason=f.reason||'';f.customer=f.customer||'C1001';f.service=f.service||x.chosenService;f.order=f.order||x.chosenOrder;f.validTo=f.validTo||'2026-12-31';
switch(kind){
case 'customer-new':title='新增客户 · 本地登记';body=field('企业名称','name',f.name||'')+field('联系人电话（脱敏示例）','phone',f.phone||'138****0000');break;
case 'customer-status':var c=item(customers,id)||customers[0];title='客户状态变更影响确认';body=pairs([['目标客户ID',c.id],['业务主体',c.name],['原状态',c.status],['关联服务数',services.filter(function(s){return s.customer===c.id;}).length]])+field('目标状态','targetStatus',f.targetStatus||'冻结','select',['冻结','正常','停用'])+field('操作原因','reason',f.reason,'textarea');break;
case 'choose-customer':title='选择客户查看主子表';body=field('选择客户','customer',f.customer,'select',customers.map(function(c){return [c.id,c.name+' / '+c.id];}));break;
case 'auth-open':case 'auth-review':var a=item(auths,id)||auths[0];title='认证审核 · '+a.id;body=pairs([['申请号',a.id],['所属客户',customerName(a.customer)],['证件号（脱敏）',a.number],['当前状态',a.status]])+field('审批结果','review',f.review||'拒绝','select',['拒绝','通过'])+field('审批理由','reason',f.reason,'textarea');id=a.id;kind='auth-review';break;
case 'account-new':title='新增客户账号';body=field('客户','customer',f.customer,'select',customers.map(function(c){return [c.id,c.name];}))+field('登录名','name',f.name||'');break;
case 'account-status':var ac=item(accounts,id)||accounts[0];title='账号状态确认';body=pairs([['账号',ac.name],['所属客户',customerName(ac.customer)],['原状态',ac.status]])+field('目标状态','targetStatus',f.targetStatus||'冻结','select',['冻结','正常','停用'])+field('原因','reason',f.reason,'textarea');id=ac.id;break;
case 'pool-new':title='新建来源资源池';body=field('资源池名称','name',f.name||'')+field('初始线路','line',f.line||lines[0].id,'select',lines.map(function(l){return [l.id,l.name];}));break;
case 'pool-members':var pool=item(pools,id)||pools[0];title='资源池成员变更预检';body=pairs([['资源池',pool.id],['现有线路',pool.members.join(', ')],['在用服务',services.filter(function(s){return s.pool===pool.id;}).map(function(s){return s.id;}).join(', ')||'无']])+field('待添加线路','line',f.line||lines[0].id,'select',lines.map(function(l){return [l.id,l.name];}))+field('变更类型','change',f.change||'添加','select',['添加','移除']);id=pool.id;break;
case 'exclusive-check':title='独享公网IPv4分配预检';body=field('拟分配出口IPv4','ip',f.ip||'203.0.113.18','select',[['203.0.113.18','203.0.113.18 · 宽限占用'],['203.0.113.82','203.0.113.82 · 本地未发现占用']])+field('目标客户','customer',f.customer,'select',customers.map(function(c){return [c.id,c.name];}))+tip('独享预检永远需要真实产品组出口验证；本地“无冲突”不能直接确认占用。');break;
case 'dns-update':title='DNS解析变更预检';body=field('业务域名','domain',f.domain||domains[0].domain,'select',domains.map(function(d){return [d.domain,d.domain];}))+field('目标线路','line',f.line||lines[0].id,'select',lines.map(function(l){return [l.id,l.name];}))+field('变更原因','reason',f.reason,'textarea');break;
case 'supplier-new':title='新增供应商';body=field('供应商名称','name',f.name||'')+field('联系邮箱','contact',f.contact||'ops@example.invalid')+tip('不要求在模拟原型输入真实Secret；生产必须加密保存、脱敏返回。');break;
case 'purchase-new':title='创建外部采购 · 付款与期限预检';body=field('付款来源订单','order',f.order,'select',orders.map(function(o){return [o.id,o.id+' / '+customerName(o.customer)+(o.paid?' / 已付款':' / 待付款')];}))+field('供应商','supplier',f.supplier||suppliers[0].id,'select',suppliers.map(function(s){return [s.id,s.name];}))+field('采购有效期','validTo',f.validTo,'date')+tip('采购必须绑定同客户已付款订单，覆盖客户服务完整销售期限。','warning');break;
case 'proxy-new':title='配置平台代理入口 · 采购约束';body=field('关联采购','purchase',f.purchase||purchases[0].id,'select',purchases.map(function(p){return [p.id,p.id+' / '+p.validTo];}))+field('目标服务','service',f.service,'select',services.map(function(s){return [s.id,s.id+' / '+customerName(s.customer)];}))+field('平台代理地址（示例）','host',f.host||'proxy.example.invalid:443')+field('代理有效期','validTo',f.validTo,'date');break;
case 'package-new':title='新增销售套餐';body=field('套餐名称','name',f.name||'')+field('产品类型','product',f.product||'长效IP','select',productTypes.map(function(p){return [p.name,p.name];}))+field('业务天数','days',f.days||30,'number')+field('宽限天数','grace',f.grace||7,'number');break;
case 'package-edit':var pk=item(packages,id)||packages[0];title='套餐宽限快照配置';body=pairs([['套餐ID',pk.id],['产品',pk.product],['当前宽限',pk.grace+'天'],['既有服务','历史快照不追改']])+field('新的宽限天数','grace',f.grace==null?pk.grace:f.grace,'number');id=pk.id;break;
case 'order-new':title='新建订单 · 本地草稿';body=field('客户','customer',f.customer,'select',customers.map(function(c){return [c.id,c.name];}))+field('产品','product',f.product||'长效IP','select',productTypes.map(function(p){return [p.name,p.name];}))+field('实例数量','qty',f.qty||1,'number')+tip('新建订单默认未付款。不得直接开通。');break;
case 'direct-open':title='特权直开权限复核';body=pairs([['当前原型角色',x.role],['操作属性','高危·需要真正后端RBAC和审计']])+field('客户','customer',f.customer,'select',customers.map(function(c){return [c.id,c.name];}))+field('授权原因','reason',f.reason,'textarea');break;
case 'backup-new':title='新增服务备用候选';body=field('服务','service',f.service,'select',services.map(function(s){return [s.id,s.id];}))+field('候选线路','line',f.line||'LINE-HZ-02','select',lines.map(function(l){return [l.id,l.name];}))+tip('候选真实健康态为UNKNOWN，无法视作可切换。');break;
case 'service-renew':title='服务续费 · 付款/期限/幂等预检';body=field('目标服务','service',f.service,'select',services.map(function(s){return [s.id,s.id+' / '+customerName(s.customer)];}))+field('付款来源订单','order',f.order,'select',orders.map(function(o){return [o.id,o.id+' / '+customerName(o.customer)+(o.paid?' / 已付款':' / 未付款')];}))+field('续费天数','days',f.days||30,'number')+field('操作原因','reason',f.reason,'textarea');break;
case 'service-change':title='服务暂停/恢复/终止预检';body=field('目标服务','service',f.service,'select',services.map(function(s){return [s.id,s.id];}))+field('变更操作','change',f.change||'暂停','select',['暂停','恢复','终止'])+field('原因','reason',f.reason,'textarea');break;
case 'release-check':var sr=item(services,id)||item(services,f.service)||services[0];title='服务到期与释放预检';body=pairs([['服务ID',sr.id],['业务到期',sr.expiry],['宽限天数',sr.grace+'天'],['公网出口Claim',sr.egress],['远端停服/释放',sr.remote]])+field('申请理由','reason',f.reason,'textarea')+tip('实际回执缺失：不能真释放或使IPv4进入可售池。','danger');id=sr.id;break;
case 'monitor-new':title='新建长效监控配置';body=field('客户长效服务','service',f.service,'select',services.filter(function(s){return s.product==='长效IP';}).map(function(s){return [s.id,s.id];}))+field('探测目标（测试域）','target',f.target||'https://probe.example.invalid')+field('周期秒数','interval',f.interval||60,'number')+field('连续失败阈值','fail',f.fail||3,'number')+field('连续恢复阈值','recover',f.recover||2,'number');break;
case 'bot-new':title='新增客户机器人（不发送）';body=field('客户','customer',f.customer,'select',customers.map(function(c){return [c.id,c.name];}))+field('平台类型','kind',f.kind||'钉钉','select',['钉钉','飞书'])+field('显示名称','name',f.name||'')+tip('不输入真实Webhook/密钥。测试时只追加模拟通知记录。');break;
case 'bot-test':var bo=item(bots,id)||bots[0];title='模拟机器人发送预检';body=pairs([['机器人',bo.id],['所属客户',bo.customer],['平台',bo.kind],['状态',bo.status]])+field('测试原因','reason',f.reason,'textarea');id=bo.id;break;
case 'role-edit':var role=item(roles,id)||roles[0];title='角色权限变更 · 模拟';body=pairs([['角色编码',role.id],['当前权限',role.perms.join('、')],['用户数',role.users]])+field('候选权限','perms',f.perms||role.perms.join('、'),'text')+field('操作原因','reason',f.reason,'textarea');id=role.id;break;
case 'product-capability':title='五类产品能力核对';body=tip('短效、长效、独享、VPN、隧道各需真实开通、使用、停服与释放的独立实网证据。31项平台/产品组候选接口尚不代表签署。','warning');break;
case 'refresh':title='采样/设备刷新模拟';body=tip('正式资源同步需要稳定资源ID、采样时间、接口状态和出错清旧。本原型无资源API，故不会捏造在线数据。');break;
case 'export-logs':title='模拟日志导出';body=tip('当前只有示例数据，无可靠日志来源，不提供貌似真实的日志文件。可返回日志页面切换11类视图。');break;
case 'probe-unknown':title='探测结果 UNKNOWN';body=tip('UNKNOWN 不累计成功/失败，不触发OPEN告警恢复。该操作不会真正探测网络。','danger');break;
case 'stats-period':title='统计时间段示例';body=field('开始日期','from',f.from||'2026-10-01','date')+field('结束日期','to',f.to||'2026-10-19','date')+tip('筛选仅改变模拟查询条件。');break;
case 'audit-show':title='本地操作轨迹';body=history.map(function(z){return '<p>'+h(z.at)+'　'+h(z.text)+'</p>';}).join('')||tip('尚无本地操作');break;
}
return {title:title,body:body,kind:kind,id:id||'',foot:foot};
}
function begin(raw){
var q=raw.split(':'),kind=q[0],id=q.slice(1).join(':');x.form={};if(kind==='release-check'&&id)x.form.service=id;if(kind==='service-renew'&&id)x.form.service=id;
x.modal=modalForm(kind,id);renderLayer();}
function validation(kind,id){
var f=x.form,err=[],effect='';
if(['customer-new','account-new','supplier-new','package-new','order-new','bot-new','pool-new'].indexOf(kind)>=0&&!(f.name||'').trim())err.push('名称为必填项');
if(['customer-status','account-status','auth-review','dns-update','direct-open','service-renew','service-change','release-check','bot-test','role-edit'].indexOf(kind)>=0&&!(f.reason||'').trim())err.push('必须填写明确的业务原因');
if(kind==='auth-review'){var a=item(auths,id);if(a.status!=='待审核')err.push('申请已处理，必须拒绝重复审核');}
if(kind==='purchase-new'){var o=item(orders,f.order||x.chosenOrder);if(!o||!o.paid)err.push('PAYMENT_NOT_CONFIRMED：未付款订单不能采购');if(o&&String(f.validTo||'')<o.validTo)err.push('采购有效期不能早于订单服务期限 '+o.validTo);}
if(kind==='proxy-new'){var p=item(purchases,f.purchase||purchases[0].id),s=item(services,f.service||x.chosenService);if(!p||!s)err.push('采购或服务不存在');else {if(p.customer!==s.customer)err.push('采购与服务客户不一致');if(String(f.validTo||'')>p.validTo)err.push('代理有效期不能超过上游采购期限');if(p.validTo<s.expiry)err.push('采购有效期不足以覆盖既有服务');}}
if(kind==='exclusive-check'&&(f.ip||'203.0.113.18')==='203.0.113.18')err.push('EGRESS_IPV4_EXCLUSIVE_CONFLICT：宽限占用仍然锁定该公网IPv4');
if(kind==='direct-open'&&x.role!=='管理员')err.push('AUTH_FORBIDDEN：只有特权管理员可以进入直开预检');
if(kind==='pool-members'){var pool=item(pools,id),line=f.line||lines[0].id;if(f.change==='移除'&&pool&&pool.members.indexOf(line)>=0&&services.some(function(s){return s.pool===pool.id&&s.line===line;}))err.push('POOL_LAST_SOURCE_IN_USE：在用服务最后来源不得移除');}
if(kind==='service-renew'){var srv=item(services,f.service||x.chosenService),order=item(orders,f.order||x.chosenOrder);if(!srv||!order||!order.paid)err.push('目标服务及已付款续费订单必须存在');else{if(srv.customer!==order.customer)err.push('CUSTOMER_MISMATCH：付款订单与目标服务归属不同');if(srv.mode==='EXTERNAL_PROXY'){var proxy=proxies.find(function(p){return p.service===srv.id;}),pur=proxy&&item(purchases,proxy.purchase);if(!pur||pur.validTo<srv.expiry)err.push('UPSTREAM_EXPIRY_NOT_COVERED：上游采购期限不足');}if(tasks.some(function(t){return t.type==='续费'&&t.service===srv.id}))err.push('DUPLICATE_OPERATION：该服务已存在待回查续费任务');}if(Number(f.days||30)<=0)err.push('续费天数必须大于0');}
if(kind==='bot-test'&&item(bots,id).status!=='启用')err.push('机器人已停用，不允许向适配器发送');
if(kind==='package-edit'&&Number(f.grace)<0)err.push('宽限天数不能为负');
if(kind==='order-new'&&Number(f.qty||1)<1)err.push('实例数量必须大于0');
if(kind==='monitor-new'&&(!(f.target||'').trim()||Number(f.interval||0)<30))err.push('探测目标必填，周期不少于30秒');
if(kind==='service-change'&&tasks.some(function(t){return t.type==='变更'&&t.service===(f.service||x.chosenService)}))err.push('当前服务有待回查变更，不能重复提交');
if(kind==='release-check')err.push('REAL_REMOTE_RECEIPT_MISSING：没有产品组真实停服/释放回执，禁止释放出口占用或回池');
if(kind==='role-edit'&&!(f.perms||'').trim())err.push('不得将读取失败或空权限当作明确清空授权');
return err;
}
function commitModal(){
if(!x.modal)return;var m=x.modal,k=m.kind,f=x.form,err=validation(k,m.id);
if(err.length){x.modal.error=err.join('；');renderLayer();notify('模拟操作阻断：'+err.join('；'));return;}
var id='LOCAL-'+String(counter++).padStart(3,'0');
if(k==='customer-new')customers.push({id:'C-DEMO-'+counter,name:f.name,type:'企业',auth:'待认证',owner:'演示运营',status:'正常',phone:f.phone||'未提供'});
if(k==='customer-status'){item(customers,m.id).status=f.targetStatus||'冻结';}
if(k==='choose-customer')x.selected=f.customer||'C1001';
if(k==='auth-review')item(auths,m.id).status=f.review||'拒绝';
if(k==='account-new')accounts.push({id:id,customer:f.customer||'C1001',name:f.name,status:'正常',token:'tok_****（示例）'});
if(k==='account-status')item(accounts,m.id).status=f.targetStatus||'冻结';
if(k==='pool-new')pools.push({id:id,name:f.name,members:[f.line||lines[0].id],status:'启用'});
if(k==='pool-members'){var pool=item(pools,m.id),line=f.line||lines[0].id;if(f.change==='移除')pool.members=pool.members.filter(function(v){return v!==line;});else if(pool.members.indexOf(line)<0)pool.members.push(line);}
if(k==='dns-update'){var dns=domains.find(function(d){return d.domain===(f.domain||domains[0].domain);});dns.line=f.line||lines[0].id;dns.status='PENDING_UPDATE（模拟）';tasks.push({id:id,type:'DNS变更',status:'PENDING_REMOTE'});}
if(k==='supplier-new')suppliers.push({id:id,name:f.name,contact:f.contact||'',status:'待核验',secret:'****'});
if(k==='purchase-new'){var o=item(orders,f.order||x.chosenOrder);purchases.push({id:id,supplier:f.supplier||suppliers[0].id,customer:o.customer,order:o.id,validTo:f.validTo||'2026-12-31',status:'管理面采购草稿',cost:'未核实'});}
if(k==='proxy-new'){var p=item(purchases,f.purchase||purchases[0].id);proxies.push({id:id,purchase:p.id,customer:p.customer,service:f.service||x.chosenService,host:f.host||'proxy.example.invalid:443',status:'待网关验证',validTo:f.validTo||p.validTo});}
if(k==='package-new')packages.push({id:id,name:f.name,product:f.product||'长效IP',days:Number(f.days||30),grace:Number(f.grace||0),price:0});
if(k==='package-edit')item(packages,m.id).grace=Number(f.grace);
if(k==='order-new')orders.push({id:id,customer:f.customer||'C1001',paid:false,status:'待支付',items:[{product:f.product||'长效IP',qty:Number(f.qty||1),mode:'自有资源'}],validTo:'待付款后确认'});
if(k==='backup-new')candidates.push({id:id,service:f.service||x.chosenService,line:f.line||'LINE-HZ-02',egress:'UNKNOWN',health:'UNKNOWN',fresh:'未采样'});
if(k==='service-renew')tasks.push({id:id,type:'续费',service:f.service||x.chosenService,order:f.order||x.chosenOrder,operationId:'demo:renew:'+(f.service||x.chosenService),status:'PENDING_REMOTE'});
if(k==='service-change')tasks.push({id:id,type:'变更',service:f.service||x.chosenService,operationId:'demo:change:'+(f.service||x.chosenService),status:'PENDING_REMOTE'});
if(k==='monitor-new')monitors.push({id:id,service:f.service||'S-LONG-01',customer:'C1001',target:f.target||'https://probe.example.invalid',interval:Number(f.interval||60),fail:Number(f.fail||3),recover:Number(f.recover||2),status:'UNKNOWN'});
if(k==='bot-new')bots.push({id:id,customer:f.customer||'C1001',kind:f.kind||'钉钉',name:f.name,url:'****',status:'停用'});
if(k==='bot-test')alarms.push({id:id,service:'S-LONG-01',target:'通知测试（模拟）',status:'UNKNOWN',notice:'PENDING · 未真实发送',time:'本地操作'});
if(k==='role-edit')item(roles,m.id).perms=(f.perms||'').split(/[，,、]/).map(function(a){return a.trim();}).filter(Boolean);
notify(k+' · 本地业务操作已登记（模拟；远端/网络未验证）');
x.modal={title:'本地操作反馈 · '+id,kind:'result',body:tip('<b>业务界面已更新（仅本地模拟）</b>。如涉及外部资源/网络执行，远端回执与公网可用性仍为 UNKNOWN。','good')+pairs([['本地操作标识',id],['业务动作',k],['真实 API 请求','未发起'],['数据库写入','未发生'],['人工验收','未签署']])};
render();
}
function renderLayer(){
var el=document.getElementById('layer');if(!el)return;
if(!x.modal){el.innerHTML='';return;}
var m=x.modal;el.innerHTML='<div class="b-modal-backdrop" data-biz-action="close"><div class="b-modal" role="dialog" aria-modal="true"><div class="b-modal-head"><div><small>IP代理管理平台 / 业务模拟</small><h3>'+h(m.title)+'</h3></div>'+button('关闭','close')+'</div><div class="b-modal-body">'+(m.error?tip(h(m.error),'danger'):'')+m.body+'</div><div class="b-modal-foot">'+button('取消','close')+(m.kind!=='result'&&m.kind!=='detail'?button('确认（仅本地演示）','confirm','primary'):'')+'</div></div></div>';
}
function shellNav(){
var groups=[];P.forEach(function(p){if(groups.indexOf(p[2])<0)groups.push(p[2]);});
return groups.map(function(group){
var open=x.openGroup===group,ps=P.filter(function(p){return p[2]===group&&(p[1]+p[3]).toLowerCase().indexOf(x.navQuery.toLowerCase())>=0;});
if(!ps.length)return '';
return '<div class="b-nav-group"><button class="b-nav-heading" data-biz-action="group:'+h(group)+'"><span>'+h(group)+'</span><span>'+(open?'⌄':'›')+'</span></button>'+(open?ps.map(function(p){return '<button class="b-nav-item '+(x.route===p[0]?'current':'')+'" data-biz-action="go:'+h(p[0])+'" title="'+h(p[3])+'"><span class="b-nav-dot"></span>'+h(p[1])+'</button>';}).join(''):'')+'</div>';
}).join('');
}
function render(){
var pageMeta=P.find(function(p){return p[0]===x.route;})||P[0],root=document.getElementById('app');
root.innerHTML='<div class="b-app"><aside class="b-aside"><div class="b-brand"><div class="b-mark">IP</div><div><b>IP 代理管理平台</b><small>BUSINESS PROTOTYPE / V1.3</small></div></div><div class="b-menu-filter"><input aria-label="搜索功能" data-biz-field="navQuery" placeholder="⌕  搜索36个业务页面" value="'+h(x.navQuery)+'"></div><nav class="b-menu">'+shellNav()+'</nav><div class="b-aside-foot"><div class="b-env-dot"></div><span>原型沙箱 · 不连接生产</span></div></aside><div class="b-main"><header class="b-top"><div class="b-top-title"><span>业务交互演示</span><b>/ '+h(pageMeta[1])+'</b></div><div class="b-top-right"><span class="b-top-pill">36 业务页面</span><span class="b-top-pill muted">模拟数据</span>'+button('重置沙箱','reset-all')+'<span class="b-avatar">管</span></div></header><main class="b-content">'+freshBanner()+page()+'</main><footer class="b-footer">IP Proxy Platform · V1.3 交互原型　｜　全部为浏览器本地模拟，不代表正式工程验收</footer></div></div>';
renderLayer();
}
function act(action){
if(action==='close'){x.modal=null;renderLayer();return;}
if(action==='confirm'){commitModal();return;}
if(action.indexOf('go:')===0){x.modal=null;var target=action.slice(3);if(target==='services.failover'){x.step=0;}nav(target);return;}
if(action.indexOf('group:')===0){x.openGroup=x.openGroup===action.slice(6)?'':action.slice(6);render();return;}
if(action.indexOf('tab:')===0){x.tab=action.slice(4);render();return;}
if(action.indexOf('open:')===0){var w=action.split(':');open(w[1],w.slice(2).join(':'));return;}
if(action.indexOf('begin:')===0){begin(action.slice(6));return;}
if(action==='flow-reset'){flowReset();render();return;}
if(action==='flow-prev'){x.step=Math.max(0,x.step-1);render();return;}
if(action==='flow-next'){if(x.step===0&&!item(orders,x.chosenOrder).paid){notify('未付款订单不得进入开通');drawer('付款先行阻断',tip('PAYMENT_NOT_CONFIRMED：订单仅确认但尚未付款，禁止开通。','danger'));return;}if(x.step===2&&checkProvision().fail.length){drawer('资源与业务预检阻断',tip(h(checkProvision().fail.join('；')),'danger'));return;}x.step=Math.min(3,x.step+1);render();return;}
if(action==='flow-submit'){flowSubmit();render();return;}
if(action==='failover-reset'){x.step=0;x.consent='no';render();return;}
if(action==='failover-prev'){x.step=Math.max(0,x.step-1);render();return;}
if(action==='failover-next'){x.step=Math.min(3,x.step+1);render();return;}
if(action==='reset-all'){x.modal={title:'刷新沙箱说明',body:tip('若要重置所有已修改的本地模拟记录，请刷新浏览器页面。这里没有保存任何生产或后端数据。'),kind:'result'};renderLayer();return;}
if(action==='filter'||action==='clear-filter'){if(action==='clear-filter')x.filter='';render();return;}
}
document.addEventListener('click',function(ev){
var b=ev.target.closest('[data-biz-action]');if(!b)return;
if(b.dataset.bizAction==='close'&&ev.target!==b&&ev.target.closest('.b-modal'))return;
ev.preventDefault();act(b.dataset.bizAction);
});
document.addEventListener('input',function(ev){var el=ev.target.closest('[data-biz-field]');if(!el)return;var n=el.dataset.bizField;if(n==='filter')x.filter=el.value;else if(n==='navQuery')x.navQuery=el.value;else x.form[n]=el.value;});
document.addEventListener('change',function(ev){var el=ev.target.closest('[data-biz-field]');if(!el)return;var n=el.dataset.bizField;if(n==='chosenOrder'){x.chosenOrder=el.value;x.step=0;}else if(n==='egress')x.egress=el.value;else if(n==='cover')x.cover=el.value;else if(n==='chosenService')x.chosenService=el.value;else if(n==='chosenCandidate')x.chosenCandidate=el.value;else if(n==='consent')x.consent=el.value;else if(n==='role')x.role=el.value;else if(n==='navQuery'){x.navQuery=el.value;}else if(n==='filter'){x.filter=el.value;}else x.form[n]=el.value;
if(!x.modal)render();});
document.addEventListener('keydown',function(ev){if(ev.key==='Enter'&&ev.target.dataset&&ev.target.dataset.bizField==='filter'){ev.preventDefault();render();}if(ev.key==='Escape'&&x.modal){x.modal=null;renderLayer();}});
window.V13Business={pageIds:routeIds.slice(),pages:P.map(function(p){return p.slice();}),checkProvision:checkProvision,validate:validation,selectOrder:function(id){x.chosenOrder=id;},selectEgress:function(ip){x.egress=ip;},setCover:function(value){x.cover=value;},setForm:function(v){x.form=v;},snapshot:function(){return {route:x.route,tasks:tasks.slice(),customers:customers.slice(),services:services.slice()};},go:nav};
render();
})();