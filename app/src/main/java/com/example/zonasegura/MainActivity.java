package com.example.zonasegura;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import java.io.IOException;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextView loginStatus;
    private MaterialButton loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SessionStore.load(this) != null) {
            startActivity(new Intent(this, InterfacesActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginStatus = findViewById(R.id.loginStatus);
        loginButton = findViewById(R.id.loginButton);

        findViewById(R.id.registerAccount).setOnClickListener(v ->
                startActivity(new Intent(this, CommunityActivity.class).putExtra("page", "Crear cuenta")));
        findViewById(R.id.recoverAccount).setOnClickListener(v ->
                startActivity(new Intent(this, CommunityActivity.class).putExtra("page", "Recuperar contraseña")));
        loginButton.setOnClickListener(view -> submitLogin());
        passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin();
                return true;
            }
            return false;
        });
    }

    private void submitLogin() {
        String email = emailInput.getText() == null ? "" : emailInput.getText().toString().trim();
        String password = passwordInput.getText() == null ? "" : passwordInput.getText().toString();
        LoginValidator.Result result = LoginValidator.validate(email, password);

        emailLayout.setError(null);
        passwordLayout.setError(null);
        loginStatus.setVisibility(View.GONE);

        if (result.emailError != LoginValidator.EmailError.NONE) {
            emailLayout.setError(getString(result.emailError == LoginValidator.EmailError.EMPTY
                    ? R.string.login_email_required : R.string.login_email_invalid));
        }
        if (result.passwordMissing) {
            passwordLayout.setError(getString(R.string.login_password_required));
        }
        if (!result.isValid()) {
            if (result.emailError != LoginValidator.EmailError.NONE) {
                emailInput.requestFocus();
            } else {
                passwordInput.requestFocus();
            }
            return;
        }

        loginButton.setEnabled(false);
        loginStatus.setText(R.string.login_loading);
        loginStatus.setVisibility(View.VISIBLE);
        new Thread(() -> {
            AuthApi.Result response;
            try {
                response = AuthApi.login(getString(R.string.api_base_url), email, password);
            } catch (IOException exception) {
                response = new AuthApi.Result(0, null, null);
            }
            AuthApi.Result outcome = response;
            runOnUiThread(() -> {
                loginButton.setEnabled(true);
                if (outcome.status == 200) {
                    try {
                        SessionStore.save(this, outcome.token);
                        passwordInput.setText("");
                        startActivity(new Intent(this, InterfacesActivity.class));
                        finish();
                    } catch (Exception exception) {
                        loginStatus.setText(R.string.login_session_error);
                    }
                } else if (outcome.status == 401) {
                    loginStatus.setText(R.string.login_invalid_credentials);
                } else if (outcome.status == -1) {
                    loginStatus.setText(R.string.login_backend_pending);
                } else if (outcome.status == 0) {
                    loginStatus.setText(R.string.login_connection_error);
                } else {
                    loginStatus.setText(R.string.login_server_error);
                }
            });
        }).start();
    }
}
