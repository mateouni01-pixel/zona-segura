'use strict';
(() => {
    const seed = document.getElementById('datos-mapa-ciudadano');
    if (!seed) return;
    const data = JSON.parse(seed.textContent);
    const map = L.map('mapa-ciudadano').setView([-17.7833, -63.1821], 13);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; OpenStreetMap contributors' }).addTo(map);
    const colors = { BAJO: '#15805f', MEDIO: '#b57408', ALTO: '#b6293f' };
    for (const zone of data.zonas) {
        const label = document.createElement('p'); label.textContent = zone.nivel + ' · ' + zone.cantidad + ' incidentes confirmados';
        L.polygon(zone.poligono.map(point => [point[1], point[0]]), { color: colors[zone.nivel], fillOpacity: .3 }).addTo(map).bindPopup(label);
    }
})();
