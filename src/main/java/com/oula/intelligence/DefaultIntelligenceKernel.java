package com.oula.intelligence;
import com.oula.matching.MatchRunOutcome;
import com.oula.platform.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;
@Service
public class DefaultIntelligenceKernel implements IntelligenceKernel {
  private final IntelligenceLedgerRepository ledger; private final Clock clock=Clock.systemUTC();
  public DefaultIntelligenceKernel(IntelligenceLedgerRepository ledger){this.ledger=ledger;}
  @Override @Transactional
  public Recommendation recommend(UUID workspaceId,UUID intentId,MatchRunOutcome matches,UUID correlationId){
    if(matches.matches().isEmpty())throw new IllegalStateException("cannot recommend without eligible matches");
    var top=matches.matches().getFirst();var alternatives=matches.matches().stream().map(m->m.propertyId()).toList();
    Recommendation r=new Recommendation(UuidV7.next(),workspaceId,intentId,top.propertyId(),alternatives,"LifeFit","v1",top.confidence(),clock.instant(),correlationId);
    ledger.save(r);return r;
  }
  @Override @Transactional
  public DecisionRecord recordDecision(Recommendation r,UUID selectedPropertyId,UUID decisionMaker,String overrideReason){
    DecisionRecord d=new DecisionRecord(UuidV7.next(),r.workspaceId(),r.id(),selectedPropertyId,decisionMaker,r.recommendedPropertyId().equals(selectedPropertyId),overrideReason,clock.instant());
    ledger.save(d);return d;
  }
  @Override @Transactional
  public Outcome observeOutcome(DecisionRecord d,String outcomeType,String expectedJson,String actualJson){
    Outcome o=new Outcome(UuidV7.next(),d.workspaceId(),d.id(),outcomeType,expectedJson,actualJson,clock.instant());ledger.save(o);return o;
  }
}
