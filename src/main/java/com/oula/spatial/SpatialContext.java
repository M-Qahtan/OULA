package com.oula.spatial;
import java.util.UUID;
public record SpatialContext(UUID placeId, String city, String district, double latitude, double longitude) {
  public SpatialContext {
    if (placeId == null || city == null || district == null) throw new NullPointerException();
    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) throw new IllegalArgumentException("invalid coordinates");
  }
}
