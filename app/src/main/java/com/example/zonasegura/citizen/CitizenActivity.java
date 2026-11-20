package com.example.zonasegura.citizen;

import android.os.Bundle;
import android.content.Intent;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.example.zonasegura.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class CitizenActivity extends AppCompatActivity {
    private BottomNavigationView navigation;
    private int currentTab = R.id.navCitizenMap;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_citizen);
        CitizenUi.insets(findViewById(R.id.citizenRoot));
        navigation = findViewById(R.id.citizenNavigation);
        navigation.setOnItemSelectedListener(item -> { select(item.getItemId()); return true; });
        currentTab = state == null ? R.id.navCitizenMap : state.getInt("currentTab", R.id.navCitizenMap);
        navigation.setSelectedItemId(currentTab);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (currentTab != R.id.navCitizenMap) openTab(R.id.navCitizenMap);
                else requestExit();
            }
        });
    }

    public void openTab(int id) { navigation.setSelectedItemId(id); }

    public void requestExit() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.zs_exit_title).setMessage(R.string.zs_exit_body)
                .setNegativeButton(R.string.zs_cancel, null)
                .setPositiveButton(R.string.zs_exit_action, (dialog, which) -> finish()).show();
    }

    private void select(int id) {
        currentTab = id;
        String tag = "citizen-tab-" + id;
        Fragment selected = getSupportFragmentManager().findFragmentByTag(tag);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (Fragment fragment : getSupportFragmentManager().getFragments()) transaction.hide(fragment);
        if (selected == null) {
            if (id == R.id.navCitizenReport) selected = new ReportFormFragment();
            else if (id == R.id.navCitizenValidate) selected = new ReportsFragment();
            else if (id == R.id.navCitizenProfile) selected = new CitizenProfileFragment();
            else selected = new CitizenMapFragment();
            transaction.add(R.id.citizenContent, selected, tag);
        } else transaction.show(selected);
        transaction.commitNow();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("showMap", false)) openTab(R.id.navCitizenMap);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("currentTab", currentTab);
        super.onSaveInstanceState(state);
    }
}
