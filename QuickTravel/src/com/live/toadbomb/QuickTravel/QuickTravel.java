package com.live.toadbomb.QuickTravel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class QuickTravel extends JavaPlugin implements Listener {
	public static QuickTravel plugin;
	private static final Logger logger = Logger.getLogger("Minecraft");
	private static final String LOG_PREFIX = "[QuickTravel] ";
	public static boolean economyEnabled;
	
	Plugin Vault;
	
	public static void info(String msg) {
        logger.log(Level.INFO, LOG_PREFIX + msg);
    }
	public static void warning(String msg) {
        logger.log(Level.WARNING, LOG_PREFIX + msg);
    }
    public static void severe(String msg) {
        logger.log(Level.SEVERE, LOG_PREFIX + msg);
    }
	
	/* Loads config files */
	public File configFile;
	protected FileConfiguration config;
	private File locationsFile = null;
	private FileConfiguration locations = null;
		
	public void onDisable() {
		info("Has been disabled.");
	}
	
	public void onEnable() {
		info("Initializing");
		
		/* Initialize config.yml */
		this.getConfig().addDefault("radius", 5);
		this.getConfig().addDefault("height-modifier", 2);
		this.getConfig().addDefault("enabled-by-default", true);
		this.getConfig().addDefault("require-discovery-by-default", true);
		this.getConfig().addDefault("require-permissions-by-default", false);
		this.getConfig().addDefault("multiworld-by-default", false);
		this.getConfig().addDefault("qt-from-anywhere", false);
		this.getConfig().addDefault("enable-economy", false);
		this.getConfig().addDefault("withdraw-from-player-not-bank", true);
		this.getConfig().addDefault("free-by-default", false);
		this.getConfig().addDefault("price-multiplier", 0.8);
		this.getConfig().addDefault("multiworld-multiplier", 1.2);
		this.getConfig().addDefault("price-multiplier", 500);
		this.getConfig().addDefault("free-from-qts", false);
		this.getConfig().options().copyDefaults(true);
		if(getConfig().get("radius-when-only-primary-set") != null) { 
			this.getConfig().set("radius", getConfig().getDouble("radius-when-only-primary-set"));
			this.getConfig().set("radius-when-only-primary-set", null);
		}
		if(getConfig().get("locations-must-be-discovered") != null) { 
			this.getConfig().set("require-discovery-by-default", getConfig().getBoolean("locations-must-be-discovered"));
			this.getConfig().set("locations-must-be-discovered", null);
		}
		if(getConfig().get("players-always-need-permissions") != null) { 
			this.getConfig().set("require-permissions-by-default", getConfig().getBoolean("players-always-need-permissions"));
			this.getConfig().set("players-always-need-permissions", null);
		}
		this.saveConfig();
		
		PluginManager pm = getServer().getPluginManager();
		/* Check if Vault is required */
		if(getConfig().getBoolean("enable-economy") == true) {
			/* Get Vault */
			Vault = pm.getPlugin("Vault");
			if(Vault == null) {
				severe("Cannot find Vault!");
				severe("Disabling economy!");
				economyEnabled = false;
			} else {
				info("Vault has been detected");
				EcoSetup eco = new EcoSetup();
			    if(!eco.setupEconomy()) {
			    	warning("Could not set up economy!");
			    	economyEnabled = false;
			    } else {
			    	info("Using " + EcoSetup.economy.getName() + " for economy.");
			    	economyEnabled = true;
			    }
			}
		} else {
			info("Economy is disabled.");
		}
		
		this.getServer().getPluginManager().registerEvents(this, this);
		
		info("v" + this.getDescription().getVersion() + " is enabled.");
	}
	
	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerMove(PlayerMoveEvent event) {
		Player p = event.getPlayer();
		String qt = checkPlayerQT(p);
		if(qt != null && playerHasPermission(p, qt)) {
			boolean discovered = false;
			@SuppressWarnings("unchecked")
			List<Object> dList = (List<Object>) getLocations().getList("locations." + getLocation(qt) + ".discovered-by");
			if(dList != null) {
				ListIterator<Object> li = dList.listIterator();
				while(li.hasNext()) {
					String v = li.next().toString();
					if(v.equalsIgnoreCase(p.getName())) {
						discovered = true;
						break;
					}
				}
			}
			if(discovered == false) {
				if(dList != null) {
					dList.add(p.getName());
				} else {
					List<Object> newDList = new ArrayList<Object>();
					newDList.add(p.getName());
					getLocations().set("locations." + getLocation(qt) + ".discovered-by", newDList);
				}
				this.saveLocations();
				p.sendMessage(ChatColor.BLUE + "You have discovered " + ChatColor.AQUA + qt + ChatColor.BLUE + "!");
				p.sendMessage("Type " + ChatColor.GOLD + "/qt" + ChatColor.WHITE + " for QuickTravel.");
			}
		}
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if(cmd.getName().equalsIgnoreCase("qt")) {
			/* Command Handling */
			if(args.length == 0) {
				/* "/qt" passed
				* Make sure is not being run from console and display list of available QTs */
				if(!(sender instanceof Player) && !(args[0].equalsIgnoreCase("list"))) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				} else {
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("create")) {
				/* "/qt create" passed 
				 * Make sure is not being run from console */
				if(!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				if(sender.hasPermission("qt.admin.create")) {
					QTCreate(sender, args);
					return true;
				} else {
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("rename") || args[0].equalsIgnoreCase("name")) {
				/* "/qt rename" passed */
				if(sender.hasPermission("qt.admin.rename")) {
					QTRename(sender, args);
					return true;
				} else {
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("type") || args[0].equalsIgnoreCase("t")) {
				/* "/qt type" passed */
				if(sender.hasPermission("qt.admin.type")) {
					QTType(sender, args);
					return true;
				} else {
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("radius") || args[0].equalsIgnoreCase("r")) {
				/* "/qt radius" passed */
				if(sender.hasPermission("qt.admin.radius")) {
					QTRadius(sender, args);
					return true;
				} else {
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("cuboid")) {
				/* "/qt cuboid" passed */
				if(sender.hasPermission("qt.admin.cuboid")) {
					QTCuboid(sender, args);
					return true;
				} else {
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("move")) {
				/* "/qt update" passed 
				 * Make sure is not being run from console */
				if(!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				if(sender.hasPermission("qt.admin.move")) {
					QTMove(sender, args);
					return true;
				} else {
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("dest")) {
				/* "/qt dest" passed 
				 * Make sure is not being run from console */
				if(!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				if(sender.hasPermission("qt.admin.dest")) {
					QTDest(sender, args);
					return true;
				} else {
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("e")) {
				/* "/qt enable" passed */
				if(sender.hasPermission("qt.admin.enable")) {
					QTEnable(sender, args);
					return true;
				} else {
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("disable")) {
				/* "/qt disable" passed */
				if(sender.hasPermission("qt.admin.disable")) {
					QTDisable(sender, args);
					return true;
				} else {
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
				}
			} else if(args[0].equalsIgnoreCase("price") || args[0].equalsIgnoreCase("charge")) {
				/* "/qt price" passed */
	    		if(sender.hasPermission("qt.admin.price")) {
	    			QTPrice(sender, args);
	    			return true;
	    		} else {
	    			/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
	    		}
			} else if(args[0].equalsIgnoreCase("free") || args[0].equalsIgnoreCase("f")) {
				/* "/qt price" passed */
	    		if(sender.hasPermission("qt.admin.free")) {
	    			QTFree(sender, args);
	    			return true;
	    		} else {
	    			/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
	    		}
			} else if(args[0].equalsIgnoreCase("discovery") || args[0].equalsIgnoreCase("discover") || args[0].equalsIgnoreCase("disc") || args[0].equalsIgnoreCase("d")) {
				/* "/qt price" passed */
	    		if(sender.hasPermission("qt.admin.discovery")) {
	    			QTDiscovery(sender, args);
	    			return true;
	    		} else {
	    			/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
	    		}
			} else if(args[0].equalsIgnoreCase("perms") || args[0].equalsIgnoreCase("perm") || args[0].equalsIgnoreCase("p")) {
				/* "/qt price" passed */
	    		if(sender.hasPermission("qt.admin.perms")) {
	    			QTPerms(sender, args);
	    			return true;
	    		} else {
	    			/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
	    		}
			} else if(args[0].equalsIgnoreCase("multiworld") || args[0].equalsIgnoreCase("multi") || args[0].equalsIgnoreCase("m")) {
				/* "/qt price" passed */
	    		if(sender.hasPermission("qt.admin.multiworld")) {
	    			QTMultiworld(sender, args);
	    			return true;
	    		} else {
	    			/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
	    		}
			} else if(args[0].equalsIgnoreCase("list")) { 
				if(sender.hasPermission("qt.admin.list")) {
					/* "/qt list" passed
					 * Get arguments and deal with appropriately */
					if(args.length == 1) {
						/* No arguments, display list */
						QTList(sender, 1, true);
						return true;
					} else if(args.length == 2) {
						/* 1 argument, should be page number
						 * Display page 1 otherwise */
						try {
							int i = Integer.parseInt(args[1]);
							if(i <= 0) {
								i = 1;
							}
							QTList(sender, i, true);
							return true;
						} catch(NumberFormatException e) {
							sender.sendMessage("'" + args[1] + "' is not a number, displaying page 1.");
							QTList(sender, 1, true);
							return true;
						}
					} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage("Shows a list of all QT points and related info.");
						sender.sendMessage("/qt list <page (optional)>");
						return true;
					}
				} else {
					/* Not authorised */
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + args[0] + ChatColor.WHITE + "!");
					QTList(sender, 1, false);
					return true;
				}
			} else if(args.length == 1) {
				/* "/qt" passed with 1 argument 
				 * Make sure is not being run from console */
				if(!(sender instanceof Player) && !(args[0].equalsIgnoreCase("list"))) {
					sender.sendMessage(ChatColor.RED + "You must be a player!");
					return true;
				}
				try {
					/* Argument is a number, throw list at player */
					int i = Integer.parseInt(args[0]);
					if(i <= 0) {
						i = 1;
					}
					QTList(sender, i, false);
					return true;
				} catch(NumberFormatException e) {
					/* Argument presumed to be a request to QT
					 * Check QT is valid */
					if(runChecks(sender, args[0], true) == true) {
						/* QT is valid, gather info and
						 * prepare to send QT */
						Player p = (Player)sender;
						String qt = checkPlayerQT(sender);
						double tax = getConfig().getDouble("multiworld-tax");
						if(qt != null) {
							/* Player is at a QT location */
							if(qt.equalsIgnoreCase(args[0])) {
								/* Player is already at the requested QT, do not send */
								sender.sendMessage(ChatColor.BLUE + "You are already at " + ChatColor.AQUA + qt + ChatColor.BLUE + "!");
								return true;
							}
							/* Check economy */
							double c = 0;
							if(economyEnabled == true) {
								/* Economy is enabled */
								if((isFree(getLocation(args[0])) && getLocations().get("locations." + getLocation(args[0]) + ".free") != null) || (isFree(getLocation(qt)) && getLocations().get("locations." + getLocation(qt) + ".free") != null)) {
									/* One or both of these QTs are free */
									if(getLocations().getString("locations." + getLocation(qt) + ".world").equalsIgnoreCase(getLocations().getString("locations." + getLocation(args[0]) + ".world"))) {
										/* Send QT */
										QT(sender, args[0], 0);
		                                return true;
									} else {
										/* Charge multiworld tax
										 * Check player has enough money */
										c = c + tax;
										if(EcoSetup.economy.has(p.getName(), c)) {
											/* Withdraw money from player */
			                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
			                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
			                            	} else {
			                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
			                            	}
			                            	/* Send QT */
			                            	QT(sender, args[0], c);
			                                return true;
			                            } else {
			                            	/* Player does not have enough money */
			                                sender.sendMessage("You do not have enough money to go there.");
			                                return true;
										}
									}
								} else {
									if(getLocations().get("locations." + getLocation(args[0]) + ".charge-from." + getLocation(qt)) != null) {
										/* Price has been manually set for QT */
										c = getLocations().getDouble("locations." + getLocation(args[0]) + ".charge-from." + getLocation(qt));
										if(!getLocations().getString("locations." + getLocation(qt) + ".world").equalsIgnoreCase(getLocations().getString("locations." + getLocation(args[0]) + ".world"))) {
											c = c + tax;
										}
										if(c > 0) {
											/* Check player has enough money */
											if(EcoSetup.economy.has(p.getName(), c)) {
												/* Withdraw money from player */
				                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
				                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
				                            	} else {
				                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
				                            	}
				                            	/* Send QT */
				                            	QT(sender, args[0], c);
				                                return true;
				                            } else {
				                            	/* Player does not have enough money */
				                                sender.sendMessage("You do not have enough money to go there.");
				                                return true;
											}
										} else {
											/* Send QT */
											QT(sender, args[0], c);
			                                return true;
										}
									} else {
										/* No custom price set, check whether it should be free
										 * or if we should set the price */
										if((getConfig().getBoolean("qt-from-anywhere") == true && getConfig().getBoolean("free-from-qts") == false) || (getConfig().getBoolean("qt-from-anywhere") == false && getConfig().getBoolean("free-by-default") == false)) {
											/* QT should not be free, calculate price */
											c = calculatePrice(getLocation(qt), getLocation(args[0]));
											if(!getLocations().getString("locations." + getLocation(qt) + ".world").equalsIgnoreCase(getLocations().getString("locations." + getLocation(args[0]) + ".world"))) {
												c = c + tax;
											}
											/* Check player has enough money */
											if(EcoSetup.economy.has(p.getName(), c)) {
												/* Withdraw money from player */
				                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
				                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
				                            	} else {
				                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
				                            	}
				                            	/* Send QT */
				                            	QT(sender, args[0], c);
				                                return true;
				                            } else {
				                            	/* Player does not have enough money */
				                                sender.sendMessage("You do not have enough money to go there.");
				                                return true;
											}
										} else {
											/* QT should be free QT */
											if(getLocations().getString("locations." + getLocation(qt) + ".world").equalsIgnoreCase(getLocations().getString("locations." + getLocation(args[0]) + ".world"))) {
												/* Send QT */
												QT(sender, args[0], 0);
				                                return true;
											} else {
												/* Charge multiworld tax
												 * Check player has enough money */
												c = c + tax;
												if(EcoSetup.economy.has(p.getName(), c)) {
													/* Withdraw money from player */
					                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
					                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
					                            	} else {
					                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
					                            	}
					                            	/* Send QT */
					                            	QT(sender, args[0], c);
					                                return true;
					                            } else {
					                            	/* Player does not have enough money */
					                                sender.sendMessage("You do not have enough money to go there.");
					                                return true;
												}
											}																	
										}
									}
								}
							} else {
								/* Economy is disabled, do not charge and send QT */
								QT(sender, args[0], 0);
								return true;	
							}
						} else if(getConfig().getBoolean("qt-from-anywhere") == true) {
							/* Player is not at a QT location,
							 * however QTs are enabled from anywhere */
							if(economyEnabled == true) {
								if(getConfig().getBoolean("free-by-default") == false) {
									/* Economy is enabled
									 * QTs are not free by default
									 * Check whether destination is free
									 * and calculate if not */
									if(isFree(getLocation(args[0])) && getLocations().get("locations." + getLocation(args[0]) + ".free") != null) {
										/* QT is free, send */
										if(p.getWorld().getName().equalsIgnoreCase(getLocations().getString("locations." + getLocation(args[0]) + ".world"))) {
											/* Send QT */
											QT(sender, args[0], 0);
			                                return true;
										} else {
											/* Charge multiworld tax
											 * Check player has enough money */
											double c = tax;
											if(EcoSetup.economy.has(p.getName(), c)) {
												/* Withdraw money from player */
				                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
				                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
				                            	} else {
				                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
				                            	}
				                            	/* Send QT */
				                            	QT(sender, args[0], c);
				                                return true;
				                            } else {
				                            	/* Player does not have enough money */
				                                sender.sendMessage("You do not have enough money to go there.");
				                                return true;
											}
										}
									} else {
										double c = calculatePrice(sender, getLocation(args[0]));
										if(!p.getWorld().getName().equalsIgnoreCase(getLocations().getString("locations." + getLocation(args[0]) + ".world"))) {
											c = c + tax;
										}
										/* Check player has enough money */
										if(EcoSetup.economy.has(p.getName(), c)) {
											/* Withdraw money from player */
			                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
			                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
			                            	} else {
			                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
			                            	}
			                            	/* Send QT */
			                            	QT(sender, args[0], c);
			                                return true;
			                            } else {
			                            	/* Player does not have enough money */
			                                sender.sendMessage("You do not have enough money to go there.");
			                                return true;
										}
									}
								} else {
									/* No price required */
									double c = 0;
									if(!p.getWorld().getName().equalsIgnoreCase(getLocations().getString("locations." + getLocation(args[0]) + ".world"))) {
										c = c + tax;
									}
									/* Check player has enough money */
									if(EcoSetup.economy.has(p.getName(), c)) {
										/* Withdraw money from player */
		                            	if(EcoSetup.economy.hasBankSupport() && getConfig().getBoolean("withdraw-from-player-not-bank") == false) {
		                            		EcoSetup.economy.bankWithdraw(p.getName(), c);
		                            	} else {
		                            		EcoSetup.economy.withdrawPlayer(p.getName(), c);
		                            	}
		                            	/* Send QT */
		                            	QT(sender, args[0], c);
		                                return true;
		                            } else {
		                            	/* Player does not have enough money */
		                                sender.sendMessage("You do not have enough money to go there.");
		                                return true;
									}
								}
							} else {
								/* Economy disabled, send QT */
								QT(sender, args[0], 0);
								return true;
							}
						} else {
							/* Player is not at a valid location to QT */
							sender.sendMessage(ChatColor.BLUE + "You are not at a QuickTravel point.");
							return true;	
						}
					} else {
						/* It has been determined that this is an invalid QT.
						 * Display list */
						QTList(sender, 1, false);
						return true;
					}
				}
			} else {
				/* Invalid QT command has been passed. */
				return false;
			}
		}
		return false;
	}
	
	public void QT(CommandSender sender, String rQT, double c) {
		if(c > 0) {
			sender.sendMessage(ChatColor.BLUE + "QuickTravelling to " + ChatColor.AQUA + getLocationName(rQT) + ChatColor.BLUE + " for " + ChatColor.GOLD + EcoSetup.economy.format(c) + ChatColor.BLUE + "...");	
		} else {
			sender.sendMessage(ChatColor.BLUE + "QuickTravelling to " + ChatColor.AQUA + getLocationName(rQT) + ChatColor.BLUE + "...");
		}
		
		Player p = (Player)sender;
		Location loc = p.getLocation();
		Server s = getServer();
        World w = s.getWorld(getLocations().getString("locations." + getLocation(rQT) + ".world"));
        if(getLocations().get("locations." + getLocation(rQT) + ".coords.dest") != null) {
        	double x = getLocations().getDouble("locations." + getLocation(rQT) + ".coords.dest.x");
        	double y = getLocations().getDouble("locations." + getLocation(rQT) + ".coords.dest.y");
        	double z = getLocations().getDouble("locations." + getLocation(rQT) + ".coords.dest.z");
        	float pitch = getLocations().getInt("locations." + getLocation(rQT) + ".coords.dest.pitch");
        	float yaw = getLocations().getInt("locations." + getLocation(rQT) + ".coords.dest.yaw");
        	Location dest = new Location(w, x, y, z, yaw, pitch);
        	Location d = checkSafe(dest, p);
        	p.teleport(d);
        	QTEffect(loc, d);
        } else {
        	double x = getLocations().getInt("locations." + getLocation(rQT) + ".coords.primary.x");
        	double y = getLocations().getInt("locations." + getLocation(rQT) + ".coords.primary.y");
        	double z = getLocations().getInt("locations." + getLocation(rQT) + ".coords.primary.z");
        	Location dest = new Location(w, x, y, z);
        	Location d = checkSafe(dest, p);
        	p.teleport(d);
        	QTEffect(loc, d);
		}
	}
	
	public Location checkSafe(Location d, Player p) {
		World w = d.getWorld();
		double x = d.getX();
		double y = d.getY();
		double z = d.getZ();
		Location d2 = new Location(w, x+1, y, z);
    	Location d3 = new Location(w, x+1, y, z+1);
    	Location d4 = new Location(w, x, y, z+1);
    	Location d5 = new Location(w, x-1, y, z+1);
    	Location d6 = new Location(w, x-1, y, z);
    	Location d7 = new Location(w, x-1, y, z-1);
    	Location d8 = new Location(w, x, y, z-1);
    	Location d9 = new Location(w, x+1, y, z-1);
    	Location db = new Location(w, x, y+1, z);
    	Location db2 = new Location(w, x+1, y+1, z);
    	Location db3 = new Location(w, x+1, y+1, z+1);
    	Location db4 = new Location(w, x, y+1, z+1);
    	Location db5 = new Location(w, x-1, y+1, z+1);
    	Location db6 = new Location(w, x-1, y+1, z);
    	Location db7 = new Location(w, x-1, y+1, z-1);
    	Location db8 = new Location(w, x, y+1, z-1);
    	Location db9 = new Location(w, x+1, y+1, z-1);
    	Location dbb = new Location(w, x, y+2, z);
    	Location dbb2 = new Location(w, x+1, y+2, z);
    	Location dbb3 = new Location(w, x+1, y+2, z+1);
    	Location dbb4 = new Location(w, x, y+2, z+1);
    	Location dbb5 = new Location(w, x-1, y+2, z+1);
    	Location dbb6 = new Location(w, x-1, y+2, z);
    	Location dbb7 = new Location(w, x-1, y+2, z-1);
    	Location dbb8 = new Location(w, x, y+2, z-1);
    	Location dbb9 = new Location(w, x+1, y+2, z-1);
    	Location dc = new Location(w, x, y-1, z);
    	Location dc2 = new Location(w, x+1, y-1, z);
    	Location dc3 = new Location(w, x+1, y-1, z+1);
    	Location dc4 = new Location(w, x, y-1, z+1);
    	Location dc5 = new Location(w, x-1, y-1, z+1);
    	Location dc6 = new Location(w, x-1, y-1, z);
    	Location dc7 = new Location(w, x-1, y-1, z-1);
    	Location dc8 = new Location(w, x, y-1, z-1);
    	Location dc9 = new Location(w, x+1, y-1, z-1);
    	Location dcc = new Location(w, x, y-2, z);
    	Location dcc2 = new Location(w, x+1, y-2, z);
    	Location dcc3 = new Location(w, x+1, y-2, z+1);
    	Location dcc4 = new Location(w, x, y-2, z+1);
    	Location dcc5 = new Location(w, x-1, y-2, z+1);
    	Location dcc6 = new Location(w, x-1, y-2, z);
    	Location dcc7 = new Location(w, x-1, y-2, z-1);
    	Location dcc8 = new Location(w, x, y-2, z-1);
    	Location dcc9 = new Location(w, x+1, y-2, z-1);
    	boolean fix = false;
		if(!d.getBlock().isEmpty()) {
			d.getBlock().setType(Material.AIR);
			fix = true;
    	}
		if (d2.getBlock().getType() == Material.LAVA || d2.getBlock().getType() == Material.STATIONARY_LAVA) {
			d2.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d3.getBlock().getType() == Material.LAVA || d3.getBlock().getType() == Material.STATIONARY_LAVA) {
			d3.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d4.getBlock().getType() == Material.LAVA || d4.getBlock().getType() == Material.STATIONARY_LAVA) {
			d4.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d5.getBlock().getType() == Material.LAVA || d5.getBlock().getType() == Material.STATIONARY_LAVA) {
			d5.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d6.getBlock().getType() == Material.LAVA || d6.getBlock().getType() == Material.STATIONARY_LAVA) {
			d6.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d7.getBlock().getType() == Material.LAVA || d7.getBlock().getType() == Material.STATIONARY_LAVA) {
			d7.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d8.getBlock().getType() == Material.LAVA || d8.getBlock().getType() == Material.STATIONARY_LAVA) {
			d8.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (d9.getBlock().getType() == Material.LAVA || d9.getBlock().getType() == Material.STATIONARY_LAVA) {
			d9.getBlock().setType(Material.GLASS);
			fix = true;
		}
		
		if(!db.getBlock().isEmpty()) {
			db.getBlock().setType(Material.AIR);
			fix = true;
    	}
		if (db2.getBlock().getType() == Material.LAVA || db2.getBlock().getType() == Material.STATIONARY_LAVA) {
			db2.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db3.getBlock().getType() == Material.LAVA || db3.getBlock().getType() == Material.STATIONARY_LAVA) {
			db3.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db4.getBlock().getType() == Material.LAVA || db4.getBlock().getType() == Material.STATIONARY_LAVA) {
			db4.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db5.getBlock().getType() == Material.LAVA || db5.getBlock().getType() == Material.STATIONARY_LAVA) {
			db5.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db6.getBlock().getType() == Material.LAVA || db6.getBlock().getType() == Material.STATIONARY_LAVA) {
			db6.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db7.getBlock().getType() == Material.LAVA || db7.getBlock().getType() == Material.STATIONARY_LAVA) {
			db7.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db8.getBlock().getType() == Material.LAVA || db8.getBlock().getType() == Material.STATIONARY_LAVA) {
			db8.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (db9.getBlock().getType() == Material.LAVA || db9.getBlock().getType() == Material.STATIONARY_LAVA) {
			db9.getBlock().setType(Material.GLASS);
			fix = true;
		}
		
		if(dbb.getBlock().getType() == Material.LAVA || dbb.getBlock().getType() == Material.STATIONARY_LAVA) {
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
    	}
		if (dbb2.getBlock().getType() == Material.LAVA || dbb2.getBlock().getType() == Material.STATIONARY_LAVA) {
			dbb2.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb3.getBlock().getType() == Material.LAVA || dbb3.getBlock().getType() == Material.STATIONARY_LAVA) {
			dbb3.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb4.getBlock().getType() == Material.LAVA || dbb4.getBlock().getType() == Material.STATIONARY_LAVA) {
			dbb4.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb5.getBlock().getType() == Material.LAVA || dbb5.getBlock().getType() == Material.STATIONARY_LAVA) {
			dbb5.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb6.getBlock().getType() == Material.LAVA || dbb6.getBlock().getType() == Material.STATIONARY_LAVA) {
			dbb6.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb7.getBlock().getType() == Material.LAVA || dbb7.getBlock().getType() == Material.STATIONARY_LAVA) {
			dbb7.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb8.getBlock().getType() == Material.LAVA || dbb8.getBlock().getType() == Material.STATIONARY_LAVA) {
			dbb8.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		if (dbb9.getBlock().getType() == Material.LAVA || dbb9.getBlock().getType() == Material.STATIONARY_LAVA) {
			dbb9.getBlock().setType(Material.GLASS);
			dbb.getBlock().setType(Material.GLASS);
			fix = true;
		}
		
		if(dc.getBlock().getType() == Material.LAVA || dc.getBlock().getType() == Material.STATIONARY_LAVA || dc.getBlock().isEmpty()) {
			dc.getBlock().setType(Material.GLASS);
			fix = true;
			if (dc2.getBlock().getType() == Material.LAVA || dc2.getBlock().getType() == Material.STATIONARY_LAVA) {
				dc2.getBlock().setType(Material.GLASS);
			}
			if (dc3.getBlock().getType() == Material.LAVA || dc3.getBlock().getType() == Material.STATIONARY_LAVA) {
				dc3.getBlock().setType(Material.GLASS);
			}
			if (dc4.getBlock().getType() == Material.LAVA || dc4.getBlock().getType() == Material.STATIONARY_LAVA) {
				dc4.getBlock().setType(Material.GLASS);
			}
			if (dc5.getBlock().getType() == Material.LAVA || dc5.getBlock().getType() == Material.STATIONARY_LAVA) {
				dc5.getBlock().setType(Material.GLASS);
			}
			if (dc6.getBlock().getType() == Material.LAVA || dc6.getBlock().getType() == Material.STATIONARY_LAVA) {
				dc6.getBlock().setType(Material.GLASS);
			}
			if (dc7.getBlock().getType() == Material.LAVA || dc7.getBlock().getType() == Material.STATIONARY_LAVA) {
				dc7.getBlock().setType(Material.GLASS);
			}
			if (dc8.getBlock().getType() == Material.LAVA || dc8.getBlock().getType() == Material.STATIONARY_LAVA) {
				dc8.getBlock().setType(Material.GLASS);
			}
			if (dc9.getBlock().getType() == Material.LAVA || dc9.getBlock().getType() == Material.STATIONARY_LAVA) {
				dc9.getBlock().setType(Material.GLASS);
			}
    	} else {
    		if (d2.getBlock().getType() == Material.GLASS && fix == true) {
    			dc2.getBlock().setType(Material.GLASS);
    		}
    		if (d3.getBlock().getType() == Material.GLASS && fix == true) {
    			dc3.getBlock().setType(Material.GLASS);
    		}
    		if (d4.getBlock().getType() == Material.GLASS && fix == true) {
    			dc4.getBlock().setType(Material.GLASS);
    		}
    		if (d5.getBlock().getType() == Material.GLASS && fix == true) {
    			dc5.getBlock().setType(Material.GLASS);
    		}
    		if (d6.getBlock().getType() == Material.GLASS && fix == true) {
    			dc6.getBlock().setType(Material.GLASS);
    		}
    		if (d7.getBlock().getType() == Material.GLASS && fix == true) {
    			dc7.getBlock().setType(Material.GLASS);
    		}
    		if (d8.getBlock().getType() == Material.GLASS && fix == true) {
    			dc8.getBlock().setType(Material.GLASS);
    		}
    		if (d9.getBlock().getType() == Material.GLASS && fix == true) {
    			dc9.getBlock().setType(Material.GLASS);
    		}
    	}
		
		if ((dcc.getBlock().getType() == Material.LAVA || dcc.getBlock().getType() == Material.STATIONARY_LAVA) && (dc.getBlock().getType() == Material.GLASS && fix == true)) {
			dcc.getBlock().setType(Material.GLASS);
		}
		if ((dcc2.getBlock().getType() == Material.LAVA || dcc2.getBlock().getType() == Material.STATIONARY_LAVA) && (dc2.getBlock().getType() == Material.GLASS && fix == true)) {
			dcc2.getBlock().setType(Material.GLASS);
		}
		if ((dcc3.getBlock().getType() == Material.LAVA || dcc3.getBlock().getType() == Material.STATIONARY_LAVA) && (dc3.getBlock().getType() == Material.GLASS && fix == true)) {
			dcc3.getBlock().setType(Material.GLASS);
		}
		if ((dcc4.getBlock().getType() == Material.LAVA || dcc4.getBlock().getType() == Material.STATIONARY_LAVA) && (dc4.getBlock().getType() == Material.GLASS && fix == true)) {
			dcc4.getBlock().setType(Material.GLASS);
		}
		if ((dcc5.getBlock().getType() == Material.LAVA || dcc5.getBlock().getType() == Material.STATIONARY_LAVA) && (dc5.getBlock().getType() == Material.GLASS && fix == true)) {
			dcc5.getBlock().setType(Material.GLASS);
		}
		if ((dcc6.getBlock().getType() == Material.LAVA || dcc6.getBlock().getType() == Material.STATIONARY_LAVA) && (dc6.getBlock().getType() == Material.GLASS && fix == true)) {
			dcc6.getBlock().setType(Material.GLASS);
		}
		if ((dcc7.getBlock().getType() == Material.LAVA || dcc7.getBlock().getType() == Material.STATIONARY_LAVA) && (dc7.getBlock().getType() == Material.GLASS && fix == true)) {
			dcc7.getBlock().setType(Material.GLASS);
		}
		if ((dcc8.getBlock().getType() == Material.LAVA || dcc8.getBlock().getType() == Material.STATIONARY_LAVA) && (dc8.getBlock().getType() == Material.GLASS && fix == true)) {
			dcc8.getBlock().setType(Material.GLASS);
		}
		if ((dcc9.getBlock().getType() == Material.LAVA || dcc9.getBlock().getType() == Material.STATIONARY_LAVA) && (dc9.getBlock().getType() == Material.GLASS && fix == true)) {
			dcc9.getBlock().setType(Material.GLASS);
		}
		
		return d;
	}
	
	public void QTEffect(Location p, Location d) {
		World w = d.getWorld();
		double x = d.getX();
		double y = d.getY();
		double z = d.getZ();
		Location d2 = new Location(w, x+1, y, z);
    	Location d3 = new Location(w, x+1, y, z+1);
    	Location d4 = new Location(w, x, y, z+1);
    	Location d5 = new Location(w, x-1, y, z+1);
    	Location d6 = new Location(w, x-1, y, z);
    	Location d7 = new Location(w, x-1, y, z-1);
    	Location d8 = new Location(w, x, y, z-1);
    	Location d9 = new Location(w, x+1, y, z-1);
    	World pw = p.getWorld();
    	x = p.getX();
		y = p.getY();
		z = p.getZ();
		Location p2 = new Location(pw, x+1, y, z);
    	Location p3 = new Location(pw, x+1, y, z+1);
    	Location p4 = new Location(pw, x, y, z+1);
    	Location p5 = new Location(pw, x-1, y, z+1);
    	Location p6 = new Location(pw, x-1, y, z);
    	Location p7 = new Location(pw, x-1, y, z-1);
    	Location p8 = new Location(pw, x, y, z-1);
    	Location p9 = new Location(pw, x+1, y, z-1);
    	p.getWorld().playEffect(p, Effect.ENDER_SIGNAL, null);
    	w.playEffect(p2, Effect.SMOKE, 4);
    	w.playEffect(p3, Effect.SMOKE, 4);
    	w.playEffect(p4, Effect.SMOKE, 4);
    	w.playEffect(p5, Effect.SMOKE, 4);
    	w.playEffect(p6, Effect.SMOKE, 4);
    	w.playEffect(p7, Effect.SMOKE, 4);
    	w.playEffect(p8, Effect.SMOKE, 4);
    	w.playEffect(p9, Effect.SMOKE, 4);
        p.getWorld().playEffect(p, Effect.GHAST_SHOOT, null);
        p.getWorld().playEffect(p, Effect.EXTINGUISH, null);
        w.playEffect(d, Effect.ENDER_SIGNAL, null);
    	w.playEffect(d2, Effect.SMOKE, 4);
    	w.playEffect(d3, Effect.SMOKE, 4);
    	w.playEffect(d4, Effect.SMOKE, 4);
    	w.playEffect(d5, Effect.SMOKE, 4);
    	w.playEffect(d6, Effect.SMOKE, 4);
    	w.playEffect(d7, Effect.SMOKE, 4);
    	w.playEffect(d8, Effect.SMOKE, 4);
    	w.playEffect(d9, Effect.SMOKE, 4);
    	w.playEffect(d, Effect.GHAST_SHOOT, null);
        w.playEffect(d, Effect.EXTINGUISH, null);
	}
	
	@SuppressWarnings({ "unchecked", "unused" })
	public void QTCreate(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		if(args.length >= 2) {
			try {
				/* Player attempting to name a QT as a number */
				int checkInt = Integer.parseInt(args[1]);
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " is not a valid name!");
				sender.sendMessage("Names must contain letters.");
				return;
			} catch(NumberFormatException e) {
				if(!(!args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("rename") && !args[1].equalsIgnoreCase("name") && !args[1].equalsIgnoreCase("type") && !args[1].equalsIgnoreCase("t") && !args[1].equalsIgnoreCase("radius") && !args[1].equalsIgnoreCase("r") && !args[1].equalsIgnoreCase("cuboid") && !args[1].equalsIgnoreCase("c") && !args[1].equalsIgnoreCase("update") && !args[1].equalsIgnoreCase("u") && !args[1].equalsIgnoreCase("dest") && !args[1].equalsIgnoreCase("enable") && !args[1].equalsIgnoreCase("e") && !args[1].equalsIgnoreCase("disable") && !args[1].equalsIgnoreCase("price") && !args[1].equalsIgnoreCase("charge") && !args[1].equalsIgnoreCase("free") && !args[1].equalsIgnoreCase("f") && !args[1].equalsIgnoreCase("discovery") && !args[1].equalsIgnoreCase("discover") && !args[1].equalsIgnoreCase("disc") && !args[1].equalsIgnoreCase("d") && !args[1].equalsIgnoreCase("perms") && !args[1].equalsIgnoreCase("perm") && !args[1].equalsIgnoreCase("p") && !args[1].equalsIgnoreCase("multiworld") && !args[1].equalsIgnoreCase("multi") && !args[1].equalsIgnoreCase("m"))) {
					/* Player attempting to name a QT after a command */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " is not a valid name!");
					sender.sendMessage("Names must not match /qt commands.");
					return;
				}
				if(containsLetter(args[1]) == false) {
					/* Player attempting to name a QT without letters */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " is not a valid name!");
					sender.sendMessage("Names must contain letters.");
					return;
				}
				if(checkLocations(args[1]) == true) {
					/* QT with name chosen already exists */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not create: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " already exists!");
					return;
				}
				/* Checks passed, create QT */
				Player p = (Player)sender;
				Location coord = p.getLocation();
				this.getLocations().set("locations." + args[1] + ".world", p.getWorld().getName());
				this.getLocations().set("locations." + args[1] + ".coords.primary.x", coord.getX());
				this.getLocations().set("locations." + args[1] + ".coords.primary.y", coord.getY());
				this.getLocations().set("locations." + args[1] + ".coords.primary.z", coord.getZ());
				this.getLocations().set("locations." + args[1] + ".coords.dest.x", coord.getX());
				this.getLocations().set("locations." + args[1] + ".coords.dest.y", coord.getY());
				this.getLocations().set("locations." + args[1] + ".coords.dest.z", coord.getZ());
				this.getLocations().set("locations." + args[1] + ".coords.dest.pitch", coord.getPitch());
				this.getLocations().set("locations." + args[1] + ".coords.dest.yaw", coord.getYaw());
				this.getLocations().set("locations." + args[1] + ".name", args[1]);
				this.getLocations().set("locations." + args[1] + ".type", "radius");
				
				List<Object> locList = (List<Object>) getLocations().getList("list");
				if(locList != null) {
					locList.add(args[1]);
				} else {
					List<Object> lList = new ArrayList<Object>();
					lList.add(args[1]);
					this.getLocations().set("list", lList);
				}
				
				sender.sendMessage("QT " + ChatColor.AQUA + args[1] + ChatColor.WHITE + " created.");

				/* QT created, check optional parameters and set options. */
				if(args.length > 2) {
					for(int i = 2; i < args.length; i++) {
						if(args[i].equalsIgnoreCase("-r") || args[i].equalsIgnoreCase("-radius")) {
							/* Set radius size */
							if(args.length > i+1) {
								try {
									double radius = Double.parseDouble(args[i+1]);
									this.getLocations().set("locations." + args[1] + ".radius", radius);
									sender.sendMessage("Radius: " + ChatColor.GOLD + radius);
									i++;
								} catch(NumberFormatException e2) {
									/* Invalid radius */
									sender.sendMessage(ChatColor.GOLD + args[i+1] + ChatColor.WHITE + " is not a valid radius, ignoring.");
								}
							} else {
								/* No radius given */
								sender.sendMessage("No radius provided, ignoring " + ChatColor.GOLD + args[i] + ChatColor.WHITE + ".");
							}
						} else if (args[i].equalsIgnoreCase("-e") || args[i].equalsIgnoreCase("-enable") || args[i].equalsIgnoreCase("-enabled")) {
							/* Set enabled status */
							if(args.length > i+1) {
								if(args[i+1].equalsIgnoreCase("true") || args[i+1].equalsIgnoreCase("false")) {
									this.getLocations().set("locations." + args[1] + ".enabled", Boolean.valueOf(args[i+1]));
									sender.sendMessage("Enabled: " + ChatColor.GOLD + args[i+1]);
									i++;
								} else {
									/* Player has not given true/false, figure out what they want. */
									boolean enabled = false;
									if(getConfig().getBoolean("enabled-by-default") == false) {
										enabled = true;
									}
									this.getLocations().set("locations." + args[1] + ".enabled", enabled);
									sender.sendMessage("Enabled: " + ChatColor.GOLD + enabled);
								}
							} else {
								/* Player has not given true/false, figure out what they want. */
								boolean enabled = false;
								if(getConfig().getBoolean("enabled-by-default") == false) {
									enabled = true;
								}
								this.getLocations().set("locations." + args[1] + ".enabled", enabled);
								sender.sendMessage("Enabled: " + ChatColor.GOLD + enabled);
							}
						} else if (args[i].equalsIgnoreCase("-f") || args[i].equalsIgnoreCase("-free")) {
							/* Set free status */
							if(args.length > i+1) {
								if(args[i+1].equalsIgnoreCase("true") || args[i+1].equalsIgnoreCase("false")) {
									this.getLocations().set("locations." + args[1] + ".free", Boolean.valueOf(args[i+1]));
									sender.sendMessage("Free: " + ChatColor.GOLD + args[i+1]);
									i++;
								} else {
									/* Player has not given true/false, figure out what they want. */
									boolean free = true;
									if(getConfig().getBoolean("qt-from-anywhere") == true && getConfig().getBoolean("free-from-qts") == true) {
										free = false;
									} else if(getConfig().getBoolean("free-by-default") == true) {
										free = false;
									}
									this.getLocations().set("locations." + args[1] + ".free", free);
									sender.sendMessage("Free: " + ChatColor.GOLD + free);
								}
							} else {
								/* Player has not given true/false, figure out what they want. */
								boolean enabled = false;
								if(getConfig().getBoolean("enabled-by-default") == false) {
									enabled = true;
								}
								this.getLocations().set("locations." + args[1] + ".enabled", enabled);
								sender.sendMessage("Enabled: " + ChatColor.GOLD + enabled);
							}
						} else if (args[i].equalsIgnoreCase("-d") || args[i].equalsIgnoreCase("-disc") || args[i].equalsIgnoreCase("-discover") || args[i].equalsIgnoreCase("-discovery")) {
							/* Set discovery status */
							if(args.length > i+1) {
								if(args[i+1].equalsIgnoreCase("true") || args[i+1].equalsIgnoreCase("false")) {
									this.getLocations().set("locations." + args[1] + ".require-discovery", Boolean.valueOf(args[i+1]));
									sender.sendMessage("Require discovery: " + ChatColor.GOLD + args[i+1]);
									i++;
								} else {
									/* Player has not given true/false, figure out what they want. */
									boolean discovery = false;
									if(getConfig().getBoolean("require-discovery-by-default") == false) {
										discovery = true;
									}
									this.getLocations().set("locations." + args[1] + ".require-discovery", discovery);
									sender.sendMessage("Require discovery: " + ChatColor.GOLD + discovery);
								}
							} else {
								/* Player has not given true/false, figure out what they want. */
								boolean discovery = false;
								if(getConfig().getBoolean("require-discovery-by-default") == false) {
									discovery = true;
								}
								this.getLocations().set("locations." + args[1] + ".require-discovery", discovery);
								sender.sendMessage("Require discovery: " + ChatColor.GOLD + discovery);
							}
						} else if (args[i].equalsIgnoreCase("-p") || args[i].equalsIgnoreCase("-perm") || args[i].equalsIgnoreCase("-perms")) {
							/* Set permissions status */
							if(args.length > i+1) {
								if(args[i+1].equalsIgnoreCase("true") || args[i+1].equalsIgnoreCase("false")) {
									this.getLocations().set("locations." + args[1] + ".require-permissions", Boolean.valueOf(args[i+1]));
									sender.sendMessage("Require permissions: " + ChatColor.GOLD + args[i+1]);
									i++;
								} else {
									/* Player has not given true/false, figure out what they want. */
									boolean permissions = true;
									if(getConfig().getBoolean("free-by-default") == true) {
										permissions = false;
									}
									this.getLocations().set("locations." + args[1] + ".require-permissions", permissions);
									sender.sendMessage("Require permissions: " + ChatColor.GOLD + permissions);
								}
							} else {
								/* Player has not given true/false, figure out what they want. */
								boolean permissions = true;
								if(getConfig().getBoolean("free-by-default") == true) {
									permissions = false;
								}
								this.getLocations().set("locations." + args[1] + ".require-permissions", permissions);
								sender.sendMessage("Require permissions: " + ChatColor.GOLD + permissions);
							}
						} else if (args[i].equalsIgnoreCase("-m") || args[i].equalsIgnoreCase("-multi") || args[i].equalsIgnoreCase("-multiworld")) {
							/* Set multiworld status */
							if(args.length > i+1) {
								if(args[i+1].equalsIgnoreCase("true") || args[i+1].equalsIgnoreCase("false")) {
									this.getLocations().set("locations." + args[1] + ".multiworld", Boolean.valueOf(args[i+1]));
									sender.sendMessage("Multiworld: " + ChatColor.GOLD + args[i+1]);
									i++;
								} else {
									/* Player has not given true/false, figure out what they want. */
									boolean multiworld = true;
									if(getConfig().getBoolean("multiworld-by-default") == true) {
										multiworld = false;
									}
									this.getLocations().set("locations." + args[1] + ".multiworld", multiworld);
									sender.sendMessage("Multiworld: " + ChatColor.GOLD + multiworld);
								}
							} else {
								/* Player has not given true/false, figure out what they want. */
								boolean multiworld = true;
								if(getConfig().getBoolean("multiworld-by-default") == true) {
									multiworld = false;
								}
								this.getLocations().set("locations." + args[1] + ".multiworld", multiworld);
								sender.sendMessage("Multiworld: " + ChatColor.GOLD + multiworld);
							}
						} else {
							/* Invalid parameter */
							sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
						}
					}
				}
				this.saveLocations();
				return;
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Creates a new QT at your current location.");
			sender.sendMessage("/qt create <name>");
			return;
		}
	}
	
	public void QTRename(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		if(args.length == 3) {
			try {
				/* Player attempting to name a QT as a number */
				@SuppressWarnings("unused")
				int i = Integer.parseInt(args[1]);
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " is not a valid name!");
				sender.sendMessage("Names must contain letters.");
				return;
			} catch(NumberFormatException e) {
				if(!(!args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("rename") && !args[1].equalsIgnoreCase("name") && !args[1].equalsIgnoreCase("type") && !args[1].equalsIgnoreCase("t") && !args[1].equalsIgnoreCase("radius") && !args[1].equalsIgnoreCase("r") && !args[1].equalsIgnoreCase("cuboid") && !args[1].equalsIgnoreCase("c") && !args[1].equalsIgnoreCase("update") && !args[1].equalsIgnoreCase("u") && !args[1].equalsIgnoreCase("dest") && !args[1].equalsIgnoreCase("enable") && !args[1].equalsIgnoreCase("e") && !args[1].equalsIgnoreCase("disable") && !args[1].equalsIgnoreCase("price") && !args[1].equalsIgnoreCase("charge") && !args[1].equalsIgnoreCase("free") && !args[1].equalsIgnoreCase("f") && !args[1].equalsIgnoreCase("discovery") && !args[1].equalsIgnoreCase("discover") && !args[1].equalsIgnoreCase("disc") && !args[1].equalsIgnoreCase("d") && !args[1].equalsIgnoreCase("perms") && !args[1].equalsIgnoreCase("perm") && !args[1].equalsIgnoreCase("p") && !args[1].equalsIgnoreCase("multiworld") && !args[1].equalsIgnoreCase("multi") && !args[1].equalsIgnoreCase("m"))) {
					/* Player attempting to name a QT after a command */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid name!");
					sender.sendMessage("Names must not match /qt commands.");
					return;
				}
				if(containsLetter(args[2]) == false) {
					/* Player attempting to name a QT without letters */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + "] Could not rename: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid name!");
					sender.sendMessage("Names must contain letters.");
					return;
				}
				if(checkLocations(args[1]) == true && checkLocations(args[2]) == false) {
					/* Checks passed, rename QT */
					this.getLocations().set("locations." + getLocation(args[1]) + ".name", args[2]);
					this.saveLocations();
					sender.sendMessage(ChatColor.AQUA + getLocationName(getLocation(args[1])) + ChatColor.WHITE + " has been renamed " + ChatColor.AQUA + args[2] + ChatColor.WHITE + ".");
					return;
				} else if(checkLocations(args[1]) == false) {
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				} else if(checkLocations(args[1]) == true && checkLocations(args[2]) == true) {
					if(getLocation(args[1]).equals(getLocation(args[2]))) {
						/* Checks passed, rename QT */
						this.getLocations().set("locations." + getLocation(args[1]) + ".name", args[2]);
						this.saveLocations();
						sender.sendMessage(ChatColor.AQUA + getLocationName(getLocation(args[1])) + ChatColor.WHITE + " has been renamed " + ChatColor.AQUA + args[2] + ChatColor.WHITE + ".");
						return;
					} else {
						/* QT with name chosen already exists */
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: " + ChatColor.AQUA + getLocationName(args[2]) + ChatColor.GOLD + " already exists!");
						return;
					}
				} else {
					/* Unknown error */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not rename: Unexpected error!");
					return;
				}
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Renames the QT <name>.");
			sender.sendMessage("/qt rename <name> <new name>");
			return;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTType(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		if(args.length == 3 || args.length == 5) {
			if(args[1].equalsIgnoreCase("*")) {
				boolean w = false;
				String world = "";
				String type = "";
				/* If player wishes to change the type of ALL QTs */
				if(args[2].equalsIgnoreCase("cuboid")) {
					type = "cuboid";
				} else if(args[2].equalsIgnoreCase("radius")) {
					type = "radius";
				} else if(args[2].equalsIgnoreCase("toggle")) {
					type = "toggle";
				} else {
					/* Invalid type */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid type!");
					return;
				}
				if(args.length == 5) {
					if(args[3].equalsIgnoreCase("-w")) {
						w = true;
						world = args[4];
					} else {
						/* Invalid arguments, throw info message. */
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: " + ChatColor.AQUA + args[3] + ChatColor.GOLD + " is not a valid parameter!");
						sender.sendMessage("Sets the type of the QT.");
						sender.sendMessage("/qt type <name | *> <radius | cuboid | toggle>");
						return;
					}
				}
				List<Object> locList = (List<Object>) getLocations().getList("list");
				if(locList != null) {
					int n = 0;
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String qt = li.next().toString();
						String qtname = getLocationName(qt);
						if(w == true) {
							if(!getLocations().getString("locations." + qt + ".world").equalsIgnoreCase(world)) {
								continue;
							}
						}
						if(type.equals("cuboid")) {
							/* Set type to cuboid */
							if(getLocations().get("locations." + qt + ".coords.secondary") != null) {
								this.getLocations().set("locations." + qt + ".type", type);
								this.saveLocations();
								n++;
								sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
								continue;
							} else {
								this.getLocations().set("locations." + qt + ".type", type);
								this.saveLocations();
								n++;
								sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.WHITE + type + ChatColor.WHITE + ".");
								sender.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
								continue;
							}
						} else if(type.equals("radius")) {
							/* Set type to radius */
							this.getLocations().set("locations." + qt + ".type", type);
							this.saveLocations();
							n++;
							sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
							continue;
						} else if(type.equals("toggle")) {
							/* Figure out what type the QT currently is and change it */
							if(getType(qt).equals("cuboid")) {
								/* Change type to radius */
								this.getLocations().set("locations." + qt + ".type", "radius");
								this.saveLocations();
								n++;
								sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + "radius" + ChatColor.WHITE + ".");
								continue;
							} else {
								/* Change type to cuboid */
								if(getLocations().get("locations." + qt + ".coords.secondary") != null) {
									this.getLocations().set("locations." + qt + ".type", "cuboid");
									this.saveLocations();
									n++;
									sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + "cuboid" + ChatColor.WHITE + ".");
									continue;
								} else {
									this.getLocations().set("locations." + qt + ".type", "cuboid");
									this.saveLocations();
									n++;
									sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.WHITE + "cuboid" + ChatColor.WHITE + ".");
									sender.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
									continue;
								}
							}
						}
					}
					if(n > 0) {
						sender.sendMessage("Done.");
					} else {
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: There are no QTs in world " + ChatColor.AQUA + world + ChatColor.WHITE + "!");
					}
					return;
				} else {
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: You have not made any QTs yet!");
					return;
				}
			} else {
				if(checkLocations(args[1]) == true) {
					/* QT is valid */
					String qt = getLocation(args[1]);
					String qtname = getLocationName(qt);
					if(args[2].equalsIgnoreCase("cuboid")) {
						/* Set type to cuboid */
						String type = "cuboid";
						if(getLocations().get("locations." + qt + ".coords.secondary") != null) {
							this.getLocations().set("locations." + qt + ".type", type);
							this.saveLocations();
							sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
							return;
						} else {
							this.getLocations().set("locations." + qt + ".type", type);
							this.saveLocations();
							sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.WHITE + type + ChatColor.WHITE + ".");
							sender.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
							return;
						}
					} else if(args[2].equalsIgnoreCase("radius")) {
						/* Set type to radius */
						String type = "radius";
						this.getLocations().set("locations." + qt + ".type", type);
						this.saveLocations();
						sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
						return;
					} else if(args[2].equalsIgnoreCase("toggle")) {
						/* Figure out what type the QT currently is and change it */
						String type = "radius";
						if(getType(qt).equals("cuboid")) {
							/* Change type to radius */
							type = "radius";
							this.getLocations().set("locations." + qt + ".type", type);
							this.saveLocations();
							sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
							return;
						} else {
							/* Change type to cuboid */
							type = "cuboid";
							if(getLocations().get("locations." + qt + ".coords.secondary") != null) {
								this.getLocations().set("locations." + qt + ".type", type);
								this.saveLocations();
								sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
								return;
							} else {
								sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.WHITE + type + ChatColor.WHITE + ".");
								sender.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
								return;
							}
						}
					} else {
						/* Invalid type */
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid type!");
						return;
					}
				} else {
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		} else if(args.length == 2) {
			if(checkLocations(args[1]) == true) {
				/* QT exists, return type */
				String qt = getLocation(args[1]);
				String qtname = getLocationName(qt);
				String type = getType(qt);
				if(type.equals("cuboid")) {
					if(getLocations().get("locations." + qt + ".coords.secondary") != null) {
						sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " is set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
						return;
					} else {
						sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " is set to " + ChatColor.WHITE + type + ChatColor.WHITE + ".");
						sender.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
						return;
					}
				} else {
					sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " is set to " + ChatColor.WHITE + type + ChatColor.WHITE + ".");
					return;
				}
			} else {
				/* QT does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set type: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
				return;
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets the type of the QT.");
			sender.sendMessage("/qt type <name | *> <radius | cuboid | toggle>");
			return;
		}
	}
	
	public String getType(String qt) {
		String type = "radius";
		if(getLocations().get("locations." + qt + ".type") != null && (getLocations().getString("locations." + qt + ".type").equalsIgnoreCase("radius") || getLocations().getString("locations." + qt + ".type").equalsIgnoreCase("cuboid"))) {
			type = getLocations().getString("locations." + qt + ".type").toLowerCase();
			return type;
		} else {
			if(getLocations().get("locations." + qt + ".coords.secondary") != null) {
				type = "cuboid";
			} else {
				type = "radius";
			}
			return type;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTRadius(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		boolean w = false;
		String world = "";
		boolean s = false;
		double size = 0;
		String type = "radius";
		if(args.length >= 2) {
			if(args.length > 2) {
				for(int i = 2; i < args.length; i++) {
					if(args[i].equalsIgnoreCase("-w")) {
						/* -w parameter given */
						if(args.length > i+1) {
							w = true;
							world = args[i+1];
							i++;
						} else {
							/* No world given. */
							if(args[1].equalsIgnoreCase("*")) {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius: No world given for -w!");
								return;	
							}
						}
					} else if(args[i].equalsIgnoreCase("-s")) {
						/* -s parameter given */
						if(args.length > i+1) {
							try {
								size = Double.parseDouble(args[i+1]);
								s = true;
								i++;
							} catch(NumberFormatException e2) {
								if(args[i+1].equalsIgnoreCase("reset")) {
									size = getConfig().getDouble("radius");
									s = true;
									i++;
								} else {
									/* Invalid size */
									sender.sendMessage(ChatColor.GOLD + args[i+1] + ChatColor.WHITE + " is not a valid size, ignoring.");
								}
							}
						} else {
							/* No size given */
							sender.sendMessage("No radius size, ignoring " + ChatColor.GOLD + args[i] + ChatColor.WHITE + ".");
						}
					} else {
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			if(args[1].equalsIgnoreCase("*")) {
				List<Object> locList = (List<Object>) getLocations().getList("list");
				if(locList != null) {
					int n = 0;
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String qt = li.next().toString();
						String qtname = getLocationName(qt);
						if(w == true) {
							if(!getLocations().getString("locations." + qt + ".world").equalsIgnoreCase(world)) {
								continue;
							}
						}
						this.getLocations().set("locations." + qt + ".type", type);
						if(s == true) {
							this.getLocations().set("locations." + qt + ".radius", size);
							sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ", size: " + ChatColor.GOLD + size + ChatColor.WHITE + ".");
						} else {
							sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
						}
						this.saveLocations();
						n++;
					}
					if(n > 0) {
						sender.sendMessage("Done.");
					} else {
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius: There are no QTs in world " + ChatColor.AQUA + world + ChatColor.WHITE + "!");
					}
					return;
				} else {
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius: You have not made any QTs yet!");
					return;
				}
			} else {
				if(checkLocations(args[1]) == true) {
					/* QT exists, set radius */
					String qt = getLocation(args[1]);
					String qtname = getLocationName(qt);
					this.getLocations().set("locations." + qt + ".type", type);
					if(s == true) {
						this.getLocations().set("locations." + qt + ".radius", size);
						sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ", size: " + ChatColor.GOLD + size + ChatColor.WHITE + ".");
					} else {
						sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
					}
					this.saveLocations();
					return;
				} else {
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set radius: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Changes the type of QT <name> to radius.");
			sender.sendMessage("/qt radius <name>");
			return;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTCuboid(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		boolean w = false;
		String world = "";
		boolean a = false;
		boolean b = false;
		String type = "cuboid";
		if(args.length >= 2) {
			if(args.length > 2) {
				for(int i = 2; i < args.length; i++) {
					if(args[i].equalsIgnoreCase("-w")) {
						/* -w parameter given */
						if(args.length > i+1) {
							w = true;
							world = args[i+1];
							i++;
						} else {
							/* No world given. */
							if(args[1].equalsIgnoreCase("*")) {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: No world given for -w!");
								return;	
							}
						}
					} else if(args[i].equalsIgnoreCase("-a")) {
						/* -a parameter given */
						a = true;
					} else if(args[i].equalsIgnoreCase("-b")) {
						/* -b parameter given */
						b = true;
					} else {
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
				if((a == true && b == true) || args[1].equalsIgnoreCase("*")) {
					a = false;
					b = false;
				}
			}
			if(args[1].equalsIgnoreCase("*")) {
				List<Object> locList = (List<Object>) getLocations().getList("list");
				if(locList != null) {
					int n = 0;
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String qt = li.next().toString();
						String qtname = getLocationName(qt);
						if(w == true) {
							if(!getLocations().getString("locations." + qt + ".world").equalsIgnoreCase(world)) {
								continue;
							}
						}
						sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
						this.getLocations().set("locations." + qt + ".type", type);
						this.saveLocations();
						n++;
					}
					if(n > 0) {
						sender.sendMessage("Done.");
					} else {
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: There are no QTs in world " + ChatColor.AQUA + world + ChatColor.WHITE + "!");
					}
					return;
				} else {
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: You have not made any QTs yet!");
					return;
				}
			} else {
				if(checkLocations(args[1]) == true) {
					/* QT exists, set cuboid */
					String qt = getLocation(args[1]);
					String qtname = getLocationName(qt);
					if(a == true || b == true) {
						if(!(sender instanceof Player)) {
							sender.sendMessage(ChatColor.RED + "You must be a player!");
							return;
						}
						Player p = (Player)sender;
						Location coord = p.getLocation();
						String pWorld = p.getWorld().getName();
						String locWorld = getLocations().getString("locations." + getLocation(args[1]) + ".world");
						
						if(pWorld.equals(locWorld)) {
							/* Checks passed, set range */
							String cuboid = "";
							if(a == true) {
								cuboid = "Primary";
							} else {
								cuboid = "Secondary";
							}
							this.getLocations().set("locations." + qt + ".type", type);
							this.getLocations().set("locations." + qt + ".coords." + cuboid.toLowerCase() + ".x", coord.getX());
							this.getLocations().set("locations." + qt + ".coords." + cuboid.toLowerCase() + ".y", coord.getY());
							this.getLocations().set("locations." + qt + ".coords." + cuboid.toLowerCase() + ".z", coord.getZ());
							this.saveLocations();
							sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
							sender.sendMessage(ChatColor.GOLD + cuboid + ChatColor.WHITE + " coords for " + ChatColor.AQUA + qtname + ChatColor.WHITE + " set.");
							if(getLocations().get("locations." + qt + ".coords.secondary") == null) {
								sender.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
							}
							return;
						} else {
							/* Incorrect world */
							sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: You are not on the correct World!");
							return;
						}
					} else {
						this.getLocations().set("locations." + qt + ".type", type);
						this.saveLocations();
						sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been set to " + ChatColor.GOLD + type + ChatColor.WHITE + ".");
						if(getLocations().get("locations." + qt + ".coords.secondary") == null) {
							sender.sendMessage("Treating as " + ChatColor.GOLD + "radius" + ChatColor.WHITE + " until shape is confirmed with /qt cuboid.");
						}
						return;
					}
				} else {
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set cuboid: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Changes the type of QT <name> to cuboid.");
			sender.sendMessage("/qt radius <name>");
			return;
		}
	}
	
	public void QTMove(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		if(args.length == 2) {
			if(checkLocations(args[1]) == true) {
				/* QT exists, get info and check world */
				String qt = getLocation(args[1]);
				String qtname = getLocationName(qt);
				if(getType(qt).equals("radius")) {
					Player p = (Player)sender;
					Location coord = p.getLocation();
					String pWorld = p.getWorld().getName();
					String locWorld = getLocations().getString("locations." + qt + ".world");
					if(pWorld.equals(locWorld)) {
						/* Checks passed, set coords */
						this.getLocations().set("locations." + qt + ".coords.primary.x", coord.getX());
						this.getLocations().set("locations." + qt + ".coords.primary.y", coord.getY());
						this.getLocations().set("locations." + qt + ".coords.primary.z", coord.getZ());
						this.getLocations().set("locations." + qt + ".coords.dest.x", coord.getX());
						this.getLocations().set("locations." + qt + ".coords.dest.y", coord.getY());
						this.getLocations().set("locations." + qt + ".coords.dest.z", coord.getZ());
						this.getLocations().set("locations." + qt + ".coords.dest.pitch", coord.getPitch());
						this.getLocations().set("locations." + qt + ".coords.dest.yaw", coord.getYaw());
						this.saveLocations();
						sender.sendMessage("Moved QT " + ChatColor.AQUA + qtname + ChatColor.WHITE + ".");
						return;
					} else {
						/* Incorrect world */
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not move: You are not on the correct World!");
						return;
					}
				} else {
					/* Is not a radius QT */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not move: " + ChatColor.AQUA + qtname + ChatColor.GOLD + " is not a radius QT!");
					return;
				}
			} else {
				/* QT does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not move: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
				return;
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Moves the selected radius QT to your current location.");
			sender.sendMessage("/qt move <name>");
			return;
		}
	}
	
	public void QTDest(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		if(args.length == 2) {
			if(checkLocations(args[1]) == true) {
				/* QT exists, get info and check world */
				String qt = getLocation(args[1]);
				String qtname = getLocationName(qt);
				Player p = (Player)sender;
				Location coord = p.getLocation();
				String pWorld = p.getWorld().getName();
				String locWorld = getLocations().getString("locations." + qt + ".world");
				if(pWorld.equals(locWorld)) {
					/* Checks passed, set dest */
					this.getLocations().set("locations." + qt + ".coords.dest.x", coord.getX());
					this.getLocations().set("locations." + qt + ".coords.dest.y", coord.getY());
					this.getLocations().set("locations." + qt + ".coords.dest.z", coord.getZ());
					this.getLocations().set("locations." + qt + ".coords.dest.pitch", coord.getPitch());
					this.getLocations().set("locations." + qt + ".coords.dest.yaw", coord.getYaw());
					this.saveLocations();
					sender.sendMessage("Destination for QT " + ChatColor.AQUA + qtname + ChatColor.WHITE + " set.");
					return;
				} else {
					/* Incorrect world */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set dest: You are not on the correct World!");
					return;
				}
			} else {
				/* QT does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set dest" + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
				return;
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets the arrival spot for the selected QT to your current location.");
			sender.sendMessage("/qt dest <name>");
			return;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTEnable(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		boolean w = false;
		boolean toggle = false;
		String world = "";
		String set = "enabled";
		boolean e = true;
		if(args.length >= 2) {
			if(args.length > 2) {
				for(int i = 2; i < args.length; i++) {
					if(args[i].equalsIgnoreCase("-w")) {
						/* -w parameter given */
						if(args.length > i+1) {
							w = true;
							world = args[i+1];
							i++;
						} else {
							/* No world given. */
							if(args[1].equalsIgnoreCase("*")) {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not enable: No world given for -w!");
								return;
							}
						}
					} else if(args[i].equalsIgnoreCase("-set")) {
						/* -set parameter given */
						if(args.length > i+1) {
							if(args[i+1].equalsIgnoreCase("true")) {
								set = "enabled";
								e = true;
								i++;
							} else if (args[i+1].equalsIgnoreCase("false")) {
								set = "disabled";
								e = false;
								i++;
							} else if (args[i+1].equalsIgnoreCase("toggle")) {
								toggle = true;
								i++;
							} else {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not enable: -set must be true, false, or toggle!");
								return;
							}
						} else {
							/* Has not specified -set parameter */
							if(args[1].equalsIgnoreCase("*")) {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not enable: -set must be true, false, or toggle!");
								return;
							}
						}
					} else {
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			if(args[1].equalsIgnoreCase("*")) {
				List<Object> locList = (List<Object>) getLocations().getList("list");
				if(locList != null) {
					int n = 0;
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String qt = li.next().toString();
						String qtname = getLocationName(qt);
						if(w == true) {
							if(!getLocations().getString("locations." + qt + ".world").equalsIgnoreCase(world)) {
								continue;
							}
						}
						if(toggle == true) {
							if(getLocations().get("locations." + qt + ".enabled") != null && getLocations().getBoolean("locations." + qt + ".enabled") == true) {
								set = "disabled";
								e = false;
							} else {
								set = "enabled";
								e = true;
							}
						}
						sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
						this.getLocations().set("locations." + qt + ".enabled", e);
						this.saveLocations();
						n++;
					}
					if(n > 0) {
						sender.sendMessage("Done.");
					} else {
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not enable: There are no QTs in world " + ChatColor.AQUA + world + ChatColor.WHITE + "!");
					}
					return;
				} else {
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not enable: You have not made any QTs yet!");
					return;
				}
			} else {
				if(checkLocations(args[1]) == true) {
					String qt = getLocation(args[1]);
					String qtname = getLocationName(qt);
					if(toggle == true) {
						if(getLocations().get("locations." + qt + ".enabled") != null && getLocations().getBoolean("locations." + qt + ".enabled") == true) {
							set = "disabled";
							e = false;
						} else {
							set = "enabled";
							e = true;
						}
					}
					sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
					this.getLocations().set("locations." + qt + ".enabled", e);
					this.saveLocations();
					return;
				} else {
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not enable: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Enables the selected QT.");
			sender.sendMessage("/qt enable <name>");
			return;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTDisable(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		boolean w = false;
		String world = "";
		String set = "disabled";
		boolean e = false;
		boolean toggle = false;
		if(args.length >= 2) {
			if(args.length > 2) {
				for(int i = 2; i < args.length; i++) {
					if(args[i].equalsIgnoreCase("-w")) {
						/* -w parameter given */
						if(args.length > i+1) {
							w = true;
							world = args[i+1];
							i++;
						} else {
							/* No world given. */
							if(args[1].equalsIgnoreCase("*")) {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not disable: No world given for -w!");
								return;
							}
						}
					} else if(args[i].equalsIgnoreCase("-set")) {
						/* -set parameter given */
						if(args.length > i+1) {
							if(args[i+1].equalsIgnoreCase("true")) {
								set = "disabled";
								e = false;
								i++;
							} else if (args[i+1].equalsIgnoreCase("false")) {
								set = "enabled";
								e = true;
								i++;
							} else if (args[i+1].equalsIgnoreCase("toggle")) {
								toggle = true;
								i++;
							} else {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not disable: -set must be true, false, or toggle!");
								return;
							}
						} else {
							/* Has not specified -set parameter */
							if(args[1].equalsIgnoreCase("*")) {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not disable: -set must be true, false, or toggle!");
								return;
							}
						}
					} else {
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			if(args[1].equalsIgnoreCase("*")) {
				List<Object> locList = (List<Object>) getLocations().getList("list");
				if(locList != null) {
					int n = 0;
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String qt = li.next().toString();
						String qtname = getLocationName(qt);
						if(w == true) {
							if(!getLocations().getString("locations." + qt + ".world").equalsIgnoreCase(world)) {
								continue;
							}
						}
						if(toggle == true) {
							if(getLocations().get("locations." + qt + ".enabled") != null && getLocations().getBoolean("locations." + qt + ".enabled") == true) {
								set = "disabled";
								e = false;
							} else {
								set = "enabled";
								e = true;
							}
						}
						sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
						this.getLocations().set("locations." + qt + ".enabled", e);
						this.saveLocations();
						n++;
					}
					if(n > 0) {
						sender.sendMessage("Done.");
					} else {
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not disable: There are no QTs in world " + ChatColor.AQUA + world + ChatColor.WHITE + "!");
					}
					return;
				} else {
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not disable: You have not made any QTs yet!");
					return;
				}
			} else {
				if(checkLocations(args[1]) == true) {
					String qt = getLocation(args[1]);
					String qtname = getLocationName(qt);
					if(toggle == true) {
						if(getLocations().get("locations." + qt + ".enabled") != null && getLocations().getBoolean("locations." + qt + ".enabled") == true) {
							set = "disabled";
							e = false;
						} else {
							set = "enabled";
							e = true;
						}
					}
					sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " has been " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
					this.getLocations().set("locations." + qt + ".enabled", e);
					this.saveLocations();
					return;
				} else {
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not disable: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Disables the selected QT.");
			sender.sendMessage("/qt disable <name>");
			return;
		}
	}
	
	public void QTPrice(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		boolean reset = false;
		double price = 0;
		if(args.length == 4) {
			try {
				price = Double.parseDouble(args[3]);
				reset = false;
			} catch(NumberFormatException e) {
				if(args[3].equalsIgnoreCase("reset")) {
					reset = true;
				} else {
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: " + ChatColor.AQUA + args[3] + ChatColor.GOLD + " is not a valid price!");
					return;
				}
			}
			if(checkLocations(args[1]) == true && checkLocations(args[2]) == true) {
				/* QTs exist, set price */
				String qt1 = getLocation(args[1]);
				String qt1name = getLocationName(qt1);
				String qt2 = getLocation(args[2]);
				String qt2name = getLocationName(qt2);
				if(reset == false) {
					this.getLocations().set("locations." + qt2 + ".charge-from." + qt1, price);
					this.saveLocations();
					sender.sendMessage("Price from " + ChatColor.AQUA + qt1name + ChatColor.WHITE + " to " + ChatColor.AQUA + qt2name + ChatColor.WHITE + " set to " + ChatColor.GOLD + price + ChatColor.WHITE + ".");
				} else {
					this.getLocations().set("locations." + qt2 + ".charge-from." + qt1, null);
					this.saveLocations();
					sender.sendMessage("Price from " + ChatColor.AQUA + qt1name + ChatColor.WHITE + " to " + ChatColor.AQUA + qt2name + ChatColor.WHITE + " has been reset.");
				}
				if(economyEnabled == false) {
					/* Economy is disabled, warn user */
					sender.sendMessage("[Warning] Economy is disabled, prices will have no effect.");
				}
				return;
			} else if(checkLocations(args[1]) != true) {
				/* QT <a> does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
				return;
			} else if(checkLocations(args[2]) != true) {
				/* QT <b> does not exist */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " does not exist!");
				return;
			} else {
				/* Unknown error */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set price: Unexpected error!");
				return;
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets the price from QT <a> to QT <b>.");
			sender.sendMessage("/qt price <a> <b> <price>");
			return;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTFree(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		boolean w = false;
		String world = "";
		boolean toggle = false;
		boolean set = true;
		if(args.length >= 3) {
			if(args[2].equalsIgnoreCase("true")) {
				toggle = false;
				set = true;
			} else if(args[2].equalsIgnoreCase("false")) {
				toggle = false;
				set = false;
			} else if(args[2].equalsIgnoreCase("toggle")) {
				toggle = true;
			} else {
				/* Invalid setting */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set free: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid setting!");
				return;
			}
			if(args.length > 3) {
				for(int i = 3; i < args.length; i++) {
					if(args[i].equalsIgnoreCase("-w")) {
						/* -w parameter given */
						if(args.length > i+1) {
							w = true;
							world = args[i+1];
							i++;
						} else {
							/* No world given. */
							if(args[1].equalsIgnoreCase("*")) {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set free: No world given for -w!");
								return;
							}
						}
					} else {
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			if(args[1].equalsIgnoreCase("*")) {
				List<Object> locList = (List<Object>) getLocations().getList("list");
				if(locList != null) {
					int n = 0;
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String qt = li.next().toString();
						String qtname = getLocationName(qt);
						if(w == true) {
							if(!getLocations().getString("locations." + qt + ".world").equalsIgnoreCase(world)) {
								continue;
							}
						}
						if(toggle == true) {
							if(getLocations().get("locations." + qt + ".free") != null && getLocations().getBoolean("locations." + qt + ".free") == true) {
								set = false;
							} else {
								set = true;
							}
						}
						sender.sendMessage("Free travel to/from " + ChatColor.AQUA + qtname + ChatColor.WHITE + ": " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
						this.getLocations().set("locations." + qt + ".free", set);
						this.saveLocations();
						n++;
					}
					if(n > 0) {
						sender.sendMessage("Done.");
					} else {
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set free: There are no QTs in world " + ChatColor.AQUA + world + ChatColor.WHITE + "!");
					}
					return;
				} else {
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set free: You have not made any QTs yet!");
					return;
				}
			} else {
				if(checkLocations(args[1]) == true) {
					String qt = getLocation(args[1]);
					String qtname = getLocationName(qt);
					if(toggle == true) {
						if(getLocations().get("locations." + qt + ".free") != null && getLocations().getBoolean("locations." + qt + ".free") == true) {
							set = false;
						} else {
							set = true;
						}
					}
					sender.sendMessage("Free travel to/from " + ChatColor.AQUA + qtname + ChatColor.WHITE + ": " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
					this.getLocations().set("locations." + qt + ".free", set);
					this.saveLocations();
					return;
				} else {
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set free: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether all travel to/from the selected QT is free.");
			sender.sendMessage("/qt free <name | *> <true | false | toggle>");
			return;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTDiscovery(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		boolean w = false;
		String world = "";
		boolean toggle = false;
		boolean set = true;
		if(args.length >= 3) {
			if(args[2].equalsIgnoreCase("true")) {
				toggle = false;
				set = true;
			} else if(args[2].equalsIgnoreCase("false")) {
				toggle = false;
				set = false;
			} else if(args[2].equalsIgnoreCase("toggle")) {
				toggle = true;
			} else {
				/* Invalid setting */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set discovery: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid setting!");
				return;
			}
			if(args.length > 3) {
				for(int i = 3; i < args.length; i++) {
					if(args[i].equalsIgnoreCase("-w")) {
						/* -w parameter given */
						if(args.length > i+1) {
							w = true;
							world = args[i+1];
							i++;
						} else {
							/* No world given. */
							if(args[1].equalsIgnoreCase("*")) {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set discovery: No world given for -w!");
								return;
							}
						}
					} else {
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			if(args[1].equalsIgnoreCase("*")) {
				List<Object> locList = (List<Object>) getLocations().getList("list");
				if(locList != null) {
					int n = 0;
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String qt = li.next().toString();
						String qtname = getLocationName(qt);
						if(w == true) {
							if(!getLocations().getString("locations." + qt + ".world").equalsIgnoreCase(world)) {
								continue;
							}
						}
						if(toggle == true) {
							if(getLocations().get("locations." + qt + ".require-discovery") != null && getLocations().getBoolean("locations." + qt + ".require-discovery") == true) {
								set = false;
							} else {
								set = true;
							}
						}
						sender.sendMessage("Require discovery for " + ChatColor.AQUA + qtname + ChatColor.WHITE + ": " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
						this.getLocations().set("locations." + qt + ".require-discovery", set);
						this.saveLocations();
						n++;
					}
					if(n > 0) {
						sender.sendMessage("Done.");
					} else {
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set discovery: There are no QTs in world " + ChatColor.AQUA + world + ChatColor.WHITE + "!");
					}
					return;
				} else {
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set discovery: You have not made any QTs yet!");
					return;
				}
			} else {
				if(checkLocations(args[1]) == true) {
					String qt = getLocation(args[1]);
					String qtname = getLocationName(qt);
					if(toggle == true) {
						if(getLocations().get("locations." + qt + ".require-discovery") != null && getLocations().getBoolean("locations." + qt + ".require-discovery") == true) {
							set = false;
						} else {
							set = true;
						}
					}
					sender.sendMessage("Require discovery for " + ChatColor.AQUA + qtname + ChatColor.WHITE + ": " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
					this.getLocations().set("locations." + qt + ".require-discovery", set);
					this.saveLocations();
					return;
				} else {
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set discovery: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether discovery is required for the selected QT.");
			sender.sendMessage("/qt discovery <name | *> <true | false | toggle>");
			return;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTPerms(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		boolean w = false;
		String world = "";
		boolean toggle = false;
		boolean set = true;
		if(args.length >= 3) {
			if(args[2].equalsIgnoreCase("true")) {
				toggle = false;
				set = true;
			} else if(args[2].equalsIgnoreCase("false")) {
				toggle = false;
				set = false;
			} else if(args[2].equalsIgnoreCase("toggle")) {
				toggle = true;
			} else {
				/* Invalid setting */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set perms: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid setting!");
				return;
			}
			if(args.length > 3) {
				for(int i = 3; i < args.length; i++) {
					if(args[i].equalsIgnoreCase("-w")) {
						/* -w parameter given */
						if(args.length > i+1) {
							w = true;
							world = args[i+1];
							i++;
						} else {
							/* No world given. */
							if(args[1].equalsIgnoreCase("*")) {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set perms: No world given for -w!");
								return;
							}
						}
					} else {
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			if(args[1].equalsIgnoreCase("*")) {
				List<Object> locList = (List<Object>) getLocations().getList("list");
				if(locList != null) {
					int n = 0;
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String qt = li.next().toString();
						String qtname = getLocationName(qt);
						if(w == true) {
							if(!getLocations().getString("locations." + qt + ".world").equalsIgnoreCase(world)) {
								continue;
							}
						}
						if(toggle == true) {
							if(getLocations().get("locations." + qt + ".require-permissions") != null && getLocations().getBoolean("locations." + qt + ".require-permissions") == true) {
								set = false;
							} else {
								set = true;
							}
						}
						sender.sendMessage("Require permissions for " + ChatColor.AQUA + qtname + ChatColor.WHITE + ": " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
						this.getLocations().set("locations." + qt + ".require-permissions", set);
						this.saveLocations();
						n++;
					}
					if(n > 0) {
						sender.sendMessage("Done.");
					} else {
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set perms: There are no QTs in world " + ChatColor.AQUA + world + ChatColor.WHITE + "!");
					}
					return;
				} else {
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set perms: You have not made any QTs yet!");
					return;
				}
			} else {
				if(checkLocations(args[1]) == true) {
					String qt = getLocation(args[1]);
					String qtname = getLocationName(qt);
					if(toggle == true) {
						if(getLocations().get("locations." + qt + ".require-permissions") != null && getLocations().getBoolean("locations." + qt + ".require-permissions") == true) {
							set = false;
						} else {
							set = true;
						}
					}
					sender.sendMessage("Require permissions for " + ChatColor.AQUA + qtname + ChatColor.WHITE + ": " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
					this.getLocations().set("locations." + qt + ".require-permissions", set);
					this.saveLocations();
					return;
				} else {
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set perms: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether permissions are required for the selected QT.");
			sender.sendMessage("/qt perms <name | *> <true | false | toggle>");
			return;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTMultiworld(CommandSender sender, String[] args) {
		/* Get arguments and deal with appropriately */
		boolean w = false;
		String world = "";
		boolean toggle = false;
		boolean set = true;
		if(args.length >= 3) {
			if(args[2].equalsIgnoreCase("true")) {
				toggle = false;
				set = true;
			} else if(args[2].equalsIgnoreCase("false")) {
				toggle = false;
				set = false;
			} else if(args[2].equalsIgnoreCase("toggle")) {
				toggle = true;
			} else {
				/* Invalid setting */
				sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set multiworld: " + ChatColor.AQUA + args[2] + ChatColor.GOLD + " is not a valid setting!");
				return;
			}
			if(args.length > 3) {
				for(int i = 3; i < args.length; i++) {
					if(args[i].equalsIgnoreCase("-w")) {
						/* -w parameter given */
						if(args.length > i+1) {
							w = true;
							world = args[i+1];
							i++;
						} else {
							/* No world given. */
							if(args[1].equalsIgnoreCase("*")) {
								sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set multiworld: No world given for -w!");
								return;
							}
						}
					} else {
						/* Invalid parameter */
						sender.sendMessage(ChatColor.GOLD + args[i] + ChatColor.WHITE + " is not a valid parameter, ignoring.");
					}
				}
			}
			if(args[1].equalsIgnoreCase("*")) {
				List<Object> locList = (List<Object>) getLocations().getList("list");
				if(locList != null) {
					int n = 0;
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String qt = li.next().toString();
						String qtname = getLocationName(qt);
						if(w == true) {
							if(!getLocations().getString("locations." + qt + ".world").equalsIgnoreCase(world)) {
								continue;
							}
						}
						if(toggle == true) {
							if(getLocations().get("locations." + qt + ".multiworld") != null && getLocations().getBoolean("locations." + qt + ".multiworld") == true) {
								set = false;
							} else {
								set = true;
							}
						}
						sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " is multiworld: " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
						this.getLocations().set("locations." + qt + ".multiworld", set);
						this.saveLocations();
						n++;
					}
					if(n > 0) {
						sender.sendMessage("Done.");
					} else {
						sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set multiworld: There are no QTs in world " + ChatColor.AQUA + world + ChatColor.WHITE + "!");
					}
					return;
				} else {
					/* Player has not made any QTs yet */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set multiworld: You have not made any QTs yet!");
					return;
				}
			} else {
				if(checkLocations(args[1]) == true) {
					String qt = getLocation(args[1]);
					String qtname = getLocationName(qt);
					if(toggle == true) {
						if(getLocations().get("locations." + qt + ".multiworld") != null && getLocations().getBoolean("locations." + qt + ".multiworld") == true) {
							set = false;
						} else {
							set = true;
						}
					}
					sender.sendMessage(ChatColor.AQUA + qtname + ChatColor.WHITE + " is multiworld: " + ChatColor.GOLD + set + ChatColor.WHITE + ".");
					this.getLocations().set("locations." + qt + ".multiworld", set);
					this.saveLocations();
					return;
				} else {
					/* QT does not exist */
					sender.sendMessage(ChatColor.RED + "[Error]" + ChatColor.GOLD + " Could not set multiworld: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " does not exist!");
					return;
				}
			}
		} else {
			/* Invalid arguments, throw info message. */
			sender.sendMessage("Sets whether the selected QT is multiworld.");
			sender.sendMessage("/qt multiworld <name | *> <true | false | toggle>");
			return;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void QTList(CommandSender sender, int page, boolean listAll) {
		if(listAll == false) {
			String qt = checkPlayerQT(sender);
			if(qt != null || getConfig().getBoolean("qt-from-anywhere") == true) {
				List<Object> locList = (List<Object>) getLocations().getList("list");
				List<Object> destList = new ArrayList<Object>();
				if(qt != null) {
					sender.sendMessage(ChatColor.BLUE + "Current Location: " + ChatColor.AQUA + qt);
				}
				sender.sendMessage(ChatColor.BLUE + "From here you can QuickTravel to:");
				if(locList != null) {
					ListIterator<Object> li = locList.listIterator();
					while(li.hasNext()) {
						String v = li.next().toString();
						if(runChecks(sender, getLocationName(v), false) == true && qt != getLocationName(v)) {
							destList.add(v);
						}
					}
					if(destList.size() <= 0) {
						sender.sendMessage("You cannot QuickTravel anywhere yet.");
					} else {
						displayList(sender, destList, qt, page);
					}
				} else {
					sender.sendMessage("You cannot QuickTravel anywhere yet.");
				}
			} else {
				sender.sendMessage(ChatColor.BLUE + "You are not at a QuickTravel point.");
			}	
		} else {
			List<Object> fullList = new ArrayList<Object>();
			List<Object> locList = (List<Object>) getLocations().getList("list");
			if(locList != null) {
				ListIterator<Object> li = locList.listIterator();
				while(li.hasNext()) {
					String v = li.next().toString();
					String w = getLocations().getString("locations." + v + ".world");
					String e = null;
					boolean eState = false;
					ChatColor eColour = ChatColor.WHITE;
					String d = null;
					boolean dState = false;
					boolean dCfg = getConfig().getBoolean("require-discovery-by-default");
					ChatColor dColour = ChatColor.WHITE;
					if(getLocations().get("locations." + v + ".enabled") != null) {
						eState = getLocations().getBoolean("locations." + v + ".enabled");
					} else {
						eState = getConfig().getBoolean("enabled-by-default");
					}
					if(eState == true) {
						e = "Enabled";
						eColour = ChatColor.GREEN;
					} else {
						e = "Disabled";
						eColour = ChatColor.RED;
					}
					List<Object> discoveredList = (List<Object>) getLocations().getList("locations." + v + ".discovered-by");
					if(discoveredList != null) {
						ListIterator<Object> dli = discoveredList.listIterator();
						while(dli.hasNext()) {
							String dv = dli.next().toString();
							if(dv.equalsIgnoreCase(sender.getName())) {
								dState = true;
							}
						}
					}
					if(dCfg == false) {
						if(dState == true) {
							d = "Discovered";
							dColour = ChatColor.GRAY;
						} else {
							d = "Undiscovered";
							dColour = ChatColor.DARK_GRAY;
						}
					} else { 
						if(dState == true) {
							d = "Discovered";
							dColour = ChatColor.GOLD;
						} else {
							d = "Undiscovered";
							dColour = ChatColor.GRAY;
						}
					}
					String x = "[" + w + "] " + ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + eColour + e + ChatColor.WHITE + " | " + dColour + d;
					fullList.add(x);
				}
				double dpages = Math.ceil((double)fullList.size() / (double)8);
				int pages = (int) dpages;
				if(page > pages) {
					sender.sendMessage("There is no page " + page + ", displaying page 1.");
					page = 1;
				}
				int start = ((page - 1) * 8) + 1;
				int end = start + 7;
				int n = 0;
				ListIterator<Object> fullLI = fullList.listIterator();
				while(fullLI.hasNext()) {
					String v = fullLI.next().toString();
					n++;
					if(n >= start && n <= end) {
						sender.sendMessage(v);
					}
				}
				String pageString = null;
				if(page < pages) {
					int nextPage = page + 1;
					pageString = "Page " + ChatColor.GOLD + page + ChatColor.WHITE + " of " + ChatColor.GOLD + pages + ChatColor.WHITE + ". Type " + ChatColor.GOLD + "/qt list " + nextPage + ChatColor.WHITE + " to read the next page.";	
				} else {
					pageString = "Page " + ChatColor.GOLD + page + ChatColor.WHITE + " of " + ChatColor.GOLD + pages;
				}
				sender.sendMessage(pageString);
			} else {
				sender.sendMessage("The list is empty.");
			}
		}
	}
	
	public void displayList(CommandSender sender, List<Object> destList, String qt, int page) {
		double dpages = Math.ceil((double)destList.size() / (double)8);
		int pages = (int) dpages;
		if(page > pages) {
			sender.sendMessage("There is no page " + page + ", displaying page 1.");
			page = 1;
		}
		int start = ((page - 1) * 8) + 1;
		int end = start + 7;
		int n = 0;
		Player p = (Player)sender;
		String pWorld = p.getWorld().getName();
		String wString = "";
		double tax = getConfig().getDouble("multiworld-tax");
		ListIterator<Object> destLI = destList.listIterator();
		while(destLI.hasNext()) {
			String v = destLI.next().toString();
			if(!getLocations().getString("locations." + v + ".world").equalsIgnoreCase(pWorld)) {
				wString = "[" + getLocations().getString("locations." + v + ".world") + "] ";
			} else {
				wString = "";
			}
			n++;
			if(n >= start && n <= end) {
				double c = 0;
				if(qt != null) {
					/* If player is at a QT, get price from this location, if any */
					/* Is server running a valid economy? */
					if(economyEnabled == true) {
						if(isFree(v) || isFree(qt)) {
							/* One or both of these QTs are free, no price */
							if(!p.getWorld().getName().equalsIgnoreCase(getLocations().getString("locations." + v + ".world"))) {
								c = c + tax;
								sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(c));
							} else {
								sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v));
							}
						} else {
							if(getLocations().get("locations." + v + ".charge-from." + getLocation(qt)) != null) {
								c = getLocations().getDouble("locations." + v + ".charge-from." + getLocation(qt));
								if(!p.getWorld().getName().equalsIgnoreCase(getLocations().getString("locations." + v + ".world"))) {
									c = c + tax;
								}
								/* If price has been manually set */
								if(c > 0) {
									sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(c));
								} else {
									sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE);
								}
							} else if((getConfig().getBoolean("qt-from-anywhere") == true && getConfig().getBoolean("free-from-qts") == false) || (getConfig().getBoolean("qt-from-anywhere") == false && getConfig().getBoolean("free-by-default") == false)) {
								/* If no price set, but server still requires payment for this QT */
								c = calculatePrice(getLocation(qt), v);
								if(!p.getWorld().getName().equalsIgnoreCase(getLocations().getString("locations." + v + ".world"))) {
									c = c + tax;
								}
								sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(c));
							} else {
								/* No price for this QT */
								if(!p.getWorld().getName().equalsIgnoreCase(getLocations().getString("locations." + v + ".world"))) {
									c = c + tax;
									sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(c));
								} else {
									sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v));
								}
							}
						}
					} else {
						/* No valid economy found, no price */
						sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v));
					}
				} else if(getConfig().getBoolean("free-by-default") == false) {
					if(economyEnabled == true) {
						/* Player is not at a QT */
						if(isFree(v) && getLocations().get("locations." + v + ".free") != null) {
							/* QT is set to free, no price */
							if(!p.getWorld().getName().equalsIgnoreCase(getLocations().getString("locations." + v + ".world"))) {
								c = c + tax;
								sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(c));
							} else {
								sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v));
							}
						} else {
							/* Calculate price */
							c = calculatePrice(sender, v);
							if(!p.getWorld().getName().equalsIgnoreCase(getLocations().getString("locations." + v + ".world"))) {
								c = c + tax;
							}
							sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " | " + ChatColor.GOLD + "Price: " + EcoSetup.economy.format(c));
						}
					} else {
						/* Economy disabled */
						sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v));
					}
				} else {
					/* No price required or economy disabled */
					sender.sendMessage(wString + ChatColor.AQUA + getLocationName(v));
				}
			}
		}
		String pageString = null;
		if(page < pages) {
			int nextPage = page + 1;
			pageString = "Page " + ChatColor.GOLD + page + ChatColor.WHITE + " of " + ChatColor.GOLD + pages + ChatColor.WHITE + ". Type " + ChatColor.GOLD + "/qt " + nextPage + ChatColor.WHITE + " to read the next page.";	
		} else {
			pageString = "Page " + ChatColor.GOLD + page + ChatColor.WHITE + " of " + ChatColor.GOLD + pages;
		}
		sender.sendMessage(pageString);
	}
	
	@SuppressWarnings("unchecked")
	public boolean runChecks(CommandSender sender, String rQT, boolean e) {
		if(checkLocations(rQT) == false) {
			// Check requested destination is valid
			if(e) {
				sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
			}
			
			return false;
		}
		if(!((getLocations().get("locations." + getLocation(rQT) + ".enabled") == null && getConfig().getBoolean("enabled-by-default") == true) || getLocations().getBoolean("locations." + getLocation(rQT) + ".enabled") == true)) {
			// Check requested destination is enabled
			if(e) {
				sender.sendMessage(ChatColor.AQUA + getLocationName(rQT) + ChatColor.WHITE + " is disabled.");
			}
			return false;
		}
		Player p = (Player)sender;
		if(!playerHasPermission(p, rQT.toLowerCase())) {
			// Check player has permission to use the requested QT
			if(e) {
				sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
			}
			return false;
		}
		/* Multiworld checks */
		String qt = checkPlayerQT(p);
		String pWorld = p.getWorld().getName();
		String locWorld = getLocations().getString("locations." + getLocation(rQT) + ".world");
		if(!pWorld.equals(locWorld)) {
			/* Player not on correct world, check multiworld settings */
			if(qt != null) {
				/* Player is at a QT, check it */
				if(getLocations().get("locations." + getLocation(qt) + ".multiworld") != null && getLocations().getBoolean("locations." + getLocation(qt) + ".multiworld") == false) {
					/* If 'multiworld' is false */
					if(e) {
						sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] You are not on the correct World!");
					}
					return false;
				} else if(getLocations().get("locations." + getLocation(qt) + ".multiworld") == null) {
					/* 'multiworld' not set for QT, use default */
					if(getConfig().getBoolean("multiworld-by-default") == false) {
						if(e) {
							sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] You are not on the correct World!");
						}
						return false;
					}
				}
			} else if(getConfig().getBoolean("multiworld-by-default") == false) {
				/* Player not at QT and server multiworld is off by default */
				if(e) {
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] You are not on the correct World!");
				}
				return false;
			}
			/* Now check the destination QT */
			if(getLocations().get("locations." + getLocation(rQT) + ".multiworld") != null && getLocations().getBoolean("locations." + getLocation(rQT) + ".multiworld") == false) {
				/* If 'multiworld' is false */
				if(e) {
					sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] You are not on the correct World!");
				}
				return false;
			} else if(getLocations().get("locations." + getLocation(rQT) + ".multiworld") == null) {
				/* 'multiworld' not set for QT, use default */
				if(getConfig().getBoolean("multiworld-by-default") == false) {
					if(e) {
						sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] You are not on the correct World!");
					}
					return false;
				}
			}
		}
		boolean discovered = false;
		if(getLocations().get("locations." + getLocation(rQT) + ".require-discovery") != null) {
			/* Is discovery required? */
			if(getLocations().getBoolean("locations." + getLocation(rQT) + ".require-discovery") == true) {
				/* Yes, check to see if player has discovered it */
				List<Object> dList = (List<Object>) getLocations().getList("locations." + getLocation(rQT) + ".discovered-by");
				if(dList != null) {
					ListIterator<Object> dli = dList.listIterator();
					while(dli.hasNext()) {
						String dv = dli.next().toString();
						if(dv.equalsIgnoreCase(sender.getName())) {
							/* Player has discovered */
							discovered = true;
							break;
						}
					}
					if(discovered == false) {
						if(e) {
							sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
						}
						return false;
					}
				} else {
					if(e) {
						sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
					}
					return false;
				}
			} else if(getLocations().getBoolean("locations." + getLocation(rQT) + ".require-discovery") == false) {
				/* No, nothing to do here */
				
			} else {
				/* This QT's discovery setting is broken, use default instead */
				if(getConfig().getBoolean("require-discovery-by-default") == true) {
					/* Discovery required by default */
					List<Object> dList = (List<Object>) getLocations().getList("locations." + getLocation(rQT) + ".discovered-by");
					if(dList != null) {
						ListIterator<Object> dli = dList.listIterator();
						while(dli.hasNext()) {
							String dv = dli.next().toString();
							if(dv.equalsIgnoreCase(sender.getName())) {
								/* Player has discovered */
								discovered = true;
								break;
							}
						}
						if(discovered == false) {
							if(e) {
								sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
							}
							return false;
						}
					} else {
						if(e) {
							sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
						}
						return false;
					}
				}
			}
		} else {
			/* No discovery setting for QT, use default instead */
			if(getConfig().getBoolean("require-discovery-by-default") == true) {
				/* Discovery required by default */
				List<Object> dList = (List<Object>) getLocations().getList("locations." + getLocation(rQT) + ".discovered-by");
				if(dList != null) {
					ListIterator<Object> dli = dList.listIterator();
					while(dli.hasNext()) {
						String dv = dli.next().toString();
						if(dv.equalsIgnoreCase(sender.getName())) {
							/* Player has discovered */
							discovered = true;
							break;
						}
					}
					if(discovered == false) {
						if(e) {
							sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
						}
						return false;
					}
				} else {
					if(e) {
						sender.sendMessage("[" + ChatColor.RED + "Error" + ChatColor.WHITE + "] We do not know " + ChatColor.AQUA + rQT + ChatColor.WHITE + "!");
					}
					return false;
				}
			}
		}
		return true;
	}
	
	public boolean isFree(String qt) {
		boolean freeByDefault = false;
		boolean qtFree = false; 
		if(getConfig().get("free-by-default") != null) {
			freeByDefault = getConfig().getBoolean("free-by-default");
		} else {
			freeByDefault = false;
		}
		if(getLocations().get("locations." + qt + ".free") != null) {
			qtFree = getLocations().getBoolean("locations." + qt + ".free");
		} else {
			qtFree = freeByDefault;
		}
		return qtFree;
	}
	
	public int calculatePrice(String from, String to) {
		double xFrom = 0;
		double yFrom = 0;
		double zFrom = 0;
		String wFrom = getLocations().getString("locations." + from + ".world");
		double xTo = 0;
		double yTo = 0;
		double zTo = 0;
		String wTo = getLocations().getString("locations." + to + ".world");
		if(getLocations().getString("locations." + from + ".coords.dest") != null) {
			xFrom = getLocations().getDouble("locations." + from + ".coords.dest.x");
			yFrom = getLocations().getDouble("locations." + from + ".coords.dest.y");
			zFrom = getLocations().getDouble("locations." + from + ".coords.dest.z");      
		} else {
			xFrom = getLocations().getInt("locations." + from + ".coords.primary.x");
			yFrom = getLocations().getInt("locations." + from + ".coords.primary.y");
			zFrom = getLocations().getInt("locations." + from + ".coords.primary.z");
		}
		if(getLocations().getString("locations." + to + ".coords.dest") != null) {
			xTo = getLocations().getDouble("locations." + to + ".coords.dest.x");
			yTo = getLocations().getDouble("locations." + to + ".coords.dest.y");
			zTo = getLocations().getDouble("locations." + to + ".coords.dest.z");      
		} else {
			xTo = getLocations().getInt("locations." + to + ".coords.primary.x");
			yTo = getLocations().getInt("locations." + to + ".coords.primary.y");
			zTo = getLocations().getInt("locations." + to + ".coords.primary.z");
		}
		double xDiff = calculateDiff(xFrom, xTo); 
		double yDiff = calculateDiff(yFrom, yTo);
		double zDiff = calculateDiff(zFrom, zTo);
		double m = getConfig().getDouble("price-multiplier");
		if(!wFrom.equalsIgnoreCase(wTo)) {
			m = getConfig().getDouble("multiworld-multiplier");
		}
		return (int) Math.ceil((xDiff + yDiff + zDiff) * m);
	}
	
	public int calculatePrice(CommandSender sender, String to) {
		Player p = (Player)sender;
		Location coord = p.getLocation();
		double xFrom = coord.getX();
		double yFrom = coord.getY();
		double zFrom = coord.getZ();
		String wFrom = p.getWorld().getName();
		double xTo = 0;
		double yTo = 0;
		double zTo = 0;
		String wTo = getLocations().getString("locations." + to + ".world");
		if(getLocations().getString("locations." + to + ".coords.dest") != null) {
			xTo = getLocations().getDouble("locations." + to + ".coords.dest.x");
			yTo = getLocations().getDouble("locations." + to + ".coords.dest.y");
			zTo = getLocations().getDouble("locations." + to + ".coords.dest.z");      
		} else {
			xTo = getLocations().getInt("locations." + to + ".coords.primary.x");
			yTo = getLocations().getInt("locations." + to + ".coords.primary.y");
			zTo = getLocations().getInt("locations." + to + ".coords.primary.z");
		}
		double xDiff = calculateDiff(xFrom, xTo); 
		double yDiff = calculateDiff(yFrom, yTo);
		double zDiff = calculateDiff(zFrom, zTo);
		double m = getConfig().getDouble("price-multiplier");
		if(!wFrom.equalsIgnoreCase(wTo)) {
			m = getConfig().getDouble("multiworld-multiplier");
		}
		return (int) Math.ceil((xDiff + yDiff + zDiff) * m);
	}
	
	public double calculateDiff(double n1, double n2) {
		if(n1 >= 0 && n2 >= 0) {
			return Math.max(n1, n2) - Math.min(n1, n2);
		} else if(n1 < 0 && n2 < 0) {
			double x1 = -n1;
			double x2 = -n2;
			return Math.max(x1, x2) - Math.min(x1, x2);
		} else if(n1 < 0) {
			return -n1 + n2; 
		} else if(n2 < 0) {
			return n1 + -n2;
		} else {
			// Not sure what went wrong here...
			return 0;
		}
	}
	
	@SuppressWarnings("unchecked")
	public String getLocation(String locName) {
		List<Object> locList = (List<Object>) getLocations().getList("list");
		if(locList != null) {
			ListIterator<Object> li = locList.listIterator();
			while(li.hasNext()) {
				String v = li.next().toString();
				String vName = getLocations().getString("locations." + v + ".name");
				if(vName.equalsIgnoreCase(locName)) {
					return v;
				}
			}
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public String getLocationName(String locName) {
		List<Object> locList = (List<Object>) getLocations().getList("list");
		if(locList != null) {
			ListIterator<Object> li = locList.listIterator();
			while(li.hasNext()) {
				String v = li.next().toString();
				String vName = getLocations().getString("locations." + v + ".name");
				if(v.equalsIgnoreCase(locName)) {
					return vName;
				}
			}
		}
		return null;
	}

	public boolean playerHasPermission(Player p, String qt) {
		if(getLocations().get("locations." + getLocation(qt) + ".require-permissions") != null) {
			if((getLocations().getBoolean("locations." + getLocation(qt) + ".require-permissions") == true && p.hasPermission("qt.use." + getLocation(qt).toLowerCase())) || getLocations().getBoolean("locations." + getLocation(qt) + ".require-permissions") == false) {
				return true;
			} else {
				return false;
			}
		} else {
			if((getConfig().getBoolean("require-permissions-by-default") == true && p.hasPermission("qt.use." + getLocation(qt).toLowerCase())) || (getConfig().getBoolean("require-permissions-by-default") == false || getConfig().get("require-permissions-by-default") == null) || p.hasPermission("qt.use.*")) {
				return true;
			} else {
				return false;
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public boolean checkLocations(String locName) {
		List<Object> locList = (List<Object>) getLocations().getList("list");
		if(locList != null) {
			ListIterator<Object> li = locList.listIterator();
			while(li.hasNext()) {
				String v = li.next().toString();
				String vName = getLocations().getString("locations." + v + ".name");
				if(vName.equalsIgnoreCase(locName)) {
					return true;
				}
			}
			return false;
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public String checkPlayerQT(CommandSender sender) {
		String QT = "";
		List<Object> locList = (List<Object>) getLocations().getList("list");
		if(locList != null) {
			ListIterator<Object> li = locList.listIterator();
			while(li.hasNext()) {
				String v = li.next().toString();
				if((getLocations().get("locations." + v + ".enabled") == null && getConfig().getBoolean("enabled-by-default") == true) || getLocations().getBoolean("locations." + v + ".enabled") == true) {
					if(getLocations().get("locations." + v + ".type") != null) {
						if(getLocations().getString("locations." + v + ".type").equals("radius")) {
							/* Type set to radius */
							QT = checkRadiusQT(sender, v);
							if(QT != null) {
								return QT;
							}
						} else if(getLocations().getString("locations." + v + ".type").equals("cuboid")) {
							/* Type set to cuboid */
							if(getLocations().get("locations." + v + ".coords.secondary") == null && getLocations().get("locations." + v + ".coords.primary") != null ) {
								/* Cuboid invalid, treat as radius */
								QT = checkRadiusQT(sender, v);
								if(QT != null) {
									return QT;
								}
							} else if(getLocations().get("locations." + v + ".coords.secondary") != null && getLocations().get("locations." + v + ".coords.primary") != null ) {
								/* Cuboid is valid */
								QT = checkCuboidQT(sender, v);
								if(QT != null) {
									return QT;
								}
							} else {
								/* Seems broken... */
								sender.sendMessage(ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " is broken!");
							}
						} else {
							/* Invalid type for QT, work out type */
							if(getLocations().get("locations." + v + ".coords.secondary") == null && getLocations().get("locations." + v + ".coords.primary") != null ) {
								/* Defaulting to radius */
								QT = checkRadiusQT(sender, v);
								if(QT != null) {
									return QT;
								}
							} else if(getLocations().get("locations." + v + ".coords.secondary") != null && getLocations().get("locations." + v + ".coords.primary") != null ) {
								/* Defaulting to cuboid */
								QT = checkCuboidQT(sender, v);
								if(QT != null) {
									return QT;
								}
							} else {
								/* Seems broken... */
								sender.sendMessage(ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " is broken!");
							}
						}
					} else {
						/* No type set, work out type */
						if(getLocations().get("locations." + v + ".coords.secondary") == null && getLocations().get("locations." + v + ".coords.primary") != null ) {
							/* Defaulting to radius */
							QT = checkRadiusQT(sender, v);
							if(QT != null) {
								return QT;
							}
						} else if(getLocations().get("locations." + v + ".coords.secondary") != null && getLocations().get("locations." + v + ".coords.primary") != null ) {
							/* Defaulting to cuboid */
							QT = checkCuboidQT(sender, v);
							if(QT != null) {
								return QT;
							}
						} else {
							/* Seems broken... */
							sender.sendMessage(ChatColor.AQUA + getLocationName(v) + ChatColor.WHITE + " is broken!");
						}
					}
				}
			}
			return null;
		} else {
			// Empty list
			return null;
		}
	}
	
	public String checkRadiusQT(CommandSender sender, String v) {
		double radius = getConfig().getDouble("radius");
		if(getLocations().get("locations." + v + ".radius") != null) {
			radius = getLocations().getDouble("locations." + v + ".radius");
		}
		int yMod = getConfig().getInt("height-modifier");
		String w = getLocations().getString("locations." + v + ".world");
		int x = getLocations().getInt("locations." + v + ".coords.primary.x");
		int y = getLocations().getInt("locations." + v + ".coords.primary.y");
		int z = getLocations().getInt("locations." + v + ".coords.primary.z");
		Player p = (Player)sender;
		Location coord = p.getLocation();
		String pWorld = p.getWorld().getName();
		
		if((pWorld.equals(w)) && (coord.getX() >= x-radius && coord.getX() <= x+radius) && (coord.getZ() >= z-radius && coord.getZ() <= z+radius) && (coord.getY() >= y-yMod && coord.getY() <= y+yMod) && (playerHasPermission(p, v))) {
			return getLocationName(v);
		} else {
			return null;
		}
	}
	
	public String checkCuboidQT(CommandSender sender, String v) {
		int yMod = getConfig().getInt("height-modifier");
		String w = getLocations().getString("locations." + v + ".world");
		int x1 = getLocations().getInt("locations." + v + ".coords.primary.x");
		int y1 = getLocations().getInt("locations." + v + ".coords.primary.y");
		int z1 = getLocations().getInt("locations." + v + ".coords.primary.z");
		int x2 = getLocations().getInt("locations." + v + ".coords.secondary.x");
		int y2 = getLocations().getInt("locations." + v + ".coords.secondary.y");
		int z2 = getLocations().getInt("locations." + v + ".coords.secondary.z");
		Player p = (Player)sender;
		Location coord = p.getLocation();
		String pWorld = p.getWorld().getName();
		
		if((pWorld.equals(w)) && ((coord.getX() >= x1 && coord.getX() <= x2) || (coord.getX() <= x1 && coord.getX() >= x2)) && ((coord.getZ() >= z1 && coord.getZ() <= z2) || (coord.getZ() <= z1 && coord.getZ() >= z2)) && ((coord.getY() >= y1-yMod && coord.getY() <= y2+yMod) || (coord.getY() <= y1+yMod && coord.getY() >= y2-yMod)) && (playerHasPermission(p, v))) {
			return getLocationName(v);
		} else {
			return null;
		}
	}
	
	public void reloadLocations() {
		if(locationsFile == null) {
			locationsFile = new File(getDataFolder(), "locations.yml");
		}
		locations = YamlConfiguration.loadConfiguration(locationsFile);
		
		// Look for defaults in the jar
		InputStream defLocationsStream = getResource("locations.yml");
		if(defLocationsStream != null) {
			YamlConfiguration defLocations = YamlConfiguration.loadConfiguration(defLocationsStream);
			locations.setDefaults(defLocations);
		}
	}
	
	public FileConfiguration getLocations() {
		if(locations == null) {
			reloadLocations();
		}
		return locations;
	}
	
	public void saveLocations() {
		if(locations == null || locationsFile == null) {
			return;
		}
		try {
			locations.save(locationsFile);
		} catch (IOException ex) {
			Logger.getLogger(JavaPlugin.class.getName()).log(Level.SEVERE, "Could not save config to " + locationsFile, ex);
		}
	}
	
	public boolean containsLetter(String s) {
		if(s == null)
			return false;
		boolean letterFound = false;
		for(int i = 0; !letterFound && i < s.length(); i++)
		letterFound = letterFound
				|| Character.isLetter(s.charAt(i));
		return letterFound;
		} 
}