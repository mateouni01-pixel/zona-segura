package com.example.zonasegura.citizen;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Almacén exclusivo de demostración. No comparte datos, tokens ni usuarios con la API. */
public final class DemoReportRepository implements ReportRepository {
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;

    public DemoReportRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("citizen_demo", Context.MODE_PRIVATE);
        synchronized (LOCK) {
            if (!preferences.contains("reports")) resetDemo();
        }
    }

    @Override public List<CitizenReport> all() {
        synchronized (LOCK) {
            List<CitizenReport> reports = read();
            reports.sort(Comparator.comparingLong((CitizenReport report) -> report.createdAt).reversed());
            return reports;
        }
    }

    @Override public CitizenReport find(int id) {
        for (CitizenReport report : all()) if (report.id == id) return report;
        return null;
    }

    @Override public CitizenReport create(int typeId, String description, double latitude, double longitude, String photoUri) {
        if ((typeId != 1 && typeId != 2) || !ReportRules.validDescription(description)
                || !ReportRules.validCoordinates(latitude, longitude)) throw new IllegalArgumentException("Reporte inválido");
        synchronized (LOCK) {
            List<CitizenReport> reports = read();
            int maxId = 1047;
            for (CitizenReport report : reports) maxId = Math.max(maxId, report.id);
            CitizenReport report = new CitizenReport();
            report.id = maxId + 1;
            report.typeId = typeId;
            report.description = description.trim();
            report.latitude = latitude;
            report.longitude = longitude;
            report.createdAt = System.currentTimeMillis();
            report.mine = true;
            report.photoUri = photoUri == null ? "" : photoUri;
            reports.add(report);
            write(reports);
            return report;
        }
    }

    @Override public ReportRules.VoteBlock vote(int id, boolean positive) {
        synchronized (LOCK) {
            List<CitizenReport> reports = read();
            for (CitizenReport report : reports) {
                if (report.id != id) continue;
                ReportRules.VoteBlock block = ReportRules.voteBlock(report);
                if (block != ReportRules.VoteBlock.NONE) return block;
                report.myVote = positive ? 1 : -1;
                if (positive) report.positiveVotes++; else report.negativeVotes++;
                // No se inventan umbrales de negocio: el prototipo conserva el estado.
                write(reports);
                return ReportRules.VoteBlock.NONE;
            }
            return ReportRules.VoteBlock.CLOSED;
        }
    }

    @Override public void resetDemo() {
        synchronized (LOCK) {
            List<CitizenReport> reports = new ArrayList<>();
            reports.add(example(1042, 1, "La cuadra está sin alumbrado desde hace tres noches.", -17.780420, -63.182100, 1, 2, 3));
            reports.add(example(1040, 2, "Hay una tapa de alcantarilla rota junto al paso peatonal.", -17.784400, -63.183000, 2, 1, 0));
            CitizenReport far = example(1038, 2, "La acera del parque necesita mantenimiento para permitir el paso.", -17.772500, -63.182100, 4, 6, 1);
            far.state = "VALIDADO";
            reports.add(far);
            reports.add(example(1037, 1, "Dos luminarias de la avenida permanecen apagadas.", -17.790000, -63.182100, 5, 1, 1));
            write(reports);
        }
    }

    private CitizenReport example(int id, int typeId, String description, double latitude, double longitude, int hours, int positive, int negative) {
        CitizenReport report = new CitizenReport();
        report.id = id;
        report.typeId = typeId;
        report.description = description;
        report.latitude = latitude;
        report.longitude = longitude;
        report.createdAt = System.currentTimeMillis() - hours * 3600000L;
        report.positiveVotes = positive;
        report.negativeVotes = negative;
        return report;
    }

    private List<CitizenReport> read() {
        List<CitizenReport> reports = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString("reports", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject value = array.getJSONObject(i);
                CitizenReport report = new CitizenReport();
                report.id = value.getInt("id");
                report.typeId = value.getInt("typeId");
                report.description = value.getString("description");
                report.state = value.getString("state");
                report.latitude = value.getDouble("latitude");
                report.longitude = value.getDouble("longitude");
                report.createdAt = value.getLong("createdAt");
                report.mine = value.getBoolean("mine");
                report.photoUri = value.optString("photoUri", "");
                report.positiveVotes = value.getInt("positiveVotes");
                report.negativeVotes = value.getInt("negativeVotes");
                report.myVote = value.getInt("myVote");
                reports.add(report);
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("No se pudieron leer los datos de demostración.", exception);
        }
        return reports;
    }

    private void write(List<CitizenReport> reports) {
        JSONArray array = new JSONArray();
        try {
            for (CitizenReport report : reports) {
                array.put(new JSONObject().put("id", report.id).put("typeId", report.typeId)
                        .put("description", report.description).put("state", report.state)
                        .put("latitude", report.latitude).put("longitude", report.longitude)
                        .put("createdAt", report.createdAt).put("mine", report.mine)
                        .put("photoUri", report.photoUri).put("positiveVotes", report.positiveVotes)
                        .put("negativeVotes", report.negativeVotes).put("myVote", report.myVote));
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("No se pudieron guardar los datos de demostración.", exception);
        }
        preferences.edit().putString("reports", array.toString()).apply();
    }
}
