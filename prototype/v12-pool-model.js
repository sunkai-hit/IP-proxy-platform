(()=>{
  // V1.2资源池最终模型：按省/市/运营商等来源属性组织自有线路；线路业务类型仅共享/长效。
  window.V12.pools=[
    {id:'POOL-QD-LT',name:'青岛联通资源池',province:'山东',city:'青岛',carrier:'联通',ros:4,lines:860,shared:612,long:248,online:821,abnormal:11,currentIp:821,unique:798,b:7,c:46,status:'启用'},
    {id:'POOL-SJZ-DX',name:'石家庄电信资源池',province:'河北',city:'石家庄',carrier:'电信',ros:3,lines:620,shared:474,long:146,online:571,abnormal:18,currentIp:571,unique:542,b:11,c:72,status:'启用'},
    {id:'POOL-HZ-YD',name:'杭州移动资源池',province:'浙江',city:'杭州',carrier:'移动',ros:3,lines:486,shared:352,long:134,online:462,abnormal:9,currentIp:462,unique:447,b:8,c:55,status:'启用'}
  ];
  const baseResources=resources;
  resources=function(){
    if(state.tabs.resources!==4)return baseResources();
    const ts=['资源概览','CentOS列表','ROS列表','家宽/线路','资源池','独享资源','域名配置','外部供应商','外部套餐/采购','反向代理交付'];
    const body=
      note('资源池不是产品类型。它按省/市/运营商等资源来源属性组织自有家宽线路；池内单条线路业务类型仅“共享 / 长效”二选一。同一线路可加入多个符合来源属性的资源池。独享IP、VPN、隧道在对应产品/业务模块维护，不作为资源池线路类型。')+
      toolbar('<input class="input" placeholder="资源池名称"/><input class="input" placeholder="省"/><input class="input" placeholder="市"/><input class="input" placeholder="运营商"/><select class="select"><option>全部状态</option><option>启用</option><option>停用</option></select>')+
      table([['池编号','id'],['资源池名称','name'],['省','province'],['市','city'],['运营商','carrier'],['ROS数','ros'],['线路总数','lines'],['共享线路','shared'],['长效线路','long'],['在线','online'],['异常','abnormal'],['当前IP','currentIp'],['去重IP','unique'],['B段','b'],['C段','c'],['状态','status',tag]],D.pools);
    return head('资源管理','V1.2：CentOS → ROS → 家宽/线路（节点）→ 当前IP；资源池按来源属性组织共享/长效线路')+tabs(ts,4)+body;
  };
})();
