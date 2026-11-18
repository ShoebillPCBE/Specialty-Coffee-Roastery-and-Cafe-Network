package ro.upt.cafe.agent.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ro.upt.cafe.agent.config.AgentProperties.Cafe;

final class Geo {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Geo() {}

    static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static Map<String, Map<String, Double>> distanceMatrix(List<Cafe> cafes) {
        Map<String, Map<String, Double>> matrix = new LinkedHashMap<>();
        for (Cafe a : cafes) {
            Map<String, Double> row = new LinkedHashMap<>();
            for (Cafe b : cafes) {
                if (!a.id().equals(b.id())) {
                    row.put(b.id(), Math.round(haversineKm(a.lat(), a.lng(), b.lat(), b.lng()) * 100) / 100.0);
                }
            }
            matrix.put(a.id(), row);
        }
        return matrix;
    }
}
