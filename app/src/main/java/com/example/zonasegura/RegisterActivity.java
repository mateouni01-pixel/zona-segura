package com.example.zonasegura;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.IOException;

public class RegisterActivity extends AppCompatActivity {
    private boolean submitting;
    private MaterialButton submit;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registerRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        submit = findViewById(R.id.registerSubmit);
        status = findViewById(R.id.registerStatus);
        submit.setOnClickListener(v -> register());
        findViewById(R.id.registerBack).setOnClickListener(v -> finish());
    }

    private String value(int id) {
        TextInputEditText input = findViewById(id);
        return input.getText() == null ? "" : input.getText().toString();
    }

    private boolean error(int id, boolean invalid, int message) {
        TextInputLayout field = findViewById(id);
        field.setError(invalid ? getString(message) : null);
        return invalid;
    }

    private void register() {
        if (submitting) return;
        String name = value(R.id.registerName).trim();
        String email = value(R.id.registerEmail).trim();
        String password = value(R.id.registerPassword);
        boolean invalid = error(R.id.registerNameLayout, name.isEmpty() || name.length() > 150, R.string.register_name_error);
        invalid |= error(R.id.registerEmailLayout, email.length() > 254 ||
                LoginValidator.validate(email, password).emailError != LoginValidator.EmailError.NONE, R.string.login_email_invalid);
        invalid |= error(R.id.registerPasswordLayout, password.length() < 8 ||
                password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72, R.string.register_password_error);
        invalid |= error(R.id.registerConfirmLayout, !password.equals(value(R.id.registerConfirm)), R.string.register_confirm_error);
        status.setVisibility(View.GONE);
        if (invalid) return;
        submitting = true;
        submit.setEnabled(false);
        status.setText(R.string.register_loading);
        status.setVisibility(View.VISIBLE);
        // Evita otra solicitud al girar el dispositivo mientras se envía el formulario.
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        new Thread(() -> {
            int response;
            try { response = AuthApi.register(getString(R.string.api_base_url), name, email, password); }
            catch (IOException exception) { response = 0; }
            int result = response;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                submitting = false;
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                if (result == 201) {
                    ((TextInputEditText)findViewById(R.id.registerPassword)).setText("");
                    ((TextInputEditText)findViewById(R.id.registerConfirm)).setText("");
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.register_success_title)
                            .setMessage(R.string.register_success)
                            .setCancelable(false)
                            .setPositiveButton(R.string.register_back, (dialog, which) -> finish()).show();
                } else {
                    submit.setEnabled(true);
                    status.setText(result == 409 ? R.string.register_duplicate :
                            result == 400 ? R.string.register_invalid :
                            result == -1 ? R.string.login_backend_pending :
                            result == 0 ? R.string.register_connection_error : R.string.login_server_error);
                }
            });
        }).start();
    }
}
