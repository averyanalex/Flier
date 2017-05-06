/** This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://www.wtfpl.net/ for more details.
 */
package pl.betoncraft.flier.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.scheduler.BukkitRunnable;

import pl.betoncraft.flier.api.Flier;
import pl.betoncraft.flier.api.content.Bonus;
import pl.betoncraft.flier.api.content.Game;
import pl.betoncraft.flier.api.content.Lobby;
import pl.betoncraft.flier.api.content.Wings;
import pl.betoncraft.flier.api.core.Arena;
import pl.betoncraft.flier.api.core.Attacker;
import pl.betoncraft.flier.api.core.FancyStuffWrapper;
import pl.betoncraft.flier.api.core.InGamePlayer;
import pl.betoncraft.flier.api.core.LoadingException;
import pl.betoncraft.flier.api.core.NoArenaException;
import pl.betoncraft.flier.api.core.Kit;
import pl.betoncraft.flier.api.core.Kit.AddResult;
import pl.betoncraft.flier.api.core.Kit.RespawnAction;
import pl.betoncraft.flier.api.core.SetApplier;
import pl.betoncraft.flier.api.core.Target;
import pl.betoncraft.flier.core.DefaultKit;
import pl.betoncraft.flier.core.DefaultPlayer;
import pl.betoncraft.flier.core.DefaultSetApplier;
import pl.betoncraft.flier.event.FlierClickButtonEvent;
import pl.betoncraft.flier.event.FlierPlayerKillEvent;
import pl.betoncraft.flier.event.FlierPlayerKillEvent.Type;
import pl.betoncraft.flier.sidebar.Altitude;
import pl.betoncraft.flier.sidebar.Ammo;
import pl.betoncraft.flier.sidebar.Fuel;
import pl.betoncraft.flier.sidebar.Health;
import pl.betoncraft.flier.sidebar.Money;
import pl.betoncraft.flier.sidebar.Speed;
import pl.betoncraft.flier.sidebar.Time;
import pl.betoncraft.flier.util.DoubleClickBlocker;
import pl.betoncraft.flier.util.EffectListener;
import pl.betoncraft.flier.util.LangManager;
import pl.betoncraft.flier.util.Utils;
import pl.betoncraft.flier.util.ValueLoader;

/**
 * Basic rules of a game.
 *
 * @author Jakub Sapalski
 */
public abstract class DefaultGame implements Listener, Game {
	
	protected static final List<DamageCause> allowedDamage = Arrays.asList(new DamageCause[]{
			DamageCause.CONTACT, DamageCause.CUSTOM, DamageCause.FALL, DamageCause.FLY_INTO_WALL,
			DamageCause.HOT_FLOOR, DamageCause.DROWNING, DamageCause.FALLING_BLOCK, DamageCause.FIRE,
			DamageCause.FIRE_TICK, DamageCause.LAVA, DamageCause.LIGHTNING, DamageCause.SUFFOCATION
	});

	protected final String id;
	protected final int uniqueNumber = new Random().nextInt(Integer.MAX_VALUE);
	protected final ValueLoader loader;
	protected GameHeartBeat heartBeat;
	protected WaitingRoom waitingRoom;
	
	protected final Map<UUID, InGamePlayer> dataMap = new HashMap<>();
	protected final Map<UUID, Target> targets = new HashMap<>();
	protected final FancyStuffWrapper fancyStuff;
	protected final EffectListener listener;
	protected final List<Bonus> bonuses = new ArrayList<>();
	protected final Map<String, Button> buttons = new HashMap<>();
	protected final Map<InGamePlayer, List<Button>> unlocked = new HashMap<>();
	protected final RespawnAction respawnAction;
	protected final boolean rounds;
	protected final int maxPlayers;
	protected final int maxTime;
	protected final Kit defKit;
	protected final int heightLimit;
	protected final double heightDamage;
	protected final boolean useMoney;
	protected final int enemyKillMoney;
	protected final int enemyHitMoney;
	protected final int friendlyKillMoney;
	protected final int friendlyHitMoney;
	protected final int byEnemyDeathMoney;
	protected final int byEnemyHitMoney;
	protected final int byFriendlyDeathMoney;
	protected final int byFriendlyHitMoney;
	protected final int suicideMoney;

	protected Lobby lobby;
	protected Arena arena;
	protected boolean running = false;
	protected int timeLeft;
	protected List<Block> leaveBlocks = new ArrayList<>();
	protected Location center;
	protected int minX, minZ, maxX, maxZ;
	protected boolean roundFinished = false;
	
	public DefaultGame(ConfigurationSection section, Lobby lobby) throws LoadingException, NoArenaException {
		
		Flier flier = Flier.getInstance();
		this.lobby = lobby;
		id = section.getName();
		loader = new ValueLoader(section);
		
		// select an arena
		List<String> viableArenas = section.getStringList("viable_arenas");
		if (viableArenas.isEmpty()) {
			throw new LoadingException("No viable arenas are specified.");
		}
		for (Entry<String, Arena> entry : lobby.getArenas().entrySet()) {
			if (viableArenas.contains(entry.getKey()) && !entry.getValue().isUsed()) {
				arena = entry.getValue();
				arena.setUsed(true);
				break;
			}
		}
		if (arena == null) {
			throw new NoArenaException();
		}
		
		// calculate borders
		center = arena.getLocation(loader.loadString("center"));
		int radius = loader.loadPositiveInt("radius");
		minX = center.getBlockX() - radius;
		maxX = center.getBlockX() + radius;
		minZ = center.getBlockZ() - radius;
		maxZ = center.getBlockZ() + radius;
		
		// load "leave" blocks
		for (String name : section.getStringList("leave_blocks")) {
			leaveBlocks.add(arena.getLocation(name).getBlock());
		}
		
		fancyStuff = flier.getFancyStuff();
		listener = new EffectListener(section.getStringList("effects"), this);
		rounds = loader.loadBoolean("rounds");
		maxPlayers = loader.loadNonNegativeInt("max_players", 0);
		maxTime = loader.loadNonNegativeInt("max_time", 0) * 20;
		timeLeft = maxTime;
		respawnAction = loader.loadEnum("respawn_action", RespawnAction.class);
		waitingRoom = new WaitingRoom(this);
		for (String bonusName : section.getStringList("bonuses")) {
			bonuses.add(flier.getBonus(bonusName, this));
		}
		ConfigurationSection buttonsSection = section.getConfigurationSection("buttons");
		if (buttonsSection != null) for (String button : buttonsSection.getKeys(false)) {
			ConfigurationSection buttonSection = buttonsSection.getConfigurationSection(button);
			if (buttonSection == null) {
				throw new LoadingException(String.format("'%s' is not a button.", button));
			}
			try {
				buttons.put(button, new DefaultButton(buttonSection));
			} catch (LoadingException e) {
				throw (LoadingException) new LoadingException(String.format("Error in '%s' button.", button)).initCause(e);
			}
		}
		try {
			defKit = new DefaultKit(section.getStringList("default_kit"), respawnAction);
		} catch (LoadingException e) {
			throw (LoadingException) new LoadingException("Error in default kit.").initCause(e);
		}
		heightLimit = loader.loadInt("height_limit", 512);
		heightDamage = loader.loadNonNegativeDouble("height_damage", 0.5);
		useMoney = loader.loadBoolean("money.enabled", false);
		enemyKillMoney = loader.loadInt("money.enemy_kill", 0);
		enemyHitMoney = loader.loadInt("money.enemy_hit", 0);
		friendlyKillMoney = loader.loadInt("money.friendly_kill", 0);
		friendlyHitMoney = loader.loadInt("money.friendly_hit", 0);
		byEnemyDeathMoney = loader.loadInt("money.by_enemy_death", 0);
		byEnemyHitMoney = loader.loadInt("money.by_enemy_hit", 0);
		byFriendlyDeathMoney = loader.loadInt("money.by_friendly_death", 0);
		byFriendlyHitMoney = loader.loadInt("money.by_friendly_hit", 0);
		suicideMoney = loader.loadInt("money.suicide", 0);
		Bukkit.getPluginManager().registerEvents(this, Flier.getInstance());
	}
	
	protected class GameHeartBeat extends BukkitRunnable {
		
		private int tickCounter = 0;
		
		public GameHeartBeat(DefaultGame game) {
			runTaskTimer(Flier.getInstance(), 1, 1);
		}

		@Override
		public void run() {
			if (maxTime != 0 && --timeLeft == 0) {
				endGame();
			}
			if (running) {
				for (InGamePlayer data : getPlayers().values()) {
					Location loc = data.getPlayer().getLocation();
					// height damage
					if (loc.getBlockX() < minX || loc.getBlockX() > maxX ||
							loc.getBlockZ() < minZ || loc.getBlockZ() > maxZ) {
						data.getPlayer().damage(data.getPlayer().getHealth() + 1);
					}
				}
				if (heightLimit > 0 && tickCounter % 20 == 0) {
					for (InGamePlayer data : getPlayers().values()) {
						if (data.getPlayer().getLocation().getY() > heightLimit) {
							data.getPlayer().damage(heightDamage);
						}
					}
				}
				tickCounter++;
				if (tickCounter > 1000) {
					tickCounter = 0;
				}
			}
		}
	}
	
	public class DefaultButton implements Button {
		
		protected final String id;
		protected List<Location> locations = new ArrayList<>();
		protected final Set<String> requirements;
		protected final Set<Permission> permissions;
		protected final int buyCost;
		protected final int sellCost;
		protected final int unlockCost;
		protected final SetApplier onBuy;
		protected final SetApplier onSell;
		protected final SetApplier onUnlock;

		public DefaultButton(ConfigurationSection section) throws LoadingException {
			id = section.getName();
			ValueLoader loader = new ValueLoader(section);
			for (String locationName : section.getStringList("blocks")) {
				locations.add(arena.getLocation(locationName));
			}
			if (locations.isEmpty()) {
				throw new LoadingException("Blocks must be specified.");
			}
			requirements = new HashSet<>(section.getStringList("required"));
			permissions = new HashSet<>(
					section.getStringList("permissions").stream()
							.map(str -> new Permission(str))
							.collect(Collectors.toList())
			);
			buyCost = loader.loadInt("buy_cost", 0);
			sellCost = loader.loadInt("sell_cost", 0);
			unlockCost = loader.loadNonNegativeInt("unlock_cost", 0);
			try {
				ConfigurationSection buySection = section.getConfigurationSection("on_buy");
				onBuy = buySection == null ? null : new DefaultSetApplier(buySection);
			} catch (LoadingException e) {
				throw (LoadingException) new LoadingException("Error in 'on_buy' section.").initCause(e);
			}
			try {
				ConfigurationSection sellSection = section.getConfigurationSection("on_sell");
				onSell = sellSection == null ? null : new DefaultSetApplier(sellSection);
			} catch (LoadingException e) {
				throw (LoadingException) new LoadingException("Error in 'on_sell' section.").initCause(e);
			}
			try {
				ConfigurationSection unlockSection = section.getConfigurationSection("on_unlock");
				onUnlock = unlockSection == null ? null : new DefaultSetApplier(unlockSection);
			} catch (LoadingException e) {
				throw (LoadingException) new LoadingException("Error in 'on_unlock' section.").initCause(e);
			}
		}
		
		@Override
		public String getID() {
			return id;
		}
		
		@Override
		public List<Location> getLocations() {
			return locations;
		}
		
		@Override
		public void setLocations(List<Location> locs) {
			locations = new ArrayList<>(locs.size());
			locs.forEach(location ->
					locations.add(new Location(
							location.getWorld(),
							location.getBlockX(),
							location.getBlockY(),
							location.getBlockZ()
					))
			);
		}
		
		@Override
		public Set<String> getRequirements() {
			return requirements;
		}
		
		@Override
		public Set<Permission> getPermissions() {
			return permissions;
		}

		@Override
		public int getBuyCost() {
			return buyCost;
		}

		@Override
		public int getSellCost() {
			return sellCost;
		}

		@Override
		public int getUnlockCost() {
			return unlockCost;
		}

		@Override
		public SetApplier getOnBuy() {
			return onBuy;
		}

		@Override
		public SetApplier getOnSell() {
			return onSell;
		}

		@Override
		public SetApplier getOnUnlock() {
			return onUnlock;
		}
		
	}
	
	/**
	 * Reason for making the player wait in the waiting room.
	 */
	protected enum WaitReason {
		NO_WAIT, MORE_PLAYERS, START_DELAY, RESPAWN_DELAY, ROUND, GAME_ENDS
	}
	
	/**
	 * Represents the waiting room which can hold players currently not in-game
	 * and spawn them into the game.
	 */
	protected class WaitingRoom {
		
		protected final List<Integer> displayTimes = Arrays.asList(
				1, 2, 3, 4, 5, 10, 15, 30, 60, 90, 120, 180, 240, 300, 600).stream()
				.map(i -> i * 20).collect(Collectors.toList());
		
		protected final Game game;
		protected final int minPlayers;
		protected final int respawnDelay;
		protected final int startDelay;
		protected final boolean locking;
		protected final Location location;

		protected List<InGamePlayer> waitingPlayers = new ArrayList<>();
		protected WaitReason reason = WaitReason.NO_WAIT;
		protected int currentWaitingTime;
		protected BukkitRunnable ticker;
		protected boolean locked = false;
		
		public WaitingRoom(Game game) throws LoadingException {
			this.game = game;
			minPlayers = loader.loadPositiveInt("min_players", 1);
			respawnDelay = loader.loadNonNegativeInt("respawn_delay", 0);
			startDelay = loader.loadNonNegativeInt("start_delay", 0);
			locking = loader.loadBoolean("locking", false);
			location = arena.getLocation(loader.loadString("waiting_room"));
			ticker = new BukkitRunnable() {
				public void run() {
					tick();
				};
			};
			ticker.runTaskTimer(Flier.getInstance(), 1, 1);
			currentWaitingTime = -1; // lower than 0 means the waiting room is idle
		}
		
		/**
		 * @return whenever the waiting room is locked for new players
		 */
		public boolean isLocked() {
			return locked;
		}
		
		/**
		 * Adds this player to the waiting room.
		 * 
		 * @param player the player to add
		 * @return the type of waiting the player experiences
		 */
		public WaitReason addPlayer(InGamePlayer player) {
			waitingPlayers.add(player);
			// if the game has ended just move the player
			if (reason == WaitReason.GAME_ENDS) {
				player.getPlayer().teleport(location);
				return WaitReason.GAME_ENDS;
			}
			if (!running) {
				// the game hasn't started yet
				if (waitingPlayers.size() >= minPlayers) {
					// minimum player amount has been reached
					if (startDelay == 0) {
						// no start delay, start immediately
						reason = WaitReason.NO_WAIT;
					} else {
						// start delay should be applied
						if (currentWaitingTime <= 0) {
							// create start delay
							currentWaitingTime = startDelay;
						}
						reason = WaitReason.START_DELAY;
					} 
				} else {
					// not enough players yet
					reason = WaitReason.MORE_PLAYERS;
				}
			} else {
				// the game has already started
				if (rounds && !roundFinished) {
					// the round still in progress
					reason = WaitReason.ROUND;
					currentWaitingTime = respawnDelay;
				} else if (respawnDelay == 0) {
					// no respawn delay, start immediately
					reason = WaitReason.NO_WAIT; // players teleported, no need to put them in the waiting room
				} else {
					if (currentWaitingTime <= 0) {
						// create respawn delay
						currentWaitingTime = respawnDelay;
					}
					reason = WaitReason.RESPAWN_DELAY;
				}
			}
			// after the player has been added to the list and waiting time was set
			// we need to teleport them to the waiting room or immediately to the game
			if (reason == WaitReason.NO_WAIT) {
				startPlayers();
			} else {
				player.getPlayer().teleport(location);
			}
			return reason;
		}
		
		/**
		 * Removes the player from the waiting room. It does not teleport him in
		 * any way.
		 * 
		 * @param player the player to remove
		 */
		public void removePlayer(InGamePlayer player) {
			// it's assumed the teleporting of the player was already done
			waitingPlayers.remove(player);
		}
		
		/**
		 * Starts the game for all waiting players.
		 */
		public void startPlayers() {
			if (!running) {
				// start the game in case it's not running
				start();
			} else {
				// run the regular respawn routine
				for (InGamePlayer player : waitingPlayers) {
					handleRespawn(player);
				}
			}
			waitingPlayers.clear();
			roundFinished = false;
		}
		
		/**
		 * Called every tick so waiting room can decrease the counters.
		 */
		public void tick() {
			boolean shouldCountdown = reason != WaitReason.GAME_ENDS && (!running || !rounds || roundFinished);
			if (shouldCountdown) {
				// start the game if the waiting time is exactly 0
				// lower means the game was already started and the waiting room is idle
				if (currentWaitingTime == 0) {
					startPlayers();
				} else {
					// display countdown on specific seconds
					if (displayTimes.contains(currentWaitingTime)) {
						if (reason == WaitReason.RESPAWN_DELAY ||
								reason == WaitReason.START_DELAY ||
								reason == WaitReason.ROUND) {
							waitingPlayers.forEach(
									data -> LangManager.sendMessage(data, "countdown",
											(double) currentWaitingTime / 20.0));
						}
					}
				}
				currentWaitingTime--;
			}
		}
		
	}
	
	/**
	 * Ends the game by selecting the winner.
	 */
	public void endGame() {
		// move all players to the waiting room
		running = false;
		waitingRoom.reason = WaitReason.GAME_ENDS;
		dataMap.values().stream()
				.filter(player -> !waitingRoom.waitingPlayers.contains(player))
				.forEach(player -> moveToWaitingRoom(player));
		// display message
		dataMap.values().forEach(player -> LangManager.sendMessage(player, "game_ends"));
		// end game
		int delay = waitingRoom.respawnDelay == 0 ? 20 * 10 : waitingRoom.respawnDelay;
		Bukkit.getScheduler().scheduleSyncDelayedTask(Flier.getInstance(), () -> lobby.endGame(this), delay);
	}
	
	@Override
	public String getID() {
		return id;
	}
	
	@Override
	public int getUniqueNumber() {
		return uniqueNumber;
	}

	@Override
	public InGamePlayer addPlayer(Player player) {
		// can't join if the waiting room is locked
		if (waitingRoom.isLocked()) {
			throw new IllegalStateException("The game is locked.");
		}
		// can't join if already joined
		UUID uuid = player.getUniqueId();
		if (dataMap.containsKey(uuid)) {
			throw new IllegalStateException("Player is already in game.");
		}
		InGamePlayer data =  new DefaultPlayer(player, this, defKit.replicate());
		dataMap.put(uuid, data);
		targets.put(uuid, data);
		Flier.getInstance().getPlayers().put(player.getUniqueId(), data);
		// creating default stuff
		data.getLines().add(new Fuel(data));
		data.getLines().add(new Health(data));
		data.getLines().add(new Speed(data));
		data.getLines().add(new Altitude(data));
		// ammunition will be displayed on action bar if possible
		if (!fancyStuff.hasActionBarHandler()) {
			data.getLines().add(new Ammo(data));
		}
		if (useMoney) {
			data.getLines().add(new Money(data));
		}
		if (maxTime != 0) {
			data.getLines().add(new Time(data));
		}
		// move into waiting room
		moveToWaitingRoom(data);
		return data;
	}
	
	/**
	 * Displays a message about the cause of the waiting.
	 * 
	 * @param player
	 * @param reason
	 */
	public void waitMessage(Player player, WaitReason reason) {
		switch (reason) {
		case MORE_PLAYERS:
			for (InGamePlayer data : waitingRoom.waitingPlayers) {
				LangManager.sendMessage(data, "more_players", waitingRoom.minPlayers - dataMap.size());
			}
			break;
		case NO_WAIT:
			// nothing
			break;
		case RESPAWN_DELAY:
			LangManager.sendMessage(player, "respawn_delay", (double) waitingRoom.currentWaitingTime / 20.0);
			break;
		case START_DELAY:
			LangManager.sendMessage(player, "start_delay", (double) waitingRoom.currentWaitingTime / 20.0);
			break;
		case ROUND:
			LangManager.sendMessage(player, "round_delay");
			break;
		default:
			break;
		}
	}
	
	@Override
	public void removePlayer(Player player) {
		InGamePlayer data = dataMap.remove(player.getUniqueId());
		if (data == null) {
			return;
		}
		targets.remove(player.getUniqueId());
		unlocked.remove(data);
		waitingRoom.removePlayer(data);
		Flier.getInstance().getPlayers().remove(player.getUniqueId());
		data.exitGame();
		data.getPlayer().teleport(lobby.getSpawn());
	}
	
	@Override
	public Map<UUID, InGamePlayer> getPlayers() {
		return Collections.unmodifiableMap(dataMap);
	}
	
	@Override
	public Map<UUID, Target> getTargets() {
		return targets;
	}

	@Override
	public void start() {
		running = true;
		heartBeat = new GameHeartBeat(this);
		for (Bonus bonus : bonuses) {
			bonus.start();
		}
		for (InGamePlayer player : dataMap.values()) {
			handleRespawn(player);
		}
		if (waitingRoom.locking) {
			waitingRoom.locked = true;
		}
	}

	@Override
	public void stop() {
		HandlerList.unregisterAll(this);
		arena.setUsed(false);
		for (Bonus bonus : bonuses) {
			bonus.stop();
		}
		if (heartBeat != null) {
			heartBeat.cancel();
		}
		if (waitingRoom.ticker != null) {
			waitingRoom.ticker.cancel();
		}
		Collection<InGamePlayer> copy = new ArrayList<>(dataMap.values());
		for (InGamePlayer data : copy) {
			removePlayer(data.getPlayer());
		}
	}
	
	@Override
	public boolean isRunning() {
		return running;
	}
	
	@Override
	public boolean isLocked() {
		return waitingRoom.isLocked();
	}
	
	@Override
	public void handleKill(InGamePlayer killed, DamageCause cause) {
		Attacker attacker = killed.getAttacker();
		InGamePlayer killer = attacker == null ? null : attacker.getShooter();
		boolean fall = cause == DamageCause.FALL;
		if (killer != null && !killer.equals(killed)) {
			if (fall) {
				shotDownMessage("shot_down", killed, killer);
			} else {
				killedMessage("killed", killed, killer);
			}
			// fire an event
			FlierPlayerKillEvent deathEvent = new FlierPlayerKillEvent(killed, killer,
					fall ? Type.SHOT_DOWN : Type.KILLED);
			Bukkit.getPluginManager().callEvent(deathEvent);
			Attitude a = getAttitude(killer, killed);
			if (a == Attitude.FRIENDLY) {
				pay(killer, friendlyKillMoney);
				pay(killed, byFriendlyDeathMoney);
			} else if (a == Attitude.HOSTILE) {
				pay(killer, enemyKillMoney);
				pay(killed, byEnemyDeathMoney);
			}
		} else {
			suicideMessage("suicide", killed);
			// fire an event
			FlierPlayerKillEvent deathEvent = new FlierPlayerKillEvent(killed, killed,
					fall ? Type.SHOT_DOWN : Type.KILLED);
			Bukkit.getPluginManager().callEvent(deathEvent);
			pay(killed, suicideMoney);
		}
	}
	
	protected void moveToWaitingRoom(InGamePlayer player) {
		Utils.clearPlayer(player.getPlayer());
		player.setPlaying(false);
		Kit kit = player.getKit();
		kit.onRespawn();
		player.updateKit();
		WaitReason reason = waitingRoom.addPlayer(player);
		waitMessage(player.getPlayer(), reason);
	}
	
	@Override
	public void handleHit(Target attacked, Attacker attacker) {
		InGamePlayer shooter = attacker.getShooter();
		boolean hit = attacked.handleHit(attacker);
		// handle a general hit
		if (hit && shooter != null && attacked instanceof InGamePlayer) {
			// pay money for a hit
			InGamePlayer attackedPlayer = (InGamePlayer) attacked;
			Attitude a = getAttitude(shooter, attacked);
			if (a == Attitude.FRIENDLY) {
				pay(shooter, friendlyHitMoney);
				pay(attackedPlayer, byFriendlyHitMoney);
			} else if (a == Attitude.HOSTILE) {
				pay(shooter, enemyHitMoney);
				pay(attackedPlayer, byEnemyHitMoney);
			}
		}
	}
	
	@Override
	public void handleRespawn(InGamePlayer player) {
		player.getPlayer().getInventory().setHeldItemSlot(0);
		new BukkitRunnable() {
			@Override
			public void run() {
				player.setPlaying(true);
			}
		}.runTaskLater(Flier.getInstance(), 20);
		LangManager.sendMessage(player, "no_waiting");
		// spawn event must be called after teleportation
	}
	
	@Override
	public Map<String, Button> getButtons() {
		return buttons;
	}

	@Override
	public boolean applyButton(InGamePlayer player, Button button, boolean buy, boolean notify) {
		boolean applied = false;
		if (button != null) {
			// check permissions
			if (!button.getPermissions().stream().allMatch(perm -> player.getPlayer().hasPermission(perm))) {
				LangManager.sendMessage(player, "no_permission");
				return applied;
			}
			List<Button> ul = unlocked.computeIfAbsent(player, k -> new LinkedList<>());
			boolean unlocked = button.getUnlockCost() == 0 || ul.contains(button);
			if (!unlocked) {
				if (!button.getRequirements().stream().map(name -> buttons.get(name)).allMatch(b -> ul.contains(b))) {
					if (notify) LangManager.sendMessage(player, "unlock_other");
				} else if (button.getUnlockCost() <= player.getMoney()) {
					SetApplier applier = button.getOnUnlock();
					Runnable run = () -> {
						ul.add(button);
						player.setMoney(player.getMoney() - button.getUnlockCost());
						player.updateKit();
					};
					String message;
					if (applier == null) {
						run.run();
						applied = true;
						message = "unlocked";
					} else {
						AddResult result = applier.isSaving() ? player.getKit().addStored(applier) :
							player.getKit().addCurrent(applier);
						switch (result) {
						case ADDED:
						case FILLED:
						case REPLACED:
						case REMOVED:
							run.run();
							applied = true;
							message = "unlocked";
							break;
						default:
							message = "cant_use";
						}
					}
					if (notify) LangManager.sendMessage(player, message);
				} else {
					if (notify) LangManager.sendMessage(player, "no_money_unlock");
				}
			} else {
				int cost;
				SetApplier applier;
				if (buy) {
					cost = button.getBuyCost();
					applier = button.getOnBuy();
				} else {
					cost = button.getSellCost();
					applier = button.getOnSell();
				}
				if (applier != null) {
					if (cost <= player.getMoney()) {
						AddResult result = applier.isSaving() ? player.getKit().addStored(applier) :
							player.getKit().addCurrent(applier);
						Runnable run = () -> {
							player.setMoney(player.getMoney() - cost);
							player.updateKit();
						};
						String message = null;
						switch (result) {
						case ADDED:
							run.run();
							applied = true;
							message = "items_added";
							break;
						case FILLED:
							run.run();
							applied = true;
							message = "items_refilled";
							break;
						case REMOVED:
							run.run();
							applied = true;
							message = "items_removed";
							break;
						case REPLACED:
							run.run();
							applied = true;
							message = "items_replaced";
							break;
						case ALREADY_EMPTIED:
							// no running, items were not added
							message = "cant_sell";
							break;
						case ALREADY_MAXED:
							// no running, items were not added
							message = "item_limit";
							break;
						case SKIPPED:
							// no running, items were not added
							message = "item_conflict";
							break;
						}
						if (notify) LangManager.sendMessage(player, message);
					} else {
						if (notify) LangManager.sendMessage(player, "no_money_buy");
					}
				} else {
					if (notify) LangManager.sendMessage(player, "cant_do");
				}
			}
		}
		return applied;
	}
	
	@Override
	public Lobby getLobby() {
		return lobby;
	}
	
	@Override
	public List<Bonus> getBonuses() {
		return bonuses;
	}
	
	@Override
	public int getHeightLimit() {
		return heightLimit;
	}
	
	@Override
	public Location getCenter() {
		return center;
	}
	
	@Override
	public Arena getArena() {
		return arena;
	}
	
	@Override
	public int getMaxPlayers() {
		return maxPlayers;
	}
	
	@Override
	public int getTimeLeft() {
		return timeLeft;
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onClick(PlayerInteractEvent event) {
		InGamePlayer data = getPlayers().get(event.getPlayer().getUniqueId());
		if (data != null) {
			event.setCancelled(true);
			// handle button clicking
			if (event.hasBlock()) {
				// this prevents double clicks on next tick
				if (DoubleClickBlocker.isBlocked(event.getPlayer())) {
					return;
				} else {
					DoubleClickBlocker.block(event.getPlayer());
				}
				// apply the button
				Button button = buttons.values().stream()
						.filter(b -> b.getLocations().stream()
								.map(loc -> loc.getBlock())
								.anyMatch(block -> event.getClickedBlock().equals(block))
						)
						.findFirst()
						.orElse(null);
				if (button != null) {
					FlierClickButtonEvent e = new FlierClickButtonEvent(data, button);
					Bukkit.getPluginManager().callEvent(e);
					if (!e.isCancelled()) {
						applyButton(data, button, event.getAction() == Action.LEFT_CLICK_BLOCK, true);
					}
					return;
				}
				// handle leaving block
				if (leaveBlocks.contains(event.getClickedBlock())) {
					lobby.leaveGame(event.getPlayer());
				}
			}
			// not a button
			ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
			Wings wings = data.getKit().getWings();
			if (item != null && wings != null && item.isSimilar(wings.getItem(data))) {
				// handle wearing wings
				event.getPlayer().getInventory().setChestplate(item);
				event.getPlayer().getInventory().setItemInMainHand(null);
				event.getPlayer().getWorld().playSound(
						event.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1, 1);
			} else {
				// handle a regular click
				switch (event.getAction()) {
				case LEFT_CLICK_AIR:
				case LEFT_CLICK_BLOCK:
					data.addTrigger("left_click");
					break;
				case RIGHT_CLICK_AIR:
				case RIGHT_CLICK_BLOCK:
					data.addTrigger("right_click");
					break;
				default:
					break;
				}
			}
		}
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onDamage(EntityDamageEvent event) {
		if (event.isCancelled()) {
			return;
		}
		// prevent out-of-game damage to targets
		Target target = getTargets().get(event.getEntity().getUniqueId());
		if (target != null) {
			if (!allowedDamage.contains(event.getCause())) {
				event.setCancelled(true);
			}
			// handle death of players
			if (target instanceof InGamePlayer) {
				InGamePlayer data = (InGamePlayer) target;
				if (data.getPlayer().getHealth() - event.getFinalDamage() <= 0) {
					event.setCancelled(true);
					handleKill(data, event.getCause());
				}
			}
		}
		// prevent damage to entities caused by in-game targets
		if (event instanceof EntityDamageByEntityEvent) {
			EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
			Target attacker = getTargets().get(entityEvent.getDamager().getUniqueId());
			if (attacker != null) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void onExplode(ExplosionPrimeEvent event) {
		if (event.isCancelled()) {
			return;
		}
		Attacker weapon = Attacker.getAttacker(event.getEntity());
		if (weapon == null) {
			return;
		}
		if (!weapon.getDamager().isExploding()) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onBlockExplode(EntityExplodeEvent event) {
		if (Attacker.getAttacker(event.getEntity()) != null) {
			event.blockList().clear();
		}
	}
	
	@EventHandler
	public void onInvInteract(InventoryClickEvent event) {
		if (getPlayers().containsKey(event.getWhoClicked().getUniqueId())) {
			event.setCancelled(true);
		}
	}
	
	private void shotDownMessage(String message, InGamePlayer killed, InGamePlayer killer) {
		for (InGamePlayer player : dataMap.values()) {
			LangManager.sendMessage(player, message,
					Utils.formatPlayer(killed, player),
					Utils.formatPlayer(killer, player));
		}
	}
	
	private void killedMessage(String message, InGamePlayer killed, InGamePlayer killer) {
		for (InGamePlayer player : dataMap.values()) {
			LangManager.sendMessage(player, message,
					Utils.formatPlayer(killed, player),
					Utils.formatPlayer(killer, player));
		}
	}
	
	private void suicideMessage(String message, InGamePlayer killed) {
		for (InGamePlayer player : dataMap.values()) {
			LangManager.sendMessage(player, message,
					Utils.formatPlayer(killed, player));
		}
	}
	
	private void pay(InGamePlayer player, int amount) {
		player.setMoney(player.getMoney() + amount);
	}

}