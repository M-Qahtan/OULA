package com.oula.matching;

import com.oula.intent.Intent;
import com.oula.property.PropertyCandidate;

import java.util.Comparator;
import java.util.List;

public final class CandidateRetrievalService {
    public List<PropertyCandidate> retrieve(Intent intent, List<PropertyCandidate> supply) {
        if (!intent.isActive()) {
            throw new IllegalStateException("intent must be ACTIVE before matching");
        }
        return supply.stream()
                .filter(p -> p.askingPrice().compareTo(intent.budgetMax()) <= 0)
                .filter(p -> p.bedrooms() >= intent.minimumBedrooms())
                .filter(p -> intent.preferredDistricts().isEmpty()
                        || intent.preferredDistricts().contains(p.district()))
                .sorted(Comparator
                        .comparing(PropertyCandidate::askingPrice)
                        .thenComparing(PropertyCandidate::propertyId))
                .toList();
    }
}
