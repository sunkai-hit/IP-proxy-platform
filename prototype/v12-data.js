window.V12 = {
  customers: [
    {id:'C202608001',name:'威海云启科技',type:'企业',contact:'张经理',phone:'138****8821',auth:'已认证',status:'正常',services:3,biz:'短效IP / 隧道',expire:'2026-09-05',owner:'王强'},
    {id:'C202608002',name:'青岛星途数据',type:'企业',contact:'李女士',phone:'186****3904',auth:'已认证',status:'正常',services:2,biz:'长效IP / VPN',expire:'2026-09-11',owner:'赵磊'},
    {id:'C202608003',name:'陈明',type:'个人',contact:'陈明',phone:'135****1037',auth:'待审核',status:'正常',services:1,biz:'短效IP',expire:'2026-09-02',owner:'王强'}
  ],
  centos: [
    {id:'CT-001',name:'centos-sd-01',status:'在线',account:'ops_sd01',ip:'10.20.1.10',poolCount:6,location:'山东青岛',rosCount:5,carrier:'联通',remark:'山东资源集群',cpu:'31%',memory:'42%',load:'0.86',disk:'48%',vpn:'支持',pppoe:'bras-sd-01',heartbeat:'38秒前'},
    {id:'CT-002',name:'centos-hb-01',status:'在线',account:'ops_hb01',ip:'10.20.2.10',poolCount:4,location:'河北石家庄',rosCount:5,carrier:'电信',remark:'华北资源集群',cpu:'48%',memory:'55%',load:'1.32',disk:'62%',vpn:'支持',pppoe:'bras-hb-03',heartbeat:'1分钟前'},
    {id:'CT-004',name:'centos-zj-01',status:'离线',account:'ops_zj01',ip:'10.20.4.10',poolCount:3,location:'浙江杭州',rosCount:5,carrier:'移动',remark:'待排障',cpu:'—',memory:'—',load:'—',disk:'—',vpn:'支持',pppoe:'bras-zj-02',heartbeat:'18分钟前'}
  ],
  ros: [
    {id:'ROS-SD-01',centos:'CT-001',cpu:'28%',memory:'36%',load:'0.74',disk:'32%',lines:168,online:162,uptime:'36天 8小时',record:'山东联通01',status:'在线',ha:'主',replacement:'ROS-SD-02',switch:'正常',auto:'开启',ipCount:162,bCount:7,cCount:46,basCount:3,report:'42秒前'},
    {id:'ROS-HB-03',centos:'CT-002',cpu:'61%',memory:'58%',load:'2.18',disk:'51%',lines:212,online:187,uptime:'18天 3小时',record:'河北电信03',status:'异常',ha:'主',replacement:'ROS-HB-04',switch:'待检查',auto:'开启',ipCount:187,bCount:11,cCount:72,basCount:4,report:'2分钟前'},
    {id:'ROS-ZJ-04',centos:'CT-004',cpu:'—',memory:'—',load:'—',disk:'—',lines:178,online:0,uptime:'—',record:'浙江移动04',status:'离线',ha:'备',replacement:'ROS-ZJ-03',switch:'已切备',auto:'开启',ipCount:0,bCount:0,cCount:0,basCount:2,report:'18分钟前'}
  ],
  lines: [
    {id:'LN-SD-01001',ros:'ROS-SD-01',centos:'CT-001',alias:'青岛联通-001',account:'qdlt001@pppoe',prefix:'qdlt',password:'••••••••',type:'长效',province:'山东',city:'青岛',carrier:'联通',ip:'112.254.31.18',http:18081,socks:28081,domain:'qd001.proxy.example.com',customer:'青岛星途数据',status:'在线',latency:'18ms',managed:'是',vpnMonitor:'启用',limit:1,used:1,restart:'24小时',available:'00:00-24:00',vpn:'支持',bas:'BRAS-QD-LT-01',dial:'2026-09-07 07:18',report:'41秒前'},
    {id:'LN-SD-01002',ros:'ROS-SD-01',centos:'CT-001',alias:'青岛联通-002',account:'qdlt002@pppoe',prefix:'qdlt',password:'••••••••',type:'共享',province:'山东',city:'青岛',carrier:'联通',ip:'119.189.66.24',http:18082,socks:28082,domain:'—',customer:'—',status:'在线',latency:'22ms',managed:'是',vpnMonitor:'关闭',limit:20,used:7,restart:'6小时',available:'00:00-24:00',vpn:'支持',bas:'BRAS-QD-LT-02',dial:'2026-09-07 06:32',report:'46秒前'},
    {id:'LN-HB-03018',ros:'ROS-HB-03',centos:'CT-002',alias:'石家庄电信-018',account:'sjzdx018@pppoe',prefix:'sjzdx',password:'••••••••',type:'共享',province:'河北',city:'石家庄',carrier:'电信',ip:'110.249.22.90',http:19118,socks:29118,domain:'—',customer:'—',status:'异常',latency:'—',managed:'是',vpnMonitor:'启用',limit:30,used:0,restart:'4小时',available:'00:00-24:00',vpn:'支持',bas:'BRAS-SJZ-DX-03',dial:'拨号失败',report:'2分钟前'}
  ],
  pools: [
    {id:'POOL-SD-LT',name:'山东联通共享池',type:'共享',region:'山东',carrier:'联通',lines:860,online:821,available:804,unique:798,customers:126,products:2,status:'启用'},
    {id:'POOL-HB-DX',name:'河北电信共享池',type:'共享',region:'河北',carrier:'电信',lines:620,online:571,available:548,unique:542,customers:83,products:2,status:'启用'},
    {id:'POOL-LONG-SD',name:'山东长效资源池',type:'长效',region:'山东',carrier:'多运营商',lines:312,online:304,available:288,unique:286,customers:98,products:1,status:'启用'}
  ],
  exclusive: [
    {id:'EX-001',line:'LN-SD-01001',ip:'112.254.31.18',customer:'青岛星途数据',service:'SVC-100274',assigned:'2026-08-11',expire:'2026-10-11',status:'占用中'}
  ],
  domains: [
    {domain:'qd001.proxy.example.com',ip:'112.254.31.18',status:'正常',customer:'青岛星途数据',line:'LN-SD-01001',dns:'DNSPOD',created:'2026-08-11 10:20',lastSync:'2026-09-07 07:18'}
  ],
  suppliers: [
    {id:'SUP-A',name:'供应商A',contact:'赵工',account:'corp_a',auth:'API Key',endpoint:'https://api.supplier-a.example',status:'启用',remark:'长效/隧道补充资源'},
    {id:'SUP-B',name:'供应商B',contact:'王工',account:'corp_b',auth:'账号密码',endpoint:'https://api.supplier-b.example',status:'启用',remark:'全国住宅代理'}
  ],
  purchases: [
    {id:'PUR-260901-01',supplier:'供应商A',package:'华东长效50线',supplierNo:'A-LONG-50',customer:'杭州嘉云信息',account:'cust_8842',cycle:'30天',quota:'50线路',remain:'50线路',cost:'¥4,200',start:'2026-09-01',expire:'2026-09-30',status:'生效中'},
    {id:'PUR-260903-02',supplier:'供应商B',package:'全国隧道200G',supplierNo:'B-TUN-200G',customer:'北京极数网络',account:'cust_9917',cycle:'30天',quota:'200GB',remain:'162GB',cost:'¥3,600',start:'2026-09-03',expire:'2026-10-02',status:'生效中'}
  ],
  reverseProxy: [
    {id:'RP-260901-01',purchase:'PUR-260901-01',customer:'杭州嘉云信息',service:'SVC-100351',protocol:'HTTP/SOCKS5',upstream:'supplier-a.example:31080',platform:'hz-long.proxy.example.com:18100',auth:'上游账号已加密',start:'2026-09-01',expire:'2026-09-30',status:'正常'},
    {id:'RP-260903-02',purchase:'PUR-260903-02',customer:'北京极数网络',service:'SVC-100366',protocol:'HTTP',upstream:'supplier-b.example:443',platform:'tunnel.proxy.example.com:18443',auth:'API Key已加密',start:'2026-09-03',expire:'2026-10-02',status:'正常'}
  ],
  products: [
    {id:'P001',name:'高效短效代理',type:'短效IP',access:'API提取',protocol:'HTTP/HTTPS',auth:'Token + 白名单',region:'华东/华北',status:'启用',packages:4,services:142},
    {id:'P002',name:'稳定长效代理',type:'长效IP',access:'直连',protocol:'HTTP/SOCKS5',auth:'账号密码',region:'全国',status:'启用',packages:3,services:88},
    {id:'P003',name:'独享IP代理',type:'独享IP',access:'直连',protocol:'HTTP/HTTPS',auth:'白名单',region:'山东/河北',status:'启用',packages:2,services:61},
    {id:'P004',name:'ROSVPN',type:'VPN',access:'VPN连接',protocol:'VPN',auth:'私钥/账号',region:'全国',status:'启用',packages:3,services:97},
    {id:'P005',name:'高速隧道代理',type:'隧道',access:'固定入口',protocol:'HTTP/SOCKS5',auth:'账号密码',region:'全国',status:'启用',packages:5,services:254}
  ],
  packages: [
    {id:'PKG-001',name:'企业标准版',product:'高效短效代理',cycle:'30天',quota:'300万次提取',price:'¥3,600',status:'启用'},
    {id:'PKG-002',name:'20线路套餐',product:'ROSVPN',cycle:'30天',quota:'20线路',price:'¥6,800',status:'启用'},
    {id:'PKG-003',name:'100IP套餐',product:'独享IP代理',cycle:'90天',quota:'100 IP',price:'¥12,000',status:'启用'}
  ],
  orders: [
    {id:'O20260907001',customer:'威海云启科技',type:'新购',product:'高效短效代理',package:'企业标准版',amount:'¥3,600',status:'待开通',created:'2026-09-07 07:18',effective:'2026-09-07',expire:'2026-10-06'},
    {id:'O20260906018',customer:'青岛星途数据',type:'续费',product:'稳定长效代理',package:'长效20线',amount:'¥7,200',status:'已开通',created:'2026-09-06 16:44',effective:'2026-10-11',expire:'2026-11-10'}
  ],
  services: [
    {id:'SVC-100328',customer:'威海云启科技',product:'高效短效代理',package:'企业标准版',type:'短效IP',delivery:'SELF_OWNED',status:'正常',effective:'2026-09-07',expire:'2026-10-06',resource:'山东联通共享池',credential:'Token正常'},
    {id:'SVC-100274',customer:'青岛星途数据',product:'稳定长效代理',package:'长效20线',type:'长效IP',delivery:'SELF_OWNED',status:'正常',effective:'2026-08-11',expire:'2026-11-10',resource:'LN-SD-01001等20线',credential:'账号密码'},
    {id:'SVC-100351',customer:'杭州嘉云信息',product:'稳定长效代理',package:'外部50线',type:'长效IP',delivery:'EXTERNAL_PROXY',status:'正常',effective:'2026-09-01',expire:'2026-09-30',resource:'RP-260901-01',credential:'平台代理账号'},
    {id:'SVC-100366',customer:'北京极数网络',product:'高速隧道代理',package:'200G套餐',type:'隧道',delivery:'EXTERNAL_PROXY',status:'正常',effective:'2026-09-03',expire:'2026-10-02',resource:'RP-260903-02',credential:'平台代理账号'}
  ],
  monitorCentos: [
    {object:'CT-001',status:'正常',cpu:'31%',memory:'42%',load:'0.86',disk:'48%',last:'38秒前'},
    {object:'CT-002',status:'正常',cpu:'48%',memory:'55%',load:'1.32',disk:'62%',last:'1分钟前'},
    {object:'CT-004',status:'离线',cpu:'—',memory:'—',load:'—',disk:'—',last:'18分钟前'}
  ],
  monitorRos: [
    {object:'ROS-SD-01',status:'正常',cpu:'28%',memory:'36%',load:'0.74',disk:'32%',lines:'162/168',b:'7',c:'46',last:'42秒前'},
    {object:'ROS-HB-03',status:'异常',cpu:'61%',memory:'58%',load:'2.18',disk:'51%',lines:'187/212',b:'11',c:'72',last:'2分钟前'},
    {object:'ROS-ZJ-04',status:'离线',cpu:'—',memory:'—',load:'—',disk:'—',lines:'0/178',b:'0',c:'0',last:'18分钟前'}
  ],
  longMonitors: [
    {id:'LM-001',customer:'青岛星途数据',service:'SVC-100274',target:'112.254.31.18:18081',method:'代理连通性',cycle:'60秒',status:'正常',fails:0,last:'2026-09-07 07:47:10',bot:'BOT-DD-001',enabled:'启用'},
    {id:'LM-002',customer:'杭州嘉云信息',service:'SVC-100351',target:'hz-long.proxy.example.com:18100',method:'代理请求',cycle:'60秒',status:'异常',fails:3,last:'2026-09-07 07:47:08',bot:'BOT-FS-002',enabled:'启用'}
  ],
  bots: [
    {id:'BOT-DD-001',customer:'青岛星途数据',type:'钉钉',webhook:'https://oapi.dingtalk.com/robot/send?access_token=••••',secret:'SEC••••91',status:'启用',test:'成功'},
    {id:'BOT-FS-002',customer:'杭州嘉云信息',type:'飞书',webhook:'https://open.feishu.cn/open-apis/bot/v2/hook/••••',secret:'SEC••••37',status:'启用',test:'成功'}
  ],
  alarms: [
    {id:'LA-260907-01',customer:'杭州嘉云信息',service:'SVC-100351',target:'hz-long.proxy.example.com:18100',problem:'连续3次代理请求失败',status:'告警中',time:'2026-09-07 07:47:08',channel:'飞书'},
    {id:'LA-260906-08',customer:'青岛星途数据',service:'SVC-100274',target:'112.254.31.18:18081',problem:'代理连接超时',status:'已恢复',time:'2026-09-06 20:18:22',channel:'钉钉'}
  ],
  notifications: [
    {id:'NT-001',alarm:'LA-260907-01',customer:'杭州嘉云信息',channel:'飞书',result:'成功',sent:'2026-09-07 07:47:09',message:'长效代理异常：连续3次代理请求失败'},
    {id:'NT-002',alarm:'LA-260906-08',customer:'青岛星途数据',channel:'钉钉',result:'成功',sent:'2026-09-06 20:18:25',message:'长效代理异常：代理连接超时'}
  ]
};