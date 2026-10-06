package com.oula.intelligence;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
@Repository
class IntelligenceLedgerRepository {
  private final JdbcClient jdbc; private final JsonMapper json;
  IntelligenceLedgerRepository(JdbcClient jdbc,JsonMapper json){this.jdbc=jdbc;this.json=json;}
  void save(Recommendation r){
    jdbc.sql("""insert into intelligence.recommendation
      (id,workspace_id,intent_id,recommended_property_id,alternatives,model_id,model_version,confidence,generated_at,correlation_id)
      values (:id,:w,:i,:p,cast(:alts as jsonb),:m,:mv,:c,:g,:corr)""")
      .param("id",r.id()).param("w",r.workspaceId()).param("i",r.intentId()).param("p",r.recommendedPropertyId())
      .param("alts",write(r.alternatives())).param("m",r.modelId()).param("mv",r.modelVersion()).param("c",r.confidence()).param("g",r.generatedAt()).param("corr",r.correlationId()).update();
  }
  void save(DecisionRecord d){
    jdbc.sql("""insert into intelligence.decision_record
      (id,workspace_id,recommendation_id,selected_property_id,decision_maker,accepted_recommendation,override_reason,decided_at)
      values (:id,:w,:r,:p,:dm,:a,:o,:at)""")
      .param("id",d.id()).param("w",d.workspaceId()).param("r",d.recommendationId()).param("p",d.selectedPropertyId())
      .param("dm",d.decisionMaker()).param("a",d.acceptedRecommendation()).param("o",d.overrideReason()).param("at",d.decidedAt()).update();
  }
  void save(Outcome o){
    jdbc.sql("""insert into intelligence.outcome
      (id,workspace_id,decision_id,outcome_type,expected_json,actual_json,observed_at)
      values (:id,:w,:d,:t,cast(:e as jsonb),cast(:a as jsonb),:at)""")
      .param("id",o.id()).param("w",o.workspaceId()).param("d",o.decisionId()).param("t",o.outcomeType())
      .param("e",o.expectedJson()).param("a",o.actualJson()).param("at",o.observedAt()).update();
  }
  private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("failed to serialize intelligence ledger",e);}}
}
