const $=(s,r=document)=>r.querySelector(s), $$=(s,r=document)=>[...r.querySelectorAll(s)];
const esc=v=>String(v??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m]));
const state={page:'dashboard',tabs:{customers:0,resources:0,products:0,orders:0,monitor:0,logs:0,alarms:0,statistics:0,system:0},dict:'customer_type'};
const menu=[
 ['业务中心',[['dashboard','◫','首页工作台'],['customers','♙','客户管理'],['resources','◇','资源管理'],['products','▦','产品管理'],['orders','▣','订单与服务']]],
 ['运维中心',[['monitor','◉','监控中心'],['logs','≣','日志中心'],['alarms','⚠','告警中心'],['statistics','▥','数据统计']]],
 ['平台设置',[['system','⚙','系统管理']]]
];

const D={
 customers:[
  {id:'C202608001',name:'威海云启科技',type:'企业',contact:'张经理',phone:'138****8821',auth:'已认证',status:'正常',services:3,biz:'短效IP / 隧道',expire:'2026-09-05',owner:'王强',created:'2026-08-18 09:32',remark:'重点企业客户'},
  {id:'C202608002',name:'青岛星途数据',type:'企业',contact:'李女士',phone:'186****3904',auth:'已认证',status:'正常',services:2,biz:'长效IP / VPN',expire:'2026-09-11',owner:'赵磊',created:'2026-08-16 14:10',remark:'VPN需求稳定'},
  {id:'C202608003',name:'陈明',type:'个人',contact:'陈明',phone:'135****1037',auth:'待审核',status:'正常',services:1,biz:'短效IP',expire:'2026-09-02',owner:'王强',created:'2026-08-26 20:01',remark:'个人体验客户'},
  {id:'C202608004',name:'北京极数网络',type:'企业',contact:'刘经理',phone:'139****1201',auth:'已认证',status:'冻结',services:4,biz:'独享IP / 隧道',expire:'2026-10-18',owner:'孙浩',created:'2026-07-29 10:22',remark:'当前服务人工暂停'}
 ],
 auth:[
  {id:'AUTH-260828-03',customer:'陈明',type:'个人认证',submitted:'2026-08-28 11:20',status:'待审核',reviewer:'—',docs:'身份证正反面'},
  {id:'AUTH-260827-08',customer:'杭州嘉云信息',type:'企业认证',submitted:'2026-08-27 16:08',status:'待审核',reviewer:'—',docs:'营业执照 / 法人身份证'},
  {id:'AUTH-260824-02',customer:'青岛星途数据',type:'企业认证',submitted:'2026-08-24 09:12',status:'已通过',reviewer:'管理员',docs:'营业执照 / 法人身份证'}
 ],
 centos:[
  {id:'CT-01',name:'centos-shandong-01',ip:'10.20.1.10',region:'山东',status:'在线',ros:5,abnormal:0,cpu:'31%',memory:'42%',disk:'48%',sync:'1分钟前'},
  {id:'CT-02',name:'centos-hebei-01',ip:'10.20.2.10',region:'河北',status:'在线',ros:5,abnormal:1,cpu:'48%',memory:'55%',disk:'62%',sync:'2分钟前'},
  {id:'CT-04',name:'centos-zhejiang-01',ip:'10.20.4.10',region:'浙江',status:'离线',ros:5,abnormal:5,cpu:'—',memory:'—',disk:'—',sync:'18分钟前'}
 ],
 ros:[
  {id:'ROS-SD-01',centos:'CT-01',region:'山东',carrier:'联通',lines:168,online:162,abnormal:6,status:'在线',ips:126,sync:'48秒前'},
  {id:'ROS-HB-03',centos:'CT-02',region:'河北',carrier:'电信',lines:212,online:187,abnormal:25,status:'异常',ips:144,sync:'2分钟前'},
  {id:'ROS-ZJ-04',centos:'CT-04',region:'浙江',carrier:'移动',lines:178,online:0,abnormal:178,status:'离线',ips:0,sync:'18分钟前'}
 ],
 lines:[
  {id:'LN-SD-01001',ros:'ROS-SD-01',region:'山东青岛',carrier:'联通',ip:'112.254.31.18',status:'在线',dial:'正常',latency:'18ms',availability:'99.8%',usage:'共享'},
  {id:'LN-HB-03018',ros:'ROS-HB-03',region:'河北石家庄',carrier:'电信',ip:'110.249.22.90',status:'异常',dial:'拨号失败',latency:'—',availability:'82.1%',usage:'空闲'},
  {id:'LN-ZJ-04031',ros:'ROS-ZJ-04',region:'浙江杭州',carrier:'移动',ip:'—',status:'离线',dial:'离线',latency:'—',availability:'64.3%',usage:'空闲'}
 ],
 ips:[
  {id:'112.254.31.18',source:'自有',ros:'ROS-SD-01',line:'LN-SD-01001',region:'山东',carrier:'联通',status:'可用',latency:'18ms',dup:'否',pool:'山东联通池',occupy:'共享使用'},
  {id:'119.189.66.24',source:'自有',ros:'ROS-SD-01',line:'LN-SD-01002',region:'山东',carrier:'联通',status:'可用',latency:'22ms',dup:'否',pool:'山东联通池',occupy:'独享占用'},
  {id:'39.105.71.88',source:'外部',ros:'—',line:'供应商A',region:'北京',carrier:'联通',status:'可用',latency:'31ms',dup:'否',pool:'华北综合池',occupy:'共享使用'},
  {id:'47.96.128.19',source:'外部',ros:'—',line:'供应商B',region:'浙江',carrier:'移动',status:'不可用',latency:'—',dup:'是',pool:'华东综合池',occupy:'空闲'}
 ],
 pools:[
  {id:'POOL-SD-LT',name:'山东联通池',region:'山东',carrier:'联通',source:'自有+外部',total:5620,unique:5211,available:5068,products:4,status:'启用',priority:'自有优先'},
  {id:'POOL-HB-DX',name:'河北电信池',region:'河北',carrier:'电信',source:'自有',total:3218,unique:3077,available:2746,products:3,status:'启用',priority:'质量优先'},
  {id:'POOL-NORTH',name:'华北综合池',region:'华北',carrier:'多运营商',source:'外部',total:1850,unique:1714,available:1382,products:2,status:'异常',priority:'供应商A优先'}
 ],
 exclusive:[{id:'119.189.66.24',type:'IP',pool:'山东联通池',customer:'北京极数网络',service:'SVC-100211',assigned:'2026-07-18',expire:'2026-10-18',status:'已分配',health:'正常'}],
 suppliers:[
  {id:'SUP-A',name:'供应商A',contact:'赵工',endpoint:'https://api.supplier-a.example',auth:'API Key',regions:'华北/华东',status:'启用',interface:'正常',success:'99.2%'},
  {id:'SUP-B',name:'供应商B',contact:'王工',endpoint:'https://api.supplier-b.example',auth:'账号+签名',regions:'全国',status:'暂停',interface:'异常',success:'91.6%'}
 ],
 access:[
  {id:'SYNC-82811',supplier:'供应商A',region:'北京',carrier:'联通',requested:1000,returned:998,unique:932,available:901,pool:'华北综合池',status:'已入池',started:'13:45:10'},
  {id:'SYNC-82810',supplier:'供应商B',region:'浙江',carrier:'移动',requested:1200,returned:1102,unique:1008,available:887,pool:'华东综合池',status:'部分失败',started:'13:40:25'}
 ],
 products:[
  {id:'P001',name:'高效短效代理',type:'短效IP',access:'API提取',protocol:'HTTP/HTTPS',auth:'Token + 白名单',region:'华东/华北',status:'启用',packages:4,services:142},
  {id:'P002',name:'稳定长效代理',type:'长效IP',access:'直连',protocol:'HTTP/SOCKS5',auth:'账号密码',region:'全国',status:'启用',packages:3,services:88},
  {id:'P003',name:'独享IP代理',type:'独享IP',access:'直连',protocol:'HTTP/HTTPS',auth:'白名单',region:'山东/河北',status:'启用',packages:2,services:61},
  {id:'P004',name:'ROSVPN',type:'VPN',access:'VPN连接',protocol:'VPN',auth:'私钥/账号',region:'全国',status:'启用',packages:3,services:97},
  {id:'P005',name:'高速隧道代理',type:'隧道',access:'固定入口',protocol:'HTTP/SOCKS5',auth:'账号密码',region:'全国',status:'启用',packages:5,services:254}
 ],
 packages:[
  {id:'PKG-001',name:'企业标准版',product:'高效短效代理',cycle:'30天',quota:'300万次提取',concurrency:'—',white:'20',price:'¥3,600',status:'启用'},
  {id:'PKG-002',name:'20线路套餐',product:'ROSVPN',cycle:'30天',quota:'20线路',concurrency:'20',white:'—',price:'¥6,800',status:'启用'},
  {id:'PKG-003',name:'100IP套餐',product:'独享IP代理',cycle:'90天',quota:'100 IP',concurrency:'—',white:'30',price:'¥12,000',status:'启用'}
 ],
 strategies:[
  {id:'STR-001',name:'短效-山东联通优先',product:'高效短效代理',pools:'山东联通池 / 华北综合池',region:'山东',carrier:'联通',source:'自有优先',dispatch:'质量优先+轮询',switch:'失败即重选',status:'启用'},
  {id:'STR-002',name:'VPN-华东稳定线路',product:'ROSVPN',pools:'华东综合池',region:'华东',carrier:'多运营商',source:'自有优先',dispatch:'延迟优先',switch:'连续3次失败切换',status:'启用'}
 ],
 orders:[
  {id:'O20260828001',customer:'威海云启科技',type:'新购',product:'高效短效代理',package:'企业标准版',amount:'¥3,600',status:'待开通',created:'2026-08-28 09:18',effective:'2026-08-28',expire:'2026-09-27',owner:'王强'},
  {id:'O20260827018',customer:'青岛星途数据',type:'续费',product:'ROSVPN',package:'20线路套餐',amount:'¥6,800',status:'已开通',created:'2026-08-27 16:44',effective:'2026-09-11',expire:'2026-10-11',owner:'赵磊'}
 ],
 services:[
  {id:'SVC-100328',customer:'威海云启科技',product:'高效短效代理',package:'企业标准版',type:'短效IP',status:'正常',effective:'2026-08-28',expire:'2026-09-05',resource:'山东联通池',credential:'Token正常',usage:'67%',strategy:'短效-山东联通优先',token:'tk_9fd2••••••c83a'},
  {id:'SVC-100274',customer:'青岛星途数据',product:'ROSVPN',package:'20线路套餐',type:'VPN',status:'正常',effective:'2026-08-11',expire:'2026-10-11',resource:'ROS-SD-01 / LN-SD-01002',credential:'凭证正常',usage:'52%',strategy:'VPN-华东稳定线路',token:'vpn-key-••••91'},
  {id:'SVC-100211',customer:'北京极数网络',product:'独享IP代理',package:'100IP套餐',type:'独享IP',status:'暂停',effective:'2026-07-18',expire:'2026-10-18',resource:'独享IP × 100',credential:'凭证冻结',usage:'80%',strategy:'独享-山东河北',token:'white-list'}
 ],
 changes:[{id:'CHG-260828-01',service:'SVC-100274',customer:'青岛星途数据',type:'续费',before:'到期 2026-09-11',after:'到期 2026-10-11',status:'成功',operator:'赵磊',time:'2026-08-27 16:52'}],
 alarms:[
  {id:'ALM-8291',level:'严重',object:'ROS-ZJ-04',name:'ROS离线',value:'已持续18分钟',status:'未恢复',time:'2026-08-28 13:41',handler:'—',remark:'CentOS CT-04 同时异常'},
  {id:'ALM-8290',level:'重要',object:'ROS-HB-03',name:'异常线路比例超过10%',value:'25条异常 / 212条',status:'处理中',time:'2026-08-28 13:38',handler:'管理员',remark:'已通知线路运维'},
  {id:'ALM-8287',level:'一般',object:'华北综合池',name:'可用IP低于阈值',value:'1,382 / 阈值1,500',status:'已确认',time:'2026-08-28 13:20',handler:'王强',remark:'外部资源补充中'}
 ],
 rules:[
  {id:'RULE-001',name:'ROS离线告警',object:'ROS',metric:'在线状态',condition:'连续3次=离线',level:'严重',notify:'钉钉+平台',status:'启用'},
  {id:'RULE-002',name:'线路异常比例',object:'ROS',metric:'异常线路比例',condition:'>10% 持续5分钟',level:'重要',notify:'钉钉+平台',status:'启用'}
 ],
 users:[
  {id:'admin',name:'管理员',role:'超级管理员',dept:'平台运营部',status:'正常',last:'2026-08-28 16:58',ip:'203.0.113.18'},
  {id:'wangqiang',name:'王强',role:'业务运营',dept:'业务部',status:'正常',last:'2026-08-28 16:32',ip:'203.0.113.31'},
  {id:'ops01',name:'赵磊',role:'运维人员',dept:'运维部',status:'正常',last:'2026-08-28 16:50',ip:'203.0.113.47'}
 ],
 roles:[
  {id:'ROLE-ADMIN',name:'超级管理员',users:1,scope:'全部数据',desc:'全平台管理权限',status:'启用'},
  {id:'ROLE-OPS',name:'运维人员',users:6,scope:'资源/监控/告警',desc:'资源与运行维护',status:'启用'},
  {id:'ROLE-BIZ',name:'业务运营',users:8,scope:'客户/产品/订单/服务',desc:'客户与业务运营',status:'启用'}
 ],
 params:[
  {group:'服务参数',id:'service.expire.remind_days',name:'服务到期提醒天数',value:'7',status:'启用',updated:'2026-08-25 10:11'},
  {group:'同步参数',id:'resource.sync.interval',name:'基础资源同步周期',value:'60秒',status:'启用',updated:'2026-08-27 18:10'},
  {group:'安全参数',id:'token.mask.enabled',name:'Token默认脱敏',value:'true',status:'启用',updated:'2026-08-21 09:18'}
 ],
 loginLogs:[
  {id:'LOGIN-9911',time:'2026-08-28 16:58:21',user:'admin / 管理员',ip:'203.0.113.18',client:'Edge 151 / Windows',result:'成功',reason:'—'},
  {id:'LOGIN-9910',time:'2026-08-28 16:50:04',user:'ops01 / 赵磊',ip:'203.0.113.47',client:'Chrome / Windows',result:'成功',reason:'—'},
  {id:'LOGIN-9907',time:'2026-08-28 15:22:11',user:'unknown',ip:'198.51.100.27',client:'Chrome / macOS',result:'失败',reason:'账号或密码错误'}
 ],
 auditLogs:[
  {id:'AUD-88021',time:'2026-08-28 16:41:06',user:'管理员',module:'产品管理',object:'STR-001',action:'编辑资源策略',result:'成功',ip:'203.0.113.18',detail:'自有资源优先级 80 → 90'},
  {id:'AUD-88020',time:'2026-08-28 16:20:18',user:'王强',module:'客户管理',object:'C202608001',action:'编辑客户',result:'成功',ip:'203.0.113.31',detail:'备注信息更新'},
  {id:'AUD-88018',time:'2026-08-28 15:56:44',user:'赵磊',module:'订单与服务',object:'SVC-100274',action:'服务续费',result:'成功',ip:'203.0.113.47',detail:'到期时间延长30天'}
 ]
};

D.logSets={
 extract:[
  {id:'EXT-001',time:'2026-08-28 13:56:22',customer:'威海云启科技',service:'SVC-100328',account:'api_yunqi',clientIp:'203.0.113.11',protocol:'HTTP',region:'山东',carrier:'联通',request:10,returned:10,pool:'山东联通池',result:'成功',elapsed:'26ms'},
  {id:'EXT-002',time:'2026-08-28 13:51:06',customer:'陈明',service:'SVC-100188',account:'chenming',clientIp:'198.51.100.8',protocol:'HTTPS',region:'浙江',carrier:'移动',request:5,returned:5,pool:'华东综合池',result:'成功',elapsed:'31ms'}
 ],
 usage:[
  {id:'USE-001',time:'2026-08-28 13:55:47',customer:'青岛星途数据',service:'SVC-100274',clientIp:'203.0.113.18',outIp:'119.189.66.24',line:'LN-SD-01002',protocol:'VPN',target:'api.example.com',result:'成功',up:'1.2MB',down:'8.4MB',elapsed:'38ms'},
  {id:'USE-002',time:'2026-08-28 13:53:09',customer:'威海云启科技',service:'SVC-100329',clientIp:'203.0.113.11',outIp:'39.105.71.88',line:'供应商A',protocol:'HTTP',target:'data.example.net',result:'成功',up:'320KB',down:'2.6MB',elapsed:'42ms'}
 ],
 api:[
  {id:'REQ-826811',time:'2026-08-28 13:56:22',customer:'威海云启科技',token:'tk_9fd2••••c83a',api:'提取IP',path:'/api/proxy/extract',clientIp:'203.0.113.11',code:'200',result:'成功',error:'—',elapsed:'26ms'},
  {id:'REQ-826808',time:'2026-08-28 13:52:41',customer:'陈明',token:'tk_19aa••••2187',api:'查询白名单',path:'/api/white/list',clientIp:'198.51.100.8',code:'200',result:'成功',error:'—',elapsed:'18ms'}
 ],
 ros:[
  {id:'ROSLOG-001',time:'2026-08-28 13:41:02',centos:'CT-04',ros:'ROS-ZJ-04',event:'离线',content:'连续3次通信失败',lines:178,abnormal:178,ips:0,result:'异常',recovered:'—'},
  {id:'ROSLOG-002',time:'2026-08-28 12:11:40',centos:'CT-02',ros:'ROS-HB-03',event:'线路异常增加',content:'异常线路由18增加至25',lines:212,abnormal:25,ips:144,result:'告警',recovered:'—'}
 ],
 dial:[
  {id:'DIAL-001',time:'2026-08-28 13:48:11',line:'LN-SD-01001',ros:'ROS-SD-01',type:'自动重拨',before:'112.254.30.9',after:'112.254.31.18',result:'成功',reason:'—',elapsed:'4.2s',trigger:'短效策略'},
  {id:'DIAL-002',time:'2026-08-28 13:37:20',line:'LN-HB-03018',ros:'ROS-HB-03',type:'自动重连',before:'110.249.22.90',after:'—',result:'失败',reason:'运营商认证失败',elapsed:'12.6s',trigger:'线路恢复'}
 ],
 ipchange:[
  {id:'IPCHG-001',time:'2026-08-28 13:48:16',ros:'ROS-SD-01',line:'LN-SD-01001',before:'112.254.30.9',after:'112.254.31.18',reason:'自动重拨',duplicate:'否',domain:'—',domainResult:'—',service:'短效共享'},
  {id:'IPCHG-002',time:'2026-08-28 12:22:07',ros:'ROS-HB-03',line:'LN-HB-03011',before:'110.249.16.31',after:'110.249.21.77',reason:'故障恢复',duplicate:'否',domain:'vpn-hb.example.com',domainResult:'已更新',service:'SVC-100274'}
 ],
 external:[
  {id:'SUPLOG-001',time:'2026-08-28 13:45:10',supplier:'供应商A',api:'/proxy/list',region:'北京',carrier:'联通',request:1000,returned:998,unique:932,available:901,pool:'华北综合池',result:'成功',elapsed:'812ms'},
  {id:'SUPLOG-002',time:'2026-08-28 13:40:25',supplier:'供应商B',api:'/getips',region:'浙江',carrier:'移动',request:1200,returned:1102,unique:1008,available:887,pool:'华东综合池',result:'部分失败',elapsed:'1.8s'}
 ],
 operation:[
  {id:'OP-001',time:'2026-08-28 16:41:06',user:'管理员',module:'产品管理',type:'编辑',object:'STR-001',before:'自有优先级=80',after:'自有优先级=90',result:'成功',ip:'203.0.113.18'},
  {id:'OP-002',time:'2026-08-28 15:56:44',user:'赵磊',module:'订单与服务',type:'续费',object:'SVC-100274',before:'到期=2026-09-11',after:'到期=2026-10-11',result:'成功',ip:'203.0.113.47'}
 ]
};

D.monitorSets={
 centos:D.centos,
 ros:D.ros,
 line:D.lines,
 ip:[
  {id:'山东联通池',total:5620,unique:5211,available:5068,invalid:153,duplicate:'7.3%',new:268,sourceRos:5,external:620,status:'正常'},
  {id:'华北综合池',total:1850,unique:1714,available:1382,invalid:332,duplicate:'7.4%',new:91,sourceRos:0,external:1850,status:'异常'}
 ],
 external:[
  {id:'供应商A',interface:'正常',response:'812ms',calls:286,success:284,fail:2,rate:'99.2%',resources:4821,available:4478,last:'13:45:10',status:'正常'},
  {id:'供应商B',interface:'异常',response:'1.8s',calls:244,success:223,fail:21,rate:'91.6%',resources:3912,available:3188,last:'13:40:25',status:'异常'}
 ],
 service:[
  {id:'SVC-100328',customer:'威海云启科技',type:'短效IP',status:'正常',resource:'山东联通池',connection:'API正常',success:'99.7%',concurrency:'—',response:'29ms',last:'13:56:22',alarm:'无'},
  {id:'SVC-100274',customer:'青岛星途数据',type:'VPN',status:'正常',resource:'ROS-SD-01 / LN-SD-01002',connection:'在线18',success:'99.2%',concurrency:'18/20',response:'38ms',last:'13:55:47',alarm:'无'},
  {id:'SVC-100211',customer:'北京极数网络',type:'独享IP',status:'暂停',resource:'独享IP×100',connection:'已阻断',success:'—',concurrency:'—',response:'—',last:'08-27 20:11',alarm:'服务暂停'}
 ]
};

const DICTS=[
 {code:'customer_type',name:'客户类型',module:'客户管理',remark:'客户主体分类',items:[['enterprise','企业',1,'启用'],['personal','个人',2,'启用']]},
 {code:'customer_status',name:'客户状态',module:'客户管理',remark:'客户主档状态',items:[['normal','正常',1,'启用'],['frozen','冻结',2,'启用'],['disabled','停用',3,'启用']]},
 {code:'auth_type',name:'认证类型',module:'客户管理',remark:'实名认证类型',items:[['personal','个人认证',1,'启用'],['enterprise','企业认证',2,'启用']]},
 {code:'auth_status',name:'认证状态',module:'客户管理',remark:'认证审核流程状态',items:[['unverified','未认证',1,'启用'],['pending','待审核',2,'启用'],['review','待复核',3,'启用'],['approved','已通过',4,'启用'],['rejected','已驳回',5,'启用']]},
 {code:'product_type',name:'产品类型',module:'产品管理',remark:'五类产品',items:[['short','短效IP',1,'启用'],['long','长效IP',2,'启用'],['exclusive','独享IP',3,'启用'],['vpn','VPN',4,'启用'],['tunnel','隧道',5,'启用']]},
 {code:'access_mode',name:'接入方式',module:'产品管理',remark:'客户使用产品的接入方式',items:[['api','API提取',1,'启用'],['direct','直连',2,'启用'],['vpn','VPN连接',3,'启用'],['tunnel','固定入口',4,'启用']]},
 {code:'proxy_protocol',name:'代理协议',module:'产品管理',remark:'支持协议',items:[['http','HTTP',1,'启用'],['https','HTTPS',2,'启用'],['socks5','SOCKS5',3,'启用'],['vpn','VPN',4,'启用']]},
 {code:'auth_method',name:'认证方式',module:'产品/服务',remark:'客户服务认证方式',items:[['token','Token',1,'启用'],['whitelist','白名单',2,'启用'],['account','账号密码',3,'启用'],['private_key','私钥',4,'启用'],['certificate','证书',5,'启用']]},
 {code:'carrier',name:'运营商',module:'资源管理',remark:'网络运营商',items:[['unicom','联通',1,'启用'],['telecom','电信',2,'启用'],['mobile','移动',3,'启用'],['multi','多运营商',4,'启用']]},
 {code:'resource_source',name:'资源来源',module:'资源管理',remark:'IP/资源来源类型',items:[['self','自有',1,'启用'],['external','外部',2,'启用'],['mixed','自有+外部',3,'启用']]},
 {code:'resource_status',name:'基础资源状态',module:'资源管理',remark:'CentOS/ROS/线路通用状态',items:[['online','在线',1,'启用'],['abnormal','异常',2,'启用'],['offline','离线',3,'启用'],['unknown','未知',4,'启用']]},
 {code:'dial_status',name:'拨号状态',module:'资源管理',remark:'家宽线路拨号状态',items:[['normal','正常',1,'启用'],['dialing','拨号中',2,'启用'],['failed','拨号失败',3,'启用'],['offline','离线',4,'启用']]},
 {code:'ip_status',name:'IP可用状态',module:'资源管理',remark:'IP检测结果',items:[['available','可用',1,'启用'],['unavailable','不可用',2,'启用'],['unknown','未知',3,'启用'],['disabled','停用',4,'启用']]},
 {code:'occupy_type',name:'资源占用方式',module:'资源管理',remark:'IP/线路当前占用方式',items:[['idle','空闲',1,'启用'],['shared','共享使用',2,'启用'],['exclusive','独享占用',3,'启用'],['long','长效占用',4,'启用']]},
 {code:'order_type',name:'订单类型',module:'订单与服务',remark:'订单业务类型',items:[['new','新购',1,'启用'],['renew','续费',2,'启用'],['expand','扩容',3,'启用']]},
 {code:'order_status',name:'订单状态',module:'订单与服务',remark:'订单生命周期状态',items:[['confirm','待确认',1,'启用'],['opening','待开通',2,'启用'],['opened','已开通',3,'启用'],['done','已完成',4,'启用'],['cancelled','已取消',5,'启用']]},
 {code:'service_status',name:'服务状态',module:'订单与服务',remark:'服务实例生命周期',items:[['pending','待开通',1,'启用'],['normal','正常',2,'启用'],['paused','暂停',3,'启用'],['expired','到期',4,'启用'],['terminated','终止',5,'启用']]},
 {code:'service_change_type',name:'服务变更类型',module:'订单与服务',remark:'服务变更动作',items:[['renew','续费',1,'启用'],['quota','扩容/调整额度',2,'启用'],['resource','更换IP/线路',3,'启用'],['pool','更换资源池',4,'启用'],['package','更换套餐',5,'启用'],['pause','暂停服务',6,'启用'],['resume','恢复服务',7,'启用'],['terminate','终止服务',8,'启用']]},
 {code:'alarm_level',name:'告警级别',module:'告警中心',remark:'告警严重程度',items:[['notice','提示',1,'启用'],['normal','一般',2,'启用'],['important','重要',3,'启用'],['critical','严重',4,'启用']]},
 {code:'alarm_status',name:'告警状态',module:'告警中心',remark:'告警处理状态',items:[['active','未恢复',1,'启用'],['confirmed','已确认',2,'启用'],['handling','处理中',3,'启用'],['recovered','已恢复',4,'启用'],['closed','已关闭',5,'启用']]},
 {code:'notify_channel',name:'通知方式',module:'告警中心',remark:'告警通知渠道',items:[['platform','平台',1,'启用'],['dingtalk','钉钉',2,'启用'],['both','钉钉+平台',3,'启用']]},
 {code:'supplier_status',name:'供应商状态',module:'资源管理',remark:'外部供应商业务状态',items:[['enabled','启用',1,'启用'],['paused','暂停',2,'启用'],['disabled','停用',3,'启用']]},
 {code:'sync_status',name:'同步任务状态',module:'资源管理',remark:'外部资源接入任务状态',items:[['pending','待处理',1,'启用'],['normalized','已标准化',2,'启用'],['checked','已检测',3,'启用'],['pooled','已入池',4,'启用'],['partial','部分失败',5,'启用'],['failed','失败',6,'启用']]},
 {code:'log_type',name:'日志类型',module:'日志中心',remark:'日志分类',items:[['extract','IP提取日志',1,'启用'],['usage','使用日志',2,'启用'],['api','API日志',3,'启用'],['ros','ROS运行日志',4,'启用'],['dial','线路拨号日志',5,'启用'],['ipchange','IP变化日志',6,'启用'],['external','外部资源获取日志',7,'启用'],['operation','操作日志',8,'启用']]},
 {code:'user_status',name:'后台用户状态',module:'系统管理',remark:'后台账号状态',items:[['normal','正常',1,'启用'],['frozen','冻结',2,'启用'],['disabled','停用',3,'启用']]}
];

const statusTag=v=>{const s=String(v??'');let c='gray';if(/正常|在线|启用|成功|已认证|已开通|已完成|已恢复|已通过|已入池|可用/.test(s))c='success';else if(/待|处理中|已确认|异常|部分/.test(s))c='warning';else if(/离线|失败|冻结|停用|不可用|未恢复|严重|暂停|驳回/.test(s))c='danger';return `<span class="tag ${c}">${esc(s)}</span>`};
const btn=(text,cls='',action='',id='')=>`<button class="btn ${cls}" ${action?`data-action="${action}"`:''} ${id?`data-id="${esc(id)}"`:''}>${text}</button>`;
const head=(title,sub,actions='')=>`<div class="page-head"><div><h1>${title}</h1><div class="page-subtitle">${sub}</div></div><div class="actions">${actions}</div></div>`;
const metric=(label,value,trend='',cls='')=>`<div class="card metric ${cls}"><div class="metric-label">${label}</div><div class="metric-value">${value}</div><div class="metric-trend">${trend}</div></div>`;
const section=(title,body)=>`<div class="section-title">${title}</div>${body}`;
const tabs=(group,names)=>`<div class="tabs">${names.map((n,i)=>`<div class="tab ${state.tabs[group]===i?'active':''}" data-tab-group="${group}" data-tab-index="${i}">${n}</div>`).join('')}</div>`;
const toolbar=(left='',right='')=>`<div class="toolbar">${left||'<input class="input search" placeholder="输入关键字搜索"><select class="select"><option>全部状态</option><option>正常</option><option>异常</option></select>'}<span class="toolbar-spacer"></span>${right||btn('重置')+btn('查询','primary')}</div>`;
const kv=items=>`<div class="kv-grid">${items.map(x=>`<div class="kv"><div class="kv-label">${x[0]}</div><div class="kv-value">${x[1]}</div></div>`).join('')}</div>`;
const chart=(labels,vals)=>`<div class="bar-chart">${vals.map((v,i)=>`<div class="bar-item"><span>${v}</span><div class="bar" style="height:${Math.max(12,v/Math.max(...vals)*150)}px"></div><small>${labels[i]}</small></div>`).join('')}</div>`;

function cell(v,type,action,id){if(type==='status')return statusTag(v);if(type==='link')return `<span class="link" data-action="${action}" data-id="${esc(id)}">${esc(v)}</span>`;return esc(v)}
function table(cols,rows,actions=[]){return `<div class="card table-wrap"><table><thead><tr>${cols.map(c=>`<th>${c[1]}</th>`).join('')}${actions.length?'<th>操作</th>':''}</tr></thead><tbody>${rows.map(r=>`<tr>${cols.map(c=>`<td>${cell(r[c[0]],c[2],c[3],r.id)}</td>`).join('')}${actions.length?`<td>${actions.map(a=>`<button class="text-btn" data-action="${a[1]}" data-id="${esc(r.id)}">${a[0]}</button>`).join('　')}</td>`:''}</tr>`).join('')}</tbody></table></div>`}
function fields(defs,x={}){return `<div class="form-grid">${defs.map(d=>{let [k,l,opt,placeholder,full]=d,v=x[k]??'';let input;if(Array.isArray(opt))input=`<select class="select">${opt.map(o=>`<option ${String(o)===String(v)?'selected':''}>${esc(o)}</option>`).join('')}</select>`;else if(opt==='textarea')input=`<textarea class="textarea" placeholder="${esc(placeholder||'')}">${esc(v)}</textarea>`;else input=`<input class="input" value="${esc(v)}" placeholder="${esc(placeholder||'')}">`;return `<label class="form-item ${full?'full':''}"><span class="form-label">${l}</span>${input}</label>`}).join('')}</div>`}
function drawer(title,body){$('#layer').innerHTML=`<div class="drawer-mask" data-action="close"><div class="drawer" onclick="event.stopPropagation()"><div class="drawer-head"><div class="drawer-title">${title}</div><button class="drawer-close" data-action="close">×</button></div><div class="drawer-body">${body}</div></div></div>`}
function modal(title,body,foot='',large=false){$('#layer').innerHTML=`<div class="modal-mask" data-action="close"><div class="modal ${large?'large':''}" onclick="event.stopPropagation()"><div class="modal-head"><div class="modal-title">${title}</div><button class="drawer-close" data-action="close">×</button></div><div class="modal-body">${body}</div><div class="modal-foot">${foot||btn('取消','','close')+btn('保存','primary','save')}</div></div></div>`}
function toast(text){const t=document.createElement('div');t.className='toast';t.innerHTML=`<b>✓</b><span>${esc(text)}</span>`;$('#toasts').appendChild(t);setTimeout(()=>t.remove(),2200)}
const get=(arr,id)=>arr.find(x=>String(x.id)===String(id))||arr[0];

function dashboard(){return head('首页工作台','快速掌握业务、资源与告警运行态势',btn('刷新数据','','refresh')+btn('创建服务','primary','service-open'))+
`<div class="grid cols-4">${metric('有效客户','218','较昨日 +6')}${metric('有效服务','642','即将到期 18','green')}${metric('可用IP','18,624','去重率 92.6%','brand')}${metric('未恢复告警','7','严重 1','red')}</div>`+
`<div class="grid cols-2 top-gap"><div class="card pad"><div class="card-title">资源健康度</div>${[['CentOS',75],['ROS',91],['家宽/线路',94],['IP资源',96]].map(x=>`<div class="health-row"><span>${x[0]}</span><div class="progress"><i style="width:${x[1]}%"></i></div><b>${x[1]}%</b></div>`).join('')}</div><div class="card pad"><div class="card-title">当前告警</div>${D.alarms.map(a=>`<div class="alert-item"><i class="alert-dot ${a.level==='严重'?'red':'orange'}"></i><div class="alert-body"><b>${a.name} · ${a.object}</b><small>${a.value} · ${a.time}</small></div>${statusTag(a.status)}</div>`).join('')}</div></div>`+
section('今日趋势',`<div class="card pad">${chart(['08','09','10','11','12','13'],[42,68,95,82,114,137])}</div>`);
}

function customers(){const n=state.tabs.customers;let out=head('客户管理','管理客户档案、认证、账号、服务及认证信息',n===0?btn('新增客户','primary','customer-create'):btn('刷新','','refresh'))+tabs('customers',['客户列表','认证审批']);if(n===0){out+=toolbar('<input class="input search" placeholder="客户编号/名称/联系人"><select class="select"><option>全部客户类型</option><option>企业</option><option>个人</option></select><select class="select"><option>全部认证状态</option><option>已认证</option><option>待审核</option></select>');out+=table([['id','客户编号','link','customer-detail'],['name','客户名称'],['type','类型','status'],['contact','联系人'],['auth','认证状态','status'],['status','客户状态','status'],['services','有效服务'],['biz','业务类型'],['expire','最近到期'],['owner','负责人']],D.customers,[['详情','customer-detail'],['编辑','customer-edit'],['开通服务','service-open']]);}else{out+=toolbar('<input class="input search" placeholder="客户/认证编号"><select class="select"><option>待审核</option><option>已通过</option><option>已驳回</option></select>');out+=table([['id','认证编号','link','auth-detail'],['customer','客户'],['type','认证类型'],['submitted','提交时间'],['status','审核状态','status'],['reviewer','审核人']],D.auth,[['查看','auth-detail'],['审批','auth-review']]);}return out;}

function resources(){const n=state.tabs.resources,names=['资源概览','CentOS列表','ROS列表','家宽/线路','IP资源','IP资源池','独享资源','外部供应商','外部资源接入'];let out=head('资源管理','基础资源通过API同步，平台负责展示、资源组织与服务引用')+tabs('resources',names);if(n===0)return out+`<div class="note brand">基础资源以同步、查看、筛选和关联为主；资源池、独享关系和外部供应商属于平台主动配置对象。</div><div class="grid cols-4">${metric('CentOS','4','在线3')}${metric('ROS','19','在线率91%','green')}${metric('家宽/线路','2,846','异常212','brand')}${metric('可用IP','18,624','去重17,452')}</div>`+section('资源层级',`<div class="card pad"><div class="resource-chain"><div>CentOS<span>4</span></div><b>→</b><div>ROS<span>19</span></div><b>→</b><div>家宽/线路<span>2,846</span></div><b>→</b><div>IP资源<span>18,624</span></div><b>→</b><div>统一资源池<span>4</span></div></div></div>`);
 const configs=[
  [D.centos,[['id','编号','link','resource-detail'],['name','名称'],['ip','管理地址'],['region','地区'],['status','状态','status'],['ros','ROS数'],['abnormal','异常ROS'],['cpu','CPU'],['memory','内存'],['sync','最近同步']],[['详情','resource-detail'],['监控','monitor-detail']]],
  [D.ros,[['id','ROS编号','link','resource-detail'],['centos','所属CentOS'],['region','地区'],['carrier','运营商'],['lines','线路总数'],['abnormal','异常线路'],['status','状态','status'],['ips','当前IP'],['sync','最近同步']],[['详情','resource-detail'],['监控','monitor-detail']]],
  [D.lines,[['id','线路编号','link','resource-detail'],['ros','所属ROS'],['region','地区'],['carrier','运营商'],['ip','当前IP'],['status','在线状态','status'],['dial','拨号状态','status'],['latency','延时'],['availability','可用率'],['usage','占用']],[['详情','resource-detail'],['监控','monitor-detail']]],
  [D.ips,[['id','IP地址','link','resource-detail'],['source','来源'],['ros','来源ROS'],['line','线路/供应商'],['region','地区'],['carrier','运营商'],['status','可用状态','status'],['latency','延时'],['dup','重复'],['pool','资源池'],['occupy','占用']],[['详情','resource-detail']]],
  [D.pools,[['id','资源池编号','link','pool-detail'],['name','资源池名称'],['region','地区'],['carrier','运营商'],['source','资源来源'],['total','IP总量'],['unique','去重IP'],['available','可用IP'],['products','关联产品'],['status','状态','status']],[['详情','pool-detail'],['编辑','pool-edit']]],
  [D.exclusive,[['id','资源','link','exclusive-detail'],['type','类型'],['pool','资源池'],['customer','客户'],['service','服务编号'],['assigned','分配时间'],['expire','到期时间'],['status','占用状态','status'],['health','检测状态','status']],[['详情','exclusive-detail'],['释放','exclusive-release']]],
  [D.suppliers,[['id','供应商编号','link','supplier-detail'],['name','供应商'],['contact','联系人'],['endpoint','接口地址'],['auth','认证方式'],['regions','覆盖地区'],['interface','接口状态','status'],['success','成功率'],['status','状态','status']],[['详情','supplier-detail'],['编辑','supplier-edit'],['测试接口','test']]],
  [D.access,[['id','任务编号','link','access-detail'],['supplier','供应商'],['region','地区'],['carrier','运营商'],['requested','请求数量'],['returned','原始返回'],['unique','去重后'],['available','可用'],['pool','目标资源池'],['status','状态','status']],[['详情','access-detail'],['失败明细','access-error']]]
 ];
 const add=n===5?btn('新增资源池','primary','pool-create'):n===7?btn('新增供应商','primary','supplier-create'):n===8?btn('新建接入配置','primary','access-create')+btn('立即同步','','sync'):'';
 out+=toolbar('',add);const c=configs[n-1];out+=table(c[1],c[0],c[2]);return out;
}

function products(){const n=state.tabs.products;let out=head('产品管理','五类产品共享统一资源体系，通过套餐和资源策略形成差异化能力')+tabs('products',['产品列表','套餐管理','产品资源策略']);if(n===0)return out+`<div class="note">产品定义业务能力，真正的资源选择由“产品资源策略”完成。</div>`+toolbar('',btn('新增产品','primary','product-create'))+table([['id','产品编号','link','product-detail'],['name','产品名称'],['type','产品类型','status'],['access','接入方式'],['protocol','协议'],['auth','认证方式'],['region','地区范围'],['status','状态','status'],['packages','套餐数'],['services','有效服务']],D.products,[['详情','product-detail'],['编辑','product-edit'],['复制','product-copy']]);if(n===1)return out+toolbar('',btn('新增套餐','primary','package-create'))+table([['id','套餐编号','link','package-detail'],['name','套餐名称'],['product','所属产品'],['cycle','周期'],['quota','权益'],['concurrency','并发'],['white','白名单'],['price','价格'],['status','状态','status']],D.packages,[['详情','package-detail'],['编辑','package-edit'],['复制','package-copy']]);return out+`<div class="note brand">资源策略是产品层和资源层的核心连接点，服务开通时解析策略并形成实际资源配置。</div>`+toolbar('',btn('新增资源策略','primary','strategy-create'))+table([['id','策略编号','link','strategy-detail'],['name','策略名称'],['product','适用产品'],['pools','资源池'],['region','地区'],['carrier','运营商'],['source','资源优先级'],['dispatch','调度方式'],['switch','切换规则'],['status','状态','status']],D.strategies,[['详情','strategy-detail'],['编辑','strategy-edit'],['验证资源','test']]);}

function orders(){const n=state.tabs.orders;let out=head('订单与服务管理','订单记录购买行为，服务实例记录客户实际可用能力')+tabs('orders',['订单管理','服务开通','服务实例','服务变更','到期与资源释放']);if(n===0)return out+toolbar('',btn('创建订单','primary','order-create'))+table([['id','订单编号','link','order-detail'],['customer','客户'],['type','订单类型','status'],['product','产品'],['package','套餐'],['amount','订单金额'],['status','订单状态','status'],['created','创建时间'],['owner','创建人']],D.orders,[['详情','order-detail'],['编辑','order-edit'],['开通服务','service-open']]);if(n===1)return out+`<div class="note brand">服务开通支持“从订单开通”和“人工直接开通”，使用向导演示资源选择和认证生成。</div><div class="card pad empty-state"><div class="empty-icon">＋</div><h3>创建服务实例</h3><p>根据产品资源策略选择资源并生成认证信息</p>${btn('开始服务开通','primary','service-open')}</div>`;if(n===2)return out+toolbar()+table([['id','服务编号','link','service-detail'],['customer','客户'],['product','产品'],['package','套餐'],['type','业务类型','status'],['status','服务状态','status'],['expire','到期时间'],['resource','资源摘要'],['credential','认证状态','status'],['usage','额度使用']],D.services,[['详情','service-detail'],['变更','service-change'],['暂停/恢复','toggle']]);if(n===3)return out+toolbar('',btn('发起服务变更','primary','service-change'))+table([['id','变更编号','link','change-detail'],['service','服务编号'],['customer','客户'],['type','变更类型'],['before','变更前'],['after','变更后'],['status','结果','status'],['operator','操作人'],['time','时间']],D.changes,[['详情','change-detail']]);return out+`<div class="status-summary"><span>未来7天到期：18</span><span>待回收独享资源：3</span><span>回收失败：1</span></div>`+toolbar()+table([['id','服务编号','link','service-detail'],['customer','客户'],['product','产品'],['expire','到期时间'],['status','服务状态','status'],['resource','资源摘要'],['credential','凭证状态','status']],D.services,[['续费','service-renew'],['执行回收','exclusive-release']]);}

function monitor(){const n=state.tabs.monitor,names=['CentOS监控','ROS监控','线路监控','IP资源监控','外部资源监控','服务运行监控'];let out=head('监控中心','从基础设施到客户服务的统一运行监控')+tabs('monitor',names)+`<div class="grid cols-4">${metric('CentOS在线率','75%','3/4')}${metric('ROS在线率','91%','17/19','green')}${metric('线路在线率','94%','异常212','brand')}${metric('IP可用率','96%','18,624')}</div>`;const configs=[
 [D.monitorSets.centos,[['id','CentOS','link','monitor-detail'],['status','在线状态','status'],['cpu','CPU'],['memory','内存'],['disk','磁盘'],['ros','ROS数'],['abnormal','异常ROS'],['sync','最后采集']]],
 [D.monitorSets.ros,[['id','ROS','link','monitor-detail'],['centos','所属CentOS'],['status','状态','status'],['lines','线路总数'],['online','在线线路'],['abnormal','异常线路'],['ips','当前IP'],['sync','最后采集']]],
 [D.monitorSets.line,[['id','线路','link','monitor-detail'],['ros','所属ROS'],['status','在线状态','status'],['dial','拨号状态','status'],['ip','当前出口IP'],['latency','延时'],['availability','可用率'],['usage','占用']]],
 [D.monitorSets.ip,[['id','资源池','link','monitor-detail'],['total','IP总量'],['unique','去重IP'],['available','可用IP'],['invalid','失效IP'],['duplicate','重复率'],['new','新增IP'],['sourceRos','来源ROS'],['external','外部IP'],['status','状态','status']]],
 [D.monitorSets.external,[['id','供应商','link','monitor-detail'],['interface','接口状态','status'],['response','响应时间'],['calls','调用数'],['success','成功'],['fail','失败'],['rate','成功率'],['resources','获取资源'],['available','检测可用'],['last','最后成功'],['status','状态','status']]],
 [D.monitorSets.service,[['id','服务编号','link','monitor-detail'],['customer','客户'],['type','产品类型'],['status','服务状态','status'],['resource','当前资源'],['connection','连接/运行'],['success','成功率'],['concurrency','当前并发'],['response','响应时间'],['last','最近使用'],['alarm','当前告警']]]
 ];
 const c=configs[n];return out+section(names[n],toolbar()+table(c[1],c[0],[['实时详情','monitor-detail'],['历史趋势','monitor-history']]));}

function logs(){const n=state.tabs.logs,names=['IP提取日志','使用日志','API日志','ROS运行日志','线路拨号日志','IP变化日志','外部资源获取日志','操作日志'];let out=head('日志中心','关键请求、运行事件和后台操作均可追溯')+tabs('logs',names);const cfg=[
 [D.logSets.extract,[['time','提取时间'],['customer','客户'],['service','服务编号'],['account','账号'],['clientIp','请求来源IP'],['protocol','协议'],['region','地区'],['carrier','运营商'],['request','请求数量'],['returned','返回数量'],['pool','资源池'],['result','结果','status'],['elapsed','耗时']]],
 [D.logSets.usage,[['time','使用时间'],['customer','客户'],['service','服务编号'],['clientIp','客户端IP'],['outIp','出口IP'],['line','线路/来源'],['protocol','协议'],['target','访问目标'],['result','结果','status'],['up','上行'],['down','下行'],['elapsed','耗时']]],
 [D.logSets.api,[['id','请求ID','link','log-detail'],['time','请求时间'],['customer','客户'],['token','Token标识'],['api','接口名称'],['path','请求路径'],['clientIp','客户端IP'],['code','响应码'],['result','结果','status'],['error','错误信息'],['elapsed','耗时']]],
 [D.logSets.ros,[['time','日志时间'],['ros','ROS','link','log-detail'],['centos','所属CentOS'],['event','事件类型'],['content','事件内容'],['lines','线路总数'],['abnormal','异常线路'],['ips','当前IP'],['result','处理结果','status'],['recovered','恢复时间']]],
 [D.logSets.dial,[['time','拨号时间'],['line','线路','link','log-detail'],['ros','所属ROS'],['type','拨号类型'],['before','拨号前IP'],['after','拨号后IP'],['result','结果','status'],['reason','失败原因'],['elapsed','拨号耗时'],['trigger','触发来源']]],
 [D.logSets.ipchange,[['time','变化时间'],['line','线路','link','log-detail'],['ros','来源ROS'],['before','原IP'],['after','新IP'],['reason','变化原因'],['duplicate','重复IP'],['domain','关联域名'],['domainResult','域名更新'],['service','关联服务']]],
 [D.logSets.external,[['time','调用时间'],['supplier','供应商','link','log-detail'],['api','接口'],['region','地区'],['carrier','运营商'],['request','请求数量'],['returned','返回数量'],['unique','去重后'],['available','检测可用'],['pool','目标资源池'],['result','调用结果','status'],['elapsed','响应时间']]],
 [D.logSets.operation,[['time','操作时间'],['user','操作人员'],['module','模块'],['type','操作类型'],['object','操作对象','link','log-detail'],['before','操作前'],['after','操作后'],['result','结果','status'],['ip','操作IP']]]
 ];
 const c=cfg[n];out+=`<div class="note">当前查看：<b>${names[n]}</b>。每个标签页均使用独立字段和模拟数据。</div>`;out+=toolbar('<input class="input search" placeholder="关键字 / 对象ID / IP"><input class="input" type="date" value="2026-08-28">');out+=table(c[1],c[0],[['详情','log-detail']]);return out;}

function alarms(){const n=state.tabs.alarms;let out=head('告警中心','形成监控发现、告警通知、确认处理和恢复闭环')+tabs('alarms',['告警记录','告警规则','钉钉通知']);if(n===0)return out+toolbar()+table([['id','告警编号','link','alarm-detail'],['level','级别','status'],['object','告警对象'],['name','告警内容'],['value','当前值'],['status','状态','status'],['handler','处理人'],['time','时间']],D.alarms,[['详情','alarm-detail'],['确认','alarm-confirm'],['处理','alarm-handle']]);if(n===1)return out+toolbar('',btn('新增规则','primary','rule-create'))+table([['id','规则编号','link','rule-detail'],['name','规则名称'],['object','监控对象'],['metric','指标'],['condition','触发条件'],['level','级别','status'],['notify','通知'],['status','状态','status']],D.rules,[['详情','rule-detail'],['编辑','rule-edit'],['测试','test']]);return out+`<div class="grid cols-2"><div class="card pad"><div class="card-title">运维一群机器人 ${statusTag('启用')}</div>${kv([['Webhook','https://oapi.dingtalk.com/robot/send?access_token=••••••'],['告警级别','一般 / 重要 / 严重'],['重复通知','30分钟'],['恢复通知','开启']])}<div class="card-actions top-gap">${btn('编辑','','ding-edit')}${btn('发送测试','primary','test')}</div></div></div>`;}

function statistics(){const n=state.tabs.statistics,names=['基础资源统计','IP资源统计','客户使用统计','产品与服务统计','外部资源统计'];const vals=[[75,81,88,92,94,96],[12420,13208,14811,15690,17011,18624],[42,65,88,109,131,156],[68,72,81,94,105,118],[88,91,92,95,93,97]][n];const kpis=[
 [['CentOS总数','4'],['ROS总数','19'],['线路总数','2,846'],['线路在线率','94%']],
 [['IP总量','18,624'],['去重IP','17,452'],['可用IP','17,883'],['重复率','6.3%']],
 [['活跃客户','156'],['提取量','286万'],['请求成功率','99.1%'],['流量','8.7TB']],
 [['有效产品','5'],['有效服务','642'],['平均资源使用','68%'],['服务成功率','99.3%']],
 [['供应商','2'],['外部IP','5,732'],['接口成功率','95.4%'],['检测可用率','91.2%']]
 ][n];let out=head('数据统计','按小时、日、月和自定义时间范围查看趋势',btn('导出报表'))+tabs('statistics',names)+toolbar('<input class="input" type="date" value="2026-08-23"><span>至</span><input class="input" type="date" value="2026-08-28">');out+=`<div class="grid cols-4">${kpis.map((x,i)=>metric(x[0],x[1],i?'统计周期':'当前值',i===1?'green':i===2?'brand':'' )).join('')}</div>`+section(names[n],`<div class="card pad">${chart(['08-23','08-24','08-25','08-26','08-27','08-28'],vals)}</div>`);return out;}

function dictionaryPage(){const selected=DICTS.find(d=>d.code===state.dict)||DICTS[0];const left=`<div class="card dict-list">${DICTS.map(d=>`<div class="dict-row ${d.code===selected.code?'active':''}" data-dict="${d.code}"><b>${d.name}</b><span class="dict-code">${d.code}</span><small>${d.module} · ${d.items.length}项</small></div>`).join('')}</div>`;const rows=selected.items.map((x,i)=>({id:x[0],label:x[1],sort:x[2],status:x[3],remark:'—'}));const right=`<div><div class="card pad"><div class="page-head"><div><div class="card-title">${selected.name}</div><div class="subtle">字典编码：${selected.code}　使用模块：${selected.module}<br>${selected.remark}</div></div><div class="actions">${btn('编辑字典','','dict-edit',selected.code)}${btn('新增字典项','primary','dict-item-create',selected.code)}</div></div></div><div class="top-gap">${table([['id','字典值'],['label','显示名称'],['sort','排序'],['status','状态','status'],['remark','备注']],rows,[['编辑','dict-item-edit']])}</div></div>`;return `<div class="note brand">以下内容属于高频、跨模块复用、需要后台配置的枚举项。地区采用层级区域数据管理，不放入普通字典。</div><div class="dict-layout">${left}${right}</div>`;}

function system(){const n=state.tabs.system;let out=head('系统管理','后台用户、角色权限、字典、系统参数及审计')+tabs('system',['用户管理','角色管理','字典管理','参数配置','登录日志','操作审计']);if(n===0)return out+toolbar('',btn('新增用户','primary','user-create'))+table([['id','用户账号','link','user-detail'],['name','姓名'],['role','角色'],['dept','部门'],['status','状态','status'],['last','最后登录'],['ip','登录IP']],D.users,[['详情','user-detail'],['编辑','user-edit'],['重置密码','test']]);if(n===1)return out+toolbar('',btn('新增角色','primary','role-create'))+table([['id','角色编码','link','role-detail'],['name','角色名称'],['users','用户数'],['scope','数据范围'],['desc','说明'],['status','状态','status']],D.roles,[['详情','role-detail'],['编辑权限','role-edit']]);if(n===2)return out+dictionaryPage();if(n===3)return out+toolbar('',btn('新增参数','primary','param-create'))+table([['group','分组'],['id','参数编码','link','param-detail'],['name','参数名称'],['value','参数值'],['status','状态','status'],['updated','更新时间']],D.params,[['编辑','param-edit'],['修改记录','test']]);if(n===4)return out+toolbar('<input class="input search" placeholder="账号/姓名/IP"><input class="input" type="date" value="2026-08-28">')+table([['id','记录ID','link','login-detail'],['time','登录时间'],['user','用户'],['ip','登录IP'],['client','客户端'],['result','登录结果','status'],['reason','失败原因']],D.loginLogs,[['详情','login-detail']]);return out+toolbar('<input class="input search" placeholder="操作人/对象/模块"><input class="input" type="date" value="2026-08-28">')+table([['id','审计ID','link','audit-detail'],['time','操作时间'],['user','操作人员'],['module','模块'],['object','操作对象'],['action','操作类型'],['result','结果','status'],['ip','来源IP'],['detail','变更摘要']],D.auditLogs,[['详情','audit-detail']]);}

const renders={dashboard,customers,resources,products,orders,monitor,logs,alarms,statistics,system};

function entityDetail(action,id){let arr=null;if(action==='customer-detail')arr=D.customers;else if(action==='resource-detail')arr=[...D.centos,...D.ros,...D.lines,...D.ips];else if(action==='pool-detail')arr=D.pools;else if(action==='exclusive-detail')arr=D.exclusive;else if(action==='supplier-detail')arr=D.suppliers;else if(action==='access-detail')arr=D.access;else if(action==='product-detail')arr=D.products;else if(action==='package-detail')arr=D.packages;else if(action==='strategy-detail')arr=D.strategies;else if(action==='order-detail')arr=D.orders;else if(action==='service-detail')arr=D.services;else if(action==='change-detail')arr=D.changes;else if(action==='alarm-detail')arr=D.alarms;else if(action==='rule-detail')arr=D.rules;else if(action==='user-detail')arr=D.users;else if(action==='role-detail')arr=D.roles;else if(action==='param-detail')arr=D.params;else if(action==='login-detail')arr=D.loginLogs;else if(action==='audit-detail')arr=D.auditLogs;if(!arr)return false;const x=get(arr,id);const items=Object.entries(x).slice(0,14).map(([k,v])=>[k,/status|result|level|interface/.test(k)?statusTag(v):esc(v)]);drawer((x.name||x.id)+' · 详情',kv(items)+section('模块引用关系',`<div class="note">原型通过对象ID关联客户、服务、资源、日志、监控和告警。正式系统需保留必要配置快照。</div>`));return true;}
function logDetail(id){const all=Object.values(D.logSets).flat();const x=get(all,id);drawer('日志详情 · '+x.id,kv(Object.entries(x).map(([k,v])=>[k,esc(v)]))+section('追踪建议',`<div class="note">可通过 requestId / 服务编号 / 资源ID 继续追踪关联日志与业务对象。</div>`));}
function monitorDetail(id){drawer('监控详情 · '+id,kv([['监控对象',esc(id)],['当前状态',statusTag('在线')],['最后采集','刚刚'],['数据新鲜度',statusTag('正常')]])+section('最近采样',`<div class="card pad">${chart(['-55m','-45m','-35m','-25m','-15m','现在'],[62,68,71,65,78,74])}</div>`));}
function genericForm(action,id){if(action==='customer-create'||action==='customer-edit'){const x=action.endsWith('edit')?get(D.customers,id):{};modal(action.endsWith('edit')?'编辑客户':'新增客户',fields([['name','客户名称'],['type','客户类型',['企业','个人']],['contact','联系人'],['phone','联系电话'],['owner','业务负责人',['王强','赵磊','孙浩']],['status','客户状态',['正常','冻结','停用']],['remark','备注','textarea','',true]],x),btn('取消','','close')+btn('保存','primary','save'),true);return true;}if(action==='product-create'||action==='product-edit'||action==='product-copy'){let x=action==='product-create'?{}:{...get(D.products,id)};if(action==='product-copy')x.name+='-副本';modal(action==='product-edit'?'编辑产品':action==='product-copy'?'复制产品':'新增产品',fields([['name','产品名称'],['type','产品类型',['短效IP','长效IP','独享IP','VPN','隧道']],['access','接入方式',['API提取','直连','VPN连接','固定入口']],['protocol','代理协议'],['auth','认证方式'],['region','地区范围'],['status','状态',['草稿','启用','停用']],['remark','产品说明','textarea','',true]],x),btn('取消','','close')+btn('保存草稿','','save')+btn('保存并启用','primary','save'),true);return true;}if(action==='pool-create'||action==='pool-edit'){const x=action.endsWith('edit')?get(D.pools,id):{};modal(action.endsWith('edit')?'编辑资源池':'新增资源池',fields([['name','资源池名称'],['region','地区'],['carrier','运营商'],['source','资源来源',['自有','外部','自有+外部']],['priority','资源优先级'],['status','状态',['启用','停用']],['rule','入池规则','textarea','检测、去重后入池',true]],x)+`<div class="note brand">资源池不按短效/长效拆分，产品差异由资源策略形成。</div>`,btn('取消','','close')+btn('保存资源池','primary','save'),true);return true;}if(action==='supplier-create'||action==='supplier-edit'){const x=action.endsWith('edit')?get(D.suppliers,id):{};modal(action.endsWith('edit')?'编辑供应商':'新增供应商',fields([['name','供应商名称'],['contact','联系人'],['endpoint','API地址'],['auth','认证方式',['API Key','账号+签名','Token']],['key','API Key/密钥'],['regions','覆盖地区'],['status','状态',['启用','暂停','停用']],['remark','备注','textarea','',true]],x),btn('取消','','close')+btn('测试接口','','test')+btn('保存','primary','save'),true);return true;}if(action==='package-create'||action==='package-edit'||action==='package-copy'){let x=action==='package-create'?{}:{...get(D.packages,id)};if(action==='package-copy')x.name+='-副本';modal(action==='package-edit'?'编辑套餐':action==='package-copy'?'复制套餐':'新增套餐',fields([['name','套餐名称'],['product','所属产品',D.products.map(p=>p.name)],['cycle','服务周期'],['quota','权益/额度'],['concurrency','并发上限'],['white','白名单数量'],['price','销售价格'],['status','状态',['启用','停用']]],x),btn('取消','','close')+btn('保存套餐','primary','save'),true);return true;}if(action==='strategy-create'||action==='strategy-edit'){const x=action.endsWith('edit')?get(D.strategies,id):{};modal(action.endsWith('edit')?'编辑资源策略':'新增资源策略',fields([['name','策略名称'],['product','适用产品',D.products.map(p=>p.name)],['pools','资源池范围'],['region','地区'],['carrier','运营商'],['source','资源优先级'],['dispatch','调度方式',['质量优先+轮询','延迟优先','加权随机']],['switch','切换规则'],['status','状态',['启用','停用']]],x)+`<div class="note brand">服务开通时解析资源策略，建议服务实例保存策略版本或快照。</div>`,btn('取消','','close')+btn('验证资源','','test')+btn('保存策略','primary','save'),true);return true;}if(action==='order-create'||action==='order-edit'){const x=action.endsWith('edit')?get(D.orders,id):{};modal(action.endsWith('edit')?'编辑订单':'创建订单',fields([['customer','客户',D.customers.map(c=>c.name)],['type','订单类型',['新购','续费','扩容']],['product','产品',D.products.map(p=>p.name)],['package','套餐',D.packages.map(p=>p.name)],['effective','生效日期'],['expire','到期日期'],['amount','订单金额'],['remark','备注','textarea','',true]],x),btn('取消','','close')+btn('保存草稿','','save')+btn('确认订单','primary','save'),true);return true;}if(action==='rule-create'||action==='rule-edit'){const x=action.endsWith('edit')?get(D.rules,id):{};modal(action.endsWith('edit')?'编辑告警规则':'新增告警规则',fields([['name','规则名称'],['object','监控对象',['CentOS','ROS','家宽/线路','IP资源池','客户服务']],['metric','监控指标'],['condition','触发条件'],['level','告警级别',['提示','一般','重要','严重']],['notify','通知方式',['平台','钉钉','钉钉+平台']],['status','状态',['启用','停用']]],x),btn('取消','','close')+btn('测试规则','','test')+btn('保存','primary','save'),true);return true;}if(action==='user-create'||action==='user-edit'){const x=action.endsWith('edit')?get(D.users,id):{};modal(action.endsWith('edit')?'编辑用户':'新增用户',fields([['id','用户账号'],['name','姓名'],['role','角色',D.roles.map(r=>r.name)],['dept','部门'],['status','状态',['正常','冻结','停用']]],x),btn('取消','','close')+btn('保存用户','primary','save'));return true;}if(action==='role-create'||action==='role-edit'){const x=action.endsWith('edit')?get(D.roles,id):{};modal(action.endsWith('edit')?'编辑角色与权限':'新增角色',fields([['name','角色名称'],['scope','数据范围',['全部数据','资源/监控/告警','客户/产品/订单/服务']],['desc','角色说明','textarea','',true]],x)+section('菜单与操作权限',`<div class="permission-tree">${['客户管理','资源管理','产品管理','订单与服务','Token明文查看','供应商密钥修改','服务终止','资源强制释放'].map((v,i)=>`<label><input type="checkbox" ${i<4?'checked':''}> ${v}</label>`).join('')}</div>`),btn('取消','','close')+btn('保存角色','primary','save'),true);return true;}if(action==='param-create'||action==='param-edit'){const x=action.endsWith('edit')?get(D.params,id):{};modal(action.endsWith('edit')?'编辑参数':'新增参数',fields([['group','配置分组'],['id','参数编码'],['name','参数名称'],['value','参数值'],['status','状态',['启用','停用']],['remark','说明','textarea','',true]],x),btn('取消','','close')+btn('保存参数','primary','save'));return true;}return false;}
function serviceOpen(){modal('服务开通向导',`<div class="steps"><div class="step active">1.选择客户与产品</div><div class="step active">2.资源与认证</div><div class="step">3.确认开通</div></div>`+fields([['customer','客户',D.customers.map(c=>c.name)],['order','关联订单',['人工直接开通',...D.orders.map(o=>o.id+' · '+o.customer)]],['product','产品',D.products.map(p=>p.name)],['package','套餐',D.packages.map(p=>p.name)],['effective','生效日期'],['expire','到期日期']])+section('资源与认证预览',`<div class="wizard-preview"><div><b>产品资源策略</b><span>短效-山东联通优先</span></div><div><b>候选资源池</b><span>山东联通池 · 可用IP 5,068</span></div><div><b>认证方式</b><span>Token + 白名单（20）</span></div><div><b>预计生成</b><span>1个服务实例 / 1个Token</span></div></div>`)+`<div class="note brand">本原型只演示流程，不会真实分配资源。</div>`,btn('取消','','close')+btn('模拟资源校验','','test')+btn('确认开通','primary','save'),true);}
function dictionaryAction(action,id){const d=DICTS.find(x=>x.code===id)||DICTS[0];if(action==='dict-edit'){modal('编辑字典 · '+d.name,fields([['name','字典名称'],['code','字典编码'],['module','使用模块'],['remark','说明','textarea','',true]],{name:d.name,code:d.code,module:d.module,remark:d.remark}),btn('取消','','close')+btn('保存','primary','save'));return true;}if(action==='dict-item-create'||action==='dict-item-edit'){modal((action==='dict-item-create'?'新增':'编辑')+'字典项 · '+d.name,fields([['value','字典值'],['label','显示名称'],['sort','排序'],['status','状态',['启用','停用']],['remark','备注','textarea','',true]]),btn('取消','','close')+btn('保存','primary','save'));return true;}return false;}
function secondary(action,id){if(genericForm(action,id))return;if(dictionaryAction(action,id))return;if(entityDetail(action,id))return;if(action==='log-detail'){logDetail(id);return}if(action==='monitor-detail'||action==='monitor-history'){monitorDetail(id);return}if(action==='auth-detail'||action==='auth-review'){const x=get(D.auth,id);const body=kv([['认证编号',x.id],['客户',x.customer],['认证类型',x.type],['提交时间',x.submitted],['认证资料',x.docs],['状态',statusTag(x.status)]])+section('认证材料',`<div class="doc-preview"><div><b>证件/营业执照预览</b><br><span>原型占位区域</span></div></div>`);if(action==='auth-review'&&x.status==='待审核')modal('认证审批 · '+x.customer,body+fields([['result','审批结论',['通过','驳回']],['opinion','审核意见','textarea','',true]]),btn('取消','','close')+btn('提交审批','primary','save'),true);else drawer(x.customer+' · 认证详情',body);return}if(action==='service-open'){serviceOpen();return}if(action==='service-change'||action==='service-renew'){const x=id?get(D.services,id):D.services[0];modal(action==='service-renew'?'服务续费':'服务变更',kv([['服务编号',x.id],['客户',x.customer],['当前产品',x.product],['当前状态',statusTag(x.status)]])+fields([['type','变更类型',['续费','扩容/调整额度','更换IP/线路','更换资源池','更换套餐','暂停服务','恢复服务','终止服务']],['target','变更目标/内容'],['reason','变更原因','textarea','',true]])+`<div class="note brand">资源变更采用“先确认新资源可用，再释放旧资源”的顺序。</div>`,btn('取消','','close')+btn('提交变更','primary','save'),true);return}if(action==='alarm-confirm'||action==='alarm-handle'){const x=get(D.alarms,id);modal(action==='alarm-confirm'?'确认告警':'处理告警',kv([['告警',x.name],['对象',x.object],['状态',statusTag(x.status)]])+fields([['status','处理状态',['已确认','处理中','已恢复']],['remark','处理备注','textarea','',true]]),btn('取消','','close')+btn('提交','primary','save'));return}if(action==='exclusive-release'){modal('确认资源释放',`<div class="note brand">该操作会解除服务/资源占用关系并使相关认证信息失效，正式系统属于高影响操作。</div>`+fields([['reason','操作原因','textarea','',true]]),btn('取消','','close')+btn('确认释放','ghost-danger','save'));return}if(action==='access-create'){modal('新建外部资源接入配置',fields([['supplier','供应商',D.suppliers.map(s=>s.name)],['region','地区'],['carrier','运营商'],['pool','目标资源池',D.pools.map(p=>p.name)],['mode','同步方式',['定时同步','实时API','人工触发']],['rule','标准化/检测规则','textarea','',true]]),btn('取消','','close')+btn('保存配置','primary','save'),true);return}if(['test','sync','refresh','toggle','access-error','ding-edit'].includes(action)){toast('原型演示操作已完成');return}if(action==='save'){toast('保存成功');$('#layer').innerHTML='';return}}

function render(){const fn=renders[state.page]||dashboard;$('#content').innerHTML=fn();$$('.nav-item').forEach(x=>x.classList.toggle('active',x.dataset.nav===state.page));}
function shell(){$('#app').innerHTML=`<div class="shell"><aside class="sidebar"><div class="brand"><div class="brand-mark">IP</div><div><div class="brand-title">IP代理管理平台</div><div class="brand-sub">Prototype Console</div></div></div><div class="nav-scroll">${menu.map(g=>`<div class="nav-group-title">${g[0]}</div>${g[1].map(i=>`<div class="nav-item ${i[0]===state.page?'active':''}" data-nav="${i[0]}"><span class="nav-icon">${i[1]}</span><span>${i[2]}</span>${i[0]==='alarms'?'<span class="nav-badge">7</span>':''}</div>`).join('')}`).join('')}</div></aside><main class="main"><header class="topbar"><div class="top-left"><span class="console-chip">控制台</span><span class="top-title">企业网络资源与代理服务管理</span></div><div class="top-right"><span class="env">演示环境</span><span>消息 7</span><div class="avatar">孙</div><span>孙凯 · 超级管理员</span></div></header><div class="content" id="content"></div></main></div>`;render();}
document.addEventListener('click',e=>{const nav=e.target.closest('[data-nav]');if(nav){state.page=nav.dataset.nav;render();return}const tab=e.target.closest('[data-tab-group]');if(tab){state.tabs[tab.dataset.tabGroup]=Number(tab.dataset.tabIndex);render();return}const d=e.target.closest('[data-dict]');if(d){state.dict=d.dataset.dict;render();return}const a=e.target.closest('[data-action]');if(!a)return;const action=a.dataset.action,id=a.dataset.id;if(action==='close'){$('#layer').innerHTML='';return}secondary(action,id);});
shell();