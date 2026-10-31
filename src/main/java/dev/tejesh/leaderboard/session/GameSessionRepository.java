package dev.tejesh.leaderboard.session;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {}
