/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.fio.internal;

import org.openhab.binding.fio.FioBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;


/**
 * This class is responsible for parsing the binding configuration.
 * 
 * @author Ondrej Pecta
 * @since 1.9.0
 */
public class FioGenericBindingProvider extends AbstractGenericBindingProvider implements FioBindingProvider {

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "fio";
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
		if (!(item instanceof StringItem)) {
			throw new BindingConfigParseException("item '" + item.getName()
					+ "' is of type '" + item.getClass().getSimpleName()
					+ "', only StringItems are allowed - please check your *.items configuration");
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item, String bindingConfig) throws BindingConfigParseException {
		super.processBindingConfiguration(context, item, bindingConfig);
		FioBindingConfig config = new FioBindingConfig(bindingConfig);
		
		//parse bindingconfig here ...
		
		addBindingConfig(item, config);		
	}

	@Override
	public String getItemId(String itemName) {
		final FioBindingConfig config = (FioBindingConfig) this.bindingConfigs.get(itemName);
		return config != null ? (config.getId()) : null;
	}

	@Override
	public String getItemState(String itemName) {
		final FioBindingConfig config = (FioBindingConfig) this.bindingConfigs.get(itemName);
		return config != null ? (config.getState()) : null;
	}

	@Override
	public void setItemState(String itemName, String state) {
		final FioBindingConfig config = (FioBindingConfig) this.bindingConfigs.get(itemName);
		config.setState(state);
	}


	/**
	 * This is a helper class holding binding specific configuration details
	 * 
	 * @author Ondrej Pecta
	 * @since 1.9.0
	 */
	class FioBindingConfig implements BindingConfig {
		// put member fields here which holds the parsed values
		private String id;
		private String state;

		FioBindingConfig(String id)
		{
			this.id = id;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getState() {
			return state;
		}

		public void setState(String state) {
			this.state = state;
		}
	}
	
	
}
