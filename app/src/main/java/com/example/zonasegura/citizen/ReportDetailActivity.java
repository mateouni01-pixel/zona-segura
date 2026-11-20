package com.example.zonasegura.citizen;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.zonasegura.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public final class ReportDetailActivity extends AppCompatActivity {
    private int reportId;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_citizen_detail);
        CitizenUi.insets(findViewById(R.id.detailRoot));
        ((MaterialToolbar) findViewById(R.id.detailToolbar)).setNavigationOnClickListener(view -> finish());
        reportId = getIntent().getIntExtra("reportId", -1);
        findViewById(R.id.detailConfirmVote).setOnClickListener(view -> confirmVote(true));
        findViewById(R.id.detailRejectVote).setOnClickListener(view -> confirmVote(false));
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
    private void refresh() {
        CitizenReport report = new DemoReportRepository(this).find(reportId);
        if (report == null) {
            new MaterialAlertDialogBuilder(this).setMessage(R.string.zs_report_missing)
                    .setPositiveButton(R.string.zs_back, (dialog, which) -> finish()).setCancelable(false).show(); return;
        }
        ((TextView) findViewById(R.id.detailReference)).setText(getString(R.string.zs_reference, report.id));
        ((TextView) findViewById(R.id.detailType)).setText(CitizenUi.type(this, report.typeId));
        ((TextView) findViewById(R.id.detailState)).setText(CitizenUi.stateLabel(report.state));
        ((TextView) findViewById(R.id.detailDescription)).setText(report.description);
        ((TextView) findViewById(R.id.detailCoordinates)).setText(CitizenUi.coordinates(report));
        ((TextView) findViewById(R.id.detailDistance)).setText(getString(R.string.zs_distance, CitizenUi.distance(report.distanceMeters())));
        ((TextView) findViewById(R.id.detailVeracity)).setText(getString(R.string.zs_veracity, report.confirmationPercent(), report.voteCount()));
        ((LinearProgressIndicator) findViewById(R.id.detailProgress)).setProgress(report.confirmationPercent());
        CitizenUi.showPhoto(findViewById(R.id.detailPhoto), report.photoUri);
        ReportRules.VoteBlock block = ReportRules.voteBlock(report);
        TextView status = findViewById(R.id.detailVoteStatus);
        status.setVisibility(block == ReportRules.VoteBlock.NONE ? View.GONE : View.VISIBLE);
        if (block != ReportRules.VoteBlock.NONE) status.setText(blockLabel(block));
        findViewById(R.id.detailConfirmVote).setEnabled(block == ReportRules.VoteBlock.NONE);
        findViewById(R.id.detailRejectVote).setEnabled(block == ReportRules.VoteBlock.NONE);
    }
    private int blockLabel(ReportRules.VoteBlock block) {
        switch (block) {
            case OWN_REPORT: return R.string.zs_vote_own;
            case ALREADY_VOTED: return R.string.zs_vote_duplicate;
            case TOO_FAR: return R.string.zs_vote_far;
            default: return R.string.zs_vote_closed;
        }
    }
    private void confirmVote(boolean positive) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.zs_vote_confirm_title)
                .setMessage(positive ? R.string.zs_vote_confirm_yes : R.string.zs_vote_confirm_no)
                .setNegativeButton(R.string.zs_cancel, null).setPositiveButton(R.string.zs_vote_record, (dialog, which) -> {
                    ReportRules.VoteBlock block = new DemoReportRepository(this).vote(reportId, positive);
                    refresh();
                    if (block == ReportRules.VoteBlock.NONE) startActivity(new Intent(this, ReportResultActivity.class)
                            .putExtra("reportId", reportId).putExtra("vote", positive ? 1 : -1));
                }).show();
    }
}
