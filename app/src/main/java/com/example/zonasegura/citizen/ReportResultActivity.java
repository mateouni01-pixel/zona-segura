package com.example.zonasegura.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.zonasegura.R;
import com.google.android.material.appbar.MaterialToolbar;

public final class ReportResultActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_citizen_result);
        CitizenUi.insets(findViewById(R.id.resultRoot));
        ((MaterialToolbar) findViewById(R.id.resultToolbar)).setNavigationOnClickListener(view -> finish());
        CitizenReport report = new DemoReportRepository(this).find(getIntent().getIntExtra("reportId", -1));
        if (report == null) { finish(); return; }
        int vote = getIntent().getIntExtra("vote", 0);
        ((TextView) findViewById(R.id.resultTitle)).setText(vote == 0 ? R.string.zs_confirm_title : R.string.zs_vote_done);
        ((TextView) findViewById(R.id.resultBody)).setText(vote == 0 ? R.string.zs_confirm_body
                : vote > 0 ? R.string.zs_vote_done_yes : R.string.zs_vote_done_no);
        ((TextView) findViewById(R.id.resultReference)).setText(getString(R.string.zs_reference, report.id));
        ((TextView) findViewById(R.id.resultType)).setText(CitizenUi.type(this, report.typeId));
        ((TextView) findViewById(R.id.resultState)).setText(CitizenUi.stateLabel(report.state));
        ((TextView) findViewById(R.id.resultNote)).setText(vote == 0 ? R.string.zs_public_author : R.string.zs_threshold_note);
        findViewById(R.id.resultOpen).setOnClickListener(view -> {
            startActivity(new Intent(this, ReportDetailActivity.class).putExtra("reportId", report.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish();
        });
        findViewById(R.id.resultMap).setOnClickListener(view -> {
            startActivity(new Intent(this, CitizenActivity.class).putExtra("showMap", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish();
        });
    }
}
