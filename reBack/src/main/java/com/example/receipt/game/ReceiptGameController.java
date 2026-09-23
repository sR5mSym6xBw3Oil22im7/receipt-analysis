package com.example.receipt.game;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/receipt-game")
public class ReceiptGameController {
    private final ReceiptGameService service;
    public ReceiptGameController(ReceiptGameService service){this.service=service;}
    @PostMapping(value="/candidates", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public GameCandidateResponse candidate(@RequestParam("file") MultipartFile file,@RequestParam("geminiApiKey") String apiKey) throws IOException {return service.candidate(file,apiKey);}
    @PostMapping("/monsters/{receiptTableName}")
    public MonsterResponse monster(@PathVariable String receiptTableName,@RequestParam("geminiApiKey") String apiKey){return service.generateLocal(receiptTableName,apiKey);}
    @PostMapping("/battles/local")
    public BattleResponse localBattle(@RequestBody LocalBattleRequest request){return service.localBattle(request.player1MonsterId(),request.player2MonsterId());}
    @PostMapping("/rooms")
    public RoomAccess create(@RequestBody RoomCreateRequest request){return service.createRoom(request.playerName());}
    @PostMapping("/rooms/{roomCode}/join")
    public RoomAccess join(@PathVariable String roomCode,@RequestBody RoomJoinRequest request){return service.joinRoom(roomCode,request.playerName());}
    @PostMapping(value="/rooms/{roomCode}/candidates", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public GameCandidateResponse roomCandidate(@PathVariable String roomCode,@RequestHeader("X-Receipt-Game-Token") String token,@RequestParam("file") MultipartFile file,@RequestParam("geminiApiKey") String apiKey) throws IOException{return service.roomCandidate(roomCode,token,file,apiKey);}
    @PostMapping("/rooms/{roomCode}/monsters/{receiptTableName}")
    public MonsterResponse roomMonster(@PathVariable String roomCode,@RequestHeader("X-Receipt-Game-Token") String token,@PathVariable String receiptTableName,@RequestParam("geminiApiKey") String apiKey){return service.roomMonster(roomCode,token,receiptTableName,apiKey);}
    @GetMapping("/rooms/{roomCode}")
    public RoomResponse roomState(@PathVariable String roomCode,@RequestHeader("X-Receipt-Game-Token") String token){return service.roomState(roomCode,token);}
    @PostMapping("/rooms/{roomCode}/lock")
    public RoomResponse lock(@PathVariable String roomCode,@RequestHeader("X-Receipt-Game-Token") String token,@RequestBody LockRequest request){return service.lock(roomCode,token,request.monsterId());}
    @GetMapping(value="/rooms/{roomCode}/monsters/{monsterId}/image",produces=MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<ByteArrayResource> roomImage(@PathVariable String roomCode,@RequestHeader("X-Receipt-Game-Token") String token,@PathVariable long monsterId){return image(service.imageForRoom(roomCode,token,monsterId));}
    private ResponseEntity<ByteArrayResource> image(byte[] data){return ResponseEntity.ok().cacheControl(CacheControl.maxAge(10,TimeUnit.MINUTES).cachePublic()).contentType(MediaType.IMAGE_JPEG).body(new ByteArrayResource(data));}
}
