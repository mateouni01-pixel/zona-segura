package com.example.zonasegura;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;

public class HomeActivity extends AppCompatActivity {
    private TextView welcome;
    private TextView status;
    private MaterialButton retryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!InterfaceNavigation.exigir(this,InterfazUsuario.CIUDADANO))return;
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        welcome = findViewById(R.id.homeWelcome);
        status = findViewById(R.id.homeStatus);
        retryButton = findViewById(R.id.retryButton);
        retryButton.setOnClickListener(view -> checkSession());
        findViewById(R.id.logoutButton).setOnClickListener(view -> logout());
        InterfaceNavigation.agregarSelector(this,R.id.homeStatus);
        checkSession();
    }

    private void checkSession() {
        String token = SessionStore.load(this);
        if (token == null) {
            openLogin();
            return;
        }
        status.setText(R.string.home_checking);
        findViewById(R.id.createReportButton).setVisibility(View.GONE);
        findViewById(R.id.reportsButton).setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        new Thread(() -> {
            AuthApi.Profile profile;
            try {
                profile = AuthApi.profile(getString(R.string.api_base_url), token);
            } catch (IOException exception) {
                profile = new AuthApi.Profile(0, null, null);
            }
            boolean offline=profile.status==0 || profile.status>=500;
            if(offline)profile=SessionStore.offlineProfile(this);
            AuthApi.Profile result = profile;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (result.status == 200) {
                    if(!offline)SessionStore.remember(this,result);
                    if(!result.hasRole("CIUDADANO")){InterfaceNavigation.selector(this);return;}
                    welcome.setText(getString(R.string.home_welcome, result.name));
                    status.setText(getString(R.string.home_role,"CIUDADANO"));
                    if (result.hasRole("CIUDADANO")) {
                        CitizenNavigation.attach(this, R.id.nav_inicio);
                        ReportSyncWorker.resume(this,false);
                        if(offline)status.setText("Sin conexión. Puedes guardar reportes pendientes y consultar el historial local.");
                        View reports = findViewById(R.id.reportsButton);
                        View create = findViewById(R.id.createReportButton);
                        create.setVisibility(View.VISIBLE);
                        create.setOnClickListener(view -> startActivity(new Intent(this, ReportsActivity.class)
                                .putExtra(ReportsActivity.EXTRA_CREATE_REPORT, true)));
                        CommunityActivity.addActions(this, R.id.reportsButton, "citizen");
                        reports.setVisibility(View.VISIBLE);
                        reports.setOnClickListener(view -> startActivity(
                                new Intent(this, ReportsActivity.class)));
                        InterfaceNavigation.agregarSelector(this,R.id.homeStatus);
                    }
                } else if (result.status == 401 || result.status == 403) {
                    SessionStore.clear(this);
                    openLogin();
                } else {
                    status.setText(R.string.home_connection_error);
                    retryButton.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void logout() {
        SessionStore.clear(this);
        openLogin();
    }

    private void openLogin() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
