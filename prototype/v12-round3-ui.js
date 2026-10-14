(()=>{
  // 仅业务交互演示。示例IPv4、客户/服务、任务均为Mock，绝不调用真实服务。
  const oldResources=resources,oldProducts=products,oldOrders=orders,oldAction=showAction;
  const SAMPLE=[
    {id:'S-DEMO-EX01',customer:'青岛示例客户',product:'独享IP',pkg:'独享10出口',fixedIp:'203.0.113.18',currentIp:'203.0.113.18',expiry:'2026-10-14 00:00',grace:15,graceEnd:'2026-10-29 00:00',status:'宽限停服/保留IPv4',delivery:'待真实验证',line:'LINE-QD-07'},
    {id:'S-DEMO-LONG',customer:'青岛星途数据',product:'长效IP',pkg:'长效单线',fixedIp:'198.51.100.42',currentIp:'198.51.100.42',expiry:'2026-11-12 00:00',grace:7,graceEnd:'2026-11-19 00:00',status:'已开通（管理面）',delivery:'未真实验证',line:'LINE-QD-03'},
    {id:'S-DEMO-EXT',customer:'济南示例企业',product:'隧道',pkg:'企业套餐',fixedIp:'未承诺固定IP',currentIp:'未验证',expiry:'2026-10-31 00:00',grace:null,graceEnd:'不适用',status:'待验证真实代理',delivery:'外部数据面未接入',line:'外部反向代理'}
  ];
  const SAMPLE_PKGS=[
    {id:'PKG-LONG-01',name:'长效单线',type:'长效IP',cycle:'30天',grace:7,quota:'固定公网IPv4 1个（允许共享）',active:7},
    {id:'PKG-EX-10',name:'独享10出口',type:'独享IP',cycle:'30天',grace:15,quota:'去重公网IPv4 10个（跨客户排他）',active:3},
    {id:'PKG-SHORT',name:'短效IP 10万次',type:'短效IP',cycle:'30天',grace:null,quota:'提取100000次',active:12}
  ];
  const pill=(value)=>'<span class="tag '+(/待|未|风险|宽限/.test(value)?'warning':'info')+'">'+esc(value)+'</span>';
  const demoBanner=()=>note('第三轮交互原型｜全部为演示数据；不会写入真实数据库。到期停服、人工切换、续费恢复仍须真实产品/资源API回执才能判定成功。');
  resources=function(){
    const i=state.tabs.resources;
    if(i===2){
      return head('ROS列表','只负责拨号及线路/BAS汇总，不提供设备级主备或自动切换')+demoBanner()+
        table([['ROS','id'],['对应CentOS','centos'],['CPU','cpu'],['内存','memory'],['线路数','lines'],['在线线路','online'],['当前公网IP','ipCount'],['BAS','basCount'],['状态','status',tag],['上报时间','report']],D.ros,false);
    }
    if(i===3){
      return head('家宽/线路','节点=ROS逻辑线路=家宽/线路；客户归属由服务绑定派生')+demoBanner()+
        table([['线路ID','id'],['ROS','ros'],['别名','alias'],['拨号账号','account'],['当前IP','ip'],['HTTP','http'],['SOCKS5','socks'],['来源线路类型','type'],['地区','province'],['城市','city'],['运营商','carrier'],['BAS','bas'],['在线','status',tag],['上报','report']],D.lines,false)+
        note('点击具体服务查看资源绑定、所属来源池与拨号/IP历史。家宽线路主档不直接保存“所属客户”。');
    }
    if(i===5){
      return head('独享出口IPv4占用','跨客户排他的主体是实际交付的公网IPv4，而非线路、端口或资源池')+demoBanner()+
        metrics([['示例独享出口','1','基于去重公网IPv4'],['宽限保留','1','已停服仍占用'],['跨客户冲突','1','不同端口不可绕过'],['待验证出口','1','真实数据面能力待签署']])+
        table([['客户服务','id'],['客户','customer'],['承诺出口IPv4','fixedIp'],['当前上报IP','currentIp'],['来源线路','line'],['占用/业务状态','status',pill],['宽限截止','graceEnd']],SAMPLE.slice(0,2).map(s=>({...s})),false)+
        '<div class="card pad top-gap"><b>业务操作</b><p>所有独享出口分配、切换与释放从服务实例发起；跨客户相同IPv4即使不同端口也阻断。</p>'+
        btn('演示跨客户独享冲突','r3-conflict','primary')+' '+btn('查看独享服务详情','r3-service-0')+'</div>';
    }
    if(i===4){
      const html=oldResources();
      return html+note('R3检查：来源型资源池的SHARED/LONG成员可以重叠；查看成员和服务引用必须基于主子表，不按五类产品建五种资源池。');
    }
    return oldResources();
  };
  products=function(){
    if(state.tabs.products!==1)return oldProducts();
    return head('套餐管理','长效与独享按各自套餐配置宽限，历史服务取开通时快照')+demoBanner()+
      table([['套餐编号','id'],['套餐名称','name'],['产品类型','type'],['销售周期','cycle'],['资源权益','quota'],['宽限(天)','grace',v=>v==null?'不适用':esc(v)+'天'],['当前服务','active']],SAMPLE_PKGS,false)+
      '<div class="card pad top-gap"><b>套餐维护预览</b><p>到期立即停服，宽限期间固定出口保留、客户不能访问。宽限默认值、是否允许0天尚待业务确认。</p>'+
      btn('编辑长效套餐','r3-pkg-0','primary')+' '+btn('编辑独享套餐','r3-pkg-1')+'</div>';
  };
  orders=function(){
    const i=state.tabs.orders;
    if(i===2){
      return head('服务实例','订单项产生服务实例；每个服务独立记录出口、绑定、宽限和真实交付')+demoBanner()+
        table([['服务号','id'],['客户','customer'],['产品','product'],['套餐','pkg'],['固定出口IPv4','fixedIp'],['到期','expiry'],['宽限截止','graceEnd'],['服务状态','status',pill],['真实交付','delivery',pill]],SAMPLE,false)+
        '<div class="card pad top-gap">'+SAMPLE.map((s,i)=>btn('查看 '+s.id,'r3-service-'+i,i===0?'primary':'')).join(' ')+'</div>';
    }
    if(i===3){
      return head('服务变更','不填写技术ID；从具体服务选择续费、暂停、恢复、终止')+demoBanner()+
        '<div class="card pad"><div class="card-title">按业务对象发起</div><div class="form-grid"><div class="form-item"><label class="form-label">客户服务</label><select class="select" id="r3-service-picker">'+SAMPLE.map((s,i)=>'<option value="'+i+'">'+esc(s.id+' · '+s.customer+' · '+s.product)+'</option>').join('')+'</select></div><div class="form-item"><label class="form-label">业务动作</label><select id="r3-change-type" class="select"><option value="renew">续费</option><option value="pause">暂停</option><option value="resume">恢复</option><option value="terminate">终止</option></select></div><div class="form-item full"><label class="form-label">操作原因</label><textarea class="textarea" id="r3-change-reason" placeholder="演示：需要填写业务原因"></textarea></div></div>'+btn('预检与影响展示','r3-change','primary')+'</div>';
    }
    if(i===4){
      return head('到期宽限与释放','停服→保留固定IP→宽限内续费恢复／超宽限真实释放')+demoBanner()+
        table([['服务号','id'],['客户','customer'],['到期','expiry'],['套餐宽限天数','grace'],['保留截止','graceEnd'],['服务阶段','status',pill],['实际交付','delivery',pill]],SAMPLE.filter(x=>x.grace!=null),false)+
        '<div class="card pad top-gap"><b>到期动作</b><p>必须取得产品真实停服回执才标停服；回收失败时继续占用出口，不因宽限结束提前放行他客。</p>'+btn('演示宽限内续费恢复','r3-renew','primary')+'</div>';
    }
    return oldOrders();
  };
  let selected=0,step=0,packageIdx=0;
  const close=()=>{$('#layer').innerHTML=''};
  function openModal(title,html,foot){
    $('#layer').innerHTML='<div class="modal-mask" id="r3-mask"><div class="modal" style="width:min(780px,94vw)"><div class="modal-head"><b>'+esc(title)+'</b><button class="drawer-close" id="r3-close">×</button></div><div class="modal-body">'+demoBanner()+html+'</div><div class="modal-foot">'+(foot||btn('关闭','demo'))+'</div></div></div>';
    $('#r3-close').onclick=close;$('#r3-mask').onclick=e=>{if(e.target.id==='r3-mask')close()};
    $$('[data-action]',$('#layer')).forEach(el=>el.onclick=()=>showAction(el.dataset.action,el.textContent));
  }
  function serviceModal(i){
    selected=i;const s=SAMPLE[i];
    const info='<div class="form-grid"><div class="form-item"><label class="form-label">服务/客户</label><b>'+esc(s.id+' / '+s.customer)+'</b></div><div class="form-item"><label class="form-label">产品/套餐</label>'+esc(s.product+' / '+s.pkg)+'</div><div class="form-item"><label class="form-label">业务到期</label>'+esc(s.expiry)+'</div><div class="form-item"><label class="form-label">宽限天数/截止</label>'+esc((s.grace??'不适用')+' / '+s.graceEnd)+'</div><div class="form-item"><label class="form-label">承诺出口IPv4</label>'+esc(s.fixedIp)+'</div><div class="form-item"><label class="form-label">线路当前IP</label>'+esc(s.currentIp)+'</div><div class="form-item"><label class="form-label">占用/期限</label>'+pill(s.status)+'</div><div class="form-item"><label class="form-label">实际交付验证</label>'+pill(s.delivery)+'</div></div>';
    const subs='<h4>资源绑定子表</h4><div class="note">'+esc(s.line)+' → '+esc(s.fixedIp)+'（服务主出口） · 出口排他/宽限保留单独追踪</div><h4>备用候选及人工切换</h4><p>备用候选按服务维护，校验IPv4跨客户冲突、入口变化和真实适配接口。</p><h4>执行任务/审计</h4><p>操作ID、变更原因、实际回执、失败重试与补偿任务按服务可追溯。</p>';
    openModal('服务详情 · '+s.id,info+subs,btn('人工切换预检','r3-failover','primary')+' '+btn('模拟续费/恢复','r3-renew')+' '+btn('关闭','r3-close'));
  }
  function failover(){
    const s=SAMPLE[selected];
    const steps=['选择备用','资格预检','差异影响与原因','执行/验证结果'];
    let body='<div class="steps">'+steps.map((x,i)=>'<div class="step '+(i<=step?'active':'')+'">'+(i+1)+' '+x+'</div>').join('')+'</div>';
    if(step===0)body+='<p>主出口：'+esc(s.fixedIp)+' / '+esc(s.line)+'</p><label class="form-label">备用候选（示例）</label><select class="select"><option>LINE-BACKUP-02 · 198.51.100.77 · 健康未知</option></select>';
    if(step===1)body+='<p>预检：客户与服务归属符合、固定IP将变化，需客户确认；真实资源/产品切换接口 <b>未签署</b>。</p>'+note('阻断项：实际切换和网关入口迁移能力未知。预检显示“阻断”，不是成功。');
    if(step===2)body+='<p>原固定IP：'+esc(s.fixedIp)+' → 候选出口：198.51.100.77</p><p>影响：需更新客户交付入口和专项监控目标；不能保证原固定IP不变。</p><textarea class="textarea" placeholder="人工切换原因（演示）"></textarea>';
    if(step===3)body+='<div class="note">演示结果：提交阻断｜真实接口未对接。原主出口继续保留；不会修改真实数据或自动切换。</div>';
    openModal('人工主备切换向导',body,step<3?btn('下一步（仅演示）','r3-next','primary')+' '+btn('返回服务','r3-back'):btn('返回服务','r3-back','primary'));
  }
  showAction=function(a,t){
    if(a.startsWith('r3-service-')){serviceModal(Number(a.slice(-1)));return}
    if(a==='r3-failover'){step=0;failover();return}
    if(a==='r3-next'){step++;failover();return}
    if(a==='r3-back'){serviceModal(selected);return}
    if(a==='r3-close'){close();return}
    if(a==='r3-conflict'){
      openModal('独享跨客户IPv4冲突','<p>客户B拟使用同公网出口 <b>203.0.113.18</b>（即使端口不同）；客户A已持有独享占用且处于宽限停服保留中。</p>'+note('资格校验：阻断 EGRESS_IPV4_EXCLUSIVE_CONFLICT。原客户出口不释放，新客户不能开通。'),btn('关闭','r3-close'));return
    }
    if(a==='r3-renew'){
      openModal('宽限内续费恢复','<p>到期已停服；原固定出口仍保留。续费先核对新期限和真实适配器/采购能力，再恢复。</p>'+note('演示结果：业务预检可展示；未接入真实产品恢复API，不能显示“已恢复可用”。'),btn('关闭','r3-close'));return
    }
    if(a.startsWith('r3-pkg-')){
      packageIdx=Number(a.slice(-1));const p=SAMPLE_PKGS[packageIdx];
      openModal('编辑套餐 · '+p.name,'<div class="form-grid"><div class="form-item"><label class="form-label">产品类型</label>'+esc(p.type)+'</div><div class="form-item"><label class="form-label">套餐周期</label>'+esc(p.cycle)+'</div><div class="form-item"><label class="form-label">续费宽限天数</label><input class="input" type="number" min="0" value="'+p.grace+'"/></div></div>'+note('保存只影响未来新服务；历史服务保留开通时的宽限快照。0天是否允许待业务确认。'),btn('模拟保存结果','r3-package-result','primary')+' '+btn('关闭','r3-close'));return
    }
    if(a==='r3-package-result'){openModal('套餐编辑预览','<p>本演示不会真正保存。预期：仅修改套餐宽限字段；现有服务的grace_days_snapshot与grace_until_at不追溯。</p>',btn('关闭','r3-close'));return}
    if(a==='r3-change'){
      const n=$('#r3-service-picker'),type=$('#r3-change-type'),reason=$('#r3-change-reason');
      if(!n||!type)return;const s=SAMPLE[Number(n.value)];
      if(!reason.value.trim()){openModal('业务原因必填',note('未填写操作原因，无法继续预检。'),btn('关闭','r3-close'));return}
      openModal('服务变更影响预检','<p>服务：'+esc(s.id+' / '+s.customer)+'</p><p>操作类型：'+esc(type.options[type.selectedIndex].text)+'</p><p>原到期：'+esc(s.expiry)+'；宽限截止：'+esc(s.graceEnd)+'</p><p>真实交付状态：'+esc(s.delivery)+'</p><p>操作原因：'+esc(reason.value)+'</p>'+note('预检仅演示；未接入真实数据面时，禁止宣称服务已经真实恢复/停用。'),btn('关闭','r3-close'));return
    }
    return oldAction(a,t);
  };
})();
