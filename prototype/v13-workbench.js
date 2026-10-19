(function v13PrototypeWorkbench(){
  'use strict';
  // V1.3 needs explicit separation among business records, remote execution and real connectivity.
  // This module never calls any production endpoint and cannot sign off human acceptance.
  const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const details=[
    ['01','首页工作台','dashboard'],['02','客户列表','customers.list'],['03','客户详情','customers.list'],
    ['04','客户认证','customers.auth'],['05','客户账号与服务认证','customers.accounts'],
    ['06','资源概览','resources.overview'],['07','CentOS列表与详情','resources.centos'],
    ['08','ROS列表与详情','resources.ros'],['09','家宽/线路详情','resources.lines'],
    ['10','来源资源池','resources.pools'],['11','独享IPv4出口占用','resources.exclusive'],
    ['12','备用候选管理','orders.services'],['13','人工主备切换向导','orders.services'],
    ['14','域名配置','resources.domains'],['15','供应商管理','resources.suppliers'],
    ['16','外部采购套餐','resources.purchases'],['17','反向代理管理','resources.reverse'],
    ['18','产品列表/配置','products.list'],['19','套餐管理','products.packages'],
    ['20','订单管理','orders.list'],['21','订单五步开通','orders.provision'],
    ['22','特权直接开通','orders.provision'],['23','服务实例列表与详情','orders.services'],
    ['24','服务资源绑定','orders.services'],['25','服务续费/变更','orders.changes'],
    ['26','到期/宽限与受控释放','orders.release'],['27','CentOS监控','monitor'],
    ['28','ROS监控','monitor'],['29','十一类日志中心','logs'],
    ['30','长效代理专项监控配置','alarms'],['31','客户钉钉/飞书机器人','alarms'],
    ['32','专项告警与通知','alarms'],['33','七类数据统计','statistics'],
    ['34','后台用户角色权限','system'],['35','登录日志与操作审计','system'],
    ['36','全局业务关联与高危交互','orders.services']
  ];
  const cases=[
    ['01','多项多实例新购','两个订单项采用自有和外部交付，逐实例建服务且不重复开通','订单/服务外键、两种真实产品回执及网络连通'],
    ['02','独享跨客户出口冲突','同一IPv4不同线路或端口、不同客户甚至同客户另一个独享服务均不能重用','公网出口真实证明、并发唯一性拒绝'],
    ['03','长效跨客户共享','满足健康、共享容量、客户权限隔离时优先复用长效出口','两客户真实共享连接和相同出口证明'],
    ['04','到期停服保留IP','长效/独享到期真实停服，宽限中固定出口继续保留','数据面拒绝连接、占用锁仍在'],
    ['05','宽限续费恢复','付款确认后按原到期点顺延并恢复原固定出口','实际恢复连接和同一出口IP'],
    ['06','宽限超期释放','真实解绑、撤销授权、释放后才允许恢复短效池','实际远端释放与健康检查'],
    ['07','人工主备切换成功','客户同意、备用健康、授权具备后执行并校验入口迁移','网络切换、出口确认、监控迁移'],
    ['08','人工切换失败','出口冲突或超时不得伪造成功，保持原绑定并查单补偿','失败记录、operationId与远端回查'],
    ['09','采购期限不足','供应商有效期不能覆盖拟续费期限时阻断','采购期限、上游状态和续费拒绝'],
    ['10','外部代理暂停终止','暂停/恢复/终止实际控制平台入口和上游访问','网关实测、远端操作与回执'],
    ['11','五款产品真实交付','短效、长效、独享、VPN、隧道各完成开通、使用、停用、释放','五组独立网络证据'],
    ['12','权限与凭证脱敏','只读/运营/资源/管理员权限隔离，敏感凭证不泄漏','403、脱敏回执及审计日志'],
    ['13','资源同步稳定ID','线路稳定ID不随拨号IP变化，同拨号账号不误合并','真实资源API同步样本和历史'],
    ['14','远端成功回执丢失','调用超时先按operationId查单，重试不得重复占用','产品组幂等查单与远端审计'],
    ['15','付款先行防资损','订单仅确认但付款未知时阻断采购/开通','付款审计、拒绝码、供应商未扣费'],
    ['16','采购可选值续购','成本和额度可空；需先确认付款、采购期限真实覆盖','付款凭证、供应商人工购入证据'],
    ['17','续费服务归属和去重','未付款、错客户/服务、重复提交均拒绝且保留旧期限和出口','目标外键、操作号与实际续费回执'],
    ['18','到期任务去重与停服','0/3天宽限与重复调度只建一次任务；缺远端回执不虚变','任务数据库、真实停服和释放证明']
  ];
  const I=[
    ['A01','查询产品组支持的能力','平台 → 产品组','系统接入、产品上架及接口能力变化'],
    ['A02','查询CentOS与ROS设备','平台 → 产品组','资源设备列表与监控'],
    ['A03','查询家宽线路与公网IP','平台 → 产品组','线路筛选、动态IP变化'],
    ['A04','查询短效IP资源池','平台 → 产品组','共享池情况、准备转换'],
    ['A05','查询可共享的长效IP','平台 → 产品组','长效优先复用健康且有容量的固定出口'],
    ['A06','查询某个代理资源的详情','平台 → 产品组','分配异常、人工切换预检'],
    ['B01','预检长效或独享资源分配','平台 → 产品组','付款/权限确认后分配前检查'],
    ['B02','预占代理资源','平台 → 产品组','选定长效或待转换短效后防并发'],
    ['B03','取消尚未使用的预占','平台 → 产品组','放弃、失败或失效的预占'],
    ['B04','确认分配并转换代理模式','平台 → 产品组','共享长效绑定或短效转长效/独享'],
    ['B05','转换失败后的资源恢复','平台 → 产品组','资源操作失败后的安全补偿'],
    ['C01','开通VPN、隧道及其他代理服务','平台 → 产品组','订单权益校验后实际建立产品服务'],
    ['C02','查询客户服务实际运行状态','平台 → 产品组','服务详情、交付回查、监控'],
    ['C03','暂停、恢复、续费或终止服务','平台 → 产品组','客户变更、到期停服及终止'],
    ['C04','解除最后占用并恢复短效池','平台 → 产品组','无有效保留后安全回池'],
    ['C05','人工主备切换预检','平台 → 产品组','运营选择候选并评估风险'],
    ['C06','执行人工主备切换','平台 → 产品组','授权与客户同意后切换'],
    ['C07','查询远端操作任务结果','平台 → 产品组','超时与异常的幂等回查'],
    ['D01','审核用户是否可以提取或使用资源','产品组 → 平台','客户/套餐/订单/额度/白名单鉴权'],
    ['D02','检查授权凭证是否仍然有效','产品组 → 平台','短期授权有效性确认'],
    ['D03','通知产品组撤销客户授权','平台 → 产品组','到期、冻结、暂停、终止'],
    ['E01','产品组回传资源与执行事件','产品组 → 平台','IP变化、转换、停服、释放回执'],
    ['E02','产品组上报客户使用量','产品组 → 平台','提取/连接/流量数据采集'],
    ['E03','查询资源健康与用量快照','平台 → 产品组','容量和故障评估'],
    ['E04','业务平台发起服务资源对账','平台 → 产品组','平台记录与远端实际状态不一致'],
    ['F01','查询线路拨号与公网IP变化日志','平台 → 产品组','拨号历史与IP变更原因'],
    ['F02','查询与执行域名解析变更','平台 → 产品组','DNS变更与实际解析验证'],
    ['F03','配置和检测外部反向代理','平台 → 产品组','供应商采购后平台代理与健康检查'],
    ['F04','执行已授权短效IP提取','平台 → 产品组','平台权限确认后的提取与调度'],
    ['F05','执行设备及线路受控操作','平台 → 产品组','授权诊断与重拨'],
    ['F06','查询产品真实日志与专项探测','平台 → 产品组','真实日志、探测与异常诊断']
  ];
  const orders=[
    {id:'DEMO-NEW-PAID-01',customer:'青岛星途数据',type:'长效IP',paid:true,mode:'SELF_OWNED',message:'长效优先复用已验证的健康共享出口'},
    {id:'DEMO-NEW-UNPAID-02',customer:'济南测试客户',type:'短效IP',paid:false,mode:'SELF_OWNED',message:'付款未核实必须阻断'},
    {id:'DEMO-EXCLUSIVE-03',customer:'青岛另一企业',type:'独享IP',paid:true,mode:'SELF_OWNED',message:'原IPv4已被另一独享服务占用'},
    {id:'DEMO-EXT-04',customer:'杭州示例客户',type:'隧道',paid:true,mode:'EXTERNAL_PROXY',message:'外部采购期限不能覆盖客户服务时阻断'},
    {id:'DEMO-VPN-05',customer:'威海云启科技',type:'VPN',paid:true,mode:'SELF_OWNED',message:'VPN产品组实际网关能力尚未签署'}
  ];
  const stateV={
    tab:'flow',orderIndex:0,step:0,candidate:'reuse',ip:'198.51.100.42',upstreamCover:'no',
    task:null,interfaceGroup:'all',interfaceQuery:'',interfaceSelected:null,reviewQuery:'',reviewMode:'pages',
    audit:[],attempts:0
  };
  const groupNames={A:'资源与设备查询',B:'资源分配与模式转换',C:'客户服务与生命周期',D:'业务平台权限审核',E:'运行事件与用量数据',F:'最新需求补充专项'};
  const button=(label,action,kind='')=>`<button type="button" class="v13-btn ${kind}" data-v13-action="${esc(action)}">${esc(label)}</button>`;
  const note=(body,kind='')=>`<div class="v13-note ${kind}">${body}</div>`;
  const heading=(name,desc)=>`<div class="page-head"><div><h1>${esc(name)}</h1><div class="page-subtitle">${esc(desc)}</div></div><span class="v13-label">V1.3 · 纯本地模拟</span></div>`;
  const stat=(a,b,c)=>`<div class="v13-stat"><span>${esc(a)}</span><strong>${esc(b)}</strong><small>${esc(c)}</small></div>`;
  const chosen=()=>orders[stateV.orderIndex];
  function validate(){
    const o=chosen(),fail=[],warn=[];
    if(!o.paid)fail.push('付款未核实：不得采购、开通或续费（付款先行）。');
    if(o.mode==='EXTERNAL_PROXY'){
      if(stateV.upstreamCover!=='yes')fail.push('上游采购有效期或真实代理能力未覆盖客户服务期限。');
      warn.push('F03 上游代理配置和数据面实际可用性仍待双方签署。');
    }else if(o.type==='独享IP'){
      if(stateV.ip==='198.51.100.42')fail.push('该公网IPv4已有独享占用；不同客户、同客户其他服务、不同端口均不能重复使用。');
      warn.push('真实出口IPv4与本地快照必须由产品组确认，预占与释放均需远端回执。');
    }else if(o.type==='长效IP'){
      if(stateV.candidate==='reuse')warn.push('优先复用仅适用于健康、在线、有共享容量且用户认证隔离的长效出口。');
      else warn.push('短效转长效需要停止短效新提取、迁移短效客户、原子转换和失败补偿。');
    }else if(o.type==='VPN'||o.type==='隧道'){
      warn.push('数据面接入及实际连接能力未知；Mock不能认定开通或入口可用。');
    }
    return {fail,warn,pass:fail.length===0};
  }
  function appendAudit(message){
    stateV.audit.unshift({when:new Date().toLocaleString('zh-CN'),text:message});
    stateV.audit=stateV.audit.slice(0,8);
  }
  function wizard(){
    const o=chosen(),v=validate();
    const stages=['① 订单与付款','② 交付配置','③ 资源/接口预检','④ 确认与幂等提交','⑤ 远端结果核验'];
    const steps=`<div class="v13-steps">${stages.map((l,i)=>`<div class="${i===stateV.step?'current':i<stateV.step?'finished':''}">${l}</div>`).join('')}</div>`;
    const selectOrder=`<label class="v13-field">演示订单<select data-v13-field="order">${orders.map((x,i)=>`<option value="${i}" ${i===stateV.orderIndex?'selected':''}>${esc(x.id+' · '+x.type)}</option>`).join('')}</select></label>`;
    const common=`<div class="v13-detail-grid"><div><small>客户</small><strong>${esc(o.customer)}</strong></div><div><small>产品类型</small><strong>${esc(o.type)}</strong></div><div><small>订单支付</small><strong>${o.paid?'已确认付款（模拟）':'付款未核实（模拟）'}</strong></div><div><small>期望交付</small><strong>${o.mode==='SELF_OWNED'?'自有线路':'外部反向代理'}</strong></div></div>`;
    let body='';
    if(stateV.step===0){
      body=note('新购、采购与续费均先核实客户实际付款。订单“已确认”不等于“付款成功”，不得自动向供应商扣款。','strong')+
        selectOrder+common+note('订单项应独立创建服务实例；多订单项/多实例不得被合并为一个开通任务。');
    }else if(stateV.step===1){
      body=common+`<label class="v13-field">交付方式<select data-v13-field="mode"><option value="SELF_OWNED" ${o.mode==='SELF_OWNED'?'selected':''}>SELF_OWNED · 自有资源</option><option value="EXTERNAL_PROXY" ${o.mode==='EXTERNAL_PROXY'?'selected':''}>EXTERNAL_PROXY · 外部反向代理</option></select></label>`;
      if(o.mode==='SELF_OWNED'){
        if(o.type==='长效IP')body+=`<label class="v13-field">资源分配路径<select data-v13-field="candidate"><option value="reuse" ${stateV.candidate==='reuse'?'selected':''}>优先复用已有健康长效共享出口</option><option value="convert" ${stateV.candidate==='convert'?'selected':''}>无合格长效后从短效资源池转换</option></select></label>`;
        if(o.type==='独享IP')body+=`<label class="v13-field">拟绑定公网出口<select data-v13-field="ip"><option value="198.51.100.42" ${stateV.ip==='198.51.100.42'?'selected':''}>198.51.100.42 · 已有独享占用（冲突样例）</option><option value="203.0.113.82" ${stateV.ip==='203.0.113.82'?'selected':''}>203.0.113.82 · 本地无已知占用（仍需产品实测）</option></select></label>`;
      }else body+=`<label class="v13-field">供应商采购覆盖<select data-v13-field="upstream"><option value="no" ${stateV.upstreamCover==='no'?'selected':''}>未覆盖 / 未核实真实有效期</option><option value="yes" ${stateV.upstreamCover==='yes'?'selected':''}>模拟：登记的采购有效期覆盖客户服务</option></select></label>`;
      body+=note('订单创建前的套餐权益快照和客户归属不可通过后续产品/采购编辑反向修改。');
    }else if(stateV.step===2){
      body=common+`<div class="v13-grid two"><div class="v13-panel"><h3>业务规则校验</h3><ul><li>客户/订单项/目标服务归属一致</li><li>客户付款与套餐权益已核</li><li>出口模式与来源池归属匹配</li><li>operationId 防重复预占/开通</li></ul></div><div class="v13-panel"><h3>外部真实能力</h3><ul><li>接口 A01/A05/B01/C01/C07/F03 按需确认</li><li>超时优先按相同 operationId 查单</li><li>网络连通、回执、网关入口均未实测</li></ul></div></div>`;
      body+=v.fail.length?note(v.fail.map(esc).join('<br/>'),'danger'):note('本地业务规则样例未发现阻断；并不表示真实资源可用或已有接口回执。','ok');
      if(v.warn.length)body+=note(v.warn.map(esc).join('<br/>'));
    }else if(stateV.step===3){
      body=common+note(v.fail.length?('禁止模拟提交：'+v.fail.map(esc).join('；')):'将创建一条仅驻留浏览器内存的“待远端核验”任务；不会写数据库、预占资源或请求产品组。',v.fail.length?'danger':'strong');
      body+=`<div class="v13-panel"><p><b>幂等业务键</b>：${esc('prototype:v13:'+o.id)}</p><p><b>提交语义</b>：请求受理/任务存在 ≠ 资源已锁定 ≠ 实际服务已开通 ≠ 网络可用</p><p><b>超时策略</b>：先使用 C07 查询原 operationId，不盲目重复调用 B04/C01。</p></div>`;
    }else{
      const task=stateV.task;
      body=note('演示仅完成本地业务审查。实际产品、资源API、反向代理网关均未调用。','strong')+
        `<div class="v13-detail-grid"><div><small>业务订单</small><strong>${esc(o.id)}</strong></div><div><small>本地任务标识</small><strong>${esc(task?.id||'未创建')}</strong></div><div><small>管理面阶段</small><strong>待远端回查（模拟）</strong></div><div><small>真实网络可用性</small><strong>UNKNOWN · 无实测</strong></div><div><small>资源是否真实占用</small><strong>UNKNOWN · 未获回执</strong></div><div><small>人工验收</small><strong>未签署</strong></div></div>`+
        note('需要真实 C07/E01 回执、稳定资源ID、服务实例绑定、外部网络验证和审计证据之后，正式系统才能判定交付成功。','danger');
    }
    const footer=`<div class="v13-actions">${stateV.step>0&&stateV.step<4?button('上一步','back'):''}${stateV.step<3?button('下一步','next','primary'):stateV.step===3?button(v.pass?'创建本地待核验任务':'规则阻断：返回修改',v.pass?'submit':'back',v.pass?'primary':''):button('重新开始演示','restart','primary')}${button('查看接口清单','to-interfaces')}</div>`;
    const logs=stateV.audit.length?`<div class="v13-audit"><b>本地操作轨迹（不属于真实审计）</b>${stateV.audit.map(x=>`<div><span>${esc(x.when)}</span> ${esc(x.text)}</div>`).join('')}</div>`:'';
    return heading('订单五步开通 · V1.3交互演示','付款先行、业务预检、幂等防重以及管理状态/真实执行分离')+
      note('示例客户、IP和订单全部为模拟数据。不能用本页操作结果作为真实代理交付、业务验收或接口签署证明。','strong')+
      `<div class="v13-card">${steps}<div class="v13-flow-body">${body}</div>${footer}</div>${logs}`+
      `<div class="v13-grid three">${stat('本地业务预检',v.pass?'条件通过':'存在阻断','不代表产品组确认')}${stat('远端接口回执','UNKNOWN','31项候选接口未签约')}${stat('实际代理网络','未验证','需要真实连通性证据')}</div>`;
  }
  function interfaces(){
    const list=I.filter(x=>(stateV.interfaceGroup==='all'||x[0].startsWith(stateV.interfaceGroup))&&((x[0]+' '+x[1]+' '+x[3]).includes(stateV.interfaceQuery)));
    const x=I.find(t=>t[0]===stateV.interfaceSelected);
    const detailsHtml=x?`<div class="v13-panel selected"><div class="v13-title-row"><h3>${esc(x[0]+' '+x[1])}</h3>${button('收起详情','clear-interface')}</div><p><b>调用方向：</b>${esc(x[2])}</p><p><b>使用场景：</b>${esc(x[3])}</p><p><b>接口身份：</b>V1.0 31项候选接口；URI/方法、字段类型、错误码、超时、幂等由两组技术评审冻结。</p><p><b>验收门槛：</b>授权、参数验证、来源主键、实际执行结果、operationId/查单、真实网关证明与脱敏审计。</p>${note('本原型未发起该接口。接口编号以最新31项修订稿为准，不与未经批准的33项提案混用。','strong')}</div>`:'';
    const nav=`<div class="v13-chips">${['all','A','B','C','D','E','F'].map(g=>`<button class="v13-chip ${g===stateV.interfaceGroup?'active':''}" data-v13-action="interface-group" data-v13-value="${g}">${g==='all'?'全部31项':g+' · '+groupNames[g]}</button>`).join('')}</div>`;
    return heading('两组开发接口契约 · 31项候选','按业务能力与调用方向检索；候选接口不等同实际已实现、已签约或联调成功')+
      `<div class="v13-grid three">${stat('接口总数','31','A6 / B5 / C7 / D3 / E4 / F6')}${stat('真实技术签署','0 项已确认','最新修订稿记载尚未签署')}${stat('接口对照范围','平台 ↔ 产品组','资源API组能力另行确认')}</div>`+
      note('特别注意：当前31项稿 F02=域名解析变更、F04=已授权短效提取；不能把历史33项提案中的 F 编号直接覆盖。','strong')+
      `<div class="v13-card">${nav}<label class="v13-field">关键词筛选<input data-v13-field="interfaceQuery" value="${esc(stateV.interfaceQuery)}" placeholder="接口编号、名称、用途"/></label><div class="v13-table-wrap"><table class="v13-table"><thead><tr><th>编号</th><th>接口名称</th><th>调用方向</th><th>业务触发场景</th><th></th></tr></thead><tbody>${list.map(x=>`<tr><td><b>${esc(x[0])}</b></td><td>${esc(x[1])}</td><td>${esc(x[2])}</td><td>${esc(x[3])}</td><td>${button('查看约束','interface-detail')}<span hidden data-v13-code="${esc(x[0])}"></span></td></tr>`).join('')||'<tr><td colspan="5">没有符合条件的候选接口</td></tr>'}</tbody></table></div></div>${detailsHtml}`;
  }
  function matrix(){
    const isPages=stateV.reviewMode==='pages',all=isPages?details:cases;
    const filtered=all.filter(x=>x.join(' ').includes(stateV.reviewQuery));
    const rows=filtered.map(x=>`<tr><td>UI/CASE-${esc(x[0])}</td><td><b>${esc(x[1])}</b></td><td>${isPages?'管理面入口对照':'端到端真实验证'}</td><td><span class="v13-pending">${isPages?'待逐页人工签收':(['07','10','11'].includes(x[0])?'待外部联调':'待内部人工')}</span></td><td>${isPages?button('打开对应原型页','to-route'):'需真实证据'}<span hidden data-v13-route="${esc(isPages?x[2]:x[0])}"></span></td></tr>`).join('');
    return heading('V1.3 36页面与18场景验收导航','对照详细需求第10章、第12章与GitHub唯一人工验收台账')+
      `<div class="v13-grid three">${stat('业务页面','36','仅列明映射入口，非36项签收')}${stat('端到端案例','18','案例07/10/11须实际外部联调')}${stat('人工签署','0','本地模拟不能替代签名和回执')}</div>`+
      note('GitHub工程自动化通过不等于人工验收或真实联网。必须按主外键、权限、失败恢复、operationId、真实产品证据逐项双人签收。','strong')+
      `<div class="v13-card"><div class="v13-title-row"><div class="v13-chips">${button('36页面','review-pages',isPages?'primary':'')}${button('18案例','review-cases',!isPages?'primary':'')}</div><label class="v13-field">筛选<input data-v13-field="reviewQuery" value="${esc(stateV.reviewQuery)}" placeholder="编号或关键词"/></label></div><div class="v13-table-wrap"><table class="v13-table"><thead><tr><th>编号</th><th>名称</th><th>对应范围</th><th>状态</th><th>入口</th></tr></thead><tbody>${rows||'<tr><td colspan="5">无匹配项</td></tr>'}</tbody></table></div></div>`+
      (!isPages?`<div class="v13-card"><h3>18个案例的真实取证要求</h3>${filtered.map(x=>`<div class="v13-case"><strong>${esc(x[0]+' '+x[1])}</strong><div>操作与预期：${esc(x[2])}</div><small>需留存：${esc(x[3])}</small></div>`).join('')}</div>`:'')+
      note('原型页导航与正式Vue路由不是一一独立页面对应：例如客户详情、备用候选、服务资源绑定通过现有业务页或详情入口评审，不应把跳转视为该页面已实现完整字段。');
  }
  function navigate(route){
    state.route=route;
    if(typeof renderShell==='function')renderShell();
    if(typeof renderPage==='function')renderPage();
  }
  function render(key){
    return key==='v13.flow'?wizard():key==='v13.interfaces'?interfaces():matrix();
  }
  function refresh(){
    if(!['v13.flow','v13.interfaces','v13.matrix'].includes(state.route))return;
    const area=document.getElementById('content');
    if(area)area.innerHTML=render(state.route);
  }
  function reset(){
    stateV.step=0;stateV.task=null;stateV.audit=[];stateV.candidate='reuse';stateV.ip='198.51.100.42';stateV.upstreamCover='no';
  }
  document.addEventListener('change',e=>{
    const el=e.target.closest('[data-v13-field]');if(!el)return;
    const k=el.dataset.v13Field,value=el.value;
    if(k==='order'){stateV.orderIndex=Number(value);reset();}
    if(k==='mode'){chosen().mode=value;stateV.task=null;}
    if(k==='candidate'){stateV.candidate=value;stateV.task=null;}
    if(k==='ip'){stateV.ip=value;stateV.task=null;}
    if(k==='upstream'){stateV.upstreamCover=value;stateV.task=null;}
    if(k==='interfaceQuery')stateV.interfaceQuery=value;
    if(k==='reviewQuery')stateV.reviewQuery=value;
    refresh();
  });
  document.addEventListener('input',e=>{
    const el=e.target.closest('[data-v13-field]');if(!el)return;
    const k=el.dataset.v13Field;if(k!=='interfaceQuery'&&k!=='reviewQuery')return;
    if(k==='interfaceQuery')stateV.interfaceQuery=el.value;
    if(k==='reviewQuery')stateV.reviewQuery=el.value;
    // Keep the user's typing focus/caret while only replacing table content on Enter/change.
  });
  document.addEventListener('click',e=>{
    const el=e.target.closest('[data-v13-action]');if(!el)return;
    const action=el.dataset.v13Action;
    if(action==='next'){
      if(stateV.step===0&&!chosen().paid){appendAudit('付款未核实，禁止进入开通');refresh();return;}
      if(stateV.step===2&&!validate().pass){appendAudit('预检阻断：'+validate().fail.join('；'));refresh();return;}
      stateV.step=Math.min(3,stateV.step+1);
    }else if(action==='back')stateV.step=Math.max(0,stateV.step-1);
    else if(action==='submit'){
      if(!validate().pass){appendAudit('业务预检阻断；未发起任务');refresh();return;}
      if(stateV.task){appendAudit('相同演示订单已创建本地任务，重复提交被阻断');stateV.step=4;refresh();return;}
      stateV.attempts++;
      stateV.task={id:'LOCAL-V13-'+String(stateV.attempts).padStart(3,'0'),operationId:'prototype:v13:'+chosen().id};
      stateV.step=4;appendAudit('创建本地待远端核验任务：'+stateV.task.id+'；不调用任何外部接口');
    }else if(action==='restart')reset();
    else if(action==='to-interfaces'){navigate('v13.interfaces');return;}
    else if(action==='interface-group')stateV.interfaceGroup=el.dataset.v13Value;
    else if(action==='interface-detail'){
      const row=el.closest('tr'),code=row?.querySelector('[data-v13-code]')?.dataset.v13Code;
      if(code)stateV.interfaceSelected=code;
    }else if(action==='clear-interface')stateV.interfaceSelected=null;
    else if(action==='review-pages'){stateV.reviewMode='pages';stateV.reviewQuery='';}
    else if(action==='review-cases'){stateV.reviewMode='cases';stateV.reviewQuery='';}
    else if(action==='to-route'){
      const route=el.closest('tr')?.querySelector('[data-v13-route]')?.dataset.v13Route;
      if(route){navigate(route);return;}
    }else if(action==='to-matrix'){navigate('v13.matrix');return;}
    else return;
    refresh();
  });
  function decorate(key){
    const el=document.getElementById('content');if(!el||!details.some(x=>x[2]===key))return;
    const ids=details.filter(x=>x[2]===key).map(x=>x[0]).join(' / ');
    const banner=document.createElement('div');
    banner.className='v13-mini-bar';
    banner.innerHTML=`<b>V1.3需求对照 UI-${esc(ids)}</b><span>模拟页面 · 非真实数据面验收</span>${button('返回36页对照','to-matrix')}`;
    el.insertBefore(banner,el.firstChild);
  }
  window.V13={
    renderPage:render,decorate:decorate,
    pages:details.map(x=>({id:x[0],name:x[1],route:x[2]})),
    cases:cases.map(x=>({id:x[0],name:x[1]})),
    interfaces:I.map(x=>({id:x[0],name:x[1]})),
    inspectDemo:()=>({order:chosen().id,step:stateV.step,check:validate(),task:stateV.task})
  };
})();
