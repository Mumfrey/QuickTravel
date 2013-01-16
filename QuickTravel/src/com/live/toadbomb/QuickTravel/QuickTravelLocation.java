package com.live.toadbomb.QuickTravel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.entity.Player;

/**
 * Encapsulates all information about a quicktravel location in the world, and provides mutation methods for
 * utilising the QT point. This replaces the non-stateful config-based code of prior versions and provides
 * and base for making future improvements to the plugin's functionality 
 *
 * @author Mumfrey
 */
public class QuickTravelLocation
{
	/**
	 * Type of location region, either radius or cuboid
	 */
	public enum Type
	{
		Radius,
		Cuboid
	}
	
	/**
	 * Name of this quicktravel point
	 */
	private String name = "";

	/**
	 * Currently defined type of this quicktravel location
	 */
	private Type type = Type.Radius;
	
	/**
	 * Whether this quicktravel location is enabled or not
	 */
	private boolean enabled = true;
	
	/**
	 * True if this quicktravel location requires discovery to be used
	 */
	private boolean requireDiscovery = true;
	
	/**
	 * True if this quicktravel location requires permissions to be used
	 */
	private boolean requirePermissions = false;
	
	/**
	 * True if this quicktravel location is free 
	 */
	private boolean free = false;
	
	/**
	 * True if this quicktravel location is multi-world capable
	 */
	private boolean multiworld = false;
	
	/**
	 * Locations 
	 */
	private Location primary, secondary, destination;
	
	/**
	 * Radius used when in radius mode 
	 */
	private double radius = 5.0;
	
	/**
	 * Radius squared, used for comparisons with entity ranges
	 */
	private  double radiusSquared = 25.0;
	
	/**
	 * Set of player names that have discovered this qt location 
	 */
	private Set<String> discoveredBy = new HashSet<String>();
	
	/**
	 * Specific charges from different QT points. QT's are stored by reference so that we don't have 
	 * to worry about them being renamed! 
	 */
	private Map<QuickTravelLocation, Double> chargeFrom = new HashMap<QuickTravelLocation, Double>();
	
	/**
	 * Currently configured departure effect
	 */
	private QuickTravelFX departFX = new QuickTravelFX(32);

	/**
	 * Currently configured arrival effect
	 */
	private QuickTravelFX arriveFX = new QuickTravelFX(32);
	
	/**
	 * @param name Name for this quicktravel location
	 * @param config Configuration to read the location data from
	 * @param defaultRadius default configured radius
	 * @param enabledByDefault default for "enabled"
	 * @param requireDiscoveryByDefault default for "require discovery"
	 * @param requirePermissionsByDefault default for "require permissions"
	 * @param multiworldByDefault default for "multi world"
	 * @param freeByDefault default for "free" flag
	 */
	public QuickTravelLocation(String name, ConfigurationSection config, double defaultRadius, boolean enabledByDefault, boolean requireDiscoveryByDefault, boolean requirePermissionsByDefault, boolean multiworldByDefault, boolean freeByDefault)
	{
		this.name = name.toLowerCase();
		
		this.loadFromConfigSection(config, defaultRadius, enabledByDefault, requireDiscoveryByDefault, requirePermissionsByDefault, multiworldByDefault, freeByDefault);
	}
	
	/**
	 * @param name Name for this quicktravel location
	 * @param location Location for the, er. location
	 * @param radius Initial radius to use
	 */
	public QuickTravelLocation(String name, Location location, double radius)
	{
		this.name        = name.toLowerCase();
		this.destination = location.clone();
		this.primary     = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
		
		this.setRadius(radius);
	}
	
	/**
	 * Sets this location's data using the specified configuration section
	 * 
	 * @param config Configuration to read the location data from
	 * @param defaultRadius default configured radius
	 * @param enabledByDefault default for "enabled"
	 * @param requireDiscoveryByDefault default for "require discovery"
	 * @param requirePermissionsByDefault default for "require permissions"
	 * @param multiworldByDefault default for "multi world"
	 * @param freeByDefault default for "free" flag
	 */
	@SuppressWarnings("unchecked")
	public void loadFromConfigSection(ConfigurationSection config, double defaultRadius, boolean enabledByDefault, boolean requireDiscoveryByDefault, boolean requirePermissionsByDefault, boolean multiworldByDefault, boolean freeByDefault)
	{
		this.reset(defaultRadius, enabledByDefault, requireDiscoveryByDefault, requirePermissionsByDefault, multiworldByDefault, freeByDefault);
		
		World world = Bukkit.getWorld(config.getString("world")); 

		this.name               = config.getString("name", this.getName()).toLowerCase();
		this.type               = config.getString("type", "radius").toLowerCase().equals("cuboid") ? Type.Cuboid : Type.Radius;
		this.enabled            = config.getBoolean("enabled", enabledByDefault);
		this.requireDiscovery   = config.getBoolean("require-discovery", requireDiscoveryByDefault);
		this.requirePermissions = config.getBoolean("require-permissions", requirePermissionsByDefault);
		this.free               = config.getBoolean("free", freeByDefault);
		this.multiworld         = config.getBoolean("multiworld", multiworldByDefault);

		this.primary            = this.parseLocation(config.getConfigurationSection("coords.primary"), world, null);
		this.secondary          = this.parseLocation(config.getConfigurationSection("coords.secondary"), world, null);
		this.destination        = this.parseLocation(config.getConfigurationSection("coords.dest"), world, this.primary);
		
		this.setRadius(config.getDouble("radius", defaultRadius));
		
		List<String> discoveryList = (List<String>)config.getList("discovered-by");
		if (discoveryList != null) this.discoveredBy.addAll(discoveryList);
	}
	
	/**
	 * Called after all QT's have been loaded, links up the QT's with each other
	 * 
	 * @param qtProvider
	 * @param config
	 */
	public void linkUsingConfigSection(QuickTravelLocationProvider qtProvider, MemorySection config)
	{
		MemorySection chargeFromConfig = (MemorySection)config.getConfigurationSection("charge-from");
		
		if (chargeFromConfig != null)
		{
			for (Entry<String, Object> chargeFromConfigEntry : chargeFromConfig.getValues(false).entrySet())
			{
				QuickTravelLocation chargeFromEntry = qtProvider.getLocationByName(chargeFromConfigEntry.getKey());
				if (chargeFromEntry != null) this.chargeFrom.put(chargeFromEntry, (Double)chargeFromConfigEntry.getValue());
			}
		}
	}
	
	/**
	 * Write this location to the specified config
	 * 
	 * @param config
	 */
	public void saveToConfigSection(ConfigurationSection config)
	{
		config.set("name",                this.name);
		config.set("type",                this.type.name());
		config.set("radius",              this.radius);
		config.set("require-discovery",   this.requireDiscovery);
		config.set("require-permissions", this.requirePermissions );
		config.set("free",                this.free);
		config.set("multiworld",          this.multiworld);
		
		if (this.primary != null)
		{
			if (this.primary.getWorld() != null)
			{
				config.set("world", this.primary.getWorld().getName());
			}
			
			config.set("coords.primary.x", this.primary.getX()); 
			config.set("coords.primary.y", this.primary.getY()); 
			config.set("coords.primary.z", this.primary.getZ()); 
		}
		
		if (this.secondary != null)
		{
			config.set("coords.secondary.x", this.secondary.getX()); 
			config.set("coords.secondary.y", this.secondary.getY()); 
			config.set("coords.secondary.z", this.secondary.getZ()); 
		}

		if (this.destination != null)
		{
			config.set("coords.dest.x",     this.destination.getX()); 
			config.set("coords.dest.y",     this.destination.getY()); 
			config.set("coords.dest.z",     this.destination.getZ()); 
			config.set("coords.dest.pitch", this.destination.getPitch()); 
			config.set("coords.dest.yaw",   this.destination.getYaw()); 
		}
		
		config.set("discovered-by", this.discoveredBy.toArray(new String[0]));
		
		for (Entry<QuickTravelLocation, Double> chargeFromEntry : this.chargeFrom.entrySet())
		{
			config.set("charge-from." + chargeFromEntry.getKey().name, chargeFromEntry.getValue());
		}
	}

	/**
	 * Reset all location values back to defaults
	 * 
	 * @param defaultRadius default configured radius
	 * @param enabledByDefault default for "enabled"
	 * @param requireDiscoveryByDefault default for "require discovery"
	 * @param requirePermissionsByDefault default for "require permissions"
	 * @param multiworldByDefault default for "multi world"
	 * @param freeByDefault default for "free" flag
	 */
	protected void reset(double defaultRadius, boolean enabledByDefault, boolean requireDiscoveryByDefault, boolean requirePermissionsByDefault, boolean multiworldByDefault, boolean freeByDefault)
	{
		this.type               = Type.Radius;
		this.enabled            = enabledByDefault;
		this.requireDiscovery   = requireDiscoveryByDefault;
		this.requirePermissions = requirePermissionsByDefault;
		this.free               = freeByDefault;
		this.multiworld         = multiworldByDefault;
		this.primary            = null;
		this.secondary          = null;
		this.destination        = null;
		
		this.setRadius(defaultRadius);
		
		this.discoveredBy.clear();
		this.chargeFrom.clear();
	}

	/**
	 * Read a location from the specified config
	 * 
	 * @param config Config to read from
	 * @param world World to use if no world can be parsed
	 * @param defaultValue Value to return if the config node does not contain the required data
	 * @return
	 */
	private Location parseLocation(ConfigurationSection config, World world, Location defaultValue)
	{
		if (config == null || !config.isSet("x") || !config.isSet("y") || !config.isSet("z"))
		{
			return defaultValue == null ? null : defaultValue.clone();
		}
		
		double xCoord = config.getDouble("x", 0.0);
		double yCoord = config.getDouble("y", 0.0);
		double zCoord = config.getDouble("z", 0.0);
		float yaw     = (float)config.getDouble("yaw", 0.0);
		float pitch   = (float)config.getDouble("pitch", 0.0);
		
		return new Location(world, xCoord, yCoord, zCoord, yaw, pitch);
	}
	
	/**
	 * Get whether this location's region contains the specified location
	 * 
	 * @param coords Location to test
	 * @param heightModifier Height modifier used for cuboid regions
	 * @return True if the specified location is inside this location's region
	 */
	public boolean regionContains(Location coords, int heightModifier)
	{
		if (!this.enabled || (coords != null && coords.getWorld() != this.primary.getWorld())) return false;
		
		// Use this behaviour if set to radius, or if the second point has not been set yet
		if ((this.type == Type.Radius || this.secondary == null) && this.primary != null && coords != null)
		{
			return coords.distanceSquared(this.primary) < this.radiusSquared; 
		}
		
		// Check a cuboid region
		if (this.type == Type.Cuboid && this.primary != null && this.secondary != null && coords != null)
		{
			int minX = Math.min(this.primary.getBlockX(), this.secondary.getBlockX());
			int minY = Math.min(this.primary.getBlockY(), this.secondary.getBlockY());
			int minZ = Math.min(this.primary.getBlockZ(), this.secondary.getBlockZ());
			int maxX = Math.max(this.primary.getBlockX(), this.secondary.getBlockX()) + 1;
			int maxY = Math.max(this.primary.getBlockY(), this.secondary.getBlockY()) + heightModifier;
			int maxZ = Math.max(this.primary.getBlockZ(), this.secondary.getBlockZ()) + 1;
			
			return (coords.getX() >= minX && coords.getX() < maxX && coords.getY() >= minY && coords.getY() < maxY && coords.getZ() >= minZ && coords.getZ() < maxZ);
		}
		
		return false;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return this.getName();
	}
	
	/**
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name)
	{
		this.name = name.toLowerCase();
	}

	/**
	 * @return the world
	 */
	public World getWorld()
	{
		return this.primary != null ? this.primary.getWorld() : null;
	}
	
	/**
	 * Checks whether this region is in the specified world, returns true if world is null (meaning any world)
	 * 
	 * @param other World to test or null to test all worlds
	 * @return
	 */
	public boolean isInWorld(World other)
	{
		if (other == null) return true;
		World thisWorld = this.getWorld();
		return other.equals(thisWorld);
	}
	
	/**
	 * @param player
	 * @return
	 */
	public boolean checkWorld(Player player)
	{
		if (this.multiworld) return true;
		
		if (player != null && this.primary != null)
		{
			return this.primary.getWorld() == player.getWorld();
		}
		
		return false;
	}
	
	/**
	 * @param player
	 * @return
	 */
	public boolean checkPermission(Player player)
	{
		if (this.requirePermissions)
		{
			return player.hasPermission("qt.use" + this.name);
		}
		
		return true;
	}
	
	/**
	 * @return
	 */
	public Type getType()
	{
		return this.type;
	}
	
	/**
	 * Set the type by string, currently supports "cuboid", "radius" and "toggle"
	 * 
	 * @param type
	 */
	public void setType(String type)
	{
		if (type.equalsIgnoreCase(Type.Cuboid.name()))
		{
			this.setType(Type.Cuboid);
		}
		else if (type.equalsIgnoreCase("toggle"))
		{
			this.toggleType();
		}
		else
		{
			this.setType(Type.Radius);
		}
	}
	
	/**
	 * Set the type
	 * 
	 * @param type
	 */
	public void setType(Type type)
	{
		this.type = type;
	}
	
	/**
	 * Toggle the type
	 */
	public void toggleType()
	{
		this.type = (this.type == Type.Cuboid) ? Type.Radius : Type.Cuboid;
	}
	
	/**
	 * @return the primary location
	 */
	public Location getPrimary()
	{
		return this.primary;
	}
	
	/**
	 * Set the primary location
	 * 
	 * @param location location to set
	 * @param moveDestination set this to true to move the destination point as well as the primary
	 */
	public void setPrimary(Location location, boolean moveDestination)
	{
		this.primary = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
		
		if (this.secondary != null && !this.secondary.getWorld().equals(location.getWorld()))
		{
			this.secondary = null;
		}
		
		if (moveDestination)
		{
			this.destination = location.clone();
		}
	}
	
	/**
	 * @return the secondary location
	 */
	public Location getSecondary()
	{
		return this.secondary;
	}
	
	/**
	 * Set the secondary location
	 * 
	 * @param location
	 */
	public void setSecondary(Location location)
	{
		this.secondary = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}
	
	/**
	 * @return
	 */
	public Location getDestination()
	{
		return this.destination;
	}
	
	/**
	 * @param location
	 */
	public void setDestination(Location location)
	{
		this.destination = location.clone();
	}
	
	/**
	 * @return
	 */
	public double getRadius()
	{
		return this.radius;
	}
	
	/**
	 * @return
	 */
	public double getRadiusSquared()
	{
		return this.radiusSquared;
	}
	
	/**
	 * @param radius
	 */
	public void setRadius(double radius)
	{
		this.radius = Math.max(1, Math.abs(radius));
		this.radiusSquared = this.radius * this.radius;
	}
	
	/**
	 * Check whether this QT has been discovered by the specified player
	 * 
	 * @param player
	 * @return
	 */
	public boolean isDiscoveredBy(Player player)
	{
		return this.discoveredBy.contains(player.getName());
	}
	
	/**
	 * Notify this QT that it has been discovered by the specified player
	 * 
	 * @param player
	 */
	public void setDiscovered(Player player)
	{
		this.discoveredBy.add(player.getName());
	}

	/**
	 * @return
	 */
	public boolean isEnabled()
	{
		return this.enabled;
	}
	
	/**
	 * @param enabled
	 */
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}
	
	/**
	 * @return
	 */
	public boolean isFree()
	{
		return this.free;
	}
	
	/**
	 * @param free
	 */
	public void setFree(boolean free)
	{
		this.free = free;
	}
	
	/**
	 * @return
	 */
	public boolean isMultiworld()
	{
		return this.multiworld;
	}
	
	/**
	 * @param multiWorld
	 */
	public void setMultiWorld(boolean multiWorld)
	{
		this.multiworld = multiWorld;
	}
	
	/**
	 * @return
	 */
	public boolean requiresDiscovery()
	{
		return this.requireDiscovery;
	}
	
	/**
	 * @param requireDiscovery
	 */
	public void setRequiresDiscovery(boolean requireDiscovery)
	{
		this.requireDiscovery = requireDiscovery;
	}
	
	/**
	 * @return
	 */
	public boolean requiresPermission()
	{
		return this.requirePermissions;
	}
	
	/**
	 * @param requirePermission
	 */
	public void setRequiresPermission(boolean requirePermission)
	{
		this.requirePermissions = requirePermission;
	}
	
	/**
	 * @param origin
	 * @return
	 */
	public boolean shouldChargeFrom(QuickTravelLocation origin)
	{
		return this.chargeFrom.containsKey(origin);
	}
	
	/**
	 * @param origin
	 * @return
	 */
	public double getChargeFrom(QuickTravelLocation origin)
	{
		return (this.chargeFrom.containsKey(origin)) ? this.chargeFrom.get(origin) : 0.0;
	}
	
	/**
	 * @param origin
	 * @param newCharge
	 */
	public void setChargeFrom(QuickTravelLocation origin, double newCharge)
	{
		this.chargeFrom.put(origin, newCharge);
	}
	
	/**
	 * @param origin
	 */
	public void resetChargeFrom(QuickTravelLocation origin)
	{
		this.chargeFrom.remove(origin);
	}

	/**
	 * Gets the target location (the location to teleport to if teleporting to this QT
	 * 
	 * @return
	 */
	public Location getTargetLocation()
	{
		return this.destination != null ? this.destination : (this.primary != null ? this.primary : null);
	}
	
	/**
	 * @return the departFX
	 */
	public QuickTravelFX getDepartureEffect()
	{
		return departFX;
	}

	/**
	 * @param departFX the departFX to set
	 */
	public void setDepartureEffect(QuickTravelFX departFX)
	{
		this.departFX = departFX;
	}

	/**
	 * @return the arriveFX
	 */
	public QuickTravelFX getArrivalEffect()
	{
		return this.arriveFX;
	}

	/**
	 * @param arriveFX the arriveFX to set
	 */
	public void setArrivalEffect(QuickTravelFX arriveFX)
	{
		this.arriveFX = arriveFX;
	}
	
	/**
	 * Performs the teleportation to this QT and plays effects as required
	 * 
	 * @param player Player to teleport
	 * @param departureEffect effect from the source QT or wilderness
	 * @param enableSafetyChecks True to run the safety checks before teleporting
	 */
	public void teleport(Player player, QuickTravelFX departureEffect, boolean enableSafetyChecks)
	{
		if (player == null) return;
		
		Location targetLocation = getTargetLocation();
		Location loc = player.getLocation();
		
		if (targetLocation != null && player.getLocation() != null)
		{		
			Location destination = enableSafetyChecks ? this.checkSafe(targetLocation, player) : targetLocation;
			player.teleport(destination);
			
			if (departureEffect != null) departureEffect.playTeleportEffect(loc);
			if (this.getArrivalEffect() != null) this.getArrivalEffect().playTeleportEffect(destination);
		}
	}

	
	/**
	 * 
	 * 
	 * @param d
	 * @param p
	 * @return
	 */
	public Location checkSafe(Location d, Player p)
	{
		// TODO refactor
		
		World w = d.getWorld();
		double x = d.getX();
		double y = d.getY();
		double z = d.getZ();
		Location d2 = new Location(w, x + 1, y, z);
		Location d3 = new Location(w, x + 1, y, z + 1);
		Location d4 = new Location(w, x, y, z + 1);
		Location d5 = new Location(w, x - 1, y, z + 1);
		Location d6 = new Location(w, x - 1, y, z);
		Location d7 = new Location(w, x - 1, y, z - 1);
		Location d8 = new Location(w, x, y, z - 1);
		Location d9 = new Location(w, x + 1, y, z - 1);
		Location db = new Location(w, x, y + 1, z);
		Location db2 = new Location(w, x + 1, y + 1, z);
		Location db3 = new Location(w, x + 1, y + 1, z + 1);
		Location db4 = new Location(w, x, y + 1, z + 1);
		Location db5 = new Location(w, x - 1, y + 1, z + 1);
		Location db6 = new Location(w, x - 1, y + 1, z);
		Location db7 = new Location(w, x - 1, y + 1, z - 1);
		Location db8 = new Location(w, x, y + 1, z - 1);
		Location db9 = new Location(w, x + 1, y + 1, z - 1);
		Location dbb = new Location(w, x, y + 2, z);
		Location dbb2 = new Location(w, x + 1, y + 2, z);
		Location dbb3 = new Location(w, x + 1, y + 2, z + 1);
		Location dbb4 = new Location(w, x, y + 2, z + 1);
		Location dbb5 = new Location(w, x - 1, y + 2, z + 1);
		Location dbb6 = new Location(w, x - 1, y + 2, z);
		Location dbb7 = new Location(w, x - 1, y + 2, z - 1);
		Location dbb8 = new Location(w, x, y + 2, z - 1);
		Location dbb9 = new Location(w, x + 1, y + 2, z - 1);
		Location dc = new Location(w, x, y - 1, z);
		Location dc2 = new Location(w, x + 1, y - 1, z);
		Location dc3 = new Location(w, x + 1, y - 1, z + 1);
		Location dc4 = new Location(w, x, y - 1, z + 1);
		Location dc5 = new Location(w, x - 1, y - 1, z + 1);
		Location dc6 = new Location(w, x - 1, y - 1, z);
		Location dc7 = new Location(w, x - 1, y - 1, z - 1);
		Location dc8 = new Location(w, x, y - 1, z - 1);
		Location dc9 = new Location(w, x + 1, y - 1, z - 1);
		Location dcc = new Location(w, x, y - 2, z);
		Location dcc2 = new Location(w, x + 1, y - 2, z);
		Location dcc3 = new Location(w, x + 1, y - 2, z + 1);
		Location dcc4 = new Location(w, x, y - 2, z + 1);
		Location dcc5 = new Location(w, x - 1, y - 2, z + 1);
		Location dcc6 = new Location(w, x - 1, y - 2, z);
		Location dcc7 = new Location(w, x - 1, y - 2, z - 1);
		Location dcc8 = new Location(w, x, y - 2, z - 1);
		Location dcc9 = new Location(w, x + 1, y - 2, z - 1);
		boolean fix = false;
		if (!d.getBlock().isEmpty())
		{
			d.getBlock().setType(Material.AIR);
			fix = true;
		}
		if (d2.getBlock().getType() == Material.LAVA || d2.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			d2.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d3.getBlock().getType() == Material.LAVA || d3.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			d3.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d4.getBlock().getType() == Material.LAVA || d4.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			d4.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d5.getBlock().getType() == Material.LAVA || d5.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			d5.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d6.getBlock().getType() == Material.LAVA || d6.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			d6.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d7.getBlock().getType() == Material.LAVA || d7.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			d7.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d8.getBlock().getType() == Material.LAVA || d8.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			d8.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d9.getBlock().getType() == Material.LAVA || d9.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			d9.getBlock().setType(Material.GLASS);
			fix = true;
		}
		
		if (!db.getBlock().isEmpty())
		{
			db.getBlock().setType(Material.AIR);
			fix = true;
		}
		if (db2.getBlock().getType() == Material.LAVA || db2.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			db2.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db3.getBlock().getType() == Material.LAVA || db3.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			db3.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db4.getBlock().getType() == Material.LAVA || db4.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			db4.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db5.getBlock().getType() == Material.LAVA || db5.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			db5.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db6.getBlock().getType() == Material.LAVA || db6.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			db6.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db7.getBlock().getType() == Material.LAVA || db7.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			db7.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db8.getBlock().getType() == Material.LAVA || db8.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			db8.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db9.getBlock().getType() == Material.LAVA || db9.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			db9.getBlock().setType(Material.GLASS);
			fix = true;
		}
		
		if (dbb.getBlock().getType() == Material.LAVA || dbb.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb2.getBlock().getType() == Material.LAVA || dbb2.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			dbb2.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb3.getBlock().getType() == Material.LAVA || dbb3.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			dbb3.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb4.getBlock().getType() == Material.LAVA || dbb4.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			dbb4.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb5.getBlock().getType() == Material.LAVA || dbb5.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			dbb5.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb6.getBlock().getType() == Material.LAVA || dbb6.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			dbb6.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb7.getBlock().getType() == Material.LAVA || dbb7.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			dbb7.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb8.getBlock().getType() == Material.LAVA || dbb8.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			dbb8.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb9.getBlock().getType() == Material.LAVA || dbb9.getBlock().getType() == Material.STATIONARY_LAVA)
		{
			dbb9.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		
		if (dc.getBlock().getType() == Material.LAVA || dc.getBlock().getType() == Material.STATIONARY_LAVA || dc.getBlock().isEmpty())
		{
			dc.getBlock().setType(Material.GLASS);
			fix = true;
			if (dc2.getBlock().getType() == Material.LAVA || dc2.getBlock().getType() == Material.STATIONARY_LAVA)
			{
				dc2.getBlock().setType(Material.GLASS);
			}
			if (dc3.getBlock().getType() == Material.LAVA || dc3.getBlock().getType() == Material.STATIONARY_LAVA)
			{
				dc3.getBlock().setType(Material.GLASS);
			}
			if (dc4.getBlock().getType() == Material.LAVA || dc4.getBlock().getType() == Material.STATIONARY_LAVA)
			{
				dc4.getBlock().setType(Material.GLASS);
			}
			if (dc5.getBlock().getType() == Material.LAVA || dc5.getBlock().getType() == Material.STATIONARY_LAVA)
			{
				dc5.getBlock().setType(Material.GLASS);
			}
			if (dc6.getBlock().getType() == Material.LAVA || dc6.getBlock().getType() == Material.STATIONARY_LAVA)
			{
				dc6.getBlock().setType(Material.GLASS);
			}
			if (dc7.getBlock().getType() == Material.LAVA || dc7.getBlock().getType() == Material.STATIONARY_LAVA)
			{
				dc7.getBlock().setType(Material.GLASS);
			}
			if (dc8.getBlock().getType() == Material.LAVA || dc8.getBlock().getType() == Material.STATIONARY_LAVA)
			{
				dc8.getBlock().setType(Material.GLASS);
			}
			if (dc9.getBlock().getType() == Material.LAVA || dc9.getBlock().getType() == Material.STATIONARY_LAVA)
			{
				dc9.getBlock().setType(Material.GLASS);
			}
		}
		else
		{
			if (d2.getBlock().getType() == Material.GLASS && fix == true)
			{
				dc2.getBlock().setType(Material.GLASS);
			}
			if (d3.getBlock().getType() == Material.GLASS && fix == true)
			{
				dc3.getBlock().setType(Material.GLASS);
			}
			if (d4.getBlock().getType() == Material.GLASS && fix == true)
			{
				dc4.getBlock().setType(Material.GLASS);
			}
			if (d5.getBlock().getType() == Material.GLASS && fix == true)
			{
				dc5.getBlock().setType(Material.GLASS);
			}
			if (d6.getBlock().getType() == Material.GLASS && fix == true)
			{
				dc6.getBlock().setType(Material.GLASS);
			}
			if (d7.getBlock().getType() == Material.GLASS && fix == true)
			{
				dc7.getBlock().setType(Material.GLASS);
			}
			if (d8.getBlock().getType() == Material.GLASS && fix == true)
			{
				dc8.getBlock().setType(Material.GLASS);
			}
			if (d9.getBlock().getType() == Material.GLASS && fix == true)
			{
				dc9.getBlock().setType(Material.GLASS);
			}
		}
		
		if ((dcc.getBlock().getType() == Material.LAVA || dcc.getBlock().getType() == Material.STATIONARY_LAVA) && (dc.getBlock().getType() == Material.GLASS && fix == true))
		{
			dcc.getBlock().setType(Material.GLASS);
		}
		if ((dcc2.getBlock().getType() == Material.LAVA || dcc2.getBlock().getType() == Material.STATIONARY_LAVA) && (dc2.getBlock().getType() == Material.GLASS && fix == true))
		{
			dcc2.getBlock().setType(Material.GLASS);
		}
		if ((dcc3.getBlock().getType() == Material.LAVA || dcc3.getBlock().getType() == Material.STATIONARY_LAVA) && (dc3.getBlock().getType() == Material.GLASS && fix == true))
		{
			dcc3.getBlock().setType(Material.GLASS);
		}
		if ((dcc4.getBlock().getType() == Material.LAVA || dcc4.getBlock().getType() == Material.STATIONARY_LAVA) && (dc4.getBlock().getType() == Material.GLASS && fix == true))
		{
			dcc4.getBlock().setType(Material.GLASS);
		}
		if ((dcc5.getBlock().getType() == Material.LAVA || dcc5.getBlock().getType() == Material.STATIONARY_LAVA) && (dc5.getBlock().getType() == Material.GLASS && fix == true))
		{
			dcc5.getBlock().setType(Material.GLASS);
		}
		if ((dcc6.getBlock().getType() == Material.LAVA || dcc6.getBlock().getType() == Material.STATIONARY_LAVA) && (dc6.getBlock().getType() == Material.GLASS && fix == true))
		{
			dcc6.getBlock().setType(Material.GLASS);
		}
		if ((dcc7.getBlock().getType() == Material.LAVA || dcc7.getBlock().getType() == Material.STATIONARY_LAVA) && (dc7.getBlock().getType() == Material.GLASS && fix == true))
		{
			dcc7.getBlock().setType(Material.GLASS);
		}
		if ((dcc8.getBlock().getType() == Material.LAVA || dcc8.getBlock().getType() == Material.STATIONARY_LAVA) && (dc8.getBlock().getType() == Material.GLASS && fix == true))
		{
			dcc8.getBlock().setType(Material.GLASS);
		}
		if ((dcc9.getBlock().getType() == Material.LAVA || dcc9.getBlock().getType() == Material.STATIONARY_LAVA) && (dc9.getBlock().getType() == Material.GLASS && fix == true))
		{
			dcc9.getBlock().setType(Material.GLASS);
		}
		
		return d;
	}

	/**
	 * Calculates the change from another QT
	 * 
	 * @param player
	 * @param origin
	 * @param priceMultiplier
	 * @param multiWorldMultiplier
	 * @return
	 */
	public int calculateChargeFrom(Player player, QuickTravelLocation origin, double priceMultiplier, double multiWorldMultiplier)
	{
		Location fromLocation = origin != null ? origin.getTargetLocation() : (player != null ? player.getLocation() : null);
		Location toLocation = this.getTargetLocation();
		
		World currentWorld = player != null ? player.getWorld() : Bukkit.getServer().getWorlds().get(0);
		
		if (fromLocation == null) fromLocation = new Location(currentWorld, 0, 0, 0);
		if (toLocation == null) toLocation = new Location(currentWorld, 0, 0, 0);
		
		double xDiff = Math.abs(fromLocation.getBlockX() - toLocation.getBlockX());
		double yDiff = Math.abs(fromLocation.getBlockY() - toLocation.getBlockY());
		double zDiff = Math.abs(fromLocation.getBlockZ() - toLocation.getBlockZ());
		
		double multiplier = (!fromLocation.getWorld().equals(toLocation.getWorld())) ? multiWorldMultiplier : priceMultiplier;

		return (int)Math.ceil((xDiff + yDiff + zDiff) * multiplier);
	}
	
	/**
	 * Called when another QT is deleted, allows this location to remove the deleted QT from its local stors
	 * 
	 * @param other
	 */
	public void notifyQuickTravelDeleted(QuickTravelLocation other)
	{
		this.chargeFrom.remove(other);
	}

	/**
	 * Gets information about this QT as a string, for display in the admin's QT list
	 * 
	 * @param sender
	 * @return
	 */
	public String getInfo(CommandSender sender)
	{
		StringBuilder info = new StringBuilder();
		
		if (this.getWorld() != null)
			info.append("[").append(this.getWorld().getName()).append("] ");
		
		info.append(ChatColor.AQUA).append(this.name).append(ChatColor.WHITE).append(" | ");
		
		if (this.isEnabled())
			info.append(ChatColor.GREEN).append("Enabled ");
		else
			info.append(ChatColor.RED).append("Disabled ");

		if (sender instanceof Player)
		{
			if (this.isDiscoveredBy((Player)sender))
				info.append(ChatColor.WHITE).append(" | ").append(ChatColor.GRAY).append("Discovered");
			else
				info.append(ChatColor.WHITE).append(" | ").append(ChatColor.DARK_GRAY).append("Undiscovered");
		}
		
		return info.toString();
	}
}