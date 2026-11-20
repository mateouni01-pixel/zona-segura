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
    private android.location.LocationListener legacyLocationListener;
    private final android.os.CancellationSignal gpsRequest = new android.os.CancellationSignal();

    private final androidx.activity.result.ActivityResultLauncher<String[]> gpsPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> readGps());

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
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        scroll.setBackgroundColor(getColor(R.color.login_background));
        heading = label(getString(creating ? R.string.report_create : R.string.reports_title));
        heading.setTextSize(28);
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
    }

    private void load() {
        String token = SessionStore.load(this);
        if (token == null) {
            finish();
            return;
        }
        refresh.setEnabled(false);
        status.setText("Consultando el servidor…");
        clearResults();
        new Thread(() -> {
            try {
                AuthApi.Profile profile = AuthApi.profile(getString(R.string.api_base_url), token);
                if (profile.status != 200 || !"CIUDADANO".equalsIgnoreCase(profile.role)) {
                    showError("No se pudo verificar la sesión ciudadana.");
                    return;
                }
                if (profile.citizenId <= 0) {
                    showError("El servidor de sesión debe devolver el identificador ciudadano.");
                    return;
                }
                JSONArray types = creating ? new JSONArray(request("/api/tipos-incidente", null)) : new JSONArray();
                JSONArray reports = creating ? new JSONArray() : new JSONArray(request("/api/reportes", null));
                deliver(() -> {
                    refresh.setEnabled(true);
                    citizenId = profile.citizenId;
                    show(types, reports);
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
            TextView section = label(getString(R.string.report_create));
            section.setTextSize(22);
            section.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            TextView typeLabel = label("Tipo de incidente");
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
            EditText description = new EditText(this);
            description.setHint("Describe el incidente");
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
            button("Capturar ubicación", () -> {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    gpsPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION});
                } else readGps();
            });
            MaterialButton send = button("Enviar reporte", null);
            send.setOnClickListener(view -> submit(send, ids.get(typesInput.getSelectedItemPosition()),
                    description.getText().toString().trim()));
        } else if (creating) {
            label("El servidor no tiene tipos de incidente activos.");
        }
        if (creating) return;
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
        state.setText("Estado · " + readable);
        state.setTextSize(14);
        state.setTextColor(getColor(R.color.login_primary));
        card.addView(state);
        MaterialButton photo = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        photo.setText("Adjuntar fotografía");
        photo.setOnClickListener(v -> {
            evidenceReport = report.optInt("id");
            photoPicker.launch(new String[]{"image/*"});
        });
        card.addView(photo);
    }
    @SuppressLint("MissingPermission")
    private void readGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Concede permiso de ubicación precisa para registrar el incidente.");
            return;
        }
        LocationManager manager = getSystemService(LocationManager.class);
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            status.setText("Activa el GPS para capturar la ubicación.");
            return;
        }
        stopLegacyLocation();
        location = null;
        status.setText("Buscando ubicación…");
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            manager.getCurrentLocation(LocationManager.GPS_PROVIDER, gpsRequest, getMainExecutor(), this::locationReady);
        } else {
            legacyLocationListener = new android.location.LocationListener() {
                @Override public void onLocationChanged(Location result) { locationReady(result); }
                @Override public void onStatusChanged(String provider, int state, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) { locationReady(null); }
            };
            manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, legacyLocationListener, getMainLooper());
        }
    }

    private void locationReady(Location result) {
        if (isDestroyed()) return;
        stopLegacyLocation();
        location = result;
        status.setText(result == null ? "No se pudo obtener la ubicación." : "Ubicación capturada.");
    }

    private void stopLegacyLocation() {
        if (legacyLocationListener != null) {
            getSystemService(LocationManager.class).removeUpdates(legacyLocationListener);
            legacyLocationListener = null;
        }
    }

    @Override protected void onDestroy() {
        stopLegacyLocation();
        gpsRequest.cancel();
        super.onDestroy();
    }

    private void submit(MaterialButton send, int type, String description) {
        if (description.length() < 10 || description.length() > 2000 || location == null) {
            status.setText("Escribe entre 10 y 2000 caracteres y captura la ubicación.");
            return;
        }
        if (SessionStore.load(this) == null) {
            finish();
            return;
        }
        send.setEnabled(false);
        refresh.setEnabled(false);
        Location position = location;
        status.setText("Enviando reporte…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("descripcion", description)
                        .put("tipoIncidenteId", type)
                        .put("latitud", position.getLatitude())
                        .put("longitud", position.getLongitude());
                request("/api/reportes", body);
                deliver(() -> {
                    android.widget.Toast.makeText(this, "Reporte registrado", android.widget.Toast.LENGTH_SHORT).show();
                    location = null;
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
                    status.setText("No se pudo confirmar el envío. Actualiza antes de intentarlo de nuevo.");
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
