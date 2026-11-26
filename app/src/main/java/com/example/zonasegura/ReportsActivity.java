package com.example.zonasegura;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ReportsActivity extends AppCompatActivity {
    public static final String EXTRA_CREATE_REPORT = "createReport";
    private boolean creating;
    private TextView heading;
    private LinearLayout content;
    private TextView status;
    private Location location;
    private int citizenId;
    private int evidenceReport;
    private final androidx.activity.result.ActivityResultLauncher<String[]> photoPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || evidenceReport <= 0) return;
                int id = evidenceReport;
                evidenceReport = 0;
                status.setText("Subiendo fotografía…");
                new Thread(() -> {
                    try {
                        CommunityApi.call(this, false, "/api/reportes/" + id + "/foto", null, uri);
                        deliver(() -> status.setText("Fotografía adjuntada al reporte #" + id));
                    } catch (Exception e) {
                        deliver(() -> status.setText("No se confirmó la carga de la fotografía."));
                    }
                }).start();
            });
    private EditText draftField;
    private Spinner typeField;
    private ArrayList<Integer> typeIds;
    private String draftText = "";
    private int draftType;
    private MaterialButton refresh;
    private android.location.LocationListener reportLocationListener;
    private final android.os.Handler gpsHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean foreground,gpsAttempted,gpsInProgress,gpsResumePending;
    private int gpsGeneration;
    private TextView gpsStatus;
    private static final long MAX_FIX_AGE_MS=120000;
    private static final float MAX_ACCURACY_METERS=100;

    private final androidx.activity.result.ActivityResultLauncher<String[]> gpsPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {if(foreground)readGps();else gpsResumePending=true;});

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        creating = getIntent().getBooleanExtra(EXTRA_CREATE_REPORT, false);
        if (state != null) {
            draftText = state.getString("description", "");
            draftType = state.getInt("type");
            evidenceReport = state.getInt("evidenceReport");
            location = state.getParcelable("location");
        }
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);
        if (!creating) CitizenNavigation.attach(this, R.id.nav_reportes);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        scroll.setBackgroundColor(getColor(R.color.login_background));
        heading = label(getString(creating ? R.string.report_create : R.string.reports_title));
        heading.setTextSize(30);
        ViewCompat.setAccessibilityHeading(heading, true);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        heading.setBackgroundResource(R.drawable.civic_header);
        heading.setTextColor(android.graphics.Color.WHITE);
        heading.setPadding(dp(20), dp(26), dp(20), dp(26));
        status = label("");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout actions = new LinearLayout(this);
        content.addView(actions);
        navigation(actions, "Volver", this::finish);
        refresh = navigation(actions, "Actualizar", this::load);
        load();
        androidx.work.WorkManager.getInstance(this).getWorkInfosByTagLiveData("report-sync").observe(this,infos->{
            if(!creating && citizenId>0)new Thread(()->{
                try{JSONArray cached=new JSONArray(PendingReports.get(this).cached(getString(R.string.backend_base_url),citizenId,"history"));deliver(()->{if(!creating)show(new JSONArray(),cached);});}
                catch(Exception ignored){}
            }).start();
        });
    }

    private void load() {
        String token = SessionStore.load(this);
        if (token == null) {
            finish();
            return;
        }
        refresh.setEnabled(false);
        status.setText("Cargando reportes y pendientes locales…");
        clearResults();
        new Thread(() -> {
            try {
                PendingReports db=PendingReports.get(this);
                String backend=getString(R.string.backend_base_url);
                AuthApi.Profile profile;
                boolean offline=false;
                try{profile=AuthApi.profile(getString(R.string.api_base_url),token);}
                catch(IOException e){profile=SessionStore.offlineProfile(this);offline=true;}
                if(profile.status>=500){profile=SessionStore.offlineProfile(this);offline=true;}
                if(profile.status==401 || profile.status==403){SessionStore.clear(this);showError("Inicia sesión nuevamente para acceder a tus reportes.");return;}
                if (profile.status != 200 || !profile.hasRole("CIUDADANO")) {
                    showError("No se pudo verificar la sesión ciudadana.");
                    return;
                }
                if (profile.citizenId <= 0) {
                    showError("El servidor de sesión debe devolver el identificador ciudadano.");
                    return;
                }
                if(!offline)SessionStore.remember(this,profile);
                int owner=profile.citizenId;
                JSONArray types=new JSONArray(),reports=new JSONArray();
                try{
                    if(offline)throw new IOException();
                    if(creating){types=new JSONArray(request("/api/tipos-incidente",null));db.cache(backend,0,"types",types.toString());}
                    else {reports=new JSONArray(request("/api/reportes",null));db.cache(backend,owner,"history",reports.toString());}
                }catch(IOException e){offline=true;types=new JSONArray(db.cached(backend,0,"types"));reports=new JSONArray(db.cached(backend,owner,"history"));}
                JSONArray shownTypes=types,shownReports=reports;boolean local=offline;
                ReportSyncWorker.resume(this,false);
                deliver(() -> {
                    refresh.setEnabled(true);
                    citizenId = owner;
                    show(shownTypes, shownReports);
                    status.setText(local?"Modo local. Los reportes pendientes se enviarán al recuperar la conexión y una sesión válida.":"Datos actualizados. Los pendientes se sincronizan automáticamente.");
                });
            } catch (Exception error) {
                showError("No se pudieron consultar los reportes. Revisa la conexión con la API.");
            }
        }).start();
    }

    private void rememberDraft() {
        if (draftField != null) draftText = draftField.getText().toString();
        if (typeField != null && typeField.getSelectedItemPosition() >= 0)
            draftType = typeIds.get(typeField.getSelectedItemPosition());
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        rememberDraft();
        state.putString("description", draftText);
        state.putInt("type", draftType);
        state.putInt("evidenceReport", evidenceReport);
        state.putParcelable("location", location);
    }

    private void clearResults() {
        rememberDraft();
        draftField = null;
        typeField = null;
        if (content.getChildCount() > 3) content.removeViews(3, content.getChildCount() - 3);
    }

    private void show(JSONArray types, JSONArray reports) {
        clearResults();
        status.setText(location == null ? "" : "Ubicación capturada.");
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();
        for (int index = 0; index < types.length(); index++) {
            JSONObject type = types.optJSONObject(index);
            if (type != null && type.optBoolean("activo") && type.optInt("id") > 0) {
                names.add(type.optString("nombre"));
                ids.add(type.optInt("id"));
            }
        }
        if (creating && !ids.isEmpty()) {
            gpsStatus=label(location==null?"La ubicación se captura automáticamente al abrir el formulario.":"Ubicación capturada. Puedes actualizarla antes de guardar.");
            button("Actualizar ubicación", this::requestGps);
            gpsStatus.setBackgroundResource(R.drawable.civic_card);
            gpsStatus.setPadding(dp(16), dp(14), dp(16), dp(14));
            TextView typeLabel = label("Tipo de incidente");
            typeLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            Spinner typesInput = new Spinner(this);
            var adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            typesInput.setId(View.generateViewId());
            typeLabel.setLabelFor(typesInput.getId());
            typesInput.setAdapter(adapter);
            typeField = typesInput;
            typeIds = ids;
            typesInput.setSelection(Math.max(0, ids.indexOf(draftType)));
            typesInput.setBackgroundResource(R.drawable.civic_card);
            typesInput.setMinimumHeight(dp(52));
            typesInput.setPadding(dp(12), 0, dp(12), 0);
            content.addView(typesInput);
            TextView descriptionLabel = label("Descripción");
            descriptionLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            EditText description = new EditText(this);
            description.setHint("¿Qué ocurrió? Describe el hecho.");
            draftField = description;
            description.setId(View.generateViewId());
            descriptionLabel.setLabelFor(description.getId());
            description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
            description.setText(draftText);
            description.setMinLines(3);
            description.setGravity(android.view.Gravity.TOP);
            description.setBackgroundResource(R.drawable.civic_card);
            description.setPadding(dp(14), dp(12), dp(14), dp(12));
            content.addView(description);
            MaterialButton send = button("Guardar y sincronizar reporte", null);
            send.setOnClickListener(view -> submit(send, ids.get(typesInput.getSelectedItemPosition()),
                    description.getText().toString().trim()));
            if(foreground&&location==null&&!gpsAttempted)requestGps();
        } else if (creating) {
            label("No hay tipos de incidente guardados. Conéctate una vez y pulsa Actualizar para descargar el catálogo.");
        }
        if (creating) return;
        button("Sincronizar pendientes",()->{ReportSyncWorker.resume(this,true);load();});
        for(PendingReports.Item item:PendingReports.get(this).list(getString(R.string.backend_base_url),citizenId)){
            try{JSONObject body=new JSONObject(item.payload);label("Guardado en este dispositivo · "+item.state.replace('_',' ')+"\n"+body.optString("descripcion")+"\n"+item.message);}
            catch(Exception ignored){}
        }
        button(getString(R.string.report_create), () -> {
            creating = true;
            getIntent().putExtra(EXTRA_CREATE_REPORT, true);
            heading.setText(R.string.report_create);
            load();
        });
        TextView history = label("Historial de reportes");
        history.setTextSize(22);
        history.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        int ownedReports = 0;
        for (int index = 0; index < reports.length(); index++) {
            JSONObject report = reports.optJSONObject(index);
            if (report == null || report.optInt("ciudadanoId") != citizenId) continue;
            ownedReports++;
            addReportCard(report);
        }
        if (ownedReports == 0) label("Todavía no tienes reportes.");
    }

    private void addReportCard(JSONObject report) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.civic_card);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        content.addView(card, params);
        TextView title = new TextView(this);
        title.setText(report.optString("tipoIncidenteNombre") + " · #" + report.optInt("id"));
        title.setTextColor(getColor(R.color.login_text));
        title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(title);
        TextView detail = new TextView(this);
        detail.setText(report.optString("descripcion"));
        detail.setTextSize(16);
        detail.setPadding(0, dp(8), 0, dp(10));
        detail.setTextColor(getColor(R.color.login_text));
        card.addView(detail);
        TextView state = new TextView(this);
        String raw = report.optString("estado");
        String readable = raw.equals("PENDIENTE_VALIDACION") ? "Pendiente de validación"
                : raw.replace('_', ' ').toLowerCase(new java.util.Locale("es"));
        state.setText(readable.isEmpty() ? "Estado desconocido" : readable.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + readable.substring(1));
        state.setTextSize(14);
        state.setTextColor(getColor(R.color.login_primary));
        android.graphics.drawable.GradientDrawable badge = new android.graphics.drawable.GradientDrawable();
        badge.setCornerRadius(dp(8));
        badge.setColor(android.graphics.Color.parseColor(raw.equals("RECHAZADO") ? "#FFEBEE" : raw.equals("PENDIENTE_VALIDACION") ? "#FFF1CB" : "#E6F4F1"));
        state.setTextColor(getColor(R.color.login_text));
        state.setBackground(badge);
        state.setPadding(dp(10), dp(4), dp(10), dp(4));
        card.addView(state, new LinearLayout.LayoutParams(-2, -2));
        MaterialButton photo = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        photo.setText("Adjuntar fotografía");
        photo.setOnClickListener(v -> {
            evidenceReport = report.optInt("id");
            photoPicker.launch(new String[]{"image/*"});
        });
        card.addView(photo);
    }
    private void requestGps(){
        if(!foreground){gpsResumePending=true;return;}
        gpsAttempted=true;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            gpsPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION});
        else readGps();
    }

    private void gpsMessage(String text){if(gpsStatus!=null)gpsStatus.setText(text);}

    private static boolean usableLocation(Location fix){
        if(fix==null||!Double.isFinite(fix.getLatitude())||!Double.isFinite(fix.getLongitude())||!fix.hasAccuracy()||!Float.isFinite(fix.getAccuracy())||fix.getAccuracy()<=0||fix.getAccuracy()>MAX_ACCURACY_METERS)return false;
        long age=(android.os.SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos())/1000000;
        return age>=0&&age<=MAX_FIX_AGE_MS;
    }

    @SuppressLint("MissingPermission")
    private void readGps() {
        cancelGps();
        location = null;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            gpsMessage("Concede permiso de ubicación precisa y pulsa Actualizar ubicación.");
            return;
        }
        LocationManager manager = getSystemService(LocationManager.class);
        gpsMessage("Buscando ubicación precisa… Puedes completar los datos mientras tanto.");
        gpsInProgress=true;
        int requestId=++gpsGeneration;
        try{
            ArrayList<String> providers=new ArrayList<>();
            Location bestCached=null;
            for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
                if(!manager.getAllProviders().contains(provider)||!manager.isProviderEnabled(provider))continue;
                providers.add(provider);
                Location cached=manager.getLastKnownLocation(provider);
                // Includes recent positions obtained while viewing the map.
                if(usableLocation(cached)&&(android.os.SystemClock.elapsedRealtimeNanos()-cached.getElapsedRealtimeNanos())/1000000<=30000
                        &&(bestCached==null||cached.getAccuracy()<bestCached.getAccuracy()))bestCached=cached;
            }
            if(providers.isEmpty()){
                cancelGps();gpsMessage("Activa la ubicación del teléfono y pulsa Actualizar ubicación.");return;
            }
            if(bestCached!=null){locationReady(bestCached);return;}
            reportLocationListener = new android.location.LocationListener() {
                @Override public void onLocationChanged(Location result) {
                    if(gpsInProgress&&gpsGeneration==requestId){
                        if(usableLocation(result))locationReady(result);
                        else gpsMessage("Afinando la ubicación… Se necesita una precisión de 100 m o mejor.");
                    }
                }
                @Override public void onStatusChanged(String provider, int state, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
            // Keep listening to both sources until a usable fix arrives or the request expires.
            gpsHandler.postDelayed(()->{if(gpsInProgress&&gpsGeneration==requestId){cancelGps();gpsMessage("No se obtuvo una posición precisa. Acércate a un lugar abierto y pulsa Actualizar ubicación.");}},45000);
            for(String provider:providers)manager.requestLocationUpdates(provider,1000,0,reportLocationListener,getMainLooper());
        }catch(SecurityException|IllegalArgumentException exception){cancelGps();gpsMessage("El permiso o proveedor de ubicación cambió. Revisa el GPS y pulsa Actualizar ubicación.");}
    }

    private void locationReady(Location result) {
        if (isDestroyed()||!foreground){cancelGps();gpsAttempted=false;return;}
        cancelGps();
        location=null;
        if(!usableLocation(result)){gpsMessage("La posición no es reciente o su precisión supera 100 m. Pulsa Actualizar ubicación.");return;}
        try{
            int district=TerritorioCobertura.distrito(this,result.getLatitude(),result.getLongitude());
            if(district==0){gpsMessage("La ubicación está fuera de los distritos urbanos 1 al 12 de Santa Cruz de la Sierra.");return;}
            location=result;
            gpsMessage("Ubicación lista · Distrito "+district+" · Precisión aproximada ±"+Math.round(result.getAccuracy())+" m.");
        }catch(IOException exception){gpsMessage("No se pudo comprobar la cobertura territorial. Vuelve a abrir el formulario.");}
    }

    private void stopReportLocation() {
        if (reportLocationListener != null) {
            try{getSystemService(LocationManager.class).removeUpdates(reportLocationListener);}catch(SecurityException ignored){}
            reportLocationListener = null;
        }
    }

    private void cancelGps(){gpsGeneration++;gpsInProgress=false;gpsHandler.removeCallbacksAndMessages(null);stopReportLocation();}
    @Override protected void onResume(){super.onResume();foreground=true;if(location!=null&&!usableLocation(location)){location=null;gpsAttempted=false;}if(gpsResumePending){gpsResumePending=false;gpsAttempted=true;readGps();}else if(creating&&draftField!=null&&location==null&&!gpsAttempted)requestGps();}
    @Override protected void onStop(){foreground=false;if(gpsInProgress)gpsAttempted=false;cancelGps();super.onStop();}
    @Override protected void onDestroy(){cancelGps();super.onDestroy();}

    private void submit(MaterialButton send, int type, String description) {
        if (description.length() < 10 || description.length() > 2000 || location == null) {
            status.setText("Escribe entre 10 y 2000 caracteres y captura la ubicación.");
            return;
        }
        if(!usableLocation(location)){location=null;status.setText("La ubicación venció o no es precisa. Actualízala antes de guardar.");requestGps();return;}
        try{if(TerritorioCobertura.distrito(this,location.getLatitude(),location.getLongitude())==0){status.setText("Solo se admiten incidentes dentro de los distritos urbanos 1 al 12.");return;}}
        catch(IOException exception){status.setText("No se pudo comprobar la cobertura territorial.");return;}
        if (SessionStore.load(this) == null) {
            finish();
            return;
        }
        send.setEnabled(false);
        refresh.setEnabled(false);
        Location position = location;
        status.setText("Guardando reporte en este dispositivo…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("descripcion", description)
                        .put("tipoIncidenteId", type)
                        .put("latitud", position.getLatitude())
                        .put("longitud", position.getLongitude());
                String id=PendingReports.get(this).enqueue(getString(R.string.backend_base_url),citizenId,body);
                try{ReportSyncWorker.schedule(this,id);}catch(Exception ignored){/* La fila durable se reprograma al abrir la cuenta. */}
                deliver(() -> {
                    android.widget.Toast.makeText(this, "Guardado localmente. Se confirmará cuando el servidor lo reciba.", android.widget.Toast.LENGTH_LONG).show();
                    location = null;
                    gpsAttempted=false;
                    draftField = null;
                    typeField = null;
                    draftText = "";
                    draftType = 0;
                    creating = false;
                    getIntent().putExtra(EXTRA_CREATE_REPORT, false);
                    heading.setText(R.string.reports_title);
                    load();
                });
            } catch (Exception error) {
                deliver(() -> {
                    send.setEnabled(true);
                    refresh.setEnabled(true);
                    status.setText("No se pudo guardar el reporte localmente. Conserva los datos e inténtalo otra vez.");
                });
            }
        }).start();
    }

    private String request(String path, JSONObject body) throws IOException {
        String base = getString(R.string.reports_api_url);
        if (base.isEmpty()) throw new IOException("API de reportes sin configurar");
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try {
            String token = SessionStore.load(this);
            if (token == null) throw new IOException("Inicia sesión nuevamente.");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int response = connection.getResponseCode();
            if (response == 401) {
                SessionStore.clear(this);
                deliver(() -> {
                    startActivity(new android.content.Intent(this, MainActivity.class)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
                });
            }
            if (response != (body == null ? 200 : 201)) throw new IOException("HTTP " + response);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void deliver(Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) action.run();
        });
    }

    private void showError(String message) {
        deliver(() -> {
            refresh.setEnabled(true);
            status.setText(message);
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private MaterialButton navigation(LinearLayout row, String title, Runnable action) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginEnd(dp(6));
        row.addView(button, params);
        return button;
    }

    private TextView label(String message) {
        TextView label = new TextView(this);
        label.setText(message);
        label.setTextSize(16);
        label.setLineSpacing(dp(3), 1f);
        label.setTextColor(getColor(R.color.login_text));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(12), 0, dp(6));
        content.addView(label, params);
        return label;
    }

    private MaterialButton button(String title, Runnable action) {
        MaterialButton button = new MaterialButton(this);
        button.setText(title);
        button.setCornerRadius(dp(14));
        button.setMinHeight(dp(52));
        button.setAllCaps(false);
        if (action != null) button.setOnClickListener(view -> action.run());
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }
}
