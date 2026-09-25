package com.example.receipt.controller;

import com.example.receipt.dto.MonsterCard;
import com.example.receipt.exception.ReceiptException;
import com.example.receipt.service.SvgCardService;
import com.example.receipt.service.BattleScoring;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameController {
    private final SvgCardService cards;
    public GameController(SvgCardService cards){this.cards=cards;}
    @GetMapping("/receipts") public ResponseEntity<?> receipts(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cards.list());}
    @GetMapping("/cards/{id}") public ResponseEntity<MonsterCard> card(@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cards.get(id));}
    @PostMapping("/cards/{id}/generate") public ResponseEntity<MonsterCard> generate(@PathVariable String id,@RequestBody GenerateRequest request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cards.generate(id,request.apiKey(),request.regenerate()));}
    @GetMapping(value="/cards/{id}/svg",produces="image/svg+xml;charset=UTF-8") public ResponseEntity<String> svg(@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; style-src 'none'; sandbox").body(cards.get(id).svg());}
    @GetMapping("/local") public ResponseEntity<?> localState(HttpSession session){LocalGame g=(LocalGame)session.getAttribute("gameState");if(g==null)return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("stage","EMPTY"));if(g.result()!=null)return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("stage","RESULT","result",g.result()));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("stage",g.p2()==null?"PLAYER2":"READY"));}
    @PostMapping("/local/p1") public ResponseEntity<?> p1(@RequestBody SelectRequest r,HttpSession s){MonsterCard c=cards.get(r.receiptId());s.setAttribute("gameState",new LocalGame(r.receiptId(),c.seed(),null,null,null));return ResponseEntity.ok(Map.of("stage","PLAYER2","message","Player 1の選択を確定しました。Player 2へ交代してください。"));}
    @PostMapping("/local/p2") public ResponseEntity<?> p2(@RequestBody SelectRequest r,HttpSession s){LocalGame g=(LocalGame)s.getAttribute("gameState");if(g==null||g.p2()!=null)throw bad("対戦状態がありません。新しい対戦を始めてください。");if(g.p1().equals(r.receiptId())){s.removeAttribute("gameState");throw bad("同じレシートは選べません。新しい対戦を開始してください。");}MonsterCard b=cards.get(r.receiptId());s.setAttribute("gameState",new LocalGame(g.p1(),g.seed1(),b.receiptId(),b.seed(),null));return ResponseEntity.ok(Map.of("stage","READY","player2",b));}
    @PostMapping("/local/resolve") public ResponseEntity<?> resolve(HttpSession s){LocalGame g=(LocalGame)s.getAttribute("gameState");if(g==null||g.p2()==null)throw bad("両者の選択を確定してください。");if(g.result()!=null)return ResponseEntity.ok(g.result());MonsterCard a=cards.get(g.p1()),b=cards.get(g.p2());if(!a.seed().equals(g.seed1())||!b.seed().equals(g.seed2()))throw bad("レシート内容が変わったため対戦を中止しました。カードを再確認してください。");int sa=score(a,b),sb=score(b,a);String winner=sa==sb?(a.seed()+a.receiptId()).compareTo(b.seed()+b.receiptId())>0?a.receiptId():b.receiptId():sa>sb?a.receiptId():b.receiptId();Map<String,Object> result=Map.of("stage","RESULT","player1",a,"player2",b,"scoreX2Player1",sa,"scoreX2Player2",sb,"scorePlayer1",sa/2.0,"scorePlayer2",sb/2.0,"winner",winner);s.setAttribute("gameState",new LocalGame(g.p1(),g.seed1(),g.p2(),g.seed2(),result));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);}
    @DeleteMapping("/local") public ResponseEntity<Void> cancel(HttpSession s){s.removeAttribute("gameState");return ResponseEntity.noContent().build();}
    private int score(MonsterCard a,MonsterCard b){return BattleScoring.scoreX2(a,b);}
    private ReceiptException bad(String m){return new ReceiptException(HttpStatus.CONFLICT,"GAME_STATE_INVALID",m);}
    public record GenerateRequest(String apiKey,boolean regenerate){} public record SelectRequest(String receiptId){} private record LocalGame(String p1,String seed1,String p2,String seed2,Object result){}
}
