package com.example.zonasegura.citizen;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.zonasegura.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class CitizenProfileFragment extends Fragment {
    public CitizenProfileFragment() { super(R.layout.fragment_citizen_profile); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        view.findViewById(R.id.citizenExitDemo).setOnClickListener(button -> ((CitizenActivity) requireActivity()).requestExit());
        view.findViewById(R.id.citizenResetDemo).setOnClickListener(button -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.zs_reset_title).setMessage(R.string.zs_reset_body).setNegativeButton(R.string.zs_cancel, null)
                .setPositiveButton(R.string.zs_reset_action, (dialog, which) -> {
                    new DemoReportRepository(requireContext()).resetDemo(); refresh();
                    Toast.makeText(requireContext(), R.string.zs_reset_done, Toast.LENGTH_SHORT).show();
                }).show());
        refresh();
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    @Override public void onHiddenChanged(boolean hidden) { super.onHiddenChanged(hidden); if (!hidden) refresh(); }
    private void refresh() {
        if (getView() == null) return;
        int reports = 0, votes = 0;
        for (CitizenReport report : new DemoReportRepository(requireContext()).all()) {
            if (report.mine) reports++;
            if (report.myVote != 0) votes++;
        }
        ((TextView) getView().findViewById(R.id.citizenProfileCounts)).setText(getString(R.string.zs_profile_counts, reports, votes));
    }
}
