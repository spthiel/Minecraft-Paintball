package me.gorgeousone.superpaintball.game;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import me.gorgeousone.superpaintball.team.TeamType;
import me.gorgeousone.superpaintball.util.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PbArena {

	private final String name;
	private File schemFile;
	private Location schemPos;
	private final Map<TeamType, List<Location>> teamSpawns;

	public PbArena(String name, File schemFile, Location schemPos) {
		this.name = name;
		this.schemFile = schemFile;
		this.schemPos = GameUtil.cleanSpawn(schemPos);
		this.teamSpawns = new HashMap<>();

		schemPos.setX(schemPos.getBlockX());
		schemPos.setY(schemPos.getBlockY());
		schemPos.setZ(schemPos.getBlockZ());
	}

	public PbArena(PbArena other, String name, Location schemPos) {
		this.name = name;
		this.schemFile = other.schemFile;
		this.schemPos = GameUtil.cleanSpawn(schemPos);
		this.teamSpawns = new HashMap<>();

		for (TeamType teamType : other.teamSpawns.keySet()) {
			teamSpawns.put(teamType, other.teamSpawns.get(teamType).stream().map(Location::clone).collect(Collectors.toList()));
		}
	}

	public String getName() {
		return name;
	}

	public Location getSchemPos() {
		return schemPos.clone();
	}

	public void addSpawn(TeamType teamType, Location spawnPos) {
		GameUtil.cleanSpawn(spawnPos);
		teamSpawns.computeIfAbsent(teamType, v -> new ArrayList<>());
		teamSpawns.get(teamType).add(spawnPos);
	}

	public void reset() {
		ClipboardFormat format = ClipboardFormats.findByFile(schemFile);
		Clipboard clipboard;

		try (ClipboardReader reader = format.getReader(Files.newInputStream(schemFile.toPath()))) {
			clipboard = reader.read();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		World weWorld = BukkitAdapter.adapt(schemPos.getWorld());

		try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1)) {
			Operation operation = new ClipboardHolder(clipboard)
					.createPaste(editSession)
					.to(BlockVector3.at(schemPos.getX(), schemPos.getY(), schemPos.getZ()))
					.ignoreAirBlocks(false)
					.build();
			Operations.complete(operation);
		} catch (WorldEditException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean hasSpawns(TeamType teamType) {
		return teamSpawns.containsKey(teamType);
	}

	public void spawnPlayers(TeamType teamType, Collection<UUID> playerIds) {
		if (!hasSpawns(teamType)) {
			throw new IllegalArgumentException(String.format("Arena '%s' does not have spawn points for team '%s'", name, teamType.displayName));
		}
		List<Location> spawns = teamSpawns.get(teamType);
		int i = 0;

		for (UUID playerId : playerIds) {
			Player player = Bukkit.getPlayer(playerId);
			player.teleport(spawns.get(i));
			i = (i + 1) % spawns.size();
		}
	}

	public void toYml(ConfigurationSection parentSection) {
		ConfigurationSection section = parentSection.createSection(name);
		section.set("schematic", schemFile.getName());
		section.set("position", ConfigUtil.blockPosToYmlString(schemPos));
		ConfigurationSection spawnsSection = section.createSection("spawns");

		for (TeamType teamType : teamSpawns.keySet()) {
			List<String> spawnStrings = new ArrayList<>();

			for (Location spawn : teamSpawns.get(teamType)) {
				spawnStrings.add(ConfigUtil.spawnToYmlString(spawn));
			}
			spawnsSection.set(teamType.name().toLowerCase(), spawnStrings);
		}
	}

	public static PbArena fromYml(String name, ConfigurationSection parentSection, String schemFolder) {
		ConfigurationSection section = parentSection.getConfigurationSection(name);
		PbArena arena;
		try {
			ConfigUtil.assertKeyExists(section, "schematic");
			ConfigUtil.assertKeyExists(section, "position");
			ConfigUtil.assertKeyExists(section, "spawns");
			File schemFile = ConfigUtil.schemFileFromYml(section.getString("schematic"), schemFolder);
			Location schemPos = ConfigUtil.blockPosFromYmlString(section.getString("position"));
			arena = new PbArena(name, schemFile, schemPos);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(String.format("Could not load arena '%s': %s", name, e.getMessage()));
		}
		ConfigurationSection spawnsSection = section.getConfigurationSection("spawns");

		for (String teamName : spawnsSection.getKeys(false)) {
			TeamType teamType;
			List<String> spawnStrings;
			try {
				teamType = ConfigUtil.teamTypeFromYml(teamName);
				spawnStrings = spawnsSection.getStringList(teamName);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(String.format("Could not load arena '%s': %s", name, e.getMessage()));
			}
			try {
				for (String spawnString : spawnStrings) {
					Location spawn = ConfigUtil.spawnFromYmlString(spawnString);
					arena.addSpawn(teamType, spawn);
				}
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(String.format("Could not load arena '%s' team '%s' spawns: %s", arena, teamName, e.getMessage()));
			}
		}
		Bukkit.getLogger().log(Level.INFO, String.format("'%s' loaded", name));
		return arena;
	}
}
