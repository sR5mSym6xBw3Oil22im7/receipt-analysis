package com.example.receipt.controller;

import com.example.receipt.service.RemoteGameService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/game/rooms")
public class RemoteGameController {
    private final RemoteGameService service;
    public RemoteGameController(RemoteGameService service){this.service=service;}
    @PostMapping public ResponseEntity<?> create(HttpSession session){var room=service.create(session.getId());return ResponseEntity.ok().header("Set-Cookie",cookie(room.token()).build().toString()).body(Map.of("code",room.code(),"state","OPEN"));}
    @PostMapping("/{code}/join") public ResponseEntity<?> join(@PathVariable String code,HttpSession session){String token=service.join(code,session.getId());return ResponseEntity.ok().header("Set-Cookie",cookie(token).build().toString()).body(Map.of("joined",true));}
    @GetMapping("/{code}") public ResponseEntity<?> state(@PathVariable String code,HttpServletRequest request){return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.state(code,token(request)));}
    @PostMapping("/{code}/select") public ResponseEntity<?> select(@PathVariable String code,@RequestBody GameController.SelectRequest body,HttpServletRequest request){return ResponseEntity.ok().body(service.select(code,token(request),body.receiptId()));}
    @DeleteMapping("/{code}") public ResponseEntity<Void> cancel(@PathVariable String code,HttpServletRequest request){service.cancel(code,token(request));return ResponseEntity.noContent().build();}
    private String token(HttpServletRequest request){if(request.getCookies()!=null)for(var c:request.getCookies())if("GAME_SEAT".equals(c.getName()))return c.getValue();return "";}
    private ResponseCookie.ResponseCookieBuilder cookie(String value){return ResponseCookie.from("GAME_SEAT",value).httpOnly(true).secure(service.secureCookie()).sameSite("Lax").path("/api/game/rooms").maxAge(Duration.ofMinutes(120));}
}
