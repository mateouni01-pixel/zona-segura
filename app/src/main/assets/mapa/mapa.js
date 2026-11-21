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
  const districts=L.geoJSON(null,{style:{color:'#64748b',weight:1.2,dashArray:'4 4',fill:false},interactive:false}).addTo(map);
  window.showDistricts=function(data){if(data&&data.type==='FeatureCollection'){districts.clearLayers();districts.addData(data);districts.bringToBack();}};
  map.attributionControl.addAttribution('Distritos 1–12: capa municipal publicada en <a href="https://github.com/YoViajo/geodatos/blob/master/gsc/scz_distritos_municipales_planif.geojson">YoViajo/geodatos</a>');
  map.createPane('citizen');map.getPane('citizen').style.zIndex='450';
  const citizenLayer=L.layerGroup().addTo(map);
  const citizenRenderer=L.svg({pane:'citizen'});
  let citizenMarker,accuracyCircle;
  window.clearCitizenLocation=function(){
    citizenLayer.clearLayers();citizenMarker=null;accuracyCircle=null;
  };
  window.showCitizenLocation=function(lat,lng,accuracy,center){
    if(![lat,lng,accuracy].every(Number.isFinite) || lat < -90 || lat > 90 || lng < -180 || lng > 180 || accuracy < 0)return;
    const point=[lat,lng];
    if(!citizenMarker){
      accuracyCircle=L.circle(point,{renderer:citizenRenderer,pane:'citizen',radius:accuracy,color:'#2563eb',weight:1,fillColor:'#2563eb',fillOpacity:0.12,interactive:false,className:'citizen-accuracy'}).addTo(citizenLayer);
      citizenMarker=L.circleMarker(point,{renderer:citizenRenderer,pane:'citizen',radius:9,color:'#ffffff',weight:3,fillColor:'#2563eb',fillOpacity:1,className:'citizen-location'}).bindPopup('Tu ubicación').addTo(citizenLayer);
    }else{
      citizenMarker.setLatLng(point);accuracyCircle.setLatLng(point).setRadius(accuracy);
    }
    citizenMarker.bringToFront();
    if(center)map.setView(point,Math.max(map.getZoom(),15));
  };
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
