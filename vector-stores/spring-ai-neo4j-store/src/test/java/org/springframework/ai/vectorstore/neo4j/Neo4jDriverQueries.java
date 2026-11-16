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

package org.springframework.ai.vectorstore.neo4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.summary.ResultSummary;

/**
 * Eager test queries on the Java 8-compatible driver. @author 芋道源码
 */
final class Neo4jDriverQueries {

	private Neo4jDriverQueries() {
	}

	static Query executableQuery(Driver driver, String query) {
		return new Query(driver, query, Collections.emptyMap());
	}

	/**
	 * @author 芋道源码
	 */
	static final class Query {

		private final Driver driver;

		private final String query;

		private final Map<String, Object> parameters;

		Query(Driver driver, String query, Map<String, Object> parameters) {
			this.driver = driver;
			this.query = query;
			this.parameters = Collections.unmodifiableMap(new HashMap<>(parameters));
		}

		Query withParameters(Map<String, Object> parameters) {
			return new Query(this.driver, this.query, parameters);
		}

		EagerResult execute() {
			try (Session session = this.driver.session()) {
				return session.writeTransaction(transaction -> {
					Result result = transaction.run(this.query, this.parameters);
					List<String> keys = result.keys();
					List<Record> records = result.list();
					ResultSummary summary = result.consume();
					return new EagerResult(records, keys, summary);
				});
			}
		}

	}

	/**
	 * @author 芋道源码
	 */
	static final class EagerResult {

		private final List<Record> records;

		private final List<String> keys;

		private final ResultSummary summary;

		EagerResult(List<Record> records, List<String> keys, ResultSummary summary) {
			this.records = Collections.unmodifiableList(new ArrayList<>(records));
			this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
			this.summary = summary;
		}

		List<Record> records() {
			return this.records;
		}

		List<String> keys() {
			return this.keys;
		}

		ResultSummary summary() {
			return this.summary;
		}

	}

}
