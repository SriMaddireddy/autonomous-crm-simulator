package io.srimaddireddy.crm;
import static io.srimaddireddy.crm.Domain.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Repository
public class Campaigns {
 private final JdbcTemplate db;
 public Campaigns(JdbcTemplate db) { this.db=db; }
 public Campaign get(String id) {
  return db.query("SELECT * FROM campaigns WHERE id=?", (r,n)->new Campaign(r.getString("id"),
   r.getString("name"),r.getString("channel"),r.getDouble("budget"),r.getDouble("spend"),
   r.getLong("impressions"),r.getLong("clicks"),r.getLong("conversions"),r.getDouble("revenue"),
   r.getDouble("risk")),id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Campaign not found"));
 }
 public List<Campaign> list(int limit) {
  return db.query("SELECT id FROM campaigns ORDER BY id LIMIT ?", (r,n)->get(r.getString(1)),limit);
 }
 public void create(Campaign c) {
  db.update("INSERT INTO campaigns VALUES (?,?,?,?,?,?,?,?,?,?)",c.id(),c.name(),c.channel(),
   c.budget(),c.spend(),c.impressions(),c.clicks(),c.conversions(),c.revenue(),c.risk());
 }
}
