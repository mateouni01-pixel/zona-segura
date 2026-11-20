package com.example.zonasegura.citizen;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.zonasegura.R;
import com.google.android.material.chip.ChipGroup;

public final class ReportsFragment extends Fragment {
    public ReportsFragment() { super(R.layout.fragment_citizen_reports); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        view.findViewById(R.id.citizenEmptyCreate).setOnClickListener(button -> ((CitizenActivity) requireActivity()).openTab(R.id.navCitizenReport));
        ((ChipGroup) view.findViewById(R.id.reportFilters)).setOnCheckedStateChangeListener((group, checked) -> refresh());
        refresh();
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    @Override public void onHiddenChanged(boolean hidden) { super.onHiddenChanged(hidden); if (!hidden) refresh(); }
    private void refresh() {
        View view = getView(); if (view == null) return;
        boolean mine = ((ChipGroup) view.findViewById(R.id.reportFilters)).getCheckedChipId() == R.id.reportFilterMine;
        LinearLayout list = view.findViewById(R.id.citizenReportList);
        list.removeAllViews();
        for (CitizenReport report : new DemoReportRepository(requireContext()).all()) {
            if (mine ? !report.mine : report.mine || !ReportRules.withinVotingRadius(report)) continue;
            CitizenUi.addCard(list, report);
        }
        view.findViewById(R.id.citizenReportsEmpty).setVisibility(list.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
