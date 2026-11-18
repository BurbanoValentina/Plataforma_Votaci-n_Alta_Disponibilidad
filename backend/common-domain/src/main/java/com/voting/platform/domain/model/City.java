package com.voting.platform.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Value Object representing a voter's city with deterministic Region mapping.
 */
public record City(String value) {

    private static final Map<String, Region> CITY_TO_REGION;

    static {
        Map<String, Region> map = new LinkedHashMap<>();
        // CENTRO
        map.put("Bogotá", Region.CENTRO);
        map.put("Medellín", Region.CENTRO);
        map.put("Bucaramanga", Region.CENTRO);
        map.put("Manizales", Region.CENTRO);
        map.put("Tunja", Region.CENTRO);
        // NORTE
        map.put("Barranquilla", Region.NORTE);
        map.put("Cartagena", Region.NORTE);
        map.put("Santa Marta", Region.NORTE);
        map.put("Montería", Region.NORTE);
        // SUR
        map.put("Pasto", Region.SUR);
        map.put("Popayán", Region.SUR);
        map.put("Neiva", Region.SUR);
        map.put("Florencia", Region.SUR);
        // ESTE
        map.put("Cúcuta", Region.ESTE);
        map.put("Villavicencio", Region.ESTE);
        map.put("Yopal", Region.ESTE);
        map.put("Arauca", Region.ESTE);
        // OESTE
        map.put("Cali", Region.OESTE);
        map.put("Pereira", Region.OESTE);
        map.put("Armenia", Region.OESTE);
        map.put("Quibdó", Region.OESTE);
        CITY_TO_REGION = Collections.unmodifiableMap(map);
    }

    public City {
        Objects.requireNonNull(value, "City cannot be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            throw new IllegalArgumentException("City must be between 1 and 80 characters");
        }
        value = trimmed;
    }

    public static City of(String value) {
        return new City(value);
    }

    public Region resolveRegion() {
        for (Map.Entry<String, Region> entry : CITY_TO_REGION.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(value)) {
                return entry.getValue();
            }
        }
        return Region.CENTRO;
    }

    public static Region resolveRegionFor(String cityName, String explicitRegion) {
        if (cityName != null && !cityName.isBlank()) {
            for (Map.Entry<String, Region> entry : CITY_TO_REGION.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(cityName.trim())) {
                    return entry.getValue();
                }
            }
        }
        if (explicitRegion != null && !explicitRegion.isBlank()) {
            return Region.fromString(explicitRegion);
        }
        return Region.CENTRO;
    }

    public static Map<String, Region> catalog() {
        return CITY_TO_REGION;
    }

    public static List<String> allCityNames() {
        return List.copyOf(CITY_TO_REGION.keySet());
    }

    @Override
    public String toString() {
        return value;
    }
}
