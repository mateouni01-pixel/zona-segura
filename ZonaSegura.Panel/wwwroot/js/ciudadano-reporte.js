'use strict';
(() => {
    const form = document.getElementById('crear-reporte-ciudadano');
    if (!form) return;
    const button = document.getElementById('obtener-ubicacion');
    const status = document.getElementById('ubicacion-estado');
    let captured = 0;
    button.addEventListener('click', () => {
        captured = 0; form.elements.Latitud.value = ''; form.elements.Longitud.value = '';
        if (!navigator.geolocation) { status.textContent = 'Este navegador no ofrece ubicación. Usa un navegador con permiso de ubicación.'; return; }
        button.disabled = true; status.textContent = 'Obteniendo tu ubicación…';
        navigator.geolocation.getCurrentPosition(position => {
            button.disabled = false;
            if (!Number.isFinite(position.coords.accuracy) || position.coords.accuracy > 100 || Date.now() - position.timestamp > 120000) {
                status.textContent = 'La lectura no es suficientemente precisa o reciente. Acércate a una zona abierta y vuelve a intentar.'; return;
            }
            captured = position.timestamp;
            form.elements.Latitud.value = position.coords.latitude.toFixed(6);
            form.elements.Longitud.value = position.coords.longitude.toFixed(6);
            status.textContent = 'Ubicación obtenida. Precisión aproximada: ' + Math.round(position.coords.accuracy) + ' metros.';
        }, () => {
            button.disabled = false; status.textContent = 'No se pudo obtener la ubicación. Revisa el permiso del navegador y vuelve a intentar.';
        }, { enableHighAccuracy: true, maximumAge: 30000, timeout: 45000 });
    });
    form.addEventListener('submit', event => {
        if (!captured || Date.now() - captured > 120000) { event.preventDefault(); status.textContent = 'Obtén una ubicación reciente antes de registrar el reporte.'; button.focus(); }
    });
})();
