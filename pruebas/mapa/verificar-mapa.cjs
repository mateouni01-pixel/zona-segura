const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const {chromium}=require(process.argv[2] || 'playwright');
const assets=path.resolve(__dirname,'../../app/src/main/assets/mapa');
const out=path.join(__dirname,'resultados');
fs.mkdirSync(out,{recursive:true});

(async()=>{
  const browser=await chromium.launch({executablePath:process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try{
    const page=await browser.newPage({viewport:{width:390,height:640},deviceScaleFactor:1});
    const failures=[];
    page.on('pageerror',error=>failures.push(error.message));
    await page.route('**/*',async route=>{
      const url=new URL(route.request().url());
      if(url.hostname==='appassets.androidplatform.net'){
        const file=path.basename(url.pathname);
        if(!['mapa.html','mapa.js','mapa.css','leaflet.js','leaflet.css'].includes(file))return route.abort();
        const type=file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html';
        return route.fulfill({contentType:type,body:fs.readFileSync(path.join(assets,file))});
      }
      // No descargar teselas reales durante las pruebas automatizadas.
      if(url.hostname==='tile.openstreetmap.org')return route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256"><rect width="256" height="256" fill="#edf2ef"/><path d="M0 128H256M128 0V256" stroke="#d4dfd9" stroke-width="2"/></svg>'});
      return route.abort();
    });
    await page.addInitScript(()=>{window.mapRequests=[];window.tileErrors=0;window.ZonaMap={viewport(json){window.mapRequests.push(JSON.parse(json));},tilesUnavailable(){window.tileErrors++;}};});
    await page.goto('https://appassets.androidplatform.net/assets/mapa/mapa.html');
    await page.waitForFunction(()=>window.mapRequests.length>0);
    const zone=(x,y,n,nivel)=>({id:`zona-${x}-${y}`,nombre:'Área de incidentes confirmados',nivel,cantidad:n,poligono:[[x/200,y/200],[(x+1)/200,y/200],[(x+1)/200,(y+1)/200],[x/200,(y+1)/200],[x/200,y/200]]});
    const summary={dias:7,totalReportes:9,zonas:[zone(-12637,-3557,5,'ALTO'),zone(-12636,-3557,3,'MEDIO'),zone(-12637,-3555,1,'BAJO')]};
    await page.evaluate(data=>window.renderRisk(data),summary);
    await page.mouse.click(195,320);
    await page.getByText('Riesgo alto',{exact:true}).waitFor();
    assert.match(await page.locator('.leaflet-popup-content').innerText(),/5 incidentes confirmados/);
    await page.mouse.click(25,400);
    await page.evaluate(()=>window.showRisk(false));
    await page.locator('.leaflet-popup').waitFor({state:'detached'});
    await page.evaluate(()=>new Promise(requestAnimationFrame));
    await page.mouse.click(195,320);
    assert.equal(await page.locator('.leaflet-popup').count(),0,'La capa desactivada no debe responder a toques.');
    await page.evaluate(()=>window.showRisk(true));
    await page.mouse.click(195,320);
    await page.getByText('Riesgo alto',{exact:true}).waitFor();
    await page.screenshot({path:path.join(out,'mapa-zonas.png')});
    const before=await page.evaluate(()=>window.mapRequests.at(-1));
    await page.locator('.leaflet-control-zoom-in').click();
    await page.waitForFunction(old=>window.mapRequests.at(-1).zoom>old.zoom,before);
    const after=await page.evaluate(()=>window.mapRequests.at(-1));
    assert.ok(after.norte-after.sur < before.norte-before.sur,'El zoom debe reducir el área consultada.');
    await page.mouse.move(195,360);await page.mouse.down();await page.mouse.move(270,400,{steps:12});await page.mouse.up();
    await page.waitForFunction(old=>Math.abs(window.mapRequests.at(-1).lng-old.lng)>0.00001,after);
    await page.evaluate(()=>window.renderRisk({dias:7,totalReportes:0,zonas:[]}));
    await page.locator('.leaflet-popup').waitFor({state:'detached'});
    assert.equal(await page.locator('.leaflet-popup').count(),0,'El estado vacío debe quitar las zonas anteriores.');
    // La ubicación es independiente de la capa de riesgo y solo centra a petición.
    await page.evaluate(()=>window.showCitizenLocation(-17.7833,-63.1821,35,true));
    await page.waitForFunction(()=>{
      const view=window.mapRequests.at(-1);
      return view.zoom>=15 && Math.abs(view.lat+17.7833)<0.00005 && Math.abs(view.lng+63.1821)<0.00005;
    });
    const located=await page.evaluate(()=>window.mapRequests.at(-1));
    assert.ok(Math.abs(located.lat+17.7833)<0.00005 && Math.abs(located.lng+63.1821)<0.00005,'Mi ubicación debe centrar el mapa (tolerancia de redondeo a píxeles).');
    assert.equal(await page.locator('.citizen-location').count(),1);
    assert.equal(await page.locator('.citizen-accuracy').count(),1);
    await page.locator('.citizen-location').click({force:true});
    await page.getByText('Tu ubicación',{exact:true}).waitFor();
    await page.evaluate(data=>{window.renderRisk(data);window.showRisk(false);},summary);
    assert.equal(await page.locator('.citizen-location').count(),1,'Ocultar riesgo conserva mi ubicación.');
    await page.evaluate(()=>window.showCitizenLocation(-17.784,-63.183,80,false));
    assert.equal(await page.locator('.citizen-location').count(),1,'Las actualizaciones mueven el marcador existente.');
    assert.deepEqual(await page.evaluate(()=>window.mapRequests.at(-1)),located,'Actualizar posición no debe interrumpir la navegación.');
    await page.evaluate(()=>window.showCitizenLocation(200,0,10,true));
    assert.equal(await page.locator('.citizen-location').count(),1,'Una ubicación inválida no reemplaza la anterior.');
    await page.screenshot({path:path.join(out,'mapa-mi-ubicacion.png')});
    await page.evaluate(()=>window.clearCitizenLocation());
    assert.equal(await page.locator('.citizen-location').count(),0);
    assert.equal(await page.locator('.citizen-accuracy').count(),0);
    assert.deepEqual(failures,[],'El mapa no debe generar errores JavaScript.');
    assert.equal(await page.evaluate(()=>window.tileErrors),0);
    const result={renderizado:true,popup:true,capa:true,zoom:true,desplazamiento:true,estadoVacio:true,ubicacion:true,precision:true,centrado:true,ubicacionIndependienteDeRiesgo:true,limpiezaUbicacion:true,erroresJavascript:failures,teselas:'simuladas; sin peticiones a OpenStreetMap'};
    fs.writeFileSync(path.join(out,'verificacion.json'),JSON.stringify(result,null,2));
    console.log(JSON.stringify(result));
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
