/** This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://www.wtfpl.net/ for more details.
 */
package pl.betoncraft.flier.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.util.Pair;
import pl.betoncraft.flier.api.Engine;
import pl.betoncraft.flier.api.Item;
import pl.betoncraft.flier.api.ItemSet;
import pl.betoncraft.flier.api.PlayerClass;
import pl.betoncraft.flier.api.Wings;
import pl.betoncraft.flier.exception.LoadingException;

/**
 * Default implementation of PlayerClass.
 *
 * @author Jakub Sapalski
 */
public class DefaultClass implements PlayerClass {

	private String currentName;
	private Engine currentEngine;
	private Wings currentWings;
	private Map<Item, Integer> currentItems = new HashMap<>();
	
	private String storedName;
	private Engine storedEngine;
	private Wings storedWings;
	private Map<Item, Integer> storedItems = new HashMap<>();

	private final String defaultName;
	private final Engine defaultEngine;
	private final Wings defaultWings;
	private final Map<Item, Integer> defaultItems;
	
	public DefaultClass(List<ItemSet> sets) throws LoadingException {
		for (ItemSet set : sets) {
			if (set == null) {
				throw new LoadingException("One of the item sets is not defined.");
			}
			set.apply(this);
		}
		if (currentName == null) {
			throw new LoadingException("Name is not specified.");
		}
		defaultName = currentName;
		defaultEngine = currentEngine;
		defaultWings = currentWings;
		defaultItems = currentItems;
		reset();
	}

	private DefaultClass(String defName, Engine defEngine, Wings defWings, Map<Item, Integer> defItems) {
		defaultName = defName;
		defaultEngine = defEngine;
		defaultWings = defWings;
		defaultItems = defItems;
		reset();
	}

	@Override
	public String getCurrentName() {
		return currentName;
	}

	@Override
	public Engine getCurrentEngine() {
		return currentEngine;
	}

	@Override
	public Wings getCurrentWings() {
		return currentWings;
	}

	@Override
	public Map<Item, Integer> getCurrentItems() {
		return currentItems;
	}

	@Override
	public void setCurrentName(String name) {
		currentName = name;
	}

	@Override
	public void setCurrentEngine(Engine engine) {
		currentEngine = engine;
	}

	@Override
	public void setCurrentWings(Wings wings) {
		currentWings = wings;
	}

	@Override
	public void setCurrentItems(Map<Item, Integer> items) {
		currentItems = items;
	}

	@Override
	public String getStoredName() {
		return storedName;
	}

	@Override
	public Engine getStoredEngine() {
		return (Engine) storedEngine.replicate();
	}

	@Override
	public Wings getStoredWings() {
		return (Wings) storedWings.replicate();
	}

	@Override
	public Map<Item, Integer> getStoredItems() {
		return storedItems.entrySet().stream().map(
				entry -> new Pair<>((Item) entry.getKey().replicate(), entry.getValue())
			).collect(Collectors.toMap(pair -> pair.getKey(), pair -> pair.getValue()));
	}

	@Override
	public void setStoredName(String name) {
		storedName = name;
	}

	@Override
	public void setStoredEngine(Engine engine) {
		storedEngine = engine;
	}

	@Override
	public void setStoredWings(Wings wings) {
		storedWings = wings;
	}

	@Override
	public void setStoredItems(Map<Item, Integer> items) {
		storedItems = new HashMap<>(items);
	}

	@Override
	public String getDefaultName() {
		return defaultName;
	}

	@Override
	public Engine getDefaultEngine() {
		return (Engine) defaultEngine.replicate();
	}

	@Override
	public Wings getDefaultWings() {
		return (Wings) defaultWings.replicate();
	}

	@Override
	public Map<Item, Integer> getDefaultItems() {
		return defaultItems.entrySet().stream().collect(Collectors.toMap(
				entry -> (Item) entry.getKey().replicate(), entry -> entry.getValue()
		));
	}

	@Override
	public void save() {
		// stored
		storedName = getCurrentName();
		storedEngine = getCurrentEngine();
		storedWings = getCurrentWings();
		storedItems = getCurrentItems();
	}

	@Override
	public void load() {
		// current
		currentName = getStoredName();
		currentEngine = getStoredEngine();
		currentWings = getStoredWings();
		currentItems = getStoredItems();
	}

	@Override
	public void reset() {
		// stored
		storedName = getDefaultName();
		storedEngine = getDefaultEngine();
		storedWings = getDefaultWings();
		storedItems = getDefaultItems();
		// current
		currentName = getDefaultName();
		currentEngine = getDefaultEngine();
		currentWings = getDefaultWings();
		currentItems = getDefaultItems();
	}
	
	@Override
	public DefaultClass replicate() {
		return new DefaultClass(defaultName, defaultEngine, defaultWings, defaultItems);
	}

}