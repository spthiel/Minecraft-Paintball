package me.gorgeousone.superpaintball.team;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Team {
	
	private final TeamType teamType;
	private final Set<UUID> players;
	private final Set<UUID> remainingPlayers;
	
	public Team(TeamType teamType) {
		this.teamType = teamType;
		this.players = new HashSet<>();
		this.remainingPlayers = new HashSet<>();
	}
	
	public TeamType getType() {
		return teamType;
	}
	
	public Set<UUID> getPlayers() {
		return new HashSet<>(players);
	}
	
	public Set<UUID> getRemainingPlayers() {
		return new HashSet<>(remainingPlayers);
	}
	
	public void addPlayer(Player player) {
		UUID playerId = player.getUniqueId();
		//if game started, throw
		players.add(playerId);
		remainingPlayers.add(playerId);
	}
	
	public void removePlayer(Player player) {}
	
	public void killPlayer(Player player) {}
	
	public void revivePlayer(Player player) {}
	
	public boolean hasPlayer(Player player) {
		return players.contains(player.getUniqueId());
	}
}
