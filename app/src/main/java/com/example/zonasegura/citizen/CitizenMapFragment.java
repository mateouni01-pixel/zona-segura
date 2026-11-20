package com.example.zonasegura.citizen;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.zonasegura.R;
import com.google.android.material.chip.ChipGroup;

public final class CitizenMapFragment extends Fragment {
    public CitizenMapFragment() { super(R.layout.fragment_citizen_map); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        view.findViewById(R.id.mapCreateReport).setOnClickListener(button -> ((CitizenActivity) requireActivity()).openTab(R.id.navCitizenReport));
        ((ChipGroup) view.findViewById(R.id.mapFilters)).setOnCheckedStateChangeListener((group, checked) -> refresh());
        refresh();
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    @Override public void onHiddenChanged(boolean hidden) { super.onHiddenChanged(hidden); if (!hidden) refresh(); }
    private void refresh() {
        View view = getView(); if (view == null) return;
        LinearLayout list = view.findViewById(R.id.mapReportList);
        list.removeAllViews();
        int filter = ((ChipGroup) view.findViewById(R.id.mapFilters)).getCheckedChipId();
        for (CitizenReport report : new DemoReportRepository(requireContext()).all()) {
            if (filter == R.id.mapFilterLighting && report.typeId != 1) continue;
            if (filter == R.id.mapFilterInfrastructure && report.typeId != 2) continue;
            CitizenUi.addCard(list, report);
        }
        if (list.getChildCount() == 0) {
            TextView empty = new TextView(requireContext()); empty.setText(R.string.zs_filter_empty); list.addView(empty);
        }
    }
}
