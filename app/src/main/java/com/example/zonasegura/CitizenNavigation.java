package com.example.zonasegura;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

final class CitizenNavigation {
    private CitizenNavigation() {}

    static void attach(AppCompatActivity host, int selected) {
        ViewGroup content = host.findViewById(android.R.id.content);
        if (content.findViewWithTag("citizen-navigation") != null) return;
        View screen = content.getChildAt(0);
        content.removeView(screen);
        LinearLayout column = new LinearLayout(host);
        column.setOrientation(LinearLayout.VERTICAL);
        content.addView(column);
        column.addView(screen, new LinearLayout.LayoutParams(-1, 0, 1));

        BottomNavigationView bar = new BottomNavigationView(host);
        bar.setTag("citizen-navigation");
        bar.inflateMenu(R.menu.citizen_navigation);
        bar.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        bar.setSelectedItemId(selected);
        bar.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selected) return true;
            Intent intent;
            if (id == R.id.nav_inicio) {
                intent = new Intent(host, HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            } else if (id == R.id.nav_mapa) {
                intent = new Intent(host, RiskMapActivity.class);
            } else if (id == R.id.nav_reportes) {
                intent = new Intent(host, ReportsActivity.class);
            } else {
                intent = new Intent(host, CommunityActivity.class).putExtra("page", "Validar reportes");
            }
            host.startActivity(intent);
            if (!(host instanceof HomeActivity)) host.finish();
            return false;
        });
        column.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }
}
