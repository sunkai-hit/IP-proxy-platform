(()=>{
  // 原型仅演示交付向导与业务分支，不向真实后端写入。
  const priorOrders=orders,priorShowAction=showAction;
  const demo=[
    ...D.orders.filter(o=>o.status==='待开通').map(o=>({...o,kind:'短效IP',count:1})),
    {id:'DEMO-LONG',customer:'青岛星途数据（演示）',product:'稳定长效代理',package:'长效单线',type:'新购',status:'待开通',kind:'长效IP',count:1,effective:'2026-10-13',expire:'2026-11-12'},
    {id:'DEMO-EXCLUSIVE',customer:'青岛示例客户（演示）',product:'独享家宽代理',package:'10条独享',type:'新购',status:'待开通',kind:'独享IP',count:1,effective:'2026-10-13',expire:'2026-11-12'},
    {id:'DEMO-VPN',customer:'济南示例客户（演示）',product:'VPN服务',package:'企业版',type:'新购',status:'待开通',kind:'VPN',count:1,effective:'2026-10-13',expire:'2026-11-12'},
    {id:'DEMO-TUNNEL',customer:'杭州示例客户（演示）',product:'隧道接入',package:'企业套餐',type:'新购',status:'待开通',kind:'隧道',count:1,effective:'2026-10-13',expire:'2026-11-12'}
  ];
  const rules={
    '短效IP':'自有交付：匹配SHARED共享线路所在来源型资源池；出口由提取时的内置策略决定，不在开通时固定线路。',
    '长效IP':'自有交付：推荐LONG长效线路，预检在线/端口/有效期后绑定固定线路；当前IP是运行态属性。',
    '独享IP':'自有交付：按套餐数量分配未占用的家宽线路，逐条建立独享占用记录；资源不足整单禁止提交。',
    'VPN':'自有交付：需实际VPN数据面适配器、协议与连接数检查；适配器未接入不能声称已真实开通。',
    '隧道':'自有交付：需隧道入口、端口、并发等专项参数及数据面适配器，不创建隧道类型资源池。'
  };
  orders=function(){
    if(state.tabs.orders!==1)return priorOrders();
    const rows=demo.map((o,i)=>'<tr><td>'+esc(o.id)+'</td><td>'+esc(o.customer)+'</td><td>'+esc(o.product)+'</td><td>'+esc(o.package)+'</td><td>'+esc(o.kind)+'</td><td>'+esc(o.count)+'</td><td>'+tag('待开通')+'</td><td><button class="text-btn" data-action="provision-demo-'+i+'">进入开通</button></td></tr>').join('');
    return head('服务开通','待开通订单驱动的业务闭环；已确认订单只读，开通时指定交付方式与资源')+
      note('演示页面：带“DEMO”编号的记录为交互样例，不是真实订单。特殊手工开通不在普通菜单展示。')+
      '<div class="card pad"><div class="card-title">待开通订单</div><div class="toolbar"><input class="input" placeholder="订单号/客户（示意）"/><span class="toolbar-spacer"></span><button class="btn primary" data-action="search">查询</button></div><div class="table-wrap"><table><thead><tr><th>订单号</th><th>客户</th><th>产品</th><th>套餐</th><th>类型</th><th>服务数</th><th>状态</th><th>操作</th></tr></thead><tbody>'+rows+'</tbody></table></div></div>'+
      note('订单确认 → 交付方式 → 资源预检 → 开通确认 → 交付结果。原型操作只演示界面，不会生成真实服务实例。');
  };
  let selected=null,step=0,mode='SELF_OWNED';
  function modal(){
    if(!selected)return;
    const o=selected,labels=['订单确认','交付配置','资源检查','确认开通','开通结果'];
    const stepBar='<div class="steps">'+labels.map((l,i)=>'<div class="step '+(i<=step?'active':'')+'">'+(i+1)+' '+l+'</div>').join('')+'</div>';
    const orderInfo='<div class="form-grid"><div class="form-item"><label class="form-label">订单</label><div>'+esc(o.id)+'</div></div><div class="form-item"><label class="form-label">客户</label><div>'+esc(o.customer)+'</div></div><div class="form-item"><label class="form-label">产品</label><div>'+esc(o.product)+'</div></div><div class="form-item"><label class="form-label">套餐</label><div>'+esc(o.package)+'</div></div><div class="form-item"><label class="form-label">服务数</label><div>'+esc(o.count)+'</div></div><div class="form-item"><label class="form-label">计划期限</label><div>'+esc(o.effective)+' 至 '+esc(o.expire)+'</div></div></div>';
    let details='';
    if(step===0)details=note('已确认订单信息只读；产品、套餐、服务数来自订单，不再手工输入技术ID。')+orderInfo;
    if(step===1)details='<div class="form-item"><label class="form-label">交付方式</label><select class="select" id="delivery-demo"><option value="SELF_OWNED" '+(mode==='SELF_OWNED'?'selected':'')+'>自有资源</option><option value="EXTERNAL_PROXY" '+(mode==='EXTERNAL_PROXY'?'selected':'')+'>外部反向代理</option></select></div><div class="top-gap">'+note(mode==='SELF_OWNED'?rules[o.kind]:'从状态正常、所属客户一致且未占用的平台代理入口选择资源；根据代理自动关联采购套餐，不输入采购ID。')+'</div>';
    if(step===2)details=note(mode==='SELF_OWNED'?rules[o.kind]:'示例：采购套餐ACTIVE、反向代理ACTIVE、客户一致、未占用且服务到期日不晚于上游套餐到期日。')+ '<p class="subtle">预检只产生资源快照，提交时后端仍需加锁重验，防止并发重复分配。</p>'+(mode==='SELF_OWNED'&&['VPN','隧道'].includes(o.kind)?'<p class="subtle">真实数据面未连接时禁止标记可用。</p>':'');
    if(step===3)details=note('确认后才产生服务实例、资源绑定和一次性凭证。订单全部服务成功才进入“已开通”。')+'<div class="form-item"><label class="form-label">开通原因</label><textarea id="reason-demo" class="textarea" placeholder="请输入操作原因">业务已确认，申请开通</textarea></div>';
    if(step===4)details=note('原型演示完成：正式系统返回业务结果（服务编号、交付方式、绑定资源、状态、凭证摘要），不向运营显示调试JSON。')+'<div class="card pad"><div class="card-title">服务交付结果（模拟）</div><p>服务编号：SVC-DEMO · 状态：演示成功 · 交付方式：'+(mode==='SELF_OWNED'?'自有资源':'外部反向代理')+'</p><p>真实凭证仅在成功开通后按权限一次性展示。</p></div>';
    $('#layer').innerHTML='<div class="modal-mask" id="mask"><div class="modal" style="width:min(920px,96vw);max-height:88vh;overflow:auto"><div class="modal-head"><div class="modal-title">服务开通向导（原型）</div><button class="drawer-close" id="demo-close">×</button></div><div class="modal-body">'+stepBar+details+'</div><div class="modal-foot"><button class="btn" id="demo-cancel">'+(step===4?'关闭':'取消')+'</button>'+(step>0&&step<4?'<button class="btn" id="demo-prev">上一步</button>':'')+(step<4?'<button class="btn primary" id="demo-next">'+(step===3?'模拟提交':'下一步')+'</button>':'')+'</div></div></div>';
    const close=()=>{$('#layer').innerHTML='';selected=null};
    $('#demo-close').onclick=close;$('#demo-cancel').onclick=close;
    const el=$('#delivery-demo');if(el)el.onchange=()=>{mode=el.value;modal()};
    if($('#demo-prev'))$('#demo-prev').onclick=()=>{step--;modal()};
    if($('#demo-next'))$('#demo-next').onclick=()=>{if(step===3&&(!$('#reason-demo')||!$('#reason-demo').value.trim()))return;step++;modal()};
    $('#mask').onclick=e=>{if(e.target.id==='mask')close()};
  }
  showAction=function(a,t){
    if(a&&a.startsWith('provision-demo-')){
      selected=demo[Number(a.slice('provision-demo-'.length))]||null;step=0;mode='SELF_OWNED';modal();return;
    }
    return priorShowAction(a,t);
  };
})();