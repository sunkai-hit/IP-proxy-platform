(()=>{
  const baseResources=resources;
  resources=function(){
    const i=state.tabs.resources;
    if(i>=4)return baseResources();
    const ts=['资源概览','CentOS列表','ROS列表','家宽/线路','资源池','独享资源','域名配置','外部供应商','外部套餐/采购','反向代理交付'];
    let body='';
    if(i===0)body=metrics([['CentOS','103','管理与B/C段聚合'],['ROS','103','与CentOS 1:1，负责拨号'],['家宽/线路','5,921','=家宽账号=节点'],['资源池','13','按来源属性组织线路']])+`<div class="card pad top-gap"><div class="card-title">当前基础设施职责</div><div class="resource-chain"><div>CentOS（管理）<span>→</span></div><div>ROS（拨号）<span>→</span></div><div>家宽/线路<span>→</span></div><div>当前IP</div></div>${note('当前阶段CentOS与ROS固定按1:1部署；理论模型可扩展为N:N，但本版本不开放。B/C段按CentOS下当前拨号IPv4聚合，ROS只负责拨号与结果上报。')}</div>`;
    if(i===1)body=toolbar('<input class="input" placeholder="主机名称"/><input class="input" placeholder="账号"/><select class="select"><option>全部状态</option><option>在线</option><option>离线</option></select>')+table([['ID','id'],['主机名称','name'],['状态','status',tag],['账号','account'],['IP','ip'],['资源池数','poolCount'],['地点','location'],['运营商','carrier'],['B段数','bCount'],['C段数','cCount'],['CPU','cpu'],['内存','memory'],['负载','load'],['硬盘','disk'],['VPN','vpn'],['PPPoE服务器','pppoe'],['心跳','heartbeat']],D.centos);
    if(i===2)body=toolbar('<input class="input" placeholder="ROS ID / 记录名"/><select class="select"><option>全部状态</option><option>在线</option><option>异常</option><option>离线</option></select>')+table([['ROS ID','id'],['记录名','record'],['CPU','cpu'],['内存','memory'],['负载','load'],['硬盘','disk'],['线路','lines'],['在线','online'],['当前IP','ipCount'],['BAS数','basCount'],['开机时长','uptime'],['状态','status',tag],['上报','report']],D.ros);
    if(i===3)body=toolbar('<input class="input" placeholder="拨号账号/IP/域名"/><select class="select"><option>全部ROS</option><option>ROS-SD-01</option><option>ROS-HB-03</option></select><select class="select"><option>全部状态</option><option>在线</option><option>异常</option></select>')+table([['线路ID','id'],['ROS','ros'],['别名','alias'],['拨号账号','account'],['当前IP','ip'],['HTTP','http'],['SOCKS5','socks'],['域名','domain'],['类型','type'],['状态','status',tag],['延迟','latency'],['限制人数','limit'],['在用','used'],['重拨/重启','restart'],['VPN重拨监控','vpnMonitor'],['BAS/BRAS','bas'],['省','province'],['市','city'],['运营商','carrier'],['ROS上报','report']],D.lines);
    return head('资源管理','当前阶段CentOS与ROS按1:1部署；CentOS负责管理与B/C段聚合，ROS负责拨号执行')+tabs(ts,i)+body;
  };

  monitor=function(){
    const ts=['CentOS监控','ROS监控'];const i=state.tabs.monitor;
    const body=i===0
      ?table([['对象','object'],['状态','status',tag],['CPU','cpu'],['内存','memory'],['负载','load'],['硬盘','disk'],['B段数','b'],['C段数','c'],['最近采集','last']],D.monitorCentos,false)
      :table([['对象','object'],['状态','status',tag],['CPU','cpu'],['内存','memory'],['负载','load'],['硬盘','disk'],['在线线路/总线路','lines'],['最近采集','last']],D.monitorRos,false);
    return head('监控中心','仅保留CentOS与ROS基础设施监控；CentOS负责管理及B/C段聚合，ROS负责拨号执行',btn('立即采集','collect','primary'))+tabs(ts,i)+note(i===0?'CentOS侧查看CPU、内存、负载、硬盘以及B/C段数量。':'ROS侧查看运行状态与拨号线路情况，不再展示B/C段，也不提供主备、替换或自动切换。')+body;
  };

  const desc={
    'resources.centos':'当前阶段CentOS与ROS按1:1部署；CentOS承担管理动作并汇总B/C段及运行信息',
    'resources.ros':'ROS仅负责家宽线路拨号与结果上报，通过记录名识别对应关系',
    'resources.lines':'统一承载ROS逻辑线路、家宽账号和节点；客户关系通过服务实例与资源绑定查看'
  };
  const patch=()=>{const text=desc[state.route];if(!text)return;const el=document.querySelector('#content .page-subtitle');if(el&&el.textContent!==text)el.textContent=text;};
  const observer=new MutationObserver(patch);const app=document.querySelector('#app');if(app)observer.observe(app,{subtree:true,childList:true});setTimeout(patch,0);
})();
