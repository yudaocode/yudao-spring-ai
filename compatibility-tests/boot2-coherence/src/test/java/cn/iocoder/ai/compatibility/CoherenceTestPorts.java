/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.iocoder.ai.compatibility;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Selects an available TCP/UDP cluster port outside the usual ephemeral range.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class CoherenceTestPorts {

	private CoherenceTestPorts() {
	}

	/**
	 * Pin the local and discovery ports before Coherence initialization.
	 * @throws IOException if no free local port can be selected
	 * @since 2.1.0
	 */
	public static void configure() throws IOException {
		InetAddress address = InetAddress.getByName("127.0.0.1");
		for (int attempt = 0; attempt < 128; attempt++) {
			int port = ThreadLocalRandom.current().nextInt(20000, 40000);
			try (ServerSocket tcp = new ServerSocket(port, 1, address);
					DatagramSocket udp = new DatagramSocket(port, address)) {
				String value = Integer.toString(port);
				System.setProperty("coherence.clusterport", value);
				System.setProperty("coherence.localport", value);
				System.setProperty("coherence.wka.port", value);
				System.setProperty("coherence.localport.adjust", "false");
				return;
			}
			catch (BindException ex) {
				// A listener owns one of the two protocols; try another port.
			}
		}
		throw new IOException("No free TCP/UDP port for the test Coherence cluster");
	}

}
