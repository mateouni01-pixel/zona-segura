package com.example.zonasegura.citizen;

import android.os.Bundle;
import android.util.Patterns;
import androidx.appcompat.app.AppCompatActivity;
import com.example.zonasegura.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class RecoverAccessActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_citizen_recover);
        CitizenUi.insets(findViewById(R.id.recoverRoot));
        ((MaterialToolbar) findViewById(R.id.recoverToolbar)).setNavigationOnClickListener(view -> finish());
        findViewById(R.id.recoverSubmit).setOnClickListener(view -> {
            TextInputEditText input = findViewById(R.id.recoverEmail);
            TextInputLayout layout = findViewById(R.id.recoverEmailLayout);
            String email = input.getText() == null ? "" : input.getText().toString().trim();
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                layout.setError(getString(email.isEmpty() ? R.string.login_email_required : R.string.login_email_invalid)); return;
            }
            layout.setError(null);
            new MaterialAlertDialogBuilder(this).setTitle(R.string.zs_recover_result_title)
                    .setMessage(R.string.zs_recover_result_body).setPositiveButton(R.string.zs_back, null).show();
        });
    }
}
