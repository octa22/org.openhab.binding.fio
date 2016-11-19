/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.fio.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.openhab.binding.fio.FioBindingProvider;

import org.apache.commons.lang.StringUtils;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;


/**
 * Implement this class if you are going create an actively polling service
 * like querying a Website/Device.
 * 
 * @author Ondrej Pecta
 * @since 1.9.0
 */
public class FioBinding extends AbstractActiveBinding<FioBindingProvider> {

	private static final Logger logger = 
		LoggerFactory.getLogger(FioBinding.class);

	/**
	 * The BundleContext. This is only valid when the bundle is ACTIVE. It is set in the activate()
	 * method and must not be accessed anymore once the deactivate() method was called or before activate()
	 * was called.
	 */
	private BundleContext bundleContext;
	private ItemRegistry itemRegistry;

	private String token;
    private final String HTTP_CONFLICT="HTTP_409";

	private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	//XPath
	private DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	private XPathFactory xPathfactory = XPathFactory.newInstance();
	private XPath xpath = xPathfactory.newXPath();

	/** 
	 * the refresh interval which is used to poll values from the Fio
	 * server (optional, defaults to 60000ms)
	 */
	private long refreshInterval = 60000;
	
	
	public FioBinding() {
	}
		
	
	/**
	 * Called by the SCR to activate the component with its configuration read from CAS
	 * 
	 * @param bundleContext BundleContext of the Bundle that defines this component
	 * @param configuration Configuration properties for this component obtained from the ConfigAdmin service
	 */
	public void activate(final BundleContext bundleContext, final Map<String, Object> configuration) {
		this.bundleContext = bundleContext;

		// the configuration is guaranteed not to be null, because the component definition has the
		// configuration-policy set to require. If set to 'optional' then the configuration may be null
		
			
		// to override the default refresh interval one has to add a 
		// parameter to openhab.cfg like <bindingName>:refresh=<intervalInMs>
		String refreshIntervalString = (String) configuration.get("refresh");
		if (StringUtils.isNotBlank(refreshIntervalString)) {
			refreshInterval = Long.parseLong(refreshIntervalString);
		}

		// read further config parameters here ...
		token = (String) configuration.get("token");
		listUnboundAccounts();
		setProperlyConfigured(StringUtils.isNotBlank(token));
	}

	private void listUnboundAccounts() {
		StringBuilder sb = new StringBuilder();
		String id = getAccountId();
		if (!id.isEmpty() && !isBound(id))
			sb.append("\t Account ").append("Id: ").append(id).append("\n");
		if (sb.length() > 0) {
			logger.info("Found unbound Fio account: \n" + sb.toString());
		}
	}

	private boolean isBound(String id) {
		for (final FioBindingProvider provider : providers) {
			for (final String name : provider.getItemNames()) {
				String type = provider.getItemId(name);
				if (type.equals(id))
					return true;
			}
		}
		return false;
	}

	/**
	 * Called by the SCR when the configuration of a binding has been changed through the ConfigAdmin service.
	 * @param configuration Updated configuration properties
	 */
	public void modified(final Map<String, Object> configuration) {
		// update the internal configuration accordingly
	}
	
	/**
	 * Called by the SCR to deactivate the component when either the configuration is removed or
	 * mandatory references are no longer satisfied or the component has simply been stopped.
	 * @param reason Reason code for the deactivation:<br>
	 * <ul>
	 * <li> 0 – Unspecified
     * <li> 1 – The component was disabled
     * <li> 2 – A reference became unsatisfied
     * <li> 3 – A configuration was changed
     * <li> 4 – A configuration was deleted
     * <li> 5 – The component was disposed
     * <li> 6 – The bundle was stopped
     * </ul>
	 */
	public void deactivate(final int reason) {
		this.bundleContext = null;
		// deallocate resources here that are no longer needed and 
		// should be reset when activating this binding again
	}

	public void setItemRegistry(ItemRegistry itemRegistry) {
		this.itemRegistry = itemRegistry;
	}

	public void unsetItemRegistry(ItemRegistry itemRegistry) {
		this.itemRegistry = null;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	protected String getName() {
		return "Fio Refresh Service";
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void execute() {
		// the frequently executed code (polling) goes here ...
		logger.debug("execute() method is called!");

		for (final FioBindingProvider provider : providers) {
			for (final String itemName : provider.getItemNames()) {
				String balance = getBalance(provider.getItemId(itemName));
                while( balance.equals(HTTP_CONFLICT)) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        logger.error(e.toString());
                    }
                    balance = getBalance(provider.getItemId(itemName));
                }

				State oldValue = null;
				try {
					oldValue = itemRegistry.getItem(itemName).getState();
					State newValue = new StringType(balance);
					if (!oldValue.equals(newValue)) {
						eventPublisher.postUpdate(itemName, newValue);
					}
				} catch (ItemNotFoundException e) {
					logger.error("Cannot find item " + itemName + " in item registry!");
				}
			}
		}
	}

	private String getBalance(String accountId) {

		try {
			String dt = dateFormat.format(new Date());
			URL cookieUrl = new URL("https://www.fio.cz/ib_api/rest/periods/" + token + "/" + dt + "/" + dt + "/transactions.xml");
			HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
			connection.setRequestMethod("GET");

			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(connection.getInputStream());

			XPathExpression expr = xpath.compile("/AccountStatement/Info[accountId='" + accountId + "']/closingBalance/text()");
			XPathExpression exprC = xpath.compile("/AccountStatement/Info[accountId='" + accountId + "']/currency/text()");

			String balance = formatMoney((String) expr.evaluate(doc, XPathConstants.STRING)) + " " + exprC.evaluate(doc, XPathConstants.STRING);
            //String balance = expr.evaluate(doc, XPathConstants.STRING) + " " + exprC.evaluate(doc, XPathConstants.STRING);
            logger.debug("Fio balance: " + balance);
			return balance;
		}
		catch (IOException ex)
		{
            if( ex.toString().contains("Server returned HTTP response code: 409"))
            {
                return HTTP_CONFLICT;
            }
            else {
                logger.error("Cannot get Fio balance: " + ex.toString());
                return "";
            }
		}
        catch (Exception ex)
        {
            logger.error("Cannot get Fio balance: " + ex.toString());
            return "";
        }

	}


	private String formatMoney(String balance) {
		int len = balance.length();
		int dec = balance.indexOf('.');
		String newBalance = "";
		if (dec >= 0) {
			len = dec;
			newBalance = balance.substring(dec);
		}

		int j = 0;
		for (int i = len - 1; i >= 0; i--) {
			char c = balance.charAt(i);
			newBalance = c + newBalance;
			if (++j % 3 == 0 && i > 0 && balance.charAt(i - 1) != '-')
				newBalance = " " + newBalance;
		}
		return newBalance;
	}

	private String getAccountId() {

		try {
			String dt = dateFormat.format(new Date());
			URL cookieUrl = new URL("https://www.fio.cz/ib_api/rest/periods/" + token + "/" + dt + "/" + dt + "/transactions.xml");
			HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
			connection.setRequestMethod("GET");

			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(connection.getInputStream());

			XPathExpression expr = xpath.compile("/AccountStatement/Info/accountId/text()");

			String account = (String) expr.evaluate(doc, XPathConstants.STRING);
			logger.debug("Fio balance: " + account);
			return account;
		}
		catch (Exception ex)
		{
			logger.error("Cannot get Fio account number: " + ex.toString());
			return "";
		}
	}

	private String readResponse(InputStream response) throws Exception {
		String line;
		StringBuilder body = new StringBuilder();
		BufferedReader reader = new BufferedReader(new InputStreamReader(response));

		while ((line = reader.readLine()) != null) {
			body.append(line).append("\n");
		}
		line = body.toString();
		logger.debug(line);
		return line;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void internalReceiveCommand(String itemName, Command command) {
		// the code being executed when a command was sent on the openHAB
		// event bus goes here. This method is only called if one of the 
		// BindingProviders provide a binding for the given 'itemName'.
		logger.debug("internalReceiveCommand({},{}) is called!", itemName, command);
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void internalReceiveUpdate(String itemName, State newState) {
		// the code being executed when a state was sent on the openHAB
		// event bus goes here. This method is only called if one of the 
		// BindingProviders provide a binding for the given 'itemName'.
		logger.debug("internalReceiveUpdate({},{}) is called!", itemName, newState);
	}

}
