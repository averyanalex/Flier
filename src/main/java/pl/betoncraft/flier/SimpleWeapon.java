/** This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://www.wtfpl.net/ for more details.
 */
package pl.betoncraft.flier;

import java.util.Date;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import pl.betoncraft.flier.api.Damager;

/**
 * Implementation of a simple, burst shooting weapon.
 *
 * @author Jakub Sapalski
 */
public class SimpleWeapon extends DefaultWeapon {
	
	private EntityType entity = EntityType.FIREBALL;
	private int burstAmount = 10;
	private int burstTicks = 1;
	private double projectileSpeed = 5;
	
	public SimpleWeapon(ConfigurationSection section) {
		super(section);
		entity = EntityType.valueOf(section.getString("entity", "FIREBALL").toUpperCase().replace(' ', '_'));
		burstAmount = section.getInt("burst_amount", burstAmount);
		burstTicks = section.getInt("burst_ticks", burstTicks);
		projectileSpeed = section.getDouble("projectile_speed", projectileSpeed);
	}
	
	@Override
	public void use(PlayerData data) {
		if (onlyAir() && !data.isFlying()) {
			return;
		}
		Player player = data.getPlayer();
		UUID id = player.getUniqueId();
		if (weaponCooldown.containsKey(id)) {
			return;
		}
		weaponCooldown.put(id, new Date().getTime() + 50*cooldown);
		new BukkitRunnable() {
			int counter = burstAmount;
			@Override
			public void run() {
				Vector velocity = player.getLocation().getDirection().clone().multiply(projectileSpeed);
				Vector pointer = player.getLocation().getDirection().clone().multiply(player.getVelocity().length() * 3);
				Location launch = (player.isGliding() ? player.getLocation() : player.getEyeLocation())
						.clone().add(pointer);
				Projectile projectile = (Projectile) launch.getWorld().spawnEntity(launch, entity);
				projectile.setVelocity(velocity);
				projectile.setShooter(player);
				projectile.setGravity(false);
				Damager.saveDamager(projectile, SimpleWeapon.this);
				counter --;
				if (counter <= 0) {
					cancel();
				}
			}
		}.runTaskTimer(Flier.getInstance(), 0, burstTicks);
	}

}
