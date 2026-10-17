-- Keep historical v1 recommendations traceable; new arithmetic has its own runtime identity.
INSERT INTO intelligence.model_version (
    id, model_id, version, model_type, risk_class, status, released_at,
    runtime_key, provider, capabilities, limitations
) VALUES (
    '00000000-0000-8000-8000-000000000002',
    'LifeFit', 'v2-decimal', 'RULE_ENGINE', 'R2', 'ACTIVE', now(),
    'lifefit-v2-decimal', 'OULA',
    '["hard_constraint_filtering","decimal_weighted_scoring","dimension_explanation"]'::jsonb,
    '["not_a_market_valuation","not_a_legal_determination","uncalibrated_coverage_heuristic","DECIMAL128_division_then_HALF_UP_two_decimals","not_a_probability_of_success"]'::jsonb
);
