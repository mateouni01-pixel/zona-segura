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

public class MunicipalActivity extends AppCompatActivity {
    private TextView welcome;
    private TextView cargo;
    private TextView access;
    private TextView status;
    private MaterialButton retry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!InterfaceNavigation.exigir(this,InterfazUsuario.ALCALDIA))return;
        setContentView(R.layout.activity_municipal);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.municipalRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        welcome = findViewById(R.id.municipalWelcome);
        cargo = findViewById(R.id.municipalCargo);
        access = findViewById(R.id.municipalAccess);
        status = findViewById(R.id.municipalStatus);
        retry = findViewById(R.id.municipalRetry);
        retry.setOnClickListener(view -> checkAccess());
        findViewById(R.id.municipalLogout).setOnClickListener(view -> logout());
        InterfaceNavigation.agregarSelector(this,R.id.municipalStatus);
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
            AuthApi.MunicipalProfile profile;
            try {
                profile = AuthApi.municipalProfile(getString(R.string.api_base_url), token);
            } catch (IOException exception) {
                profile = new AuthApi.MunicipalProfile(0, null, null, null);
            }
            AuthApi.MunicipalProfile result = profile;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (result.status == 200) {
                    welcome.setText(getString(R.string.municipal_welcome, result.name));
                    cargo.setText(getString(R.string.municipal_cargo, result.cargo));
                    access.setText(getString(R.string.municipal_access, result.accessRole));
                    CommunityActivity.addActions(this, R.id.municipalStatus, "municipal");
                    InterfaceNavigation.agregarSelector(this,R.id.municipalStatus);
                    status.setText(R.string.municipal_authorized);
                } else if (result.status == 401 || result.status == 403 || result.status == 404) {
                    if(result.status==401){SessionStore.clear(this);openLogin();}else InterfaceNavigation.selector(this);
                } else {
                    status.setText(R.string.municipal_error);
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
