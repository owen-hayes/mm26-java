package mech.mania.starter_pack.domain;

import mech.mania.engine.domain.model.GameStateProtos.*;
import mech.mania.engine.domain.model.PlayerProtos.*;

public interface Strategy {
    PlayerDecision makeDecision(String playerName, GameState gameState);
}
