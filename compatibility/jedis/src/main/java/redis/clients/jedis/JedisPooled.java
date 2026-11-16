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

package redis.clients.jedis;

/**
 * @author 芋道源码
 */
public class JedisPooled extends org.springframework.ai.compat.jedis.internal.JedisPooled {

	public JedisPooled() {
		super();
	}

	public JedisPooled(String uri) {
		super(uri);
	}

	public JedisPooled(String host, int port) {
		super(host, port);
	}

	public JedisPooled(String host, int port, String user, String password) {
		super(host, port, user, password);
	}

	public JedisPooled(HostAndPort address) {
		this(address, DefaultJedisClientConfig.builder().build());
	}

	public JedisPooled(HostAndPort address, JedisClientConfig config) {
		super(new org.springframework.ai.compat.jedis.internal.HostAndPort(address.getHost(), address.getPort()),
				adapt(config));
	}

	private static org.springframework.ai.compat.jedis.internal.JedisClientConfig adapt(JedisClientConfig source) {
		java.util.Objects.requireNonNull(source);
		return new org.springframework.ai.compat.jedis.internal.JedisClientConfig() {

			public int getConnectionTimeoutMillis() {
				return source.getConnectionTimeoutMillis();
			}

			public int getSocketTimeoutMillis() {
				return source.getSocketTimeoutMillis();
			}

			public int getBlockingSocketTimeoutMillis() {
				return source.getBlockingSocketTimeoutMillis();
			}

			public String getUser() {
				return source.getUser();
			}

			public String getPassword() {
				return source.getPassword();
			}

			public java.util.function.Supplier<org.springframework.ai.compat.jedis.internal.RedisCredentials> getCredentialsProvider() {
				return () -> new org.springframework.ai.compat.jedis.internal.DefaultRedisCredentials(source.getUser(),
						source.getPassword());
			}

			public int getDatabase() {
				return source.getDatabase();
			}

			public String getClientName() {
				return source.getClientName();
			}

			public boolean isSsl() {
				return source.isSsl();
			}

			public javax.net.ssl.SSLSocketFactory getSslSocketFactory() {
				return source.getSslSocketFactory();
			}

			public javax.net.ssl.SSLParameters getSslParameters() {
				return source.getSslParameters();
			}

			public javax.net.ssl.HostnameVerifier getHostnameVerifier() {
				return source.getHostnameVerifier();
			}

			public org.springframework.ai.compat.jedis.internal.HostAndPortMapper getHostAndPortMapper() {
				HostAndPortMapper mapper = source.getHostAndPortMapper();
				return mapper == null ? null : address -> {
					HostAndPort mapped = mapper.getHostAndPort(new HostAndPort(address.getHost(), address.getPort()));
					return new org.springframework.ai.compat.jedis.internal.HostAndPort(mapped.getHost(),
							mapped.getPort());
				};
			}
		};
	}

}
