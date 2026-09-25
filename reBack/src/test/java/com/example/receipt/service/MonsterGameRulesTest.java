package com.example.receipt.service;

import com.example.receipt.dto.MonsterCard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonsterGameRulesTest {
    private final SvgCardService svg = new SvgCardService(null,"test-model");

    @Test void x2ScoreUsesAllStatsAndPreservesHalfPointDifferences(){
        var a=card("a",1,1,3); var b=card("b",2,2,1);
        assertEquals(34,BattleScoring.scoreX2(a,b));
        assertEquals(35,BattleScoring.scoreX2(b,a));
        assertNotEquals(BattleScoring.scoreX2(a,b)/2.0,BattleScoring.scoreX2(b,a)/2.0);
    }

    @Test void permitsRichVectorPaths(){assertDoesNotThrow(()->svg.validateFragment("<g>"+"<path d='M0 0 L1 1' fill='#112233'/>".repeat(8)+"</g>"));}
    @Test void rejectsExecutableAndExternalSvgContent(){assertThrows(Exception.class,()->svg.validateFragment("<g><script>alert(1)</script></g>"));assertThrows(Exception.class,()->svg.validateFragment("<g><image href='https://example.invalid/x'/></g>"));}
    @Test void rejectsOutOfRangeArtworkCoordinates(){assertThrows(Exception.class,()->svg.validateFragment("<g><circle cx='9000' cy='0' r='2'/><path d='M0 0'/><path d='M0 0'/><path d='M0 0'/><path d='M0 0'/></g>"));}

    private MonsterCard card(String id,int p,int g,int s){return new MonsterCard(id,"n","species","rare",p,g,s,"seed","<svg/>");}
}
