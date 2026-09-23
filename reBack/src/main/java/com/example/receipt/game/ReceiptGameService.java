package com.example.receipt.game;

import com.example.receipt.dto.ReceiptText;
import com.example.receipt.repository.ReceiptTableRepository;
import com.example.receipt.service.ReceiptAnalyzer;
import com.example.receipt.service.ReceiptUploadValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ReceiptGameService {
    private static final String ROOM_ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final ReceiptAnalyzer analyzer; private final ReceiptUploadValidator validator; private final ReceiptTableRepository receipts; private final ReceiptGameRepository games; private final GeminiMonsterImageGenerator imageGenerator; private final int roomMinutes; private final int staleMinutes; private final SecureRandom random=new SecureRandom();
    public ReceiptGameService(ReceiptAnalyzer analyzer, ReceiptUploadValidator validator, ReceiptTableRepository receipts, ReceiptGameRepository games, GeminiMonsterImageGenerator imageGenerator, @Value("${receipt-game.room-expiration-minutes:60}") int roomMinutes, @Value("${receipt-game.generating-stale-minutes:15}") int staleMinutes) { this.analyzer=analyzer;this.validator=validator;this.receipts=receipts;this.games=games;this.imageGenerator=imageGenerator;this.roomMinutes=roomMinutes;this.staleMinutes=staleMinutes; }

    public GameCandidateResponse candidate(MultipartFile file, String apiKey) throws IOException {
        validator.validate(file); byte[] bytes=file.getBytes(); if(ImageIO.read(new ByteArrayInputStream(bytes))==null) fail("INVALID_IMAGE","画像を読み込めませんでした。別のJPEGまたはPNGを選択してください。"); String sha=sha256(bytes); String table=receipts.findTableNameBySha256(sha);
        if(table==null) { ReceiptText analyzed=analyzer.analyze(bytes,file.getContentType(),apiKey); if(analyzed.lines()==null||analyzed.lines().isEmpty() || analyzed.structuredData()==null || (analyzed.structuredData().safeItems().isEmpty() && (analyzed.structuredData().storeName()==null || analyzed.structuredData().storeName().isBlank()) && analyzed.structuredData().totalAmount()==null)) fail("NO_RECEIPT","レシートとして解析できませんでした。別の画像を選択してください。"); receipts.reserveImageHash(sha); table=receipts.createReceiptTableAndInsert(analyzed.lines(),sha); receipts.saveStructuredData(table,sha,analyzed.structuredData()); }
        GameReceipt receipt=receipts.findGameReceipt(table); if(receipt==null) fail("RECEIPT_NOT_READY","レシートデータを取得できませんでした。"); return candidateResponse(receipt);
    }
    private static GameCandidateResponse candidateResponse(GameReceipt r){ return new GameCandidateResponse(r.tableName(),r.imageSha256(),r.storeName(),r.storeCategory(),r.purchasedAt(),r.totalAmount(),r.safeItems().size()); }

    public MonsterResponse generate(String tableName, String apiKey, String imageUrl) { return generateInternal(tableName, apiKey, imageUrl, false); }
    public MonsterResponse generateLocal(String tableName, String apiKey) { return generateInternal(tableName, apiKey, null, true); }
    private MonsterResponse generateInternal(String tableName, String apiKey, String imageUrl, boolean includeImage) {
        GameReceipt receipt=requireReceipt(tableName); GameMonster existing=games.findMonsterByReceipt(tableName); if(existing!=null&&existing.ready())return MonsterResponse.from(existing, imageUrl == null ? null : imageUrl+existing.monsterId()+"/image", includeImage);
        GameMonsterProfile profile=GameRules.profile(receipt.imageSha256(), new com.example.receipt.dto.ReceiptStructuredData(receipt.storeName(),null,receipt.storeCategory(),receipt.purchasedAt(),receipt.totalAmount(),null,null,receipt.safeItems()));
        OffsetDateTime now=OffsetDateTime.now(); boolean owner=games.reserveMonster(profile,tableName,now,now.minusMinutes(staleMinutes)); GameMonster reserved=games.findMonsterByReceipt(tableName); if(!owner) { if(reserved!=null&&reserved.ready())return MonsterResponse.from(reserved,imageUrl); if(reserved!=null&&"GENERATING".equals(reserved.generationStatus())) throw new com.example.receipt.exception.ReceiptException(HttpStatus.ACCEPTED,"MONSTER_GENERATING","モンスターを生成中です。しばらくしてから確認してください。"); }
        try { byte[] image=imageGenerator.generate(profile,apiKey); games.markReady(reserved.monsterId(),image,"image/jpeg"); return MonsterResponse.from(games.findMonster(reserved.monsterId()), imageUrl == null ? null : imageUrl+reserved.monsterId()+"/image", includeImage); } catch(RuntimeException e) { games.markFailed(reserved.monsterId()); throw e; }
    }

    public BattleResponse localBattle(long firstId,long secondId) { GameMonster first=requireMonster(firstId), second=requireMonster(secondId); rejectSame(first,second); long a=GameRules.battleScoreX2(first,second), b=GameRules.battleScoreX2(second,first); String winner=GameRules.winner(first,second,a,b); return battle(first,second,a,b,winner,null,true); }

    @Transactional
    public RoomAccess createRoom(String name) { String safeName=playerName(name,"Player 1"), code; do{code=randomCode();}while(games.findRoom(code,false)!=null); String token=token(); games.createRoom(code,safeName,hash(token),OffsetDateTime.now().plusMinutes(roomMinutes)); return new RoomAccess(code,"PLAYER1",token); }
    @Transactional
    public RoomAccess joinRoom(String code,String name) { String normalized=normalizeCode(code); String token=token(); boolean ok=games.joinRoom(normalized,playerName(name,"Player 2"),hash(token),OffsetDateTime.now().plusMinutes(roomMinutes)); if(!ok)fail("ROOM_UNAVAILABLE","対戦ルームに参加できません。ルームコードを確認してください。"); return new RoomAccess(normalized,"PLAYER2",token); }
    @Transactional
    public GameCandidateResponse roomCandidate(String code,String token,MultipartFile file,String apiKey) throws IOException { GameRoom room=authorized(code,token,true); GameCandidateResponse c=candidate(file,apiKey); boolean p1=room.isPlayer1(hash(token)); if(!(p1?room.player1Candidates():room.player2Candidates()).contains(c.receiptTableName()) && !games.addCandidate(room.roomCode(),p1,c.receiptTableName())) fail("CANDIDATE_LIMIT","候補は1人あたり10枚までです。"); return c; }
    public MonsterResponse roomMonster(String code,String token,String tableName,String apiKey) { GameRoom room=authorized(code,token,true); boolean p1=room.isPlayer1(hash(token)); if(!(p1?room.player1Candidates():room.player2Candidates()).contains(tableName))fail("FORBIDDEN_CANDIDATE","自分の候補ではないレシートです。"); return generate(tableName,apiKey,"/api/receipt-game/rooms/"+code+"/monsters/"); }

    @Transactional
    public RoomResponse lock(String code,String token,long monsterId) {
        GameRoom room=authorized(code,token,true); String tokenHash=hash(token); boolean p1=room.isPlayer1(tokenHash); GameMonster selected=requireMonster(monsterId); List<String> own=p1?room.player1Candidates():room.player2Candidates(); if(!own.contains(selected.receiptTableName()))fail("FORBIDDEN_CANDIDATE","候補外のモンスターは選択できません。"); if(!selected.ready())fail("MONSTER_NOT_READY","生成済みのモンスターを選択してください。");
        Long already=p1?room.player1MonsterId():room.player2MonsterId(); boolean locked=p1?room.player1Locked():room.player2Locked(); if(locked){if(already!=monsterId)fail("LOCKED","LOCK後は変更できません。"); return response(games.findRoom(code,false),tokenHash);}
        Long opponentId=p1?room.player2MonsterId():room.player1MonsterId(); if(opponentId!=null){GameMonster opponent=requireMonster(opponentId); rejectSame(selected,opponent);}
        boolean otherLocked=p1?room.player2Locked():room.player1Locked(); games.lockRoom(code,p1,monsterId,OffsetDateTime.now().plusMinutes(roomMinutes),otherLocked?"BOTH_READY":(p1?"PLAYER1_LOCKED":"PLAYER2_LOCKED"));
        GameRoom after=games.findRoom(code,true); if(after.player1Locked()&&after.player2Locked()&&!"BATTLE_COMPLETE".equals(after.status())){GameMonster first=after.player1MonsterId()==null?null:games.findMonster(after.player1MonsterId()); GameMonster second=after.player2MonsterId()==null?null:games.findMonster(after.player2MonsterId()); if(first==null||second==null)fail("MONSTER_NOT_FOUND","選択したモンスターを利用できません。再選択してください。"); rejectSame(first,second); long a=GameRules.battleScoreX2(first,second),b=GameRules.battleScoreX2(second,first); String winner=GameRules.winner(first,second,a,b); games.completeBattle(code,a,b,winner,"PLAYER1".equals(winner)?first.monsterId():second.monsterId(),OffsetDateTime.now()); after=games.findRoom(code,false); } return response(after,tokenHash);
    }

    public RoomResponse roomState(String code,String token){ return response(authorized(code,token,false),hash(token)); }
    public byte[] imageForMonster(long id){ GameMonster m=requireMonster(id); if(!m.ready())fail("MONSTER_NOT_READY","画像はまだ生成されていません。"); return m.image(); }
    public byte[] imageForRoom(String code,String token,long id){ GameRoom room=authorized(code,token,false); GameMonster m=requireMonster(id); boolean reveal=room.player1Locked()&&room.player2Locked(); boolean own=(room.isPlayer1(hash(token))&&Objects.equals(room.player1MonsterId(),id))||(room.isPlayer2(hash(token))&&Objects.equals(room.player2MonsterId(),id)); if(!own&&!reveal)fail("FORBIDDEN","対戦開始前は相手の画像を表示できません。"); return m.image(); }
    @Scheduled(fixedDelayString = "${receipt-game.cleanup-delay-ms:300000}")
    public int cleanupExpired(){return games.deleteExpired();}

    private RoomResponse response(GameRoom room,String tokenHash){ boolean p1=room.isPlayer1(tokenHash); if(!p1&&!room.isPlayer2(tokenHash))fail("FORBIDDEN","ルームトークンが不正です。"); boolean reveal=room.player1Locked()&&room.player2Locked(); GameMonster own= (p1?room.player1MonsterId():room.player2MonsterId())==null?null:games.findMonster(p1?room.player1MonsterId():room.player2MonsterId()); GameMonster opp= reveal ? ((p1?room.player2MonsterId():room.player1MonsterId())==null?null:games.findMonster(p1?room.player2MonsterId():room.player1MonsterId())):null; BattleResponse battle=null; if(reveal&&own!=null&&opp!=null&&room.player1ScoreX2()!=null) battle=battle(games.findMonster(room.player1MonsterId()),games.findMonster(room.player2MonsterId()),room.player1ScoreX2(),room.player2ScoreX2(),room.winnerPlayer(),"/api/receipt-game/rooms/"+room.roomCode()+"/monsters/"); return new RoomResponse(room.roomCode(),room.status(),p1?"PLAYER1":"PLAYER2",room.player1Name(),room.player2Name(),p1?room.player1Candidates():room.player2Candidates(),p1?room.player1Locked():room.player2Locked(),p1?room.player2Locked():room.player1Locked(),own==null?null:MonsterResponse.from(own,"/api/receipt-game/rooms/"+room.roomCode()+"/monsters/"+own.monsterId()+"/image"),opp==null?null:MonsterResponse.from(opp,"/api/receipt-game/rooms/"+room.roomCode()+"/monsters/"+opp.monsterId()+"/image"),battle); }
    private BattleResponse battle(GameMonster a,GameMonster b,long as,long bs,String winner,String prefix){ return battle(a,b,as,bs,winner,prefix,false); }
    private BattleResponse battle(GameMonster a,GameMonster b,long as,long bs,String winner,String prefix,boolean includeImage){ String aUrl=prefix==null?null:prefix+a.monsterId()+"/image"; String bUrl=prefix==null?null:prefix+b.monsterId()+"/image"; return new BattleResponse(MonsterResponse.from(a,aUrl,includeImage),MonsterResponse.from(b,bUrl,includeImage),as,bs,winner); }
    private GameRoom authorized(String code,String token,boolean mutate){ GameRoom room=games.findRoom(normalizeCode(code),mutate); if(room==null||room.expired())fail("ROOM_EXPIRED","対戦ルームが見つからないか、有効期限が切れています。"); String h=hash(token); if(!room.isPlayer1(h)&&!room.isPlayer2(h))fail("FORBIDDEN","ルームトークンが不正です。"); return room; }
    private GameReceipt requireReceipt(String table){ GameReceipt r=receipts.findGameReceipt(table); if(r==null)fail("RECEIPT_NOT_FOUND","指定されたレシートが見つかりません。"); return r; }
    private GameMonster requireMonster(long id){ GameMonster m=games.findMonster(id); if(m==null)fail("MONSTER_NOT_FOUND","指定されたモンスターが見つかりません。"); return m; }
    private static void rejectSame(GameMonster a,GameMonster b){if(a.imageSha256().equals(b.imageSha256()))fail("SAME_RECEIPT","同じレシートを使用して対戦することはできません。別のレシートを選択してください。");}
    private static void fail(String code,String message){throw new com.example.receipt.exception.ReceiptException(HttpStatus.CONFLICT,code,message);}
    private static String playerName(String name,String fallback){String n=name==null?"":name.trim();return n.isBlank()?fallback:n.substring(0,Math.min(20,n.length()));}
    private String randomCode(){StringBuilder b=new StringBuilder(6);for(int i=0;i<6;i++)b.append(ROOM_ALPHABET.charAt(random.nextInt(ROOM_ALPHABET.length())));return b.toString();}
    private String token(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String normalizeCode(String code){if(code==null||!code.matches("[A-HJ-NP-Z2-9]{6}"))fail("INVALID_ROOM_CODE","ROOM CODEは6文字の英数字です。");return code;}
    private static String hash(String v){return sha256(v==null?"":v);}
    private static String sha256(byte[] b){try{byte[]d=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte x:d)s.append(String.format("%02x",x));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static String sha256(String s){return sha256(s.getBytes(StandardCharsets.UTF_8));}
}
