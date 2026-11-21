'use strict';
(() => {
    const seedElement = document.getElementById('map-data');
    const mapElement = document.getElementById('risk-map');
    if (!seedElement || !mapElement) return;
    const seed = JSON.parse(seedElement.textContent);
    const filters = document.getElementById('panel-filtros');
    const notice = document.getElementById('map-message');
    const coverageNotice = document.getElementById('coverage-message');
    const errorBox = document.getElementById('panel-error');
    const errorMessage = document.getElementById('panel-error-message');
    const loginLink = document.getElementById('panel-ingresar');
    const reportRows = document.getElementById('panel-reportes');
    const criticalZones = document.getElementById('critical-zones');
    const map = L.map(mapElement, { minZoom: 10, maxZoom: 19 }).setView([-17.7833, -63.1821], 13);
    const riskLayer = L.layerGroup().addTo(map), reportLayer = L.layerGroup().addTo(map);
    const colors = { BAJO: '#15805f', MEDIO: '#b57408', ALTO: '#b6293f' };
    const number = new Intl.NumberFormat('es-BO');
    const percent = new Intl.NumberFormat('es-BO', { style: 'percent', maximumFractionDigits: 0 });
    const date = new Intl.DateTimeFormat('es-BO', { dateStyle: 'short', timeStyle: 'short', timeZone: 'UTC' });
    let timer, pending, coverage;
    let generation = 0;
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors', maxZoom: 19
    }).addTo(map).on('tileerror', () => { coverageNotice.textContent = 'No se pudieron cargar algunas calles. Revisa la conexión a internet.'; });
    function node(tag, text, className) {
        const element = document.createElement(tag);
        if (text !== undefined) element.textContent = text;
        if (className) element.className = className;
        return element;
    }
    function emptyRow(message) {
        const row = node('tr'), cell = node('td', message, 'empty-state');
        cell.colSpan = 6; row.append(cell); reportRows.replaceChildren(row);
    }
    function renderReports(reports) {
        const rows = document.createDocumentFragment();
        for (const report of reports) {
            const row = node('tr'), incident = node('td'), state = node('td'), management = node('td');
            row.append(node('td', '#' + report.id));
            incident.append(node('strong', report.tipoIncidenteNombre), node('p', report.descripcion, 'report-description'));
            const timestamp = new Date(report.fechaRegistroUtc);
            row.append(incident, node('td', Number.isNaN(timestamp.getTime()) ? 'Fecha no disponible' : date.format(timestamp)));
            state.append(node('span', report.estado.replaceAll('_', ' '), 'state'));
            row.append(state, node('td', percent.format(report.indiceVeracidad)));
            if (report.estado === 'VALIDADO' || report.estado === 'PENDIENTE_VALIDACION') {
                const assign = report.estado === 'VALIDADO';
                const link = node('a', assign ? 'Crear orden' : 'Verificar', assign ? 'btn btn-sm btn-outline-primary' : '');
                const url = new URL(assign ? mapElement.dataset.asignarUrl : mapElement.dataset.verificarUrl, window.location.origin);
                if (assign) url.searchParams.set('id', String(report.id));
                link.href = url.toString(); management.append(link);
            }
            row.append(management); rows.append(row);
        }
        reportRows.replaceChildren(rows);
        if (reports.length === 0) emptyRow('No hay reportes para estos filtros en el área visible.');
    }
    function renderZones(zones) {
        const items = document.createDocumentFragment();
        const sorted = [...zones].sort((a, b) => b.cantidad - a.cantidad || a.id.localeCompare(b.id)).slice(0, 12);
        for (const zone of sorted) {
            const item = node('li'), button = node('button', number.format(zone.cantidad) + ' incidentes confirmados · ' + zone.id, 'zone-link');
            button.type = 'button';
            button.addEventListener('click', () => { map.fitBounds(L.latLngBounds(zone.poligono.map(point => [point[1], point[0]])), { maxZoom: 16, padding: [30, 30] }); });
            item.append(node('span', zone.nivel, 'state'), button); items.append(item);
        }
        criticalZones.replaceChildren(items);
        if (zones.length === 0) criticalZones.append(node('li', 'No hay zonas con incidentes confirmados en el área visible.', 'muted'));
    }
    function metrics(data) {
        return data.indicadores || {
            total: data.reportes.length,
            pendientes: data.reportes.filter(report => report.estado === 'PENDIENTE_VALIDACION').length,
            validados: data.reportes.filter(report => report.estado === 'VALIDADO').length,
            enAtencion: data.reportes.filter(report => report.estado === 'EN_ATENCION').length,
            atendidos: data.reportes.filter(report => report.estado === 'ATENDIDO').length,
            rechazados: data.reportes.filter(report => report.estado === 'RECHAZADO').length,
            zonasAltas: data.zonas.filter(zone => zone.nivel === 'ALTO').length
        };
    }
    function render(data) {
        riskLayer.clearLayers(); reportLayer.clearLayers();
        const indicators = metrics(data);
        for (const element of document.querySelectorAll('[data-metric]')) element.textContent = number.format(indicators[element.dataset.metric]);
        for (const zone of data.zonas) {
            const polygon = L.polygon(zone.poligono.map(point => [point[1], point[0]]), { color: colors[zone.nivel], fillColor: colors[zone.nivel], fillOpacity: .3, weight: 1.5 }).addTo(riskLayer);
            polygon.bindPopup(node('div', zone.nivel + ' · ' + zone.cantidad + ' incidentes confirmados en la celda y el período'));
        }
        for (const report of data.reportes) {
            const info = node('div');
            info.append(node('strong', '#' + report.id + ' · ' + report.tipoIncidenteNombre), node('p', report.estado.replaceAll('_', ' ')), node('p', report.descripcion));
            L.circleMarker([report.latitud, report.longitud], { radius: 5, color: '#112d37', fillColor: '#fff', fillOpacity: 1, weight: 2 }).addTo(reportLayer).bindPopup(info);
        }
        renderReports(data.reportes); renderZones(data.zonas);
        document.getElementById('report-period').textContent = data.desde.split('-').reverse().join('/') + ' al ' + data.hasta.split('-').reverse().join('/');
        notice.textContent = data.reportes.length === 0 ? 'No hay reportes que coincidan con los filtros en el área visible.'
            : data.zonas.length === 0 ? 'Los reportes del área todavía no participan en la capa de incidentes confirmados.'
                : 'Mapa, indicadores y listado actualizados para el área visible. Las celdas del borde conservan su conteo completo.';
        errorBox.hidden = true; loginLink.hidden = true;
    }
    function unavailable(message, expired) {
        riskLayer.clearLayers(); reportLayer.clearLayers();
        for (const element of document.querySelectorAll('[data-metric]')) element.textContent = '—';
        emptyRow('No se pudo consultar la información del área.');
        criticalZones.replaceChildren(node('li', 'Información no disponible.', 'muted'));
        errorMessage.textContent = message; errorBox.hidden = false; loginLink.hidden = !expired;
        notice.textContent = 'La consulta no se completó. No se muestran cantidades sin confirmar.';
    }
    function boundsInForm() {
        const bounds = map.getBounds();
        for (const [name, value] of Object.entries({ Sur: bounds.getSouth(), Oeste: bounds.getWest(), Norte: bounds.getNorth(), Este: bounds.getEast() })) filters.elements.namedItem(name).value = value.toFixed(7);
    }
    async function load() {
        const current = ++generation;
        pending?.abort(); pending = new AbortController(); boundsInForm();
        const url = new URL(mapElement.dataset.datosUrl, window.location.origin);
        url.search = new URLSearchParams(new FormData(filters)).toString();
        mapElement.setAttribute('aria-busy', 'true'); notice.textContent = 'Consultando el área visible…';
        try {
            const response = await fetch(url, { signal: pending.signal, credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest', Accept: 'application/json' } });
            const contentType = response.headers.get('content-type') || '';
            if (response.redirected || !contentType.includes('application/json')) {
                if (current === generation) unavailable('La sesión no está disponible. Ingresa nuevamente para consultar el mapa.', true);
                return;
            }
            const data = await response.json();
            if (current !== generation) return;
            if (!response.ok) { unavailable(data.mensaje || 'No se pudo consultar el área visible.', response.status === 401); return; }
            render(data);
        } catch (error) {
            if (error.name !== 'AbortError' && current === generation) unavailable('No se pudo actualizar el área. Revisa la conexión y vuelve a consultar.', false);
        } finally {
            if (current === generation) mapElement.setAttribute('aria-busy', 'false');
        }
    }
    function schedule() {
        clearTimeout(timer); pending?.abort();
        // Invalidate a previous response as soon as the user changes the view.
        ++generation; boundsInForm(); timer = setTimeout(load, 350);
    }
    document.getElementById('show-zones').addEventListener('change', event => {
        if (event.target.checked) riskLayer.addTo(map);
        else { map.closePopup(); map.removeLayer(riskLayer); }
    });
    document.getElementById('show-coverage').addEventListener('change', event => {
        if (!coverage) return;
        if (event.target.checked) coverage.addTo(map); else map.removeLayer(coverage);
    });
    document.getElementById('panel-reintentar').addEventListener('click', load);
    async function loadCoverage() {
        try {
            const response = await fetch(mapElement.dataset.coberturaUrl, { credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest', Accept: 'application/json' } });
            if (!response.ok || response.redirected || !(response.headers.get('content-type') || '').includes('application/json')) throw new Error('coverage');
            const data = await response.json();
            if (data.type !== 'FeatureCollection') throw new Error('coverage');
            coverage = L.geoJSON(data, { style: { color: '#536f87', weight: 1.5, fill: false, dashArray: '5 4' } });
            if (document.getElementById('show-coverage').checked) coverage.addTo(map);
            coverageNotice.textContent = 'La línea discontinua muestra la cobertura territorial configurada.';
        } catch { coverageNotice.textContent = 'No se pudieron cargar los límites de cobertura. Puedes seguir consultando los incidentes.'; }
    }
    render(seed);
    const initialBounds = ['Sur', 'Oeste', 'Norte', 'Este'].map(name => filters.elements.namedItem(name).value);
    if (initialBounds.every(value => value !== '' && Number.isFinite(Number(value)))) map.fitBounds([[Number(initialBounds[0]), Number(initialBounds[1])], [Number(initialBounds[2]), Number(initialBounds[3])]], { animate: false });
    else if (seed.reportes.length > 0) map.fitBounds(L.latLngBounds(seed.reportes.map(report => [report.latitud, report.longitud])), { maxZoom: 15, padding: [30, 30], animate: false });
    map.on('moveend', schedule); map.whenReady(load); loadCoverage();
})();
