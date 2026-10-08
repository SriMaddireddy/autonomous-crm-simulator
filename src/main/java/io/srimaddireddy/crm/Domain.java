package io.srimaddireddy.crm;
import jakarta.validation.constraints.*;
import java.util.List;
public final class Domain {
 private Domain() {}
 public record Campaign(@NotBlank @Size(max=80) String id, @NotBlank @Size(max=160) String name,
  @NotBlank @Pattern(regexp="SEARCH|SOCIAL|DISPLAY") String channel,
  @Positive double budget, @PositiveOrZero double spend,
  @PositiveOrZero long impressions, @PositiveOrZero long clicks, @PositiveOrZero long conversions,
  @PositiveOrZero double revenue, @DecimalMin("0.0") @DecimalMax("1.0") double risk) {
  public double ctr() { return impressions == 0 ? 0 : (double) clicks / impressions; }
  public double roas() { return spend == 0 ? 0 : revenue / spend; }
  public double cpa() { return conversions == 0 ? spend : spend / conversions; }
  @AssertTrue(message="metrics must be finite; clicks <= impressions and conversions <= clicks")
  public boolean isConsistent() {
   return Double.isFinite(budget) && Double.isFinite(spend) && Double.isFinite(revenue)
    && Double.isFinite(risk) && clicks <= impressions && conversions <= clicks;
  }
 }
 public record Evidence(long id, String campaignId, String content, double similarity) {}
 public record Retrieval(List<Evidence> logs, double latencyMs, String mode) {}
 public record Decision(String skill, String action, String reason, List<Long> evidenceIds) {}
 public record Result(String action, String explanation, String explanationMode,
  List<Decision> decisions, Retrieval retrieval) {}
 public record Workflow(String id, String campaignId, String idempotencyKey, String status,
  int attempts, String owner, Long leaseUntil, long createdAt, long updatedAt, String error, Result result) {}
 public record Claim(String id, String campaignId, String owner, int attempt) {}
 public record Submit(@NotBlank @Size(max=80) String campaignId,
  @NotBlank @Size(max=120) String idempotencyKey) {}
 public record Batch(@Min(1) @Max(500) int count, @NotBlank @Size(max=80) String runKey) {}
}
