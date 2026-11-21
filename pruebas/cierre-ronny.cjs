// Ejecutar con NODE_EXTRA_CA_CERTS apuntando a la CA local.
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const { execFileSync } = require('node:child_process');

const [server, database] = process.argv.slice(2);
assert(server && /^ZonaSeguraRonny_[0-9a-f]{10}$/.test(database), 'Indica instancia y base aislada de Ronny');
const run = randomUUID();

async function api(path, status, token, body) {
  const response = await fetch('https://127.0.0.1:8443/api/' + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: 'Bearer ' + token }) },
    body: JSON.stringify(body)
  });
  assert.equal(response.status, status, path + ' HTTP ' + response.status);
  const data = await response.text();
  return data && JSON.parse(data);
}

async function account(label, role) {
  const input = { nombre: 'Prueba ' + label, correo: label + '.' + run + '@example.org', password: randomUUID() };
  const { idUsuario: id } = await api('auth/register', 201, null, input);
  assert(Number.isSafeInteger(id));
  if (label === 'autor') await api('auth/register', 409, null, input);
  if (role) {
    // El registro público crea ciudadanos. Solo el operador SQL prepara los perfiles de prueba.
    const setup = role === 'PERSONAL_ALCALDIA'
      ? "INSERT dbo.PersonalAlcaldia VALUES (" + id + ",'Prueba','Prueba');" : '';
    const sql = "SET XACT_ABORT ON; BEGIN TRAN; IF NOT EXISTS (SELECT 1 FROM dbo.Usuarios WHERE id_usuario=" +
      id + " AND correo='" + input.correo + "') THROW 50000,'Cuenta de prueba incorrecta',1; " +
      "INSERT dbo.UsuarioRoles SELECT " + id + ",id_rol FROM dbo.Roles WHERE nombre='" + role + "'; " + setup + 'COMMIT;';
    execFileSync('sqlcmd', ['-S', server, '-d', database, '-E', '-C', '-I', '-b', '-Q', sql]);
  }
  return api('auth/login', 200, null, input);
}

async function main() {
  assert.equal((await api('conexion', 200)).base, database);
  const author = await account('autor');
  const voters = [];
  for (let i = 1; i <= 3; i++) voters.push(await account('voto' + i));
  const municipal = await account('municipio', 'PERSONAL_ALCALDIA');
  const operator = await account('unidad', 'UNIDAD_OPERATIVA');
  const [type] = await api('tipos-incidente', 200, author.token);
  const point = { latitud: -17.7833, longitud: -63.1821 };
  const mapPath = 'mapa/resumen?sur=-17.784&oeste=-63.183&norte=-17.782&este=-63.181&dias=7&tipoIncidenteId=' + type.id;
  const before = (await api(mapPath, 200, author.token)).totalReportes;
  const report = await api('reportes', 201, author.token, {
    ...point, tipoIncidenteId: type.id, descripcion: 'Prueba de cierre operativo ' + run
  });
  const detail = 'reportes/' + report.id;
  assert.equal((await api(mapPath, 200, author.token)).totalReportes, before);
  for (const [i, voter] of voters.entries()) {
    await api(detail + '/votos', 200, voter.token, { ...point, confirma: true });
    assert.equal((await api(detail, 200, author.token)).estado, i === 2 ? 'VALIDADO' : 'PENDIENTE_VALIDACION');
  }
  // SQL Server redondea fecha_hora a segundos. Esperar hasta que el instante
  // almacenado quede dentro del período que acaba en la hora actual.
  let map = await api(mapPath, 200, author.token);
  if (map.totalReportes === before) {
    await new Promise(resolve => setTimeout(resolve, 1100));
    map = await api(mapPath, 200, author.token);
  }
  assert.equal(map.totalReportes, before + 1);
  const level = map.totalReportes >= 5 ? 'ALTO' : map.totalReportes >= 3 ? 'MEDIO' : 'BAJO';
  assert.equal(map.zonas[0].nivel, level);
  const unit = await api('unidades', 201, municipal.token, { nombre: 'Unidad ' + run });
  await api('unidades/' + unit.id + '/miembros', 200, municipal.token, { usuarioId: operator.idUsuario });
  const resource = await api('recursos', 201, municipal.token, {
    nombre: 'Patrulla ' + run, tipo: 'PATRULLA', unidadId: unit.id, disponible: true, activo: true
  });
  const assignment = { reporteId: report.id, recursoId: resource.id, tipoOrden: 'PATRULLAJE',
    instrucciones: 'Verificar el incidente de prueba', solicitudId: randomUUID() };
  const order = await api('intervenciones', 201, municipal.token, assignment);
  assert.equal((await api('intervenciones', 201, municipal.token, assignment)).id, order.id);
  const [entry] = await api('operativa/ordenes', 200, operator.token);
  assert.equal(entry.id, order.id);
  assert.equal(entry.estado, 'EN_ATENCION');
  await api('operativa/avisos/' + entry.avisoId + '/leer', 200, operator.token, {});
  const closed = await api('operativa/ordenes/' + order.id + '/atender', 200, operator.token,
    { resultado: 'Inspección de prueba completada' });
  assert.equal(closed.estado, 'ATENDIDO');
  assert.equal(closed.estadoNotificacion, 'LEIDA');
  assert((await api('recursos', 200, municipal.token)).find(r => r.id === resource.id).disponible);
  console.log(JSON.stringify({ reporte: report.id, orden: order.id, recurso: resource.id, estado: closed.estado, cantidad: map.totalReportes, nivel: level }));
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
