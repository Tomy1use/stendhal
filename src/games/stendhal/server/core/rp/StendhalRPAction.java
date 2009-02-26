/* $Id$ */
/***************************************************************************
 *                      (C) Copyright 2003 - Marauroa                      *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/
package games.stendhal.server.core.rp;

import games.stendhal.common.Grammar;
import games.stendhal.server.core.engine.SingletonRepository;
import games.stendhal.server.core.engine.StendhalRPZone;
import games.stendhal.server.core.events.TutorialNotifier;
import games.stendhal.server.core.events.ZoneNotifier;
import games.stendhal.server.core.pathfinder.Node;
import games.stendhal.server.core.pathfinder.Path;
import games.stendhal.server.entity.Entity;
import games.stendhal.server.entity.RPEntity;
import games.stendhal.server.entity.creature.DomesticAnimal;
import games.stendhal.server.entity.creature.Pet;
import games.stendhal.server.entity.creature.Sheep;
import games.stendhal.server.entity.item.Item;
import games.stendhal.server.entity.item.StackableItem;
import games.stendhal.server.entity.npc.SpeakerNPC;
import games.stendhal.server.entity.player.Player;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;

import marauroa.server.game.rp.RPServerManager;

import org.apache.log4j.Logger;


public class StendhalRPAction {

	/** the logger instance. */
	private static final Logger logger = Logger.getLogger(StendhalRPAction.class);

	/** server manager. */
	private static RPServerManager rpman;

	public static void initialize(final RPServerManager rpman) {
		StendhalRPAction.rpman = rpman;
	}

		
	/**
	 * Do logic for starting an attack on an entity.
	 * 
	 * @param player
	 *            The player wanting to attack.
	 * @param victim
	 *            The target of attack.
	 */
	public static void startAttack(final Player player, final RPEntity victim) {
		/*
		 * Player's can't attack themselves
		 */
		if (player.equals(victim)) {
			return;
		}

		// Disable attacking NPCS that are created as not attackable.
		if (!victim.isAttackable()) {
			if ((victim instanceof SpeakerNPC)) {
				((SpeakerNPC) victim).say(player.getName() + ", if you want my attention, just say #hi.");
			}
			logger.info("REJECTED. " + player.getName() + " is attacking "
					+ victim.getName());
			return;
		}

		// Enabled PVP
		if ((victim instanceof Player) || (victim instanceof DomesticAnimal)) {
			final StendhalRPZone zone = player.getZone();

			// Make sure that you can't attack players or sheep (even wild
			// sheep) who are inside a protection area.
			if (zone.isInProtectionArea(victim)) {
				logger.info("REJECTED. " + victim.getName()
						+ " is in a protection zone");

				String name = getNiceVictimName(victim);				

				player.sendPrivateText("The powerful protective aura in this place prevents you from attacking "
						+ name + ".");
				return;
			}
			
			if (victim instanceof Player) {
				// disable attacking much weaker players, except in
				// self defence
				if ((victim.getAttackTarget() != player) && !victimIsStrongEnough(player, victim)) {
					player.sendPrivateText("Your conscience would trouble you if you carried out this attack.");
				
					return;
				}
			} else {
				// Only allow owners, if there is one, to attack the pet
				Player owner = ((DomesticAnimal) victim).getOwner();
				if ((owner != null) && (owner != player)) {
					player.sendPrivateText("You pity " + getNiceVictimName(victim) + " too much to kill it.");
					
					return;
				}
			}

			logger.info(player.getName() + " is attacking " + victim.getName());
		}

		SingletonRepository.getRuleProcessor().addGameEvent(player.getName(), "attack",
				victim.getName());

		player.setTarget(victim);
		player.faceToward(victim);
		player.applyClientDirection(false);
		player.notifyWorldAboutChanges();
	}
	
	/**
	 * Check that the victim has high enough level compared to the attacker.
	 * 
	 * @param player The player trying to attack
	 * @param victim The entity being attacked
	 * @return <code>true</code> if the victim is strong enough to allow
	 *  the attack to happen, <code>false</code> otherwise.
	 */
	private static boolean victimIsStrongEnough(final Player player, final RPEntity victim) {
		return victim.getLevel() + 2.0 >= 0.75 * player.getLevel();
	}
	
	/**
	 * Get a nice target description string to be sent to the attacker in case
	 * the attacking action is forbidden.
	 * 
	 * @param victim The attacked entity
	 * @return Description of the attacked pet or player
	 */
	private static String getNiceVictimName(final RPEntity victim) {
		String name = victim.getTitle();

		if (victim instanceof DomesticAnimal) {
			final Player owner = ((DomesticAnimal) victim).getOwner();

			if (owner != null) {
				name = Grammar.suffix_s(owner.getTitle()) + " " + name;
			} else {
				if (victim instanceof Sheep) {
					name = "that " + name;
				} else {
					name = "that poor little " + name;
				}
			}
		}

		return name;
	}

	/**
	 * Lets the attacker try to attack the defender.
	 * @param player 
	 * 
	 * @param defender
	 *            The defending RPEntity.
	 * @return true iff the attacker has done damage to the defender.
	 * 
	 */
	public static boolean playerAttack(final Player player, final RPEntity defender) {
		boolean result = false;

		final StendhalRPZone zone = player.getZone();
		if (!zone.has(defender.getID()) || (defender.getHP() == 0)) {
			logger.debug("Attack from " + player + " to " + defender
					+ " stopped because target was lost("
					+ zone.has(defender.getID()) + ") or dead.");
			player.stopAttack();

			return false;
		}

		defender.rememberAttacker(player);
		if (defender instanceof Player) {
			player.storeLastPVPActionTime();
		}

		if (!player.nextTo(defender)) {
			// The attacker is not directly standing next to the defender.
			// Find out if he can attack from the distance.
			if (player.canDoRangeAttack(defender)) {

				// Check line of view to see if there is any obstacle.
				if (zone.collidesOnLine(player.getX(), player.getY(),
						defender.getX(), defender.getY())) {
					return false;
				}
				// Get the projectile that will be thrown/shot.
				StackableItem projectilesItem = null;
				if (player.getRangeWeapon() != null) {
					projectilesItem = player.getAmmunition();
				}
				if (projectilesItem == null) {
					// no arrows... but maybe a spear?
					projectilesItem = player.getMissileIfNotHoldingOtherWeapon();
				}
				// Creatures can attack without having projectiles, but players
				// will lose a projectile for each shot.
				if (projectilesItem != null) {
					projectilesItem.removeOne();
				}
			} else {
				logger.debug("Attack from " + player + " to " + defender
						+ " failed because target is not near.");
				return false;
			}
		}

		// {lifesteal} uncomented following line, also changed name:
		final List<Item> weapons = player.getWeapons();

		if (!(defender instanceof SpeakerNPC)
				&& player.getsFightXpFrom(defender)) {
			// disabled attack xp for attacking NPC's
			player.incATKXP();
		}

		// Throw dices to determine if the attacker has missed the defender
		final boolean beaten = player.canHit(defender);

		if (beaten) {
			if ((defender instanceof Player)
					&& defender.getsFightXpFrom(player)) {
				defender.incDEFXP();
			}

			int damage = player.damageDone(defender);
			if (damage > 0) {

				// limit damage to target HP
				damage = Math.min(damage, defender.getHP());
				player.handleLifesteal(player, weapons, damage);

				defender.onDamaged(player, damage);
				player.setDamage(damage);
				logger.debug("attack from " + player.getID() + " to "
						+ defender.getID() + ": Damage: " + damage);

				result = true;
			} else {
				// The attack was too weak, it was blocked
				player.setDamage(0);
				logger.debug("attack from " + player.getID() + " to "
						+ defender.getID() + ": Damage: " + 0);
			}
		} else { 
			// Missed
			logger.debug("attack from " + player.getID() + " to "
					+ defender.getID() + ": Missed");
			player.setDamage(0);
		}

		player.notifyWorldAboutChanges();

		return result;
	}



	/**
	 * send the content of the zone the player is in to the client.
	 * 
	 * @param player
	 */
	public static void transferContent(final Player player) {

		if (rpman != null) {
			final StendhalRPZone zone = player.getZone();
			rpman.transferContent(player, zone.getContents());

		} else {
			logger.warn("rpmanager not found");
		}
	}

	/**
	 * Change an entity's zone based on it's global world coordinates.
	 * 
	 * @param entity
	 *            The entity changing zones.
	 * @param x
	 *            The entity's old zone X coordinate.
	 * @param y
	 *            The entity's old zone Y coordinate.
	 */
	public static void decideChangeZone(final Entity entity, final int x, final int y) {
		final StendhalRPZone origin = entity.getZone();

		final int entity_x = x + origin.getX();
		final int entity_y = y + origin.getY();

		final StendhalRPZone zone = SingletonRepository.getRPWorld().getZoneAt(
				origin.getLevel(), entity_x, entity_y, entity);

		if (zone != null) {
			final int nx = entity_x - zone.getX();
			final int ny = entity_y - zone.getY();

			if (logger.isDebugEnabled()) {
				logger.debug("Placing " + entity.getTitle() + " at "
						+ zone.getName() + "[" + nx + "," + ny + "]");
			}

			if (!placeat(zone, entity, nx, ny)) {
				logger.warn("Could not place " + entity.getTitle() + " at "
						+ zone.getName() + "[" + nx + "," + ny + "]");
			}
		} else {
			logger.warn("Unable to choose a new zone for entity: "
					+ entity.getTitle() + " at (" + entity_x + "," + entity_y
					+ ") source was " + origin.getName() + " at (" + x + ", "
					+ y + ")");
		}
	}

	/**
	 * Places an entity at a specified position in a specified zone. If this
	 * point is occupied the entity is moved slightly. This will remove the
	 * entity from any existing zone and add it to the target zone if needed.
	 * 
	 * @param zone
	 *            zone to place the entity in
	 * @param entity
	 *            the entity to place
	 * @param x
	 *            x
	 * @param y
	 *            y
	 * @return true, if it was possible to place the entity, false otherwise
	 */
	public static boolean placeat(final StendhalRPZone zone, final Entity entity, final int x,
			final int y) {
		return placeat(zone, entity, x, y, null);
	}

	/**
	 * Places an entity at a specified position in a specified zone. This will
	 * remove the entity from any existing zone and add it to the target zone if
	 * needed.
	 * 
	 * @param zone
	 *            zone to place the entity in
	 * @param entity
	 *            the entity to place
	 * @param x
	 *            x
	 * @param y
	 *            y
	 * @param allowedArea
	 *            only search within this area for a possible new position
	 * @return true, if it was possible to place the entity, false otherwise
	 */
	public static boolean placeat(final StendhalRPZone zone, final Entity entity, final int x,
			final int y, final Shape allowedArea) {
		if (zone == null) {
			return false;
		}
		// check in case of players that that they are still in game
		// because the entity is added to the world again otherwise.
		if (entity instanceof Player) {
			final Player player = (Player) entity;
			if (player.isDisconnected()) {
				return true;
			}
		}

		// Look for new position
		int nx = x;
		int ny = y;

		if (zone.collides(entity, x, y)) {
			boolean checkPath = true;

			if (zone.collides(entity, x, y, false)
					&& (entity instanceof Player)) {
				// something nasty happened. The player should be put on a spot
				// with a real collision (not caused by objects).
				// Try to put him anywhere possible without checking the path.
				checkPath = false;
			}

			boolean found = false;

			// We cannot place the entity on the orginal spot. Let's search
			// for a new destination up to maxDestination tiles in every way.
			final int maxDestination = 20;

			outerLoop: for (int k = 1; k <= maxDestination; k++) {
				for (int i = -k; i <= k; i++) {
					for (int j = -k; j <= k; j++) {
						if ((Math.abs(i) == k) || (Math.abs(j) == k)) {
							nx = x + i;
							ny = y + j;
							if (!zone.collides(entity, nx, ny)) {

								// OK, we may place the entity on this spot.

								// Check the possibleArea now. This is a
								// performance
								// optimization because the next step
								// (pathfinding)
								// is very expensive. (5 seconds for a
								// unplaceable
								// black dragon in deathmatch on 0_ados_wall_n)
								if ((allowedArea != null)
										&& (!allowedArea.contains(nx, ny))) {
									continue;
								}

								// We verify that there is a walkable path
								// between the original
								// spot and the new destination. This is to
								// prevent players to
								// enter not allowed places by logging in on top
								// of other players.
								// Or monsters to spawn on the other side of a
								// wall.

								final List<Node> path = Path.searchPath(entity, zone,
										x, y, new Rectangle(nx, ny, 1, 1),
										maxDestination * maxDestination, false);
								if (!checkPath || !path.isEmpty()) {

									// We found a place!

									found = true;
									
									// break all for-loops
									break outerLoop; 
									
								}
							}
						}
					}
				}
			}

			if (!found) {
				logger.info("Unable to place " + entity.getTitle() + " at "
						+ zone.getName() + "[" + x + "," + y + "]");
				return false;
			}
		}

		//
		// At this point the valid position [nx,ny] has been found
		//

		final StendhalRPZone oldZone = entity.getZone();
		final boolean zoneChanged = (oldZone != zone);

		if (entity instanceof RPEntity) {
			final RPEntity rpentity = (RPEntity) entity;

			rpentity.stop();
			rpentity.stopAttack();
			rpentity.clearPath();
		}

		Sheep sheep = null;
		Pet pet = null;

		/*
		 * Remove from old zone (if any) during zone change
		 */
		if (oldZone != null) {
			/*
			 * Player specific pre-remove handling
			 */
			if (entity instanceof Player) {
				final Player player = (Player) entity;

				/*
				 * Remove and remember dependents
				 */
				sheep = player.getSheep();

				if (sheep != null) {
					sheep.clearPath();
					sheep.stop();

					player.removeSheep(sheep);
				}

				pet = player.getPet();

				if (pet != null) {
					pet.clearPath();
					pet.stop();

					player.removePet(pet);
				}
			}

			if (zoneChanged) {
				oldZone.remove(entity);
			}
		}

		/*
		 * [Re]position (possibly while between zones)
		 */
		entity.setPosition(nx, ny);

		/*
		 * Place in new zone (if needed)
		 */
		if (zoneChanged) {
			zone.add(entity);
		}

		/*
		 * Player specific post-change handling
		 */
		if (entity instanceof Player) {
			final Player player = (Player) entity;

			/*
			 * Move and re-add removed dependents
			 */
			if (sheep != null) {
				if (placeat(zone, sheep, nx, ny)) {
					player.setSheep(sheep);
					sheep.setOwner(player);
				} else {
					// Didn't fit?
					player.sendPrivateText("You seemed to have lost your sheep while trying to squeeze in.");
				}
			}

			if (pet != null) {
				if (placeat(zone, pet, nx, ny)) {
					player.setPet(pet);
					pet.setOwner(player);
				} else {
					// Didn't fit?
					player.sendPrivateText("You seemed to have lost your pet while trying to squeeze in.");
				}
			}

			if (zoneChanged) {
				/*
				 * Zone change notifications/updates
				 */
				transferContent(player);

				if (oldZone != null) {
					final String source = oldZone.getName();
					final String destination = zone.getName();

					SingletonRepository.getRuleProcessor().addGameEvent(
							player.getName(), "change zone", destination);

					TutorialNotifier.zoneChange(player, source, destination);
					ZoneNotifier.zoneChange(player, source, destination);
				}
			}
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Placed " + entity.getTitle() + " at "
					+ zone.getName() + "[" + nx + "," + ny + "]");
		}

		return true;
	}
}
