const $=(s,r=document)=>r.querySelector(s), $$=(s,r=document)=>[...r.querySelectorAll(s)];
const D=window.V12;
const state={page:'dashboard',tabs:{customers:0,resources:0,products:0,orders:0,monitor:0,logs:0,alarms:0,statistics:0,system:0}};
const menu=[
 ['业务中心',[['dashboard','◫','首页工作台'],['customers','♙','客户管理'],['resources','◇','资源管理'],['products','▦','产品管理'],['orders','▣','订单与服务']]],
 ['运维中心',[['monitor','◉','监控中心'],['logs','≣','日志中心'],['alarms','⚠','长效代理监控'],['statistics','▥','数据统计']]],
 ['平台设置',[['system','⚙','系统管理']]]
];
const esc=v=>String(v??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m]));
const tag=v=>{const s=String(v??'');const c=/在线|正常|启用|成功|已恢复|生效|已开通/.test(s)?'success':/离线|异常|失败|告警|暂停|冻结/.test(s)?'danger':/待|处理中|部分/.test(s)?'warning':'info';return `<span class="tag ${c}">${esc(s)}</span>`};
const btn=(t,a='demo',c='')=>`<button class="btn ${c}" data-action="${a}">${t}</button>`;
const head=(title,sub,actions='')=>`<div class="page-head"><div><h1>${title}</h1><div class="page-subtitle">${sub}</div></div><div class="actions">${actions}</div></div>`;
const tabs=(items,i)=>`<div class="tabs">${items.map((x,n)=>`<div class="tab ${n===i?'active':''}" data-tab="${n}">${x}</div>`).join('')}</div>`;
const toolbar=(inputs='')=>`<div class="toolbar">${inputs}<span class="toolbar-spacer"></span>${btn('搜索','search','primary')}${btn('重置','reset')}</div>`;
const table=(cols,rows,actions=true)=>`<div class="card table-wrap"><table><thead><tr>${cols.map(c=>`<th>${c[0]}</th>`).join('')}${actions?'<th>操作</th>':''}</tr></thead><tbody>${rows.map(r=>`<tr>${cols.map(c=>`<td>${c[2]?c[2](r[c[1]],r):esc(r[c[1]])}</td>`).join('')}${actions?`<td><button class="text-btn" data-action="detail">详情</button>&nbsp;&nbsp;<button class="text-btn" data-action="edit">编辑</button></td>`:''}</tr>`).join('')}</tbody></table></div>`;
const metrics=items=>`<div class="grid cols-4">${items.map((x,i)=>`<div class="card metric ${['','green','brand','red'][i%4]}"><div class="metric-label">${x[0]}</div><div class="metric-value">${x[1]}</div><div class="metric-trend">${x[2]||''}</div></div>`).join('')}</div>`;
const note=t=>`<div class="note">${t}</div>`;

function renderShell(){
  const nav=menu.map(g=>`<div class="nav-group-title">${g[0]}</div>${g[1].map(x=>`<div class="nav-item ${state.page===x[0]?'active':''}" data-page="${x[0]}"><span class="nav-icon">${x[1]}</span><span>${x[2]}</span></div>`).join('')}`).join('');
  $('#app').innerHTML=`<div class="shell"><aside class="sidebar"><div class="brand"><div class="brand-mark">IP</div><div><div class="brand-title">IP代理管理平台</div><div class="brand-sub">V1.2 原型</div></div></div><div class="nav-scroll">${nav}</div></aside><main class="main"><div class="topbar"><div class="top-left"><span class="console-chip">V1.2</span><span class="top-title">业务模型升级原型</span></div><div class="top-right"><span class="env">原型环境</span><span>admin</span><div class="avatar">管</div></div></div><div class="content" id="content"></div></main></div>`;
  $$('[data-page]').forEach(el=>el.onclick=()=>{state.page=el.dataset.page;renderShell();renderPage()});
}
function renderPage(){
  const m={dashboard,customers,resources,products,orders,monitor,logs,alarms,statistics,system};
  $('#content').innerHTML=m[state.page](); bindCommon();
}
function bindCommon(){
  $$('[data-tab]').forEach(el=>el.onclick=()=>{state.tabs[state.page]=Number(el.dataset.tab);renderPage()});
  $$('[data-action]').forEach(el=>el.onclick=()=>showAction(el.dataset.action,el.textContent));
}
function showAction(a,t){
  const texts={detail:'查看当前对象详情及跨模块引用关系。',edit:'原型演示：编辑表单按V1.2字段设计打开。',search:'已按当前查询条件刷新列表（模拟）。',reset:'筛选条件已重置（模拟）。',sync:'已发起同步/刷新任务（模拟）。',collect:'已发起一次监控采集（模拟）。',new:'打开新增配置表单（模拟）。',test:'已发起机器人测试消息（模拟）。',provision:'打开服务开通向导，可选择自有资源交付或外部反向代理交付。',demo:'该操作在原型中仅演示，不会修改真实数据。'};
  $('#layer').innerHTML=`<div class="modal-mask" id="mask"><div class="modal"><div class="modal-head"><div class="modal-title">${esc(t)}</div><button class="drawer-close" id="close">×</button></div><div class="modal-body">${note(texts[a]||texts.demo)}<div class="form-grid"><div class="form-item"><label class="form-label">操作对象</label><input class="input" value="当前选中记录"/></div><div class="form-item"><label class="form-label">操作说明</label><input class="input" value="V1.2 原型演示"/></div><div class="form-item full"><label class="form-label">备注</label><textarea class="textarea" placeholder="请输入备注"></textarea></div></div></div><div class="modal-foot"><button class="btn" id="cancel">取消</button><button class="btn primary" id="ok">确认</button></div></div></div>`;
  const close=()=>$('#layer').innerHTML=''; $('#close').onclick=close;$('#cancel').onclick=close;$('#ok').onclick=close;$('#mask').onclick=e=>{if(e.target.id==='mask')close()};
}

function dashboard(){
 return head('首页工作台','从业务、资源和客户长效代理保障三个角度查看当前运行情况')+
 metrics([['有效客户','1,128','客户业务主体'],['有效服务','1,436','自有+外部反向代理'],['在线 CentOS','102 / 103','1台离线'],['在线 ROS','136 / 140','4台异常/离线'],['在线线路','5,841 / 5,921','家宽账号/节点'],['当前去重IP','5,612','线路当前拨号IP'],['长效监控异常','1','客户专项告警'],['3天内到期','18','服务到期提醒']])+
 `<div class="grid cols-2 top-gap"><div class="card pad"><div class="card-title">自有资源主链</div><div class="resource-chain"><div>CentOS<span>→</span></div><div>ROS<span>→</span></div><div>家宽/线路（节点）<span>→</span></div><div>当前公网IP</div></div>${note('ROS逻辑线路、家宽账号、节点在V1.2中统一建模为“家宽/线路”；当前IP是线路运行属性，不再作为独立基础资源主数据。')}</div><div class="card pad"><div class="card-title">外部资源交付链</div><div class="resource-chain"><div>外部供应商<span>→</span></div><div>运营采购套餐<span>→</span></div><div>反向代理<span>→</span></div><div>客户服务</div></div>${note('外部资源不默认进入自有资源池，而是按客户需求采购供应商套餐，通过反向代理形成平台入口后交付。')}</div></div>`;
}

function customers(){
 const ts=['客户列表','认证审批'];const i=state.tabs.customers;
 let body=i===0?table([['客户编号','id'],['客户名称','name'],['类型','type'],['联系人','contact'],['手机号','phone'],['认证','auth',tag],['服务数','services'],['业务','biz'],['到期','expire'],['负责人','owner'],['状态','status',tag]],D.customers):`<div class="card pad"><div class="card-title">认证审批</div>${note('客户认证、账号、服务认证信息沿用现有设计；运营反馈分支中的余额、折扣、客户来源等仍未合并。')}</div>`;
 return head('客户管理','统一管理客户档案、认证、账号与当前服务')+tabs(ts,i)+toolbar('<input class="input search" placeholder="客户名称/手机号"/><select class="select"><option>全部客户类型</option><option>企业</option><option>个人</option></select>')+body;
}

function resources(){
 const ts=['资源概览','CentOS列表','ROS列表','家宽/线路','资源池','独享资源','域名配置','外部供应商','外部套餐/采购','反向代理交付'];const i=state.tabs.resources;let body='';
 if(i===0) body=metrics([['CentOS','103','上层承载主机'],['ROS','140','线路控制主机'],['家宽/线路','5,921','=家宽账号=节点'],['资源池','13','逻辑资源组织']])+`<div class="card pad top-gap"><div class="card-title">资源对象关系</div><div class="resource-chain"><div>CentOS<span>→</span></div><div>ROS<span>→</span></div><div>家宽/线路<span>→</span></div><div>当前IP</div></div>${note('BAS/BRAS属于家宽拨号链路信息，在家宽/线路保存，在ROS层提供汇总统计。')}</div>`;
 if(i===1) body=toolbar('<input class="input" placeholder="主机名称"/><input class="input" placeholder="账号"/><select class="select"><option>全部状态</option><option>在线</option><option>离线</option></select>')+table([['ID','id'],['主机名称','name'],['状态','status',tag],['账号','account'],['IP','ip'],['资源池数','poolCount'],['地点','location'],['ROS数','rosCount'],['运营商','carrier'],['CPU','cpu'],['内存','memory'],['负载','load'],['硬盘','disk'],['VPN','vpn'],['PPPoE服务器','pppoe'],['心跳','heartbeat']],D.centos);
 if(i===2) body=toolbar('<input class="input" placeholder="ROS主机ID"/><select class="select"><option>全部状态</option><option>在线</option><option>异常</option><option>离线</option></select><select class="select"><option>全部自动切换</option><option>开启</option><option>关闭</option></select>')+table([['ROS ID','id'],['CentOS','centos'],['CPU','cpu'],['内存','memory'],['负载','load'],['硬盘','disk'],['线路','lines'],['在线','online'],['开机时长','uptime'],['状态','status',tag],['主备','ha'],['替换','replacement'],['切换','switch'],['自动切换','auto'],['当前IP','ipCount'],['B段数','bCount'],['C段数','cCount'],['BAS数','basCount'],['上报','report']],D.ros);
 if(i===3) body=toolbar('<input class="input" placeholder="拨号账号/IP/域名"/><select class="select"><option>全部ROS</option><option>ROS-SD-01</option><option>ROS-HB-03</option></select><select class="select"><option>全部状态</option><option>在线</option><option>异常</option></select>')+table([['线路ID','id'],['ROS','ros'],['别名','alias'],['拨号账号','account'],['当前IP','ip'],['HTTP','http'],['SOCKS5','socks'],['客户','customer'],['域名','domain'],['类型','type'],['状态','status',tag],['延迟','latency'],['限制人数','limit'],['在用','used'],['重拨/重启','restart'],['VPN重拨监控','vpnMonitor'],['BAS/BRAS','bas'],['省','province'],['市','city'],['运营商','carrier'],['ROS上报','report']],D.lines);
 if(i===4) body=toolbar('<input class="input" placeholder="资源池名称"/><select class="select"><option>全部类型</option><option>共享</option><option>长效</option></select>')+table([['池编号','id'],['名称','name'],['类型','type'],['地区','region'],['运营商','carrier'],['线路总数','lines'],['在线','online'],['可用','available'],['当前去重IP','unique'],['客户数','customers'],['关联产品','products'],['状态','status',tag]],D.pools);
 if(i===5) body=table([['记录ID','id'],['家宽/线路','line'],['当前IP','ip'],['客户','customer'],['服务实例','service'],['分配时间','assigned'],['到期时间','expire'],['状态','status',tag]],D.exclusive);
 if(i===6) body=toolbar('<input class="input" placeholder="域名/客户"/>')+table([['域名','domain'],['当前IP','ip'],['状态','status',tag],['客户','customer'],['家宽/线路','line'],['DNS','dns'],['创建时间','created'],['最近同步','lastSync']],D.domains);
 if(i===7) body=table([['供应商编号','id'],['名称','name'],['联系人','contact'],['采购账号','account'],['认证方式','auth'],['接口地址','endpoint'],['状态','status',tag],['备注','remark']],D.suppliers);
 if(i===8) body=toolbar('<input class="input" placeholder="客户/供应商/套餐"/>')+table([['采购单','id'],['供应商','supplier'],['供应商套餐','package'],['供应商套餐号','supplierNo'],['目标客户','customer'],['采购账号','account'],['周期','cycle'],['额度','quota'],['剩余额度','remain'],['采购成本','cost'],['生效','start'],['到期','expire'],['状态','status',tag]],D.purchases);
 if(i===9) body=toolbar('<input class="input" placeholder="客户/服务/平台代理入口"/>')+table([['代理编号','id'],['采购单','purchase'],['客户','customer'],['服务实例','service'],['协议','protocol'],['上游地址','upstream'],['平台代理入口','platform'],['上游认证','auth'],['生效','start'],['到期','expire'],['状态','status',tag]],D.reverseProxy);
 return head('资源管理','V1.2：CentOS → ROS → 家宽/线路（节点）→ 当前IP；外部资源走采购套餐+反向代理交付',btn('刷新资源','sync','primary'))+tabs(ts,i)+body;
}

function products(){
 const ts=['产品列表','套餐管理'];const i=state.tabs.products;let body=i===0?table([['产品编号','id'],['产品名称','name'],['产品类型','type'],['接入方式','access'],['代理协议','protocol'],['认证方式','auth'],['地区','region'],['套餐数','packages'],['服务数','services'],['状态','status',tag]],D.products):table([['套餐编号','id'],['套餐名称','name'],['所属产品','product'],['服务周期','cycle'],['服务权益','quota'],['销售价格','price'],['状态','status',tag]],D.packages);
 return head('产品管理','产品资源使用规则由代码层实现，不再提供“产品资源策略”业务配置页面',btn('新增','new','primary'))+tabs(ts,i)+note('短效、长效、独享、VPN、隧道五类产品继续保留专项业务参数；资源选择、切换和调度规则作为系统内置逻辑由开发实现。')+body;
}

function orders(){
 const ts=['订单管理','服务开通','服务实例','服务变更','到期与释放'];const i=state.tabs.orders;let body='';
 if(i===0) body=table([['订单号','id'],['客户','customer'],['类型','type'],['产品','product'],['套餐','package'],['金额','amount'],['状态','status',tag],['创建时间','created'],['生效','effective'],['到期','expire']],D.orders);
 if(i===1) body=`<div class="card pad"><div class="card-title">服务开通向导</div><div class="steps"><div class="step active">1 客户/订单</div><div class="step active">2 产品/套餐</div><div class="step active">3 交付方式</div><div class="step">4 认证</div><div class="step">5 完成</div></div><div class="grid cols-2"><div class="card pad"><b>SELF_OWNED 自有资源交付</b><p class="subtle">按系统内置规则选择资源池或具体家宽/线路，生成客户可用服务。</p>${btn('选择自有交付','provision','primary')}</div><div class="card pad"><b>EXTERNAL_PROXY 外部反向代理交付</b><p class="subtle">关联已采购供应商套餐与反向代理配置，将平台代理入口交付客户。</p>${btn('选择外部交付','provision','brand')}</div></div></div>`;
 if(i===2) body=table([['服务编号','id'],['客户','customer'],['产品','product'],['套餐','package'],['类型','type'],['交付方式','delivery'],['资源/代理','resource'],['认证','credential'],['生效','effective'],['到期','expire'],['状态','status',tag]],D.services);
 if(i===3) body=`<div class="card pad"><div class="card-title">服务变更</div>${note('支持暂停、恢复、续费、资源更换、凭证重置等；具体变更按服务类型执行对应系统内置规则。')}</div>`;
 if(i===4) body=`<div class="card pad"><div class="card-title">到期与资源释放</div>${note('自有资源服务释放具体线路/独享占用；外部反向代理服务停止平台代理入口并保留供应商采购与操作历史。')}</div>`;
 return head('订单与服务管理','订单记录购买行为，服务实例记录最终实际交付',btn('创建订单','new','primary'))+tabs(ts,i)+body;
}

function monitor(){
 const ts=['CentOS监控','ROS监控'];const i=state.tabs.monitor;let body=i===0?table([['对象','object'],['状态','status',tag],['CPU','cpu'],['内存','memory'],['负载','load'],['硬盘','disk'],['最近采集','last']],D.monitorCentos,false):table([['对象','object'],['状态','status',tag],['CPU','cpu'],['内存','memory'],['负载','load'],['硬盘','disk'],['在线线路/总线路','lines'],['B段数','b'],['C段数','c'],['最近采集','last']],D.monitorRos,false);
 return head('监控中心','V1.2仅保留 CentOS 与 ROS 的基础设施监控',btn('立即采集','collect','primary'))+tabs(ts,i)+note('家宽/线路的IP、延迟、拨号、VPN重拨等仍在线路列表和日志中查看，但不单独建设监控中心页面。')+body;
}

function logs(){
 const ts=['IP提取','使用日志','API日志','ROS运行','线路拨号','IP变化','远程操作','域名变更','外部代理','操作日志'];const i=state.tabs.logs;
 const names=[['2026-09-07 07:46:20','SVC-100328','返回10个IP','成功'],['2026-09-07 07:45:50','ROS-SD-01','状态上报','成功'],['2026-09-07 07:44:12','LN-SD-01002','重拨获得119.189.66.24','成功']];
 return head('日志中心','提取、使用、API、ROS、线路、IP变化、域名、外部反向代理和后台操作全链路留痕')+tabs(ts,i)+table([['时间','0'],['对象','1'],['内容','2'],['结果','3',tag]],names.map(x=>({'0':x[0],'1':x[1],'2':x[2],'3':x[3]})),false);
}

function alarms(){
 const ts=['长效监控配置','客户机器人','告警记录','通知记录','恢复记录'];const i=state.tabs.alarms;let body='';
 if(i===0) body=toolbar('<input class="input" placeholder="客户/服务实例"/>')+table([['监控编号','id'],['客户','customer'],['长效服务','service'],['监控目标','target'],['检测方式','method'],['检测周期','cycle'],['当前状态','status',tag],['连续失败','fails'],['最近检测','last'],['机器人','bot'],['启用','enabled',tag]],D.longMonitors);
 if(i===1) body=head('','',btn('新增机器人','new','primary'))+table([['机器人编号','id'],['客户','customer'],['类型','type'],['Webhook/API','webhook'],['Secret','secret'],['状态','status',tag],['最近测试','test',tag]],D.bots);
 if(i===2) body=table([['告警编号','id'],['客户','customer'],['服务实例','service'],['监控目标','target'],['问题','problem'],['状态','status',tag],['时间','time'],['通知渠道','channel']],D.alarms);
 if(i===3) body=table([['通知编号','id'],['告警','alarm'],['客户','customer'],['渠道','channel'],['发送结果','result',tag],['发送时间','sent'],['消息摘要','message']],D.notifications,false);
 if(i===4) body=`<div class="card pad"><div class="card-title">恢复记录</div>${note('异常代理恢复后记录恢复时间、恢复检测结果和是否向客户机器人发送恢复通知。')}</div>`;
 return head('长效代理监控与告警','面向特定客户的长效代理专项检测；异常时通知该客户配置的钉钉或飞书机器人',btn('新增监控','new','primary'))+tabs(ts,i)+body;
}

function statistics(){
 const ts=['基础资源','客户使用','产品与服务','外部资源交付'];const i=state.tabs.statistics;
 let body='';
 if(i===0) body=metrics([['CentOS总数','103'],['ROS总数','140'],['家宽/线路','5,921'],['当前去重IP','5,612']])+`<div class="card pad top-gap"><div class="card-title">ROS拨号IP网段统计</div>${note('B段数量按IPv4前两个八位组（/16）去重，C段数量按前三个八位组（/24）去重；该口径当前按V1.2实现，真实联调时可再根据运营口径校准。')}</div>`;
 if(i===1) body=metrics([['今日提取量','1,284,620'],['今日使用请求','3,812,406'],['使用去重IP','4,992'],['失败请求','18,204']]);
 if(i===2) body=metrics([['有效服务','1,436'],['短效服务','642'],['长效服务','286'],['VPN/隧道','508']]);
 if(i===3) body=metrics([['在用外部采购套餐','42'],['有效反向代理','39'],['本月采购成本','¥128,600'],['异常代理入口','1']]);
 return head('数据统计','资源、客户使用、产品服务与外部反向代理交付统计')+tabs(ts,i)+body;
}

function system(){
 const ts=['用户管理','角色权限','字典管理','参数配置','登录日志','操作审计'];const i=state.tabs.system;
 const desc=['后台用户和状态管理','菜单/按钮/数据权限','跨模块枚举字典','平台运行参数','后台登录行为','关键业务操作前后值留痕'][i];
 return head('系统管理','后台基础能力沿用现有设计')+tabs(ts,i)+`<div class="card pad"><div class="card-title">${ts[i]}</div>${note(desc+'。V1.2新增外部供应商凭证、反向代理配置、客户机器人Secret等敏感数据均要求脱敏展示、加密保存并限制查看权限。')}</div>`;
}

renderShell();renderPage();