'use strict';
(() => {
  const initial=location.hash.slice(1).split(',').map(Number);
  const restored=initial.length===3 && initial.every(Number.isFinite) && initial[2]>=11 && initial[2]<=19;
  const map=L.map('map',{minZoom:11,maxZoom:19,zoomControl:true,preferCanvas:true})
    .setView(restored ? [initial[0],initial[1]] : [-17.7833,-63.1821],restored ? initial[2] : 13);
  const tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
    maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  }).addTo(map);
  tiles.on('tileerror',()=>{if(window.ZonaMap)window.ZonaMap.tilesUnavailable();});
  const layer=L.layerGroup().addTo(map);
  let enabled=true;
  const colors={BAJO:'#15805f',MEDIO:'#b57408',ALTO:'#b6293f'};
  const names={BAJO:'Bajo',MEDIO:'Medio',ALTO:'Alto'};
  let timer;
  function request(){
    const b=map.getBounds(),c=map.getCenter();
    if(window.ZonaMap)window.ZonaMap.viewport(JSON.stringify({sur:b.getSouth(),oeste:b.getWest(),norte:b.getNorth(),este:b.getEast(),lat:c.lat,lng:c.lng,zoom:map.getZoom()}));
  }
  map.on('moveend',()=>{clearTimeout(timer);timer=setTimeout(request,350);});
  window.renderRisk=function(summary){
    map.closePopup();
    layer.clearLayers();
    for(const zone of summary.zonas || []){
      if(!colors[zone.nivel] || !Array.isArray(zone.poligono) || zone.poligono.length<4)continue;
      const ring=zone.poligono.map(p=>[Number(p[1]),Number(p[0])]);
      if(ring.some(p=>!p.every(Number.isFinite)))continue;
      const popup=document.createElement('div');
      const title=document.createElement('strong');title.textContent='Riesgo '+names[zone.nivel].toLowerCase();popup.appendChild(title);
      const detail=document.createElement('div');detail.textContent=zone.cantidad+' incidentes confirmados en los últimos '+summary.dias+' días.';popup.appendChild(detail);
      L.polygon(ring,{color:colors[zone.nivel],fillColor:colors[zone.nivel],weight:1.5,fillOpacity:0.42}).bindPopup(popup).addTo(layer);
    }
    if(!enabled && map.hasLayer(layer))map.removeLayer(layer);
  };
  window.showRisk=function(show){enabled=show;if(show)layer.addTo(map);else{map.closePopup();map.removeLayer(layer);}};
  window.centerSantaCruz=function(){map.setView([-17.7833,-63.1821],13);};
  window.refreshRisk=request;
  map.whenReady(()=>setTimeout(()=>{map.invalidateSize();request();},100));
})();
