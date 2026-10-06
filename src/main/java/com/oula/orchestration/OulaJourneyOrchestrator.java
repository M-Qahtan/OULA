package com.oula.orchestration;
import com.oula.iam.AccessContext;
import com.oula.intelligence.DecisionRecord;
import com.oula.intelligence.IntelligenceKernel;
import com.oula.intelligence.Recommendation;
import com.oula.matching.MatchingFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class OulaJourneyOrchestrator {
  private final MatchingFacade matching; private final IntelligenceKernel intelligence;
  public OulaJourneyOrchestrator(MatchingFacade matching,IntelligenceKernel intelligence){this.matching=matching;this.intelligence=intelligence;}
  @Transactional
  public JourneyRecommendation recommend(AccessContext access,UUID intentId,UUID correlationId){
    var matches=matching.run(intentId,access.workspaceId(),correlationId);
    return new JourneyRecommendation(matches,intelligence.recommend(access.workspaceId(),intentId,matches,correlationId));
  }
  @Transactional
  public DecisionRecord decide(AccessContext access,Recommendation recommendation,UUID selectedPropertyId,String overrideReason){
    if(!access.workspaceId().equals(recommendation.workspaceId()))throw new SecurityException("cross-workspace decision denied");
    return intelligence.recordDecision(recommendation,selectedPropertyId,access.actorId(),overrideReason);
  }
}
