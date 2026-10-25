-- BattleShip Online - MySQL 8+
CREATE DATABASE IF NOT EXISTS battleship
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE battleship;

CREATE TABLE IF NOT EXISTS users (
  username VARCHAR(20) PRIMARY KEY,
  password_hash CHAR(64) NOT NULL,
  total_score INT NOT NULL DEFAULT 0,
  wins INT UNSIGNED NOT NULL DEFAULT 0,
  losses INT UNSIGNED NOT NULL DEFAULT 0,
  matches_played INT UNSIGNED NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS matches (
  match_id VARCHAR(36) PRIMARY KEY,
  player1 VARCHAR(20) NOT NULL,
  player2 VARCHAR(20) NOT NULL,
  status ENUM('WAITING','PREPARATION','PLAYING','FINISHED','CANCELLED') NOT NULL DEFAULT 'WAITING',
  winner_username VARCHAR(20) NULL,
  end_reason VARCHAR(40) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at DATETIME NULL,
  ended_at DATETIME NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_matches_player1 FOREIGN KEY (player1) REFERENCES users(username),
  CONSTRAINT fk_matches_player2 FOREIGN KEY (player2) REFERENCES users(username),
  CONSTRAINT fk_matches_winner FOREIGN KEY (winner_username) REFERENCES users(username),
  INDEX idx_matches_status_created (status, created_at),
  INDEX idx_matches_winner (winner_username)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS match_players (
  match_id VARCHAR(36) NOT NULL,
  username VARCHAR(20) NOT NULL,
  player_no TINYINT UNSIGNED NOT NULL,
  result ENUM('WIN','LOSS','DRAW','ABANDONED') NULL,
  score_delta INT NOT NULL DEFAULT 0,
  joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (match_id, username),
  UNIQUE KEY uq_match_player_no (match_id, player_no),
  KEY idx_match_players_user (username, joined_at),
  CONSTRAINT fk_match_players_match FOREIGN KEY (match_id) REFERENCES matches(match_id) ON DELETE CASCADE,
  CONSTRAINT fk_match_players_user FOREIGN KEY (username) REFERENCES users(username),
  CONSTRAINT chk_match_player_no CHECK (player_no IN (1,2))
) ENGINE=InnoDB;

CREATE OR REPLACE VIEW v_ranking AS
SELECT username, total_score, wins, losses, matches_played,
       CASE WHEN matches_played = 0 THEN 0
            ELSE ROUND(wins * 100.0 / matches_played, 2) END AS win_rate_percent
FROM users;
