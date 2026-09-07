(()=>{
  const productTypeName={short:'短效IP',long:'长效IP',exclusive:'独享IP',vpn:'VPN',tunnel:'隧道'};
  const groups=[
    {key:'customer',title:'客户管理',children:[
      {key:'customers.list',title:'客户列表',module:'customers',tab:0,desc:'统一查看客户档案、实名认证状态、服务情况及客户状态'},
      {key:'customers.auth',title:'客户认证',module:'customers',tab:1,desc:'处理个人与企业实名认证申请'},
      {key:'customers.accounts',title:'客户账号',custom:'accounts',desc:'查看客户登录账号及服务认证关联情况'}
    ]},
    {key:'resource',title:'资源管理',children:[
      {key:'resources.overview',title:'资源概览',module:'resources',tab:0,desc:'CentOS、ROS、家宽/线路、资源池及外部交付资源统一概览'},
      {key:'resources.centos',title:'CentOS列表',module:'resources',tab:1,desc:'对应现有主机列表，展示服务器承载与运行状态'},
      {key:'resources.ros',title:'ROS列表',module:'resources',tab:2,desc:'对应现有ROS主机，展示线路、B/C段、BAS和负载信息'},
      {key:'resources.lines',title:'家宽/线路列表',module:'resources',tab:3,desc:'统一承载ROS逻辑线路、家宽账号和节点的业务信息'},
      {key:'resources.pools',title:'资源池',module:'resources',tab:4,desc:'按业务规则组织多个家宽/线路形成逻辑资源集合'},
      {key:'resources.exclusive',title:'独享资源',module:'resources',tab:5,desc:'管理独享服务对家宽/线路及当前IP的占用关系'},
      {key:'resources.domains',title:'域名配置',module:'resources',tab:6,desc:'维护域名、线路、客户及DNSPOD同步关系'},
      {key:'resources.suppliers',title:'外部供应商',module:'resources',tab:7,desc:'维护外部供应商档案、账号及接口基础配置'},
      {key:'resources.purchases',title:'外部套餐/资源采购',module:'resources',tab:8,desc:'记录运营根据客户需求在外部供应商购买的套餐'},
      {key:'resources.reverse',title:'反向代理交付',module:'resources',tab:9,desc:'将外部供应商套餐通过反向代理形成平台代理入口并交付客户'}
    ]},
    {key:'product',title:'产品管理',children:[
      {key:'products.list',title:'产品列表',module:'products',tab:0,desc:'统一维护五类代理产品；资源调度策略由代码实现'},
      {key:'products.short',title:'短效IP',custom:'productType',type:'short',desc:'短效IP产品及套餐配置'},
      {key:'products.long',title:'长效IP',custom:'productType',type:'long',desc:'长效IP产品及套餐配置'},
      {key:'products.exclusive',title:'独享IP',custom:'productType',type:'exclusive',desc:'独享IP产品及套餐配置'},
      {key:'products.vpn',title:'VPN',custom:'productType',type:'vpn',desc:'VPN产品及套餐配置'},
      {key:'products.tunnel',title:'隧道',custom:'productType',type:'tunnel',desc:'隧道产品及套餐配置'},
      {key:'products.packages',title:'套餐管理',module:'products',tab:1,desc:'维护各产品销售套餐、服务周期、额度和价格'}
    ]},
    {key:'order',title:'订单与服务',children:[
      {key:'orders.list',title:'订单管理',module:'orders',tab:0,desc:'管理客户新购、续费及服务开通订单'},
      {key:'orders.provision',title:'服务开通',module:'orders',tab:1,desc:'按自有资源或外部反向代理两种方式开通客户服务'},
      {key:'orders.services',title:'服务实例',module:'orders',tab:2,desc:'查看客户实际可使用的服务实例及资源引用'},
      {key:'orders.changes',title:'服务变更',module:'orders',tab:3,desc:'处理暂停、恢复、续费、资源更换等服务变更'},
      {key:'orders.release',title:'到期与资源释放',module:'orders',tab:4,desc:'处理到期服务及其资源占用释放'}
    ]}
  ];
  const singles=[
    {key:'dashboard',title:'首页工作台',module:'dashboard',desc:'客户、服务、自有资源、外部交付与长效代理保障的统一概览'},
    {key:'monitor',title:'监控中心',module:'monitor',desc:'仅监控CentOS与ROS主机运行状态'},
    {key:'logs',title:'运行日志',module:'logs',desc:'统一查询提取、使用、API、ROS、拨号、IP变化及操作日志'},
    {key:'alarms',title:'长效代理监控',module:'alarms',desc:'面向长效代理客户配置专项监控与钉钉/飞书通知'},
    {key:'statistics',title:'数据统计',module:'statistics',desc:'资源、客户使用、产品服务和外部交付统计'},
    {key:'system',title:'系统管理',module:'system',desc:'用户、角色、字典、参数及审计管理'}
  ];
  const routeMap={};
  singles.forEach(x=>routeMap[x.key]=x);groups.forEach(g=>g.children.forEach(x=>routeMap[x.key]={...x,parent:g.key}));
  state.route=state.route||'dashboard';
  state.openNav=state.openNav||{};
  const originalFns={dashboard,customers,resources,products,orders,monitor,logs,alarms,statistics,system};
  function current(){return routeMap[state.route]||routeMap.dashboard}
  function isGroupActive(g){const r=current();return r.parent===g.key}
  function isOpen(g){return state.openNav[g.key]!==undefined?state.openNav[g.key]:isGroupActive(g)}
  function navHtml(){
    const dashboardItem=`<div class="console-menu-item ${state.route==='dashboard'?'is-active':''}" data-route="dashboard">首页工作台</div>`;
    const submenus=groups.map(g=>`<div class="console-submenu ${isOpen(g)?'is-open':''}"><div class="console-submenu-title" data-toggle="${g.key}"><span>${g.title}</span><b>›</b></div><div class="console-submenu-children">${g.children.map(c=>`<div class="console-menu-item child ${state.route===c.key?'is-active':''}" data-route="${c.key}">${c.title}</div>`).join('')}</div></div>`).join('');
    const runtime=`<div class="console-menu-item ${state.route==='monitor'?'is-active':''}" data-route="monitor">监控中心</div><div class="console-menu-item ${state.route==='logs'?'is-active':''}" data-route="logs">运行日志</div><div class="console-menu-item ${state.route==='alarms'?'is-active':''}" data-route="alarms">长效代理监控</div><div class="console-menu-item ${state.route==='statistics'?'is-active':''}" data-route="statistics">数据统计</div><div class="console-menu-item ${state.route==='system'?'is-active':''}" data-route="system">系统管理</div>`;
    return dashboardItem+submenus+runtime;
  }
  function shell(){
    const r=current();
    $('#app').innerHTML=`<div class="console-shell-static"><aside class="console-aside-static"><div class="console-brand"><span>IP</span><div><b>IP代理管理平台</b><small>Management Console</small></div></div><div class="console-menu-static">${navHtml()}</div></aside><section class="console-body-static"><header class="console-header-static"><div><span class="env-chip">V1.2</span><b>${r.title}</b></div><div class="header-user-static"><span>系统管理员</span><button class="header-link" data-action="demo">退出登录</button></div></header><main class="console-main-static"><div id="content"></div></main></section></div>`;
    $$('[data-route]').forEach(el=>el.onclick=()=>{state.route=el.dataset.route;const x=current();if(x.parent)state.openNav[x.parent]=true;if(x.module)state.page=x.module;if(x.tab!==undefined&&x.module)state.tabs[x.module]=x.tab;shell();renderConsolePage()});
    $$('[data-toggle]').forEach(el=>el.onclick=()=>{const k=el.dataset.toggle;state.openNav[k]=!isOpen({key:k});shell();renderConsolePage()});
    $$('[data-action]').forEach(el=>el.onclick=()=>showAction(el.dataset.action,el.textContent));
  }
  function stripModuleTabs(){const t=$('.tabs',$('#content'));if(t)t.remove()}
  function patchHead(r){
    const h=$('.page-head h1,.page-head h2',$('#content'));if(h)h.textContent=r.title;
    const s=$('.page-subtitle',$('#content'));if(s)s.textContent=r.desc||'';
  }
  function accountsPage(){
    const rows=D.customers.map((x,i)=>({id:`ACC-${String(i+1).padStart(4,'0')}`,customer:x.name,username:`user_${x.id.slice(-3)}`,phone:x.phone,status:x.status,auth:x.auth,services:x.services,last:'2026-09-07 09:20'}));
    return head('客户账号','查看客户登录账号及其客户主体、认证和服务关系')+toolbar('<input class="input search" placeholder="用户名/客户/手机号"/><select class="select"><option>全部状态</option><option>正常</option><option>冻结</option></select>')+table([['账号ID','id'],['客户','customer'],['用户名','username'],['手机号','phone'],['认证','auth',tag],['服务数','services'],['状态','status',tag],['最近登录','last']],rows);
  }
  function productTypePage(type){
    const label=productTypeName[type],rows=D.products.filter(x=>x.type===label),packages=D.packages.filter(x=>rows.some(p=>p.name===x.product));
    return head(`${label}产品`,`${label}专项产品与套餐配置；资源选择、切换和调度规则由代码层实现`,btn('新增产品','new','primary'))+
      `<div class="metric-grid-static"><div class="el-card-static metric"><span>产品数量</span><strong>${rows.length}</strong><small>${label}</small></div><div class="el-card-static metric"><span>启用产品</span><strong>${rows.filter(x=>x.status==='启用').length}</strong><small>当前可售</small></div><div class="el-card-static metric"><span>套餐数量</span><strong>${packages.length}</strong><small>关联销售套餐</small></div><div class="el-card-static metric"><span>服务实例</span><strong>${rows.reduce((n,x)=>n+Number(x.services||0),0)}</strong><small>当前服务</small></div></div>`+
      toolbar('<input class="input" placeholder="产品编号/名称"/><select class="select"><option>全部状态</option><option>启用</option><option>停用</option></select>')+
      table([['产品编号','id'],['产品名称','name'],['产品类型','type'],['接入方式','access'],['代理协议','protocol'],['认证方式','auth'],['地区','region'],['套餐数','packages'],['服务数','services'],['状态','status',tag]],rows);
  }
  function renderConsolePage(){
    const r=current();
    if(r.module){state.page=r.module;if(r.tab!==undefined)state.tabs[r.module]=r.tab;$('#content').innerHTML=originalFns[r.module]();if(r.parent)stripModuleTabs();patchHead(r);bindCommon();return}
    if(r.custom==='accounts'){$('#content').innerHTML=accountsPage();bindCommon();return}
    if(r.custom==='productType'){$('#content').innerHTML=productTypePage(r.type);bindCommon();return}
  }
  renderPage=renderConsolePage;
  renderShell=shell;
  shell();
  renderConsolePage();
})();
