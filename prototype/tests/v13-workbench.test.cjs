(function v13PrototypeTests(){
  const test=require('node:test');
  const assert=require('node:assert/strict');
  const fs=require('node:fs');
  const path=require('node:path');
  const vm=require('node:vm');
  const source=fs.readFileSync(path.join(__dirname,'..','v13-workbench.js'),'utf8');
  function boot(){
    const listeners={},content={innerHTML:''},window={};
    const document={
      addEventListener(name,fn){listeners[name]=fn;},
      getElementById(name){return name==='content'?content:null;}
    };
    const context={window,document,Date,console,state:{route:'v13.flow'},renderShell(){},renderPage(){}};
    vm.runInNewContext(source,context,{filename:'prototype/v13-workbench.js'});
    function click(action,value,route){
      const element={dataset:{v13Action:action,v13Value:value},closest(selector){
        if(selector==='tr')return {querySelector(sel){
          if(sel==='[data-v13-code]')return {dataset:{v13Code:value}};
          if(sel==='[data-v13-route]')return {dataset:{v13Route:route}};
          return null;
        }};
        return null;
      }};
      listeners.click({target:{closest(selector){return selector==='[data-v13-action]'?element:null;}}});
    }
    function change(field,value){
      const element={dataset:{v13Field:field},value};
      listeners.change({target:{closest(selector){return selector==='[data-v13-field]'?element:null;}}});
    }
    return {window,document,content,context,click,change};
  }
  test('V1.3 uses the exact 36 / 18 / 31 current document counts',()=>{
    const h=boot();assert.equal(h.window.V13.pages.length,36);
    assert.equal(h.window.V13.cases.length,18);assert.equal(h.window.V13.interfaces.length,31);
    assert.equal(h.window.V13.interfaces.find(x=>x.id==='F02').name,'查询与执行域名解析变更');
    assert.equal(h.window.V13.interfaces.find(x=>x.id==='F04').name,'执行已授权短效IP提取');
  });
  test('unpaid orders are blocked before provisioning and leave no mock task',()=>{
    const h=boot();h.change('order','1');h.click('next');
    const v=h.window.V13.inspectDemo();assert.equal(v.step,0);assert.equal(v.task,null);
    assert.equal(v.check.pass,false);assert.match(v.check.fail.join(' '),/付款/);
  });
  test('exclusive IPv4 conflict prevents submission until candidate changes',()=>{
    const h=boot();h.change('order','2');h.click('next');h.click('next');
    h.click('next');assert.equal(h.window.V13.inspectDemo().step,2);
    h.change('ip','203.0.113.82');h.click('next');assert.equal(h.window.V13.inspectDemo().step,3);
    h.click('submit');const v=h.window.V13.inspectDemo();
    assert.equal(v.step,4);assert.match(v.task.id,/LOCAL-V13-/);
    assert.match(h.window.V13.renderPage('v13.flow'),/真实网络可用性/);
    assert.match(h.window.V13.renderPage('v13.flow'),/UNKNOWN/);
  });
  test('valid paid order yields pending remote verification, never success',()=>{
    const h=boot();h.click('next');h.click('next');h.click('next');h.click('submit');
    const html=h.window.V13.renderPage('v13.flow');
    assert.equal(h.window.V13.inspectDemo().step,4);
    assert.match(html,/待远端回查/);assert.match(html,/未签署/);
    assert.doesNotMatch(html,/真实网络已验证|实网交付成功/);
  });
  test('external delivery needs coverage and catalog still says candidate',()=>{
    const h=boot();h.change('order','3');assert.equal(h.window.V13.inspectDemo().check.pass,false);
    h.change('upstream','yes');assert.equal(h.window.V13.inspectDemo().check.pass,true);
    const c=h.window.V13.renderPage('v13.interfaces');
    assert.equal((c.match(/data-v13-action="interface-detail"/g)||[]).length,31);
    assert.match(c,/候选接口/);assert.match(c,/F04/);
  });
  test('acceptance navigator switches between 36 pages and 18 scenarios',()=>{
    const h=boot();h.context.state.route='v13.matrix';
    let html=h.window.V13.renderPage('v13.matrix');
    assert.equal((html.match(/data-v13-action="to-route"/g)||[]).length,36);
    h.click('review-cases');
    html=h.window.V13.renderPage('v13.matrix');
    assert.match(html,/人工主备切换成功/);
    assert.match(html,/待内部人工|待外部联调/);
  });
})();
