(()=>{
  const D=window.V12;if(!D)return;
  const prefixByCentos={
    'CT-001':{bCount:7,cCount:46},
    'CT-002':{bCount:11,cCount:72},
    'CT-004':{bCount:0,cCount:0}
  };
  D.centos=(D.centos||[]).map(x=>{
    const {rosCount,...rest}=x;
    return {...rest,...(prefixByCentos[x.id]||{bCount:0,cCount:0})};
  });
  D.ros=(D.ros||[]).map(x=>{
    const {centos,ha,replacement,switch:sw,auto,bCount,cCount,...rest}=x;
    return rest;
  });
  D.lines=(D.lines||[]).map(x=>{
    const {customer,...rest}=x;
    return rest;
  });
  D.monitorCentos=(D.monitorCentos||[]).map(x=>{
    const p=prefixByCentos[x.object]||{bCount:0,cCount:0};
    return {...x,b:p.bCount,c:p.cCount};
  });
  D.monitorRos=(D.monitorRos||[]).map(x=>{
    const {b,c,...rest}=x;
    return rest;
  });
})();
