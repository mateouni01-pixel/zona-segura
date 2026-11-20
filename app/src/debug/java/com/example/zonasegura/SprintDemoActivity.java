package com.example.zonasegura;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Patterns;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class SprintDemoActivity extends AppCompatActivity {
    private enum Role { CITIZEN, MUNICIPAL, ADMIN }

    private static final double SAMPLE_LATITUDE = -17.7833;
    private static final double SAMPLE_LONGITUDE = -63.1821;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT;
    private static final int EMAIL = TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
    private static final int PASSWORD = TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    private static final int INK = Color.rgb(24, 46, 52);

    private SprintDemoStore store;
    private SprintDemoAccounts accounts;
    private LinearLayout content;
    private ScrollView scroll;
    private Role role;
    private String account;
    private String displayName;
    private Double latitude;
    private Double longitude;
    private Uri photo;
    private TextView photoStatus;
    private TextView locationStatus;
    private LocationListener locationListener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable locationTimeout = () -> {
        stopLocation();
        toast(R.string.demo_location_error);
    };
    private Consumer<Location> locationResult;

    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                if (locationResult == null) return;
                if (Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                    readGps();
                } else {
                    locationResult = null;
                    toast(R.string.demo_location_error);
                }
            });

    private final ActivityResultLauncher<String[]> photoPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || photoStatus == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    photo = uri;
                    photoStatus.setText(R.string.demo_attached);
                } catch (SecurityException exception) {
                    toast(R.string.demo_photo_error);
                }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_sprint_demo);
        content = findViewById(R.id.demo_content);
        scroll = findViewById(R.id.demo_scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        store = new SprintDemoStore(this);
        accounts = new SprintDemoAccounts(this);
        var signOut = new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (role == null) {
                    if (atLogin) finish();
                    else login();
                } else if (atHome) {
                    login();
                } else {
                    home();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, signOut);
        login();
    }

    private boolean atLogin;
    private boolean atHome;

    private void screen(int title) {
        stopLocation();
        photoStatus = null;
        locationStatus = null;
        atLogin = false;
        atHome = false;
        content.removeAllViews();
        text(R.string.demo_banner, 13);
        TextView heading = text(title, 28);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        scroll.scrollTo(0, 0);
    }

    private void login() {
        role = null;
        account = null;
        displayName = null;
        screen(R.string.demo_title);
        atLogin = true;
        text(R.string.demo_login, 19);
        EditText email = input(R.string.demo_email, EMAIL);
        EditText password = input(R.string.demo_password, PASSWORD);
        button(R.string.demo_enter, () -> {
            String address = email(email);
            String name = accounts.authenticate(address, password.getText().toString());
            if (name == null) toast(R.string.demo_auth_error);
            else signIn(Role.CITIZEN, address, name);
        });
        button(R.string.demo_create, this::register);
        button(R.string.demo_recover, this::recover);
        button(R.string.demo_citizen, () -> signIn(Role.CITIZEN, "demo:citizen-1", "Ciudadano 1"));
        button(R.string.demo_switch_voter, () -> signIn(Role.CITIZEN, "demo:citizen-2", "Ciudadano 2"));
        button(R.string.demo_municipal, () -> signIn(Role.MUNICIPAL, "demo:municipal", "Alcaldía"));
        button(R.string.demo_admin, () -> signIn(Role.ADMIN, "demo:admin", "Administración"));
    }

    private void register() {
        screen(R.string.demo_create);
        EditText name = input(R.string.demo_name, TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText email = input(R.string.demo_email, EMAIL);
        EditText password = input(R.string.demo_password, PASSWORD);
        button(R.string.demo_create, () -> {
            String address = email(email);
            String person = name.getText().toString().trim();
            String secret = password.getText().toString();
            if (person.isEmpty() || person.length() > 150 || address.length() > 254
                    || !Patterns.EMAIL_ADDRESS.matcher(address).matches() || !validPassword(secret)) {
                toast(R.string.demo_account_error);
                return;
            }
            String code = accounts.create(address, person, secret);
            if (code == null) {
                toast(R.string.demo_duplicate);
                return;
            }
            login();
            new AlertDialog.Builder(this).setMessage(getString(R.string.demo_created, code))
                    .setPositiveButton(android.R.string.ok, null).show();
        });
        button(R.string.demo_back, this::login);
    }

    private void recover() {
        screen(R.string.demo_recover);
        text(R.string.demo_recovery_help, 15);
        EditText email = input(R.string.demo_email, EMAIL);
        EditText code = input(R.string.demo_recovery_code, InputType.TYPE_CLASS_NUMBER);
        EditText password = input(R.string.demo_password, PASSWORD);
        button(R.string.demo_reset, () -> {
            String secret = password.getText().toString();
            if (!validPassword(secret)
                    || !accounts.recover(email(email), code.getText().toString().trim(), secret)) {
                toast(R.string.demo_recovery_error);
                return;
            }
            toast(R.string.demo_recovered);
            login();
        });
        button(R.string.demo_back, this::login);
    }

    private static boolean validPassword(String secret) {
        return secret.length() >= 8 && secret.length() <= 128;
    }

    private String email(EditText field) {
        return field.getText().toString().trim().toLowerCase(Locale.ROOT);
    }

    private void signIn(Role nextRole, String id, String name) {
        role = nextRole;
        account = id;
        displayName = name;
        home();
    }

    private void home() {
        switch (role) {
            case CITIZEN:
                screen(R.string.demo_home);
                button(R.string.demo_map, () -> map(true));
                if (!store.isSanctioned(account)) {
                    button(R.string.demo_report, this::reportForm);
                    button(R.string.demo_nearby, this::nearbyLocation);
                } else {
                    text(R.string.demo_sanctioned, 16);
                }
                button(R.string.demo_mine, this::myReports);
                break;
            case MUNICIPAL:
                screen(R.string.demo_municipal_home);
                button(R.string.demo_map, () -> map(true));
                button(R.string.demo_resources, this::resources);
                
                break;
            case ADMIN:
                screen(R.string.demo_admin_home);
                button(R.string.demo_roles, this::roles);
                button(R.string.demo_moderate, this::moderation);
                break;
        }
        text(getString(R.string.demo_signed, displayName), 15);
        button(R.string.demo_logout, this::login);
        atHome = true;
    }

    private void roles() {
        screen(R.string.demo_roles);
        text(R.string.demo_roles_help, 16);
        back();
    }

    private void map(boolean thermal) {
        screen(R.string.demo_map);
        text(R.string.demo_map_help, 16);
        List<float[]> points = new ArrayList<>();
        try (Cursor reports = store.reports("synced=1 AND state IN ('VALIDADO','EN_ATENCION','ATENDIDO')")) {
            while (reports.moveToNext()) {
                double lat = decimal(reports, "lat");
                double lng = decimal(reports, "lng");
                if (lat >= -17.790 && lat <= -17.776 && lng >= -63.192 && lng <= -63.172) {
                    // Keep the public display at block scale, with no coordinates in the UI.
                    float x = Math.round((lng + 63.192) * 1600) * 10f;
                    float y = Math.round((-17.776 - lat) * 1900) * 10f;
                    points.add(new float[]{x, y});
                }
            }
        }
        content.addView(new SprintDemoMap(this, thermal, points),
                new LinearLayout.LayoutParams(-1, dp(290)));
        text(getString(R.string.demo_map_count, points.size()), 15);
        button(thermal ? R.string.demo_heat_off : R.string.demo_heat_on, () -> map(!thermal));
        back();
    }

    private void reportForm() {
        screen(R.string.demo_report);
        latitude = longitude = null;
        photo = null;
        Spinner type = new Spinner(this);
        type.setContentDescription(getString(R.string.demo_type));
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.demo_incident_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        type.setAdapter(adapter);
        content.addView(type, new LinearLayout.LayoutParams(-1, dp(52)));
        EditText description = input(R.string.demo_description, TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        description.setMinLines(3);
        locationStatus = text("", 15);
        button(R.string.demo_gps, () -> locate(location -> setReportLocation(
                location.getLatitude(), location.getLongitude())));
        button(R.string.demo_sample, () -> {
            stopLocation();
            setReportLocation(SAMPLE_LATITUDE, SAMPLE_LONGITUDE);
        });
        photoStatus = text("", 15);
        button(R.string.demo_attach, () -> photoPicker.launch(new String[]{"image/*"}));
        button(R.string.demo_save, () -> {
            String value = description.getText().toString().trim();
            if (type.getSelectedItemPosition() == 0 || value.isEmpty()
                    || value.length() > 2000 || latitude == null || longitude == null) {
                toast(R.string.demo_invalid_report);
                return;
            }
            if (store.isSanctioned(account)) {
                toast(R.string.demo_sanctioned);
                home();
                return;
            }
            store.saveReport(account, type.getSelectedItem().toString(), value, latitude, longitude,
                    photo == null ? null : photo.toString());
            toast(R.string.demo_saved);
            myReports();
        });
        back();
    }

    private void setReportLocation(double lat, double lng) {
        latitude = lat;
        longitude = lng;
        locationStatus.setText(R.string.demo_location_set);
    }

    private void myReports() {
        screen(R.string.demo_mine);
        button(R.string.demo_sync, () -> {
            toast(getString(R.string.demo_synced, store.simulateSync(account)));
            myReports();
        });
        list("author=?", new String[]{account}, row -> {
            reportCard(row, true);
            if (number(row, "synced") == 0) text(R.string.demo_pending_sync, 14);
            return true;
        });
        back();
    }

    private void nearbyLocation() {
        screen(R.string.demo_nearby);
        text(R.string.demo_nearby_help, 16);
        button(R.string.demo_gps, () -> locate(location -> nearby(location.getLatitude(), location.getLongitude())));
        button(R.string.demo_sample, () -> nearby(SAMPLE_LATITUDE, SAMPLE_LONGITUDE));
        back();
    }

    private void nearby(double lat, double lng) {
        screen(R.string.demo_nearby);
        text(R.string.demo_nearby_help, 16);
        list("synced=1 AND state='PENDIENTE_VALIDACION' AND author<>?", new String[]{account}, row -> {
            float[] distance = new float[1];
            Location.distanceBetween(lat, lng, decimal(row, "lat"), decimal(row, "lng"), distance);
            if (distance[0] > 500) return false;
            reportCard(row, false);
            text(getString(R.string.demo_distance, Math.round(distance[0])), 14);
            long id = number(row, "id");
            button(R.string.demo_yes, () -> vote(id, true, lat, lng));
            button(R.string.demo_no, () -> vote(id, false, lat, lng));
            return true;
        });
        back();
    }

    private void vote(long id, boolean confirms, double lat, double lng) {
        boolean saved = !store.isSanctioned(account) && store.vote(id, account, confirms);
        toast(saved ? R.string.demo_vote_saved : R.string.demo_vote_error);
        nearby(lat, lng);
    }

    private void moderation() {
        screen(R.string.demo_moderate);
        text(R.string.demo_moderation_help, 16);
        list("synced=1 AND flagged=1 AND state IN ('PENDIENTE_VALIDACION','RECHAZADO')", null, row -> {
            reportCard(row, true);
            text(R.string.demo_flagged, 14);
            long id = number(row, "id");
            String author = value(row, "author");
            if ("RECHAZADO".equals(value(row, "state"))) {
                if (!store.isSanctioned(author)) {
                    button(R.string.demo_sanction, () -> {
                        toast(store.sanction(id, author) ? R.string.demo_moderated : R.string.demo_action_error);
                        moderation();
                    });
                } else {
                    text(R.string.demo_sanctioned, 14);
                }
            } else {
                button(R.string.demo_reject, () -> {
                    toast(store.changeState(id, "RECHAZADO", "PENDIENTE_VALIDACION")
                            ? R.string.demo_moderated : R.string.demo_action_error);
                    moderation();
                });
            }
            return true;
        });
        back();
    }

    private final ActivityResultLauncher<String> pdfFile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), uri -> export(uri, true));
    private final ActivityResultLauncher<String> excelFile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            uri -> export(uri, false));

    private void notices() {
        screen(R.string.demo_notices);
        text(R.string.demo_recipient, 16);
        list("id IN (SELECT report FROM orders)", null, row -> {
            text(getString(R.string.demo_notice_item, number(row, "id")), 18);
            text(value(row, "state").replace('_', ' '), 15);
            return true;
        });
        button(R.string.demo_back, this::resources);
    }

    private void export(Uri uri, boolean pdf) {
        if (uri == null || role != Role.MUNICIPAL) return;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Reporte", "Tipo", "Estado"});
        try (Cursor cursor = store.reports("synced=1 AND state IN ('VALIDADO','EN_ATENCION','ATENDIDO')")) {
            while (cursor.moveToNext()) rows.add(new String[]{value(cursor,"id"),
                    value(cursor,"type"), value(cursor,"state")});
        }
        try (java.io.OutputStream output = getContentResolver().openOutputStream(uri,"wt")) {
            if (output == null) throw new java.io.IOException();
            if (pdf) InterventionExport.pdf(rows, output);
            else InterventionExport.excel(rows, output);
            toast(R.string.demo_export_ok);
        } catch (java.io.IOException | SecurityException exception) {
            toast(R.string.demo_export_error);
        }
    }
    private void resources() {
        screen(R.string.demo_resources);
        text(R.string.demo_resource_help, 16);
        button(R.string.demo_pdf, () -> pdfFile.launch("intervenciones.pdf"));
        button(R.string.demo_excel, () -> excelFile.launch("intervenciones.xlsx"));
        button(R.string.demo_notices, this::notices);
        list("synced=1 AND state IN ('VALIDADO','EN_ATENCION','ATENDIDO')", null, row -> {
            reportCard(row, true);
            long id = number(row, "id");
            String state = value(row, "state");
            if ("VALIDADO".equals(state)) {
                button(R.string.demo_assign, () -> {
                    toast(store.assignResource(id) ? R.string.demo_order_ok : R.string.demo_resource_error);
                    resources();
                });
            } else if ("EN_ATENCION".equals(state)) {
                button(R.string.demo_complete, () -> {
                    toast(store.changeState(id, "ATENDIDO", "EN_ATENCION")
                            ? R.string.demo_order_ok : R.string.demo_resource_error);
                    resources();
                });
            }
            return true;
        });
        back();
    }

    private interface ReportRow {
        boolean render(Cursor row);
    }

    private void list(String filter, String[] args, ReportRow renderer) {
        boolean shown = false;
        try (Cursor rows = store.reports(filter, args)) {
            while (rows.moveToNext()) shown |= renderer.render(rows);
        }
        if (!shown) text(R.string.demo_empty, 16);
    }

    private void reportCard(Cursor row, boolean allowPhoto) {
        text(getString(R.string.demo_report_card, value(row, "type"), number(row, "id")), 19)
                .setTypeface(null, android.graphics.Typeface.BOLD);
        text(value(row, "description"), 16);
        text(getString(R.string.demo_labeled_state, value(row, "state").replace('_', ' ')), 14);
        String evidence = value(row, "photo");
        if (evidence != null && allowPhoto) button(R.string.demo_view_photo, () -> showPhoto(evidence));
    }

    private void showPhoto(String evidence) {
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setMaxHeight(dp(420));
        image.setContentDescription(getString(R.string.demo_report_has_photo));
        image.setImageURI(Uri.parse(evidence));
        if (image.getDrawable() == null) {
            toast(R.string.demo_photo_error);
            return;
        }
        new AlertDialog.Builder(this).setView(image).setPositiveButton(android.R.string.ok, null).show();
    }

    private void locate(Consumer<Location> result) {
        stopLocation();
        locationResult = result;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            readGps();
        } else {
            locationPermission.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void readGps() {
        LocationManager manager = getSystemService(LocationManager.class);
        try {
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                stopLocation();
                toast(R.string.demo_location_error);
                return;
            }
            toast(R.string.demo_location_wait);
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    Consumer<Location> result = locationResult;
                    stopLocation();
                    if (result != null) result.accept(location);
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) {
                    stopLocation();
                    toast(R.string.demo_location_error);
                }
            };
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
                    locationListener, Looper.getMainLooper());
            handler.postDelayed(locationTimeout, 12_000);
        } catch (IllegalArgumentException | SecurityException exception) {
            stopLocation();
            toast(R.string.demo_location_error);
        }
    }

    private void stopLocation() {
        handler.removeCallbacks(locationTimeout);
        if (locationListener != null) {
            getSystemService(LocationManager.class).removeUpdates(locationListener);
            locationListener = null;
        }
        locationResult = null;
    }

    @Override protected void onDestroy() {
        stopLocation();
        store.close();
        super.onDestroy();
    }

    private void back() {
        button(R.string.demo_back, this::home);
    }

    private EditText input(int hint, int type) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextColor(INK);
        field.setHintTextColor(0xff476268);
        field.setInputType(type);
        field.setSaveEnabled(false);
        content.addView(field, new LinearLayout.LayoutParams(-1, -2));
        return field;
    }

    private TextView text(int value, int size) {
        return text(getString(value), size);
    }

    private TextView text(String value, int size) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(INK);
        label.setPadding(0, dp(8), 0, dp(6));
        content.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return label;
    }

    private void button(int label, Runnable action) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(50));
        button.setOnClickListener(view -> action.run());
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private void toast(int text) {
        toast(getString(text));
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String value(Cursor row, String key) {
        return row.getString(row.getColumnIndexOrThrow(key));
    }

    private static double decimal(Cursor row, String key) {
        return row.getDouble(row.getColumnIndexOrThrow(key));
    }

    private static long number(Cursor row, String key) {
        return row.getLong(row.getColumnIndexOrThrow(key));
    }
}

