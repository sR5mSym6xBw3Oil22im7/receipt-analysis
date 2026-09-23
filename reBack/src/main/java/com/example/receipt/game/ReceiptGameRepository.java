package com.example.receipt.game;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class ReceiptGameRepository {
    private final JdbcTemplate jdbc;
    public ReceiptGameRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public GameMonster findMonsterByReceipt(String tableName) {
        List<GameMonster> rows = jdbc.query("SELECT monster_id, receipt_table_name, image_sha256, monster_name, species, rarity, power, guard, speed, generation_status, generation_started_at, monster_image, monster_image_mime_type, visual_seed, visual_profile_json, image_prompt_version FROM receipt_game_monster WHERE receipt_table_name = ?", this::monster, tableName);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public GameMonster findMonster(long id) {
        List<GameMonster> rows = jdbc.query("SELECT monster_id, receipt_table_name, image_sha256, monster_name, species, rarity, power, guard, speed, generation_status, generation_started_at, monster_image, monster_image_mime_type, visual_seed, visual_profile_json, image_prompt_version FROM receipt_game_monster WHERE monster_id = ?", this::monster, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private GameMonster monster(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new GameMonster(rs.getLong("monster_id"), rs.getString("receipt_table_name"), rs.getString("image_sha256"), rs.getString("monster_name"), rs.getString("species"), rs.getString("rarity"), rs.getInt("power"), rs.getInt("guard"), rs.getInt("speed"), rs.getString("generation_status"), rs.getObject("generation_started_at", OffsetDateTime.class), rs.getBytes("monster_image"), rs.getString("monster_image_mime_type"), rs.getString("visual_seed"), rs.getString("visual_profile_json"), rs.getString("image_prompt_version"));
    }
    public boolean reserveMonster(GameMonsterProfile p, String tableName, OffsetDateTime now, OffsetDateTime staleBefore) {
        try {
            int inserted = jdbc.update("INSERT INTO receipt_game_monster (receipt_table_name, image_sha256, monster_name, species, rarity, power, guard, speed, generation_status, generation_started_at, visual_seed, visual_profile_json, image_prompt_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (image_sha256) DO NOTHING", tableName, p.imageSha256(), p.monsterName(), p.species(), p.rarity(), p.power(), p.guard(), p.speed(), "GENERATING", now, p.visualSeed(), p.visualProfileJson(), p.imagePromptVersion());
            if (inserted == 1) return true;
        } catch (DuplicateKeyException ignored) { }
        int stale = jdbc.update("UPDATE receipt_game_monster SET generation_status='GENERATING', generation_started_at=?, updated_at=CURRENT_TIMESTAMP, monster_name=?, species=?, rarity=?, power=?, guard=?, speed=?, visual_seed=?, visual_profile_json=?, image_prompt_version=? WHERE image_sha256=? AND generation_status='FAILED' OR (image_sha256=? AND generation_status='GENERATING' AND generation_started_at < ?)", now, p.monsterName(), p.species(), p.rarity(), p.power(), p.guard(), p.speed(), p.visualSeed(), p.visualProfileJson(), p.imagePromptVersion(), p.imageSha256(), p.imageSha256(), staleBefore);
        return stale == 1;
    }
    public void markReady(long id, byte[] image, String mime) { jdbc.update("UPDATE receipt_game_monster SET generation_status='READY', monster_image=?, monster_image_mime_type=?, updated_at=CURRENT_TIMESTAMP WHERE monster_id=?", image, mime, id); }
    public void markFailed(long id) { jdbc.update("UPDATE receipt_game_monster SET generation_status='FAILED', monster_image=NULL, monster_image_mime_type=NULL, updated_at=CURRENT_TIMESTAMP WHERE monster_id=?", id); }

    public GameRoom findRoom(String code, boolean lock) {
        String sql="SELECT * FROM receipt_game_room WHERE room_code = ?" + (lock ? " FOR UPDATE" : "");
        List<GameRoom> rows=jdbc.query(sql, (rs,row)->room(rs), code); return rows.isEmpty()?null:rows.getFirst();
    }
    private GameRoom room(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new GameRoom(rs.getLong("room_id"), rs.getString("room_code"), rs.getString("status"), rs.getString("player1_name"), rs.getString("player2_name"), rs.getString("player1_token_hash"), rs.getString("player2_token_hash"), array(rs.getArray("player1_candidate_receipt_table_names")), array(rs.getArray("player2_candidate_receipt_table_names")), (Long)rs.getObject("player1_selected_monster_id"), (Long)rs.getObject("player2_selected_monster_id"), rs.getBoolean("player1_locked"), rs.getBoolean("player2_locked"), (Long)rs.getObject("player1_battle_score_x2"), (Long)rs.getObject("player2_battle_score_x2"), rs.getString("winner_player"), (Long)rs.getObject("winner_monster_id"), rs.getObject("battle_completed_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class));
    }
    private static List<String> array(Array a) throws java.sql.SQLException { return a == null ? List.of() : Arrays.asList((String[])a.getArray()); }
    public void createRoom(String code, String name, String tokenHash, OffsetDateTime expires) { jdbc.update("INSERT INTO receipt_game_room (room_code,status,player1_name,player1_token_hash,expires_at) VALUES (?, 'WAITING_PLAYER2', ?, ?, ?)",code,name,tokenHash,expires); }
    @Transactional
    public boolean joinRoom(String code, String name, String tokenHash, OffsetDateTime expires) { return jdbc.update("UPDATE receipt_game_room SET player2_name=?, player2_token_hash=?, status='SELECTING', last_activity_at=?, expires_at=? WHERE room_code=? AND player2_token_hash IS NULL AND status='WAITING_PLAYER2' AND expires_at > CURRENT_TIMESTAMP",name,tokenHash,OffsetDateTime.now(),expires,code)==1; }
    public boolean addCandidate(String code, boolean p1, String tableName) { String column=p1?"player1_candidate_receipt_table_names":"player2_candidate_receipt_table_names"; return jdbc.update("UPDATE receipt_game_room SET "+column+"=array_append("+column+", ?), last_activity_at=CURRENT_TIMESTAMP, expires_at=CURRENT_TIMESTAMP + INTERVAL '60 minutes' WHERE room_code=? AND NOT (? = ANY("+column+")) AND cardinality("+column+") < 10",tableName,code,tableName)==1; }
    public void lockRoom(String code, boolean p1, long monsterId, OffsetDateTime expires, String status) { String id=p1?"player1":"player2"; jdbc.update("UPDATE receipt_game_room SET "+id+"_selected_monster_id=?, "+id+"_locked=TRUE, status=?, last_activity_at=CURRENT_TIMESTAMP, expires_at=? WHERE room_code=?",monsterId,status,expires,code); }
    public void completeBattle(String code, long p1Score, long p2Score, String winner, long winnerMonster, OffsetDateTime now) { jdbc.update("UPDATE receipt_game_room SET player1_battle_score_x2=?, player2_battle_score_x2=?, winner_player=?, winner_monster_id=?, battle_completed_at=?, status='BATTLE_COMPLETE', last_activity_at=?, expires_at=? WHERE room_code=? AND status <> 'BATTLE_COMPLETE'",p1Score,p2Score,winner,winnerMonster,now,now,now.plusMinutes(60),code); }
    public void touch(String code, OffsetDateTime expires) { jdbc.update("UPDATE receipt_game_room SET last_activity_at=CURRENT_TIMESTAMP, expires_at=? WHERE room_code=?",expires,code); }
    public int deleteExpired() { return jdbc.update("DELETE FROM receipt_game_room WHERE expires_at <= CURRENT_TIMESTAMP"); }
}
