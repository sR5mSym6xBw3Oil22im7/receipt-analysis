package com.example.receipt.service;

import com.example.receipt.dto.MonsterCard;

public final class BattleScoring {
    private BattleScoring() {}
    public static int scoreX2(MonsterCard a,MonsterCard b){return 6*(a.power()+a.guard()+a.speed())+4*Math.max(a.power()-b.guard(),0)+2*Math.max(a.speed()-b.speed(),0)+Math.max(a.guard()-b.power(),0);}
}
