-- OULA Riyadh DEMO-only opt-in fixture. NOT Flyway. Do not run in production.
-- Fictional inventory; no real seller, owner, title verification or public listing.
BEGIN;
INSERT INTO iam.workspace(id,workspace_type,name,status)
VALUES ('d0000000-0000-4000-8000-000000000001','PERSONAL','OULA Riyadh DEMO ONLY','ACTIVE')
ON CONFLICT (id) DO NOTHING;
INSERT INTO property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price,location)
VALUES ('d0000000-0000-4000-8000-000000000101','d0000000-0000-4000-8000-000000000001','RESIDENTIAL','الملقا',3,1250000,
 ST_SetSRID(ST_MakePoint(46.6,24.8),4326)::geography)
ON CONFLICT (id) DO NOTHING;
INSERT INTO property.fact(id,property_id,fact_key,value_json,truth_status,source_type)
VALUES ('d0000000-0000-4000-8000-000000000201','d0000000-0000-4000-8000-000000000101','intake_origin','{"value":"DEMO"}'::jsonb,'DECLARED','DEMO_FIXTURE')
ON CONFLICT (id) DO NOTHING;
INSERT INTO market.listing(id,workspace_id,property_id,transaction_type,status,asking_price,currency)
VALUES ('d0000000-0000-4000-8000-000000000301','d0000000-0000-4000-8000-000000000001','d0000000-0000-4000-8000-000000000101','SALE','DRAFT',1250000,'SAR')
ON CONFLICT (id) DO NOTHING;
INSERT INTO property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price,location)
VALUES ('d0000000-0000-4000-8000-000000000102','d0000000-0000-4000-8000-000000000001','RESIDENTIAL','الياسمين',4,1580000,
 ST_SetSRID(ST_MakePoint(46.67,24.82),4326)::geography)
ON CONFLICT (id) DO NOTHING;
INSERT INTO property.fact(id,property_id,fact_key,value_json,truth_status,source_type)
VALUES ('d0000000-0000-4000-8000-000000000202','d0000000-0000-4000-8000-000000000102','intake_origin','{"value":"DEMO"}'::jsonb,'DECLARED','DEMO_FIXTURE')
ON CONFLICT (id) DO NOTHING;
INSERT INTO market.listing(id,workspace_id,property_id,transaction_type,status,asking_price,currency)
VALUES ('d0000000-0000-4000-8000-000000000302','d0000000-0000-4000-8000-000000000001','d0000000-0000-4000-8000-000000000102','SALE','DRAFT',1580000,'SAR')
ON CONFLICT (id) DO NOTHING;
INSERT INTO property.asset(id,workspace_id,asset_type,district,bedrooms,asking_price,location)
VALUES ('d0000000-0000-4000-8000-000000000103','d0000000-0000-4000-8000-000000000001','RESIDENTIAL','حطين',2,980000,
 ST_SetSRID(ST_MakePoint(46.61,24.77),4326)::geography)
ON CONFLICT (id) DO NOTHING;
INSERT INTO property.fact(id,property_id,fact_key,value_json,truth_status,source_type)
VALUES ('d0000000-0000-4000-8000-000000000203','d0000000-0000-4000-8000-000000000103','intake_origin','{"value":"DEMO"}'::jsonb,'DECLARED','DEMO_FIXTURE')
ON CONFLICT (id) DO NOTHING;
INSERT INTO market.listing(id,workspace_id,property_id,transaction_type,status,asking_price,currency)
VALUES ('d0000000-0000-4000-8000-000000000303','d0000000-0000-4000-8000-000000000001','d0000000-0000-4000-8000-000000000103','SALE','DRAFT',980000,'SAR')
ON CONFLICT (id) DO NOTHING;
COMMIT;
-- Optional run: psql -v ON_ERROR_STOP=1 -f docs/fixtures/riyadh_demo_seed.sql
-- Matching travel signals are intentionally absent; do not invent confidence.
