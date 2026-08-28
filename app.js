const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

const icons = { dashboard: '◫', customer: '♙', resource: '◇', product: '▦', order: '▣', monitor: '◉', log: '≣', alarm: '⚠', stats: '▥', system: '⚙' };
const menu = [
  { group: '业务中心', items: [['dashboard','首页工作台','dashboard'], ['customers','客户管理','customer'], ['resources','资源管理','resource'], ['products','产品管理','product'], ['orders','订单与服务','order']]},
  { group: '运维中心', items: [['monitor','监控中心','monitor'], ['logs','日志中心','log'], ['alarms','告警中心','alarm','7'], ['statistics','数据统计','stats']]},
  { group: '平台设置', items: [['system','系统管理','system']] }
];

const mock = {
  customers: [
    ['C202608001','威海云启科技','企业','张经理','已认证','正常','3','短效IP / 隧道','2026-09-05','王强'],
    ['C202608002','青岛星途数据','企业','李女士','已认证','正常','2','长效IP / VPN','2026-09-11','赵磊'],
    ['C202608003','陈明','个人','陈明','待审核','正常','1','短效IP','2026-09-02','王强'],
    ['C202608004','北京极数网络','企业','刘经理','已认证','冻结','4','独享IP / 隧道','2026-10-18','孙浩'],
    ['C202608005','杭州嘉云信息','企业','周女士','未认证','正常','0','—','—','赵磊']
  ],
  centos: [
    ['CT-01','centos-shandong-01','10.20.1.10','山东','在线','5','5','0','31%','42%','1分钟前'],
    ['CT-02','centos-hebei-01','10.20.2.10','河北','在线','5','4','1','48%','55%','2分钟前'],
    ['CT-03','centos-jiangsu-01','10.20.3.10','江苏','在线','4','4','0','26%','38%','1分钟前'],
    ['CT-04','centos-zhejiang-01','10.20.4.10','浙江','离线','5','0','5','—','—','18分钟前']
  ],
  ros: [
    ['ROS-SD-01','CT-01','山东','联通','168','162','6','在线','126','48秒前'],
    ['ROS-SD-02','CT-01','山东','移动','135','132','3','在线','103','1分钟前'],
    ['ROS-HB-03','CT-02','河北','电信','212','187','25','异常','144','2分钟前'],
    ['ROS-JS-01','CT-03','江苏','联通','96','96','0','在线','81','40秒前'],
    ['ROS-ZJ-04','CT-04','浙江','移动','178','0','178','离线','0','18分钟前']
  ],
  lines: [
    ['LN-SD-01001','ROS-SD-01','山东青岛','联通','112.254.31.18','在线','正常','18ms','99.8%','共享'],
    ['LN-SD-01002','ROS-SD-01','山东济南','联通','119.189.66.24','在线','正常','22ms','99.6%','独享'],
    ['LN-HB-03018','ROS-HB-03','河北石家庄','电信','110.249.22.90','异常','拨号失败','—','82.1%','空闲'],
    ['LN-JS-01008','ROS-JS-01','江苏苏州','联通','122.97.11.64','在线','正常','16ms','99.9%','长效'],
    ['LN-ZJ-04031','ROS-ZJ-04','浙江杭州','移动','—','离线','离线','—','64.3%','空闲']
  ],
  ips: [
    ['112.254.31.18','自有','ROS-SD-01','LN-SD-01001','山东','联通','可用','18ms','否','山东联通池','共享使用'],
    ['119.189.66.24','自有','ROS-SD-01','LN-SD-01002','山东','联通','可用','22ms','否','山东联通池','独享占用'],
    ['110.249.22.90','自有','ROS-HB-03','LN-HB-03018','河北','电信','不可用','—','否','河北电信池','空闲'],
    ['39.105.71.88','外部','—','供应商A','北京','联通','可用','31ms','否','华北综合池','共享使用'],
    ['47.96.128.19','外部','—','供应商B','浙江','移动','可用','27ms','是','华东综合池','共享使用']
  ],
  products: [
    ['P001','高效短效代理','短效IP','API提取','HTTP/HTTPS','Token + 白名单','华东/华北','启用','4'],
    ['P002','稳定长效代理','长效IP','直连','HTTP/SOCKS5','账号密码','全国','启用','3'],
    ['P003','独享IP代理','独享IP','直连','HTTP/HTTPS','白名单','山东/河北','启用','2'],
    ['P004','ROSVPN','VPN','VPN连接','VPN','私钥/账号','全国','启用','3'],
    ['P005','高速隧道代理','隧道','固定入口','HTTP/SOCKS5','账号密码','全国','启用','5']
  ],
  orders: [
    ['O20260828001','威海云启科技','新购','高效短效代理','企业标准版','¥3,600','待开通','2026-08-28 09:18'],
    ['O20260827018','青岛星途数据','续费','ROSVPN','20线路套餐','¥6,800','已开通','2026-08-27 16:44'],
    ['O20260827011','北京极数网络','扩容','独享IP代理','100IP套餐','¥12,000','已完成','2026-08-27 11:26'],
    ['O20260826006','陈明','新购','高效短效代理','个人体验版','¥199','已开通','2026-08-26 20:03']
  ],
  services: [
    ['SVC-100328','威海云启科技','高效短效代理','企业标准版','短效IP','正常','2026-09-05','山东联通池','Token正常','67%'],
    ['SVC-100329','威海云启科技','高速隧道代理','10并发套餐','隧道','正常','2026-09-18','华东综合池','认证正常','41%'],
    ['SVC-100274','青岛星途数据','ROSVPN','20线路套餐','VPN','正常','2026-10-11','山东/河北','凭证正常','52%'],
    ['SVC-100211','北京极数网络','独享IP代理','100IP套餐','独享IP','暂停','2026-10-18','独享IP×100','凭证冻结','80%']
  ],
  logs: [
    ['2026-08-28 13:56:22','威海云启科技','SVC-100328','提取IP','112.254.31.18','成功','26ms'],
    ['2026-08-28 13:55:47','青岛星途数据','SVC-100274','VPN连接','119.189.66.24','成功','38ms'],
    ['2026-08-28 13:54:11','北京极数网络','SVC-100211','代理请求','110.249.22.90','失败','超时'],
    ['2026-08-28 13:53:09','威海云启科技','SVC-100329','隧道请求','39.105.71.88','成功','42ms'],
    ['2026-08-28 13:50:28','陈明','SVC-100188','提取IP','47.96.128.19','成功','31ms']
  ],
  alarms: [
    ['ALM-8291','严重','ROS-ZJ-04','ROS离线','已持续18分钟','未恢复','2026-08-28 13:41'],
    ['ALM-8290','重要','ROS-HB-03','异常线路比例超过10%','25条异常 / 212条','处理中','2026-08-28 13:38'],
    ['ALM-8287','一般','华北综合池','可用IP低于阈值','1,382 / 阈值1,500','已确认','2026-08-28 13:20'],
    ['ALM-8282','一般','供应商B','接口平均响应偏高','1.8s / 阈值1.5s','已恢复','2026-08-28 12:58']
  ]
};

const button=(t,cls='')=>`<button class="btn ${cls}">${t}</button>`;
const pageHead=(title,sub,actions='')=>`<div class="page-head"><div><h1>${title}</h1><div class="page-subtitle">${sub}</div></div><div class="actions">${actions}</div></div>`;
const metric=(label,value,trend='',color='blue')=>`<div class="card metric metric-${color}"><div class="metric-label">${label}</div><div class="metric-value">${value}</div><div class="metric-trend">${trend}</div></div>`;
const toolbar=(extras='')=>`<div class="toolbar"><input class="input search" placeholder="输入关键字搜索"/><select class="select"><option>全部状态</option><option>正常</option><option>异常</option></select><select class="select"><option>全部地区</option><option>山东</option><option>河北</option><option>江苏</option><option>浙江</option></select>${extras}<span class="toolbar-spacer"></span>${button('重置')}${button('查询','primary')}</div>`;

function formatCell(v,header,options,ri){
  const text=String(v);
  if(options.linkFirst && header===options.linkFirst) return `<span class="link" data-drawer="${options.drawer||'default'}" data-index="${ri}">${text}</span>`;
  if(/状态|认证|结果|拨号/.test(header)){
    let cls='gray';
    if(/正常|在线|启用|成功|已认证|已开通|已完成|已恢复/.test(text)) cls='success';
    else if(/异常|待|处理中|已确认/.test(text)) cls='warning';
    else if(/离线|失败|冻结|停用|不可用|未恢复|严重/.test(text)) cls='danger';
    return `<span class="tag ${cls}">${text}</span>`;
  }
  if(header==='类型'||header==='业务类型'||header==='产品类型') return `<span class="tag info">${text}</span>`;
  return text;
}
function table(headers,rows,options={}){return `<div class="card table-wrap"><table><thead><tr>${headers.map(h=>`<th>${h}</th>`).join('')}</tr></thead><tbody>${rows.map((r,ri)=>`<tr>${r.map((c,ci)=>`<td>${formatCell(c,headers[ci],options,ri)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`}
function tabs(id,names){return `<div class="tabs" id="${id}">${names.map((n,i)=>`<div class="tab ${i===0?'active':''}" data-tab="${i}">${n}</div>`).join('')}</div>`}
function kv(items){return `<div class="kv-grid">${items.map(x=>`<div class="kv"><div class="kv-label">${x[0]}</div><div class="kv-value">${x[1]}</div></div>`).join('')}</div>`}
function health(name,val,cls='blue'){return `<div class="health-row"><span>${name}</span><div class="progress ${cls}"><i style="width:${val}%"></i></div><span class="health-value">${val}%</span></div>`}
function flow(names){return `<div class="card pad"><div class="flow">${names.map((n,i)=>`${i?'<div class="flow-arrow">→</div>':''}<div class="flow-step"><strong>${n}</strong><span>${['定义业务能力','配置地区、运营商、优先级','统一承载自有与外部IP','按需限定底层资源','根据产品类型执行策略','生成实际服务实例'][i]||''}</span></div>`).join('')}</div></div>`}
function alarmList(){return mock.alarms.slice(0,3).map(a=>`<div class="alert-item"><i class="alert-dot ${a[1]==='严重'?'red':'orange'}"></i><div class="alert-body"><div class="alert-title">${a[3]} · ${a[2]}</div><div class="alert-meta">${a[4]} · ${a[6]}</div></div><span class="tag ${a[5]==='未恢复'?'danger':'warning'}">${a[5]}</span></div>`).join('')}

function dashboard(){return `${pageHead('首页工作台','快速掌握业务、资源与告警运行态势',button('刷新数据')+button('创建服务','primary'))}
<div class="grid cols-4">${metric('有效客户','218','较昨日 <strong class="up">+6</strong>','blue')}${metric('有效服务','642','即将到期 <strong>18</strong>','green')}${metric('可用IP','18,624','去重率 <strong>92.6%</strong>','brand')}${metric('未恢复告警','7','严重 <strong class="down">1</strong>','red')}</div>
<div class="grid cols-2" style="margin-top:14px"><div class="card pad"><div class="card-title">资源健康度 <small>最近5分钟</small></div><div class="health-list">${health('CentOS在线率',75,'orange')}${health('ROS在线率',91,'blue')}${health('家宽/线路在线率',94,'green')}${health('IP可用率',96,'blue')}${health('外部供应商接口',88,'orange')}</div></div><div class="card pad"><div class="card-title">业务量概览 <small>今日</small></div><div class="grid cols-2">${metric('IP提取量','126,840','成功率 99.2%','blue')}${metric('代理请求量','4.82M','平均延时 41ms','green')}${metric('去重IP量','12,306','覆盖 17 个地区','brand')}${metric('新增服务','23','新购 16 / 续费 7','blue')}</div></div></div>
<div class="grid cols-2" style="margin-top:14px"><div class="card pad"><div class="card-title">当前告警 <span class="link" data-nav="alarms">查看全部</span></div>${alarmList()}</div><div class="card pad"><div class="card-title">即将到期服务 <span class="link" data-nav="orders">查看全部</span></div>${table(['客户','产品','服务编号','到期时间','状态'],[['威海云启科技','高效短效代理','SVC-100328','2026-09-05','正常'],['陈明','高效短效代理','SVC-100188','2026-09-02','正常'],['青岛星途数据','稳定长效代理','SVC-100233','2026-09-08','正常']])}</div></div>`}
function customers(){return `${pageHead('客户管理','维护客户档案、认证、账号、服务及认证信息',button('导出')+button('+ 新增客户','primary'))}${toolbar('<select class="select"><option>全部认证状态</option><option>已认证</option><option>待审核</option></select>')}${table(['客户编号','客户名称','类型','联系人','认证状态','客户状态','有效服务','业务类型','最近到期','负责人'],mock.customers,{linkFirst:'客户编号',drawer:'customer'})}`}
function resourceOverview(){return `<div class="grid cols-2"><div><div class="section-title">资源层级与容量</div>${kv([['CentOS节点','4'],['ROS实例','19'],['家宽/线路','2,846'],['当前IP','20,118'],['去重IP','18,624'],['资源池','12'],['独享占用','386'],['外部IP占比','16.4%']])}</div><div><div class="section-title">资源同步状态</div>${health('基础资源同步成功率',99,'green')}${health('IP检测通过率',96,'blue')}${health('外部资源有效率',88,'orange')}<div class="note" style="margin-top:18px">基础资源列表原则上不提供新增、编辑、删除底层对象的操作，仅支持同步、查看、筛选、关联和监控跳转。</div></div></div>`}
function resources(){return `${pageHead('资源管理','基础资源由接口同步，平台负责列表展示、组织、监控与业务使用',button('手工同步')+button('+ 新建资源池','primary'))}<div class="note brand">资源层级：CentOS → ROS → 家宽/线路 → IP资源 → 统一IP资源池。短效、长效、VPN、隧道属于产品能力，不在资源层建立独立资源。</div><div class="grid cols-4">${metric('CentOS','4','在线 3 / 离线 1','blue')}${metric('ROS','19','在线 18 / 异常 1','green')}${metric('家宽/线路','2,846','在线率 94.3%','brand')}${metric('IP资源','20,118','可用 18,624','blue')}</div>${tabs('resource-tabs',['资源概览','CentOS列表','ROS列表','家宽/线路','IP资源','IP资源池','外部供应商'])}<div id="resource-tabs-panel" class="tab-panel">${resourceOverview()}</div>`}
function products(){return `${pageHead('产品管理','五类产品共享统一资源体系，通过产品资源策略形成不同服务能力',button('套餐管理')+button('+ 新增产品','primary'))}<div class="note">五类产品：短效IP、长效IP、独享IP、VPN、隧道。资源来源统一通过资源池和资源策略关联。</div>${toolbar('<select class="select"><option>全部产品类型</option><option>短效IP</option><option>长效IP</option><option>独享IP</option><option>VPN</option><option>隧道</option></select>')}${table(['产品编号','产品名称','产品类型','接入方式','协议','认证方式','覆盖区域','状态','套餐数'],mock.products,{linkFirst:'产品编号',drawer:'product'})}<div class="section-title">产品资源策略示意</div>${flow(['产品/套餐','资源策略','资源池','ROS/线路范围','IP保持/切换','形成客户服务'])}`}
function ordersTable(){return table(['订单编号','客户','订单类型','产品','套餐','订单金额','状态','创建时间'],mock.orders,{linkFirst:'订单编号',drawer:'order'})}
function servicesTable(){return table(['服务编号','客户','产品','套餐','业务类型','状态','到期时间','资源摘要','认证','额度使用'],mock.services,{linkFirst:'服务编号',drawer:'service'})}
function orders(){return `${pageHead('订单与服务管理','订单记录购买行为，服务实例记录客户实际可使用的代理服务',button('服务开通')+button('+ 创建订单','primary'))}<div class="grid cols-4">${metric('待确认订单','8','今日新增 5','brand')}${metric('待开通','12','资源校验中 3','blue')}${metric('正常服务','642','较昨日 +4','green')}${metric('即将到期','18','未来7天','red')}</div>${tabs('order-tabs',['订单管理','服务实例','服务变更','到期与资源释放'])}<div id="order-tabs-panel" class="tab-panel">${ordersTable()}</div><div class="section-title">服务开通流程</div>${flow(['订单/直接开通','读取产品套餐','应用资源策略','选择/锁定资源','生成认证信息','服务启用'])}`}
function spark(vals,orange=false){let max=Math.max(...vals),min=Math.min(...vals);return `<div style="height:160px;display:flex;align-items:flex-end;gap:8px;padding-top:16px">${vals.map((v,i)=>`<div title="${v}" style="flex:1;height:${35+(v-min)/(max-min||1)*100}px;background:${orange?'linear-gradient(#ffad66,#ff6a00)':'linear-gradient(#78b2ff,#1677ff)'};border-radius:4px 4px 0 0;opacity:${.62+i*.045}"></div>`).join('')}</div><div style="display:flex;justify-content:space-between;color:var(--muted);font-size:11px"><span>12:00</span><span>13:00</span><span>14:00</span></div>`}
function monitor(){return `${pageHead('监控中心','从 CentOS、ROS、线路、IP 到客户服务的统一运行监控',button('刷新监控')+button('监控配置','primary'))}<div class="grid cols-4">${metric('在线CentOS','3/4','1台离线','red')}${metric('在线ROS','18/19','1个异常','brand')}${metric('线路在线率','94.3%','异常 163 条','blue')}${metric('服务成功率','99.1%','平均 41ms','green')}</div>${tabs('monitor-tabs',['CentOS','ROS','家宽/线路','IP资源','外部资源','服务运行'])}<div id="monitor-tabs-panel" class="tab-panel">${table(['ROS','所属CentOS','地区','运营商','线路总数','在线','异常','状态','当前IP','最后通信'],mock.ros,{linkFirst:'ROS',drawer:'ros'})}</div><div class="section-title">最近1小时质量趋势</div><div class="grid cols-2"><div class="card pad"><div class="card-title">线路在线率</div>${spark([92,93,95,94,96,95,94,94.3])}</div><div class="card pad"><div class="card-title">平均代理延时</div>${spark([46,44,42,45,43,41,40,41],true)}</div></div>`}
function logs(){return `${pageHead('日志中心','用于业务追溯、故障定位、安全审计和数据统计',button('导出当前结果'))}${tabs('log-tabs',['IP提取日志','使用日志','API日志','ROS运行日志','线路拨号日志','IP变化日志','外部资源获取日志','操作日志'])}<div id="log-tabs-panel" class="tab-panel">${toolbar()}${table(['时间','客户','服务编号','类型','出口IP','结果','耗时'],mock.logs,{linkFirst:'服务编号',drawer:'service'})}</div>`}
function alarms(){return `${pageHead('告警中心','监控异常触发告警，并形成通知、处理、恢复和关闭闭环',button('告警规则')+button('钉钉机器人','primary'))}<div class="grid cols-4">${metric('未恢复','7','严重 1','red')}${metric('处理中','3','平均处理 18min','brand')}${metric('今日恢复','26','恢复率 79%','green')}${metric('钉钉发送成功率','99.8%','失败 1 条','blue')}</div>${toolbar('<select class="select"><option>全部级别</option><option>严重</option><option>重要</option><option>一般</option></select>')}${table(['告警编号','级别','告警对象','告警内容','当前值','状态','告警时间'],mock.alarms,{linkFirst:'告警编号',drawer:'alarm'})}<div class="section-title">告警处理闭环</div>${flow(['监控采集','阈值判断','生成告警','钉钉/平台通知','运维确认处理','指标恢复关闭'])}`}
function statistics(){return `${pageHead('数据统计','按资源、IP、客户、产品与外部供应商等维度进行统计分析',button('导出报表'))}<div class="toolbar"><select class="select"><option>今日</option><option>昨日</option><option>近7天</option><option>近30天</option></select><select class="select"><option>按小时</option><option>按天</option></select><span class="toolbar-spacer"></span>${button('刷新','primary')}</div>${tabs('stats-tabs',['基础资源统计','IP资源统计','客户使用统计','产品与服务统计','外部资源统计'])}<div id="stats-tabs-panel" class="tab-panel"><div class="grid cols-4">${metric('IP总量','20,118','自有 83.6%','blue')}${metric('去重IP','18,624','重复率 7.4%','green')}${metric('今日提取','126,840','成功率 99.2%','brand')}${metric('代理请求','4.82M','失败 43,380','blue')}</div><div class="grid cols-2" style="margin-top:14px"><div class="card pad"><div class="card-title">按产品使用量</div>${health('短效IP',88,'blue')}${health('隧道',72,'green')}${health('VPN',53,'orange')}${health('长效IP',36,'blue')}${health('独享IP',28,'orange')}</div><div class="card pad"><div class="card-title">外部资源供应质量</div>${health('供应商A',96,'green')}${health('供应商B',82,'orange')}${health('供应商C',91,'blue')}<div class="note" style="margin-top:14px">外部资源仅作为资源来源，统一进入资源池，不作为独立产品统计。</div></div></div></div>`}
function system(){return `${pageHead('系统管理','后台用户、角色权限、系统参数、登录日志与操作审计',button('+ 新增用户','primary'))}${tabs('system-tabs',['用户管理','角色管理','参数配置','登录日志','操作审计'])}<div id="system-tabs-panel" class="tab-panel">${sysUsers()}<div class="note brand">敏感权限建议单独控制：Token明文查看/重置、VPN私钥、供应商API Key、独享资源分配、服务终止、角色权限变更等。</div></div>`}

function resourceCentos(){return toolbar()+table(['CentOS编号','名称','管理地址','地区','在线状态','ROS数量','在线ROS','异常ROS','CPU','内存','最后同步'],mock.centos,{linkFirst:'CentOS编号',drawer:'centos'})}
function resourceRos(){return toolbar()+table(['ROS编号','所属CentOS','地区','运营商','线路总数','在线线路','异常线路','状态','当前IP','最后通信'],mock.ros,{linkFirst:'ROS编号',drawer:'ros'})}
function resourceLines(){return toolbar()+table(['线路编号','所属ROS','地区','运营商','当前公网IP','在线状态','拨号状态','延时','可用率','占用状态'],mock.lines,{linkFirst:'线路编号',drawer:'line'})}
function resourceIps(){return toolbar()+table(['IP地址','来源类型','来源ROS','来源线路/供应商','地区','运营商','可用状态','延时','是否重复','资源池','占用状态'],mock.ips,{linkFirst:'IP地址',drawer:'ip'})}
function resourcePools(){return toolbar()+table(['资源池编号','资源池名称','地区','运营商','来源','IP总量','去重IP','可用IP','状态'],[['POOL-001','山东联通池','山东','联通','自有+外部','4,282','4,031','3,966','启用'],['POOL-002','河北电信池','河北','电信','自有','3,912','3,614','3,420','启用'],['POOL-003','华东综合池','华东','多运营商','自有+外部','5,880','5,324','5,146','启用'],['POOL-004','华北综合池','华北','多运营商','外部','1,604','1,493','1,382','异常']],{linkFirst:'资源池编号',drawer:'pool'})}
function resourceSuppliers(){return toolbar()+table(['供应商编号','供应商名称','覆盖地区','接口状态','最近成功','连续失败','当前资源量','状态'],[['SUP-01','供应商A','全国','正常','13:56','0','1,246','启用'],['SUP-02','供应商B','华东','异常','13:48','3','818','启用'],['SUP-03','供应商C','华北','正常','13:55','0','732','启用']],{linkFirst:'供应商编号',drawer:'supplier'})}
function serviceChange(){return toolbar()+table(['变更编号','服务编号','客户','变更类型','申请时间','生效时间','处理结果','操作人'],[['CHG-1021','SVC-100274','青岛星途数据','续费','2026-08-27 16:44','2026-09-11 00:00','成功','王强'],['CHG-1019','SVC-100211','北京极数网络','暂停','2026-08-27 11:36','2026-08-27 11:38','成功','孙浩'],['CHG-1018','SVC-100328','威海云启科技','额度扩容','2026-08-26 15:12','2026-08-26 15:13','成功','王强']])}
function serviceExpiry(){return table(['服务编号','客户','产品','到期时间','凭证状态','资源摘要','回收状态'],[['SVC-100188','陈明','高效短效代理','2026-09-02','有效','山东联通池','未到期'],['SVC-100328','威海云启科技','高效短效代理','2026-09-05','有效','山东联通池','未到期'],['SVC-100176','某历史客户','独享IP代理','2026-08-27','已失效','独享IP×20','已回收']])}
function logTable(type){return toolbar()+table(['时间','客户','服务编号','类型','出口IP','结果','耗时'],mock.logs.map(x=>[...x.slice(0,3),type,...x.slice(4)]),{linkFirst:'服务编号',drawer:'service'})}
function logApi(){return toolbar()+table(['请求时间','客户','Token标识','接口','客户端IP','响应码','结果','耗时'],[['13:56:22','威海云启科技','tk_***82ab','/api/ip/extract','120.224.**.18','200','成功','26ms'],['13:54:41','陈明','tk_***1d80','/api/whitelist/list','27.198.**.63','200','成功','18ms'],['13:50:05','未知','—','/api/token','60.212.**.20','401','失败','12ms']])}
function logRos(){return table(['时间','CentOS','ROS','事件类型','事件内容','结果'],[['13:41:03','CT-04','ROS-ZJ-04','通信异常','连续3次心跳失败','失败'],['13:39:18','CT-02','ROS-HB-03','线路异常','异常线路比例超过10%','异常'],['13:30:02','CT-01','ROS-SD-01','状态同步','同步168条线路','成功']])}
function logDial(){return table(['时间','ROS','线路','拨号前IP','拨号后IP','结果','耗时'],[['13:52:11','ROS-SD-01','LN-SD-01001','112.254.31.12','112.254.31.18','成功','4.1s'],['13:48:20','ROS-HB-03','LN-HB-03018','110.249.22.90','—','失败','8.0s']])}
function logIpChange(){return table(['变化时间','ROS','线路','原IP','新IP','原因','关联服务'],[['13:52:15','ROS-SD-01','LN-SD-01001','112.254.31.12','112.254.31.18','自动重拨','SVC-100328'],['13:46:30','ROS-JS-01','LN-JS-01008','122.97.11.60','122.97.11.64','线路恢复','SVC-100233']])}
function logExternal(){return table(['调用时间','供应商','接口','返回资源','有效资源','入池','结果','耗时'],[['13:55:12','供应商A','/resource/fetch','100','96','92','成功','620ms'],['13:49:31','供应商B','/proxy/list','100','82','78','成功','1.8s'],['13:45:06','供应商B','/proxy/list','—','—','—','失败','超时']])}
function logOperation(){return table(['时间','操作人','模块','操作类型','对象','结果','来源IP'],[['13:44:02','王强','客户服务','重置Token','SVC-100328','成功','10.1.3.21'],['13:40:55','张磊','告警中心','确认告警','ALM-8290','成功','10.1.2.33'],['13:33:18','admin','资源管理','编辑资源池','POOL-004','成功','10.1.2.18']])}
function statsBase(){return `<div class="grid cols-4">${metric('CentOS','4','在线3','blue')}${metric('ROS','19','在线18','green')}${metric('家宽/线路','2,846','在线率94.3%','brand')}${metric('IP总量','20,118','可用18,624','blue')}</div>`}
function statsIp(){return statsBase()+`<div class="section-title">资源分布</div>${table(['地区','IP总量','去重IP','可用IP','重复率'],[['山东','6,804','6,442','6,210','5.3%'],['河北','4,812','4,310','4,021','10.4%'],['江苏','3,996','3,712','3,560','7.1%'],['浙江','2,884','2,560','2,481','11.2%']])}`}
function statsCustomer(){return table(['客户','提取量','使用量','去重IP','请求数','成功率','流量','额度使用率'],[['威海云启科技','42,880','1.28M','3,806','1.28M','99.4%','3.8TB','67%'],['青岛星途数据','18,220','830K','2,614','830K','99.1%','2.1TB','52%'],['北京极数网络','31,900','620K','1,998','620K','97.8%','1.6TB','80%']])}
function statsProduct(){return table(['产品','客户数','有效服务','使用量','去重IP','成功率'],[['高效短效代理','86','146','2.02M','7,802','99.2%'],['高速隧道代理','52','118','1.44M','4,106','99.0%'],['ROSVPN','41','92','820K','2,711','98.7%'],['稳定长效代理','24','54','350K','1,049','99.6%']])}
function statsExternal(){return table(['供应商','调用次数','成功率','平均响应','获取IP','有效IP','实际入池'],[['供应商A','284','99.6%','620ms','28,400','27,156','24,980'],['供应商B','261','94.8%','1.8s','25,800','21,320','18,202'],['供应商C','176','98.3%','740ms','17,600','16,146','14,889']])}
function sysUsers(){return table(['账号','姓名','角色','部门','状态','最后登录','登录IP'],[['admin','孙凯','超级管理员','产品中心','正常','2026-08-28 13:42','10.1.2.18'],['ops_zhang','张磊','运维管理员','运维中心','正常','2026-08-28 13:31','10.1.2.33'],['service_wang','王强','客户运营','客户中心','正常','2026-08-28 12:56','10.1.3.21'],['audit_li','李敏','审计查看','综合管理','冻结','2026-08-26 18:08','10.1.8.9']],{linkFirst:'账号',drawer:'user'})}
function sysRoles(){return table(['角色','编码','用户数','菜单权限','数据权限','状态'],[['超级管理员','admin','1','全部','全部','启用'],['运维管理员','ops','8','资源/监控/日志/告警','全部资源','启用'],['客户运营','service','12','客户/订单/服务/统计','负责客户','启用'],['审计查看','audit','3','日志/审计','只读全部','启用']])}
function sysParams(){return table(['分组','参数名称','参数编码','参数值','敏感','状态','更新时间'],[['告警','默认离线阈值','alarm.offline.threshold','3次','否','启用','2026-08-20'],['服务','到期提醒天数','service.expire.days','7','否','启用','2026-08-18'],['认证','Token有效期','auth.token.ttl','24h','是','启用','2026-08-15']])}
function sysLogin(){return table(['时间','账号','姓名','登录IP','结果','客户端'],[['13:42:21','admin','孙凯','10.1.2.18','成功','Chrome / Windows'],['13:31:02','ops_zhang','张磊','10.1.2.33','成功','Edge / Windows'],['12:58:44','unknown','—','61.179.**.2','失败','Chrome / Windows']])}
function sysAudit(){return logOperation()}

const pages={dashboard,customers,resources,products,orders,monitor,logs,alarms,statistics,system};
let current='dashboard';
function shell(){
  $('#app').innerHTML=`<div class="shell"><aside class="sidebar"><div class="brand"><div class="brand-mark">IP</div><div class="brand-title">IP代理管理平台<span class="brand-sub">Prototype Console</span></div></div><div class="nav-scroll">${menu.map(g=>`<div class="nav-group"><div class="nav-group-title">${g.group}</div>${g.items.map(i=>`<div class="nav-item" data-route="${i[0]}"><span class="nav-icon">${icons[i[2]]}</span><span>${i[1]}</span>${i[3]?`<span class="nav-badge">${i[3]}</span>`:''}</div>`).join('')}</div>`).join('')}</div></aside><main class="main"><div class="topbar"><div class="top-left"><span class="console-chip">ALIYUN STYLE</span><span class="top-title" id="crumb">首页工作台</span></div><div class="top-right"><span>华东区</span><span class="env">原型演示环境</span><span>帮助</span><span>消息</span><span class="avatar">SK</span></div></div><section id="content" class="content"></section></main></div><div class="drawer-mask" id="drawerMask"><div class="drawer"><div class="drawer-head"><div class="drawer-title" id="drawerTitle">详情</div><button class="drawer-close" id="drawerClose">×</button></div><div class="drawer-body" id="drawerBody"></div></div></div>`;
  bindGlobal(); render();
}
function bindGlobal(){ $$('.nav-item').forEach(el=>el.addEventListener('click',()=>navigate(el.dataset.route))); $('#drawerClose').addEventListener('click',closeDrawer); $('#drawerMask').addEventListener('click',e=>{if(e.target.id==='drawerMask')closeDrawer()}); window.addEventListener('hashchange',()=>{const r=location.hash.replace('#/',''); if(pages[r]){current=r; render();}}); }
function navigate(r){location.hash=`#/${r}`; if(location.hash===`#/${r}`){current=r;render();}}
function render(){const route=location.hash.replace('#/','')||current; current=pages[route]?route:'dashboard'; $('#content').innerHTML=pages[current](); $$('.nav-item').forEach(el=>el.classList.toggle('active',el.dataset.route===current)); $('#crumb').textContent=menu.flatMap(g=>g.items).find(i=>i[0]===current)?.[1]||''; bindContent(); window.scrollTo({top:0,behavior:'smooth'});}
function bindContent(){
  $$('[data-nav]').forEach(el=>el.addEventListener('click',()=>navigate(el.dataset.nav)));
  $$('[data-drawer]').forEach(el=>el.addEventListener('click',()=>openDrawer(el.dataset.drawer,+el.dataset.index)));
  bindTabs('resource-tabs','resource-tabs-panel',[resourceOverview(),resourceCentos(),resourceRos(),resourceLines(),resourceIps(),resourcePools(),resourceSuppliers()]);
  bindTabs('order-tabs','order-tabs-panel',[ordersTable(),servicesTable(),serviceChange(),serviceExpiry()]);
  bindTabs('monitor-tabs','monitor-tabs-panel',[resourceCentos(),resourceRos(),resourceLines(),resourceIps(),resourceSuppliers(),servicesTable()]);
  bindTabs('log-tabs','log-tabs-panel',[logTable('提取IP'),logTable('代理请求'),logApi(),logRos(),logDial(),logIpChange(),logExternal(),logOperation()]);
  bindTabs('stats-tabs','stats-tabs-panel',[statsBase(),statsIp(),statsCustomer(),statsProduct(),statsExternal()]);
  bindTabs('system-tabs','system-tabs-panel',[sysUsers(),sysRoles(),sysParams(),sysLogin(),sysAudit()]);
}
function bindTabs(tabId,panelId,contents){const root=$(`#${tabId}`), panel=$(`#${panelId}`); if(!root||!panel)return; $$('.tab',root).forEach((el,i)=>el.addEventListener('click',()=>{ $$('.tab',root).forEach(x=>x.classList.remove('active')); el.classList.add('active'); panel.innerHTML=contents[i]; bindContent(); }));}

function openDrawer(type,index){
  let title='详情',body='';
  if(type==='customer'){const r=mock.customers[index];title=`客户详情 · ${r[1]}`;body=customerDrawer(r)}
  else if(type==='service'){const r=mock.services[index]||mock.services[0];title=`服务实例 · ${r[0]}`;body=serviceDrawer(r)}
  else if(type==='product'){const r=mock.products[index];title=`产品详情 · ${r[1]}`;body=productDrawer(r)}
  else if(type==='order'){const r=mock.orders[index];title=`订单详情 · ${r[0]}`;body=orderDrawer(r)}
  else if(type==='alarm'){const r=mock.alarms[index];title=`告警详情 · ${r[0]}`;body=alarmDrawer(r)}
  else {title='资源详情';body=genericDrawer(type,index)}
  $('#drawerTitle').textContent=title; $('#drawerBody').innerHTML=body; $('#drawerMask').classList.add('open');
}
function closeDrawer(){ $('#drawerMask').classList.remove('open'); }
function customerDrawer(r){return `${kv([['客户编号',r[0]],['客户类型',r[2]],['认证状态',r[4]],['客户状态',r[5]],['联系人',r[3]],['负责人',r[9]],['业务类型',r[7]],['最近到期',r[8]]])}<div class="section-title">已开通服务</div>${servicesTable()}<div class="section-title">服务认证信息</div>${table(['服务编号','认证类型','Token/账号','白名单','状态','最后使用'],[['SVC-100328','Token','tk_***82ab','4/10','有效','13:56'],['SVC-100329','账号密码','tunnel_whqy_01','2/5','有效','13:53']])}`}
function serviceDrawer(r){return `${kv([['服务编号',r[0]],['客户',r[1]],['产品',r[2]],['套餐',r[3]],['业务类型',r[4]],['状态',r[5]],['到期时间',r[6]],['额度使用',r[9]]])}<div class="section-title">资源配置</div>${kv([['资源池',r[7]],['资源策略','华东优先策略 V3'],['ROS/线路','按策略动态选择'],['认证状态',r[8]]])}<div class="section-title">最近使用日志</div>${logTable('代理请求')}`}
function productDrawer(r){return `${kv([['产品编号',r[0]],['产品类型',r[2]],['接入方式',r[3]],['协议',r[4]],['认证方式',r[5]],['覆盖区域',r[6]],['状态',r[7]],['套餐数',r[8]]])}<div class="section-title">资源策略</div>${flow(['产品','资源策略','资源池','ROS/线路','IP策略','服务实例'])}<div class="section-title">套餐</div>${table(['套餐名称','周期','额度/数量','并发','价格','状态'],[['体验版','7天','5,000次','—','¥199','启用'],['标准版','30天','100,000次','—','¥1,280','启用'],['企业版','30天','500,000次','—','¥3,600','启用']])}`}
function orderDrawer(r){return `${kv([['订单编号',r[0]],['客户',r[1]],['订单类型',r[2]],['产品',r[3]],['套餐',r[4]],['订单金额',r[5]],['订单状态',r[6]],['创建时间',r[7]]])}<div class="section-title">开通进度</div>${flow(['订单已创建','订单确认','资源校验','资源锁定','凭证生成','服务启用'])}`}
function alarmDrawer(r){return `${kv([['告警编号',r[0]],['级别',r[1]],['告警对象',r[2]],['告警内容',r[3]],['当前值',r[4]],['状态',r[5]],['告警时间',r[6]],['处理人','张磊']])}<div class="section-title">处理记录</div>${table(['时间','动作','处理人','备注'],[['13:41','告警触发','系统','已发送钉钉通知'],['13:43','确认告警','张磊','开始检查CentOS与ROS通信'],['13:48','标记处理中','张磊','等待上游网络恢复']])}`}
function genericDrawer(type,index){let source=type==='centos'?mock.centos:type==='ros'?mock.ros:type==='line'?mock.lines:type==='ip'?mock.ips:null;let r=source?.[index]||['—'];return `<div class="note">原型演示：该详情页展示对象基础信息、关联资源、监控状态、日志和告警入口。</div>${kv(r.map((v,i)=>[`字段 ${i+1}`,v]))}<div class="section-title">最近运行状态</div>${health('在线/可用率',94,'green')}${health('质量评分',88,'blue')}`}

shell();
