/**
 * Copyright (c) 2014 Digi International Inc.,
 * All rights not expressly granted are reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Digi International Inc. 11001 Bren Road East, Minnetonka, MN 55343
 * =======================================================================
 */
package com.digi.xbee.api;

/**
 * This class represents a DigiMesh Network.
 *  
 * <p>The network allows the discovery of remote devices in the same network 
 * as the local one and stores them.</p>
 * 
 * @see DigiPointNetwork
 * @see Raw802Network
 * @see XBeeNetwork
 * @see ZigBeeNetwork
 */
public class DigiMeshNetwork extends XBeeNetwork {

	/**
	 * Instantiates a new DigiMesh Network object.
	 * 
	 * @param device Local DigiMesh device to get the network from.
	 * 
	 * @throws NullPointerException if {@code device == null}.
	 * 
	 * @see DigiMeshDevice
	 */
	DigiMeshNetwork(DigiMeshDevice device) {
		super(device);
	}
}
