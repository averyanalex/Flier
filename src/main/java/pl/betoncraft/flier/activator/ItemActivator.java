/**
 * Copyright (c) 2017 Jakub Sapalski
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 */
package pl.betoncraft.flier.activator;

import java.util.Optional;

import org.bukkit.configuration.ConfigurationSection;

import pl.betoncraft.flier.api.Flier;
import pl.betoncraft.flier.api.core.InGamePlayer;
import pl.betoncraft.flier.api.core.LoadingException;
import pl.betoncraft.flier.api.core.Owner;
import pl.betoncraft.flier.api.core.UsableItem;

/**
 * Checks if the player has a UsableItem.
 *
 * @author Jakub Sapalski
 */
public class ItemActivator extends DefaultActivator {
	
	private UsableItem item;

	public ItemActivator(ConfigurationSection section, Optional<Owner> owner) throws LoadingException {
		super(section, owner);
		if (!owner.isPresent()) {
			throw new LoadingException("Cannot use item activator without a player.");
		}
		item = Flier.getInstance().getItem(loader.loadString("item"), owner.get().getPlayer());
	}

	@Override
	public boolean isActive(InGamePlayer player, InGamePlayer source) {
		UsableItem found = player.getKit().getItems().stream()
				.filter(i -> i.isSimilar(this.item))
				.findFirst()
				.orElse(null);
		return found != null && found.getAmount() > 0;
	}

}
