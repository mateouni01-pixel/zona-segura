package com.example.zonasegura.citizen;

public final class CitizenReport {
    public int id;
    public int typeId;
    public String description;
    public String state = "PENDIENTE_VALIDACION";
    public double latitude;
    public double longitude;
    public long createdAt;
    public boolean mine;
    public String photoUri = "";
    public int positiveVotes;
    public int negativeVotes;
    public int myVote; // 0: sin votar; 1: confirma; -1: falso/spam.

    public int voteCount() { return positiveVotes + negativeVotes; }
    public int confirmationPercent() {
        return voteCount() == 0 ? 0 : Math.round(100f * positiveVotes / voteCount());
    }
    public int distanceMeters() {
        return ReportRules.distanceMeters(-17.783300, -63.182100, latitude, longitude);
    }
}
