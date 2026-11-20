package com.example.zonasegura.citizen;

public final class ReportRules {
    public enum VoteBlock { NONE, OWN_REPORT, ALREADY_VOTED, TOO_FAR, CLOSED }
    private ReportRules() { }

    public static boolean validDescription(String text) {
        return text != null && text.trim().length() >= 10 && text.trim().length() <= 2000;
    }

    public static boolean validCoordinates(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    public static VoteBlock voteBlock(CitizenReport report) {
        if (report.mine) return VoteBlock.OWN_REPORT;
        if (report.myVote != 0) return VoteBlock.ALREADY_VOTED;
        if (!"PENDIENTE_VALIDACION".equals(report.state)) return VoteBlock.CLOSED;
        if (!withinVotingRadius(report)) return VoteBlock.TOO_FAR;
        return VoteBlock.NONE;
    }

    public static int distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        return (int) Math.round(exactDistanceMeters(lat1, lon1, lat2, lon2));
    }

    public static boolean withinVotingRadius(CitizenReport report) {
        return exactDistanceMeters(-17.783300, -63.182100, report.latitude, report.longitude) <= 500.000001;
    }

    private static double exactDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(deltaLon / 2), 2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
    }
}
