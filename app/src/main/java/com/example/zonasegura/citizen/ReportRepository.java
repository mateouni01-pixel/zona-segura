package com.example.zonasegura.citizen;

import java.util.List;

/** Contrato local del prototipo; la implementación HTTP futura debe conservar los modelos de UI. */
public interface ReportRepository {
    List<CitizenReport> all();
    CitizenReport find(int id);
    CitizenReport create(int typeId, String description, double latitude, double longitude, String photoUri);
    ReportRules.VoteBlock vote(int id, boolean positive);
    void resetDemo();
}
