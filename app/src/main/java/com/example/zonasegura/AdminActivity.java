package com.example.zonasegura;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;

public class AdminActivity extends AppCompatActivity {
    private TextView welcome;
    private TextView status;
    private View retry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!InterfaceNavigation.exigir(this,InterfazUsuario.ADMINISTRADOR))return;
        setContentView(R.layout.activity_admin);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.adminRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        welcome = findViewById(R.id.adminWelcome);
        status = findViewById(R.id.adminStatus);
        retry = findViewById(R.id.adminRetry);
        retry.setOnClickListener(view -> checkAccess());
        findViewById(R.id.adminLogout).setOnClickListener(view -> logout());
        checkAccess();
    }

    private void checkAccess() {
        String token = SessionStore.load(this);
        if (token == null) {
            openLogin();
            return;
        }
        status.setText(R.string.home_checking);
        retry.setVisibility(View.GONE);
        new Thread(() -> {
            AuthApi.Profile profile;
            int access = 0;
            try {
                profile = AuthApi.profile(getString(R.string.api_base_url), token);
                if (profile.status == 200 && profile.hasRole("ADMINISTRADOR")) {
                    SessionStore.remember(this,profile);
                    access = AuthApi.adminAccess(getString(R.string.api_base_url), token);
                }
            } catch (IOException exception) {
                profile = new AuthApi.Profile(0, null, null);
            }
            AuthApi.Profile result = profile;
            int accessResult = access;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (result.status == 200 && accessResult == 200) {
                    welcome.setText(getString(R.string.admin_welcome, result.name));
                    CommunityActivity.addActions(this, R.id.adminStatus, "admin");
                    InterfaceNavigation.agregarSelector(this,R.id.adminStatus);
                    status.setText(R.string.admin_authorized);
                } else if (result.status == 401 || result.status == 403
                        || (result.status == 200 && (accessResult == 401 || accessResult == 403
                        || !result.hasRole("ADMINISTRADOR")))) {
                    if(result.status==401||accessResult==401){SessionStore.clear(this);openLogin();}
                    else InterfaceNavigation.selector(this);
                } else {
                    status.setText(R.string.admin_error);
                    retry.setVisibility(View.VISIBLE);
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
