package com.example.zonasegura.citizen;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.zonasegura.R;
import java.util.Locale;

final class CitizenUi {
    private CitizenUi() { }
    static void insets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
    static String type(Context context, int id) { return context.getString(id == 1 ? R.string.zs_lighting : R.string.zs_infrastructure); }
    static int stateLabel(String state) {
        switch (state) {
            case "VALIDADO": return R.string.zs_validated;
            case "RECHAZADO": return R.string.zs_rejected;
            case "EN_ATENCION": return R.string.zs_attention;
            case "ATENDIDO": return R.string.zs_attended;
            default: return R.string.zs_pending;
        }
    }
    static String distance(int meters) {
        return meters < 1000 ? meters + " m" : String.format(new Locale("es", "BO"), "%.1f km", meters / 1000f);
    }
    static String coordinates(CitizenReport report) {
        return String.format(Locale.US, "%.6f, %.6f", report.latitude, report.longitude);
    }
    static void showPhoto(ImageView image, String uri) {
        image.setVisibility(View.GONE);
        if (uri == null || uri.isEmpty()) return;
        try {
            image.setImageURI(Uri.parse(uri));
            if (image.getDrawable() != null) image.setVisibility(View.VISIBLE);
        } catch (SecurityException | IllegalArgumentException ignored) {
            image.setImageDrawable(null);
        }
    }
    static void addCard(LinearLayout container, CitizenReport report) {
        Context context = container.getContext();
        View card = LayoutInflater.from(context).inflate(R.layout.item_citizen_report, container, false);
        long hours = Math.max(0, (System.currentTimeMillis() - report.createdAt) / 3600000L);
        String age = hours == 0 ? context.getString(R.string.zs_just_now) : context.getString(R.string.zs_hours_ago, hours);
        ((TextView) card.findViewById(R.id.reportCardMeta)).setText(context.getString(R.string.zs_report_meta, report.id, age));
        ((TextView) card.findViewById(R.id.reportCardType)).setText(type(context, report.typeId));
        ((TextView) card.findViewById(R.id.reportCardDescription)).setText(report.description);
        TextView state = card.findViewById(R.id.reportCardState);
        state.setText(stateLabel(report.state));
        if ("VALIDADO".equals(report.state)) {
            state.setBackgroundResource(R.drawable.zs_badge);
            state.setTextColor(context.getColor(R.color.zs_green));
        }
        ((TextView) card.findViewById(R.id.reportCardDistance)).setText(context.getString(R.string.zs_distance, distance(report.distanceMeters())));
        View button = card.findViewById(R.id.reportCardOpen);
        button.setContentDescription(context.getString(R.string.zs_open_report) + " R-" + report.id);
        button.setOnClickListener(view -> context.startActivity(new Intent(context, ReportDetailActivity.class).putExtra("reportId", report.id)));
        container.addView(card);
    }
}
