package com.oula.intelligence;

import com.oula.iam.AccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RealityMemoryService {
    private final RealityMemoryRepository repository;

    public RealityMemoryService(RealityMemoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RealityCaseView realityCase(AccessContext access, UUID outcomeId) {
        requireAccess(access);
        return repository.realityCase(access.workspaceId(), Objects.requireNonNull(outcomeId));
    }

    @Transactional(readOnly = true)
    public List<CalibrationProjectionView> calibration(
            AccessContext access,
            UUID modelVersionId
    ) {
        requireAccess(access);
        return repository.calibration(
                access.workspaceId(),
                Objects.requireNonNull(modelVersionId)
        );
    }

    private void requireAccess(AccessContext access) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(access.actorId(), "actorId");
        Objects.requireNonNull(access.workspaceId(), "workspaceId");
        Objects.requireNonNull(access.purpose(), "purpose");
    }
}
