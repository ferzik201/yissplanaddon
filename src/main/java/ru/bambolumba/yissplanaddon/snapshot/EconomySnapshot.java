package ru.bambolumba.yissplanaddon.snapshot;

import java.time.Instant;
import java.util.List;

public record EconomySnapshot(Instant capturedAt, double totalCoins, List<PlayerEntry> top5) {
}
